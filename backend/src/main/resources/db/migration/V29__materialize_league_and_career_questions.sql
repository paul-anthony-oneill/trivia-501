-- V29: Materialize answers for draft league-scope and career football questions.
--
-- Covers two materializer types:
--
--   football.player_competition_metric_since  — league-wide questions, e.g.
--     "Goals in the Premier League since 2000"
--     "Goals + Assists in La Liga since 2000"
--
--   football.player_career_metric  — career totals, e.g.
--     "Career goals in top-flight football since 2000"
--
-- Both sets were in draft status after V28 fixed their q_scope metadata.
-- This migration materialises answers and updates difficulty metrics for all of them.
-- Idempotent: ON CONFLICT DO UPDATE on answers; UPDATE (not INSERT) on questions.


-- ══════════════════════════════════════════════════════════════════════════════
-- PART A: football.player_competition_metric_since
-- One question per (competition, metric). Players aggregated across all clubs
-- in the competition since start_year.
-- ══════════════════════════════════════════════════════════════════════════════

-- ── A1. Insert / refresh answers ─────────────────────────────────────────────
WITH raw AS (
    SELECT
        q.id                                AS question_id,
        q.metric_key,
        p.normalized_name                   AS answer_key,
        MIN(p.name)                         AS display_text,
        SUM(pss.goals)                      AS g,
        SUM(pss.assists)                    AS a,
        SUM(pss.appearances)                AS app,
        SUM(pss.sub_appearances)            AS sub_app
    FROM questions q
    JOIN question_templates qt
        ON q.template_id = qt.id
       AND qt.materializer_key = 'football.player_competition_metric_since'
    JOIN player_season_stints pss
        ON pss.competition_id = (q.template_params->>'competition_id')::uuid
    JOIN seasons s
        ON pss.season_id = s.id
       AND s.start_year >= COALESCE((q.template_params->>'start_year')::integer, 2000)
    JOIN players p
        ON pss.player_id = p.id
    WHERE q.status = 'draft'
    GROUP BY q.id, q.metric_key, p.normalized_name
),
computed AS (
    SELECT
        question_id,
        answer_key,
        display_text,
        CAST(
            CASE metric_key
                WHEN 'goals'           THEN g
                WHEN 'assists'         THEN a
                WHEN 'appearances'     THEN app
                WHEN 'goals_assists'   THEN g + a
                WHEN 'sub_appearances' THEN sub_app
            END
        AS INTEGER) AS score
    FROM raw
    WHERE metric_key IN ('goals','assists','appearances','goals_assists','sub_appearances')
),
final_answers AS (
    SELECT
        question_id,
        answer_key,
        display_text,
        score,
        (score BETWEEN 1 AND 180
            AND score NOT IN (163,166,169,172,173,175,176,178,179)) AS is_valid_darts,
        (score > 180)                                                AS is_bust
    FROM computed
    WHERE score > 0
)
INSERT INTO answers
    (id, question_id, answer_key, display_text, score, is_valid_darts, is_bust, metadata, created_at, materialized_at)
SELECT
    gen_random_uuid(),
    question_id,
    answer_key,
    display_text,
    score,
    is_valid_darts,
    is_bust,
    '{}'::jsonb,
    NOW(),
    NOW()
FROM final_answers
ON CONFLICT (question_id, answer_key) DO UPDATE SET
    score           = EXCLUDED.score,
    is_valid_darts  = EXCLUDED.is_valid_darts,
    is_bust         = EXCLUDED.is_bust,
    materialized_at = NOW();

-- ── A2. Update difficulty metrics and activate ────────────────────────────────
WITH zone_counts AS (
    SELECT
        a.question_id,
        COUNT(*) FILTER (WHERE a.is_valid_darts AND a.score BETWEEN 100 AND 180) AS high_value_count,
        COUNT(*) FILTER (WHERE a.is_valid_darts AND a.score BETWEEN 20  AND 99)  AS mid_range_count,
        COUNT(*) FILTER (WHERE a.is_valid_darts AND a.score BETWEEN 1   AND 19)  AS checkout_count,
        COUNT(*) FILTER (WHERE a.is_valid_darts)                                  AS total_valid_count,
        COALESCE(SUM(a.score) FILTER (WHERE a.is_valid_darts), 0)                AS total_score_pool
    FROM answers a
    JOIN questions q ON a.question_id = q.id
    JOIN question_templates qt ON q.template_id = qt.id
    WHERE qt.materializer_key = 'football.player_competition_metric_since'
      AND q.status = 'draft'
    GROUP BY a.question_id
),
with_viability AS (
    SELECT *,
        (total_score_pool >= 501 AND total_valid_count >= 15) AS viable
    FROM zone_counts
),
with_ease AS (
    SELECT *,
        LEAST(high_value_count::float / 25.0, 1.0) * 0.50
      + LEAST(mid_range_count::float  / 40.0, 1.0) * 0.30
      + LEAST(checkout_count::float   / 12.0, 1.0) * 0.20  AS ease_score
    FROM with_viability
),
with_base AS (
    SELECT *,
        CASE WHEN checkout_count = 0
             THEN GREATEST(10.0 * (1.0 - ease_score), 7.0)
             ELSE 10.0 * (1.0 - ease_score)
        END AS base_difficulty
    FROM with_ease
),
final_metrics AS (
    SELECT
        question_id,
        high_value_count,
        mid_range_count,
        checkout_count,
        total_valid_count,
        total_score_pool,
        viable,
        GREATEST(0.0, LEAST(10.0,
            base_difficulty - LEAST(total_valid_count::float / 200.0, 1.0) * 1.5
        )) AS difficulty_score
    FROM with_base
)
UPDATE questions q SET
    high_value_count           = f.high_value_count,
    mid_range_count            = f.mid_range_count,
    checkout_count             = f.checkout_count,
    total_valid_count          = f.total_valid_count,
    total_score_pool           = f.total_score_pool,
    single_question_viable     = f.viable,
    viability_exclusion_reason = CASE
        WHEN f.viable THEN NULL
        ELSE CONCAT_WS('; ',
            CASE WHEN f.total_score_pool < 501
                 THEN 'insufficient_score_pool: ' || f.total_score_pool || ' < 501'
                 ELSE NULL END,
            CASE WHEN f.total_valid_count < 15
                 THEN 'insufficient_answer_count: ' || f.total_valid_count || ' < 15'
                 ELSE NULL END
        )
    END,
    difficulty_score           = f.difficulty_score,
    status                     = CASE WHEN f.viable THEN 'active' ELSE 'excluded' END,
    updated_at                 = NOW()
FROM final_metrics f
WHERE q.id = f.question_id;

-- ── A3. Ensure footballer entities exist for autocomplete ─────────────────────
INSERT INTO entities (entity_type, display_name, normalized_name)
SELECT DISTINCT
    'footballer',
    p.name,
    unaccent(lower(p.name))
FROM answers a
JOIN questions q  ON a.question_id = q.id
JOIN question_templates qt ON q.template_id = qt.id
JOIN players p    ON p.normalized_name = a.answer_key
WHERE qt.materializer_key = 'football.player_competition_metric_since'
ON CONFLICT (entity_type, normalized_name) DO NOTHING;


-- ══════════════════════════════════════════════════════════════════════════════
-- PART B: football.player_career_metric
-- One question per metric. Players aggregated across ALL tier-1 domestic leagues
-- since start_year.
-- ══════════════════════════════════════════════════════════════════════════════

-- ── B1. Insert / refresh answers ─────────────────────────────────────────────
WITH raw AS (
    SELECT
        q.id                                AS question_id,
        q.metric_key,
        p.normalized_name                   AS answer_key,
        MIN(p.name)                         AS display_text,
        SUM(pss.goals)                      AS g,
        SUM(pss.assists)                    AS a,
        SUM(pss.appearances)                AS app,
        SUM(pss.sub_appearances)            AS sub_app
    FROM questions q
    JOIN question_templates qt
        ON q.template_id = qt.id
       AND qt.materializer_key = 'football.player_career_metric'
    JOIN player_season_stints pss
        ON pss.competition_id IN (
            SELECT id FROM competitions
            WHERE competition_type = 'domestic_league'
              AND tier = 1
        )
    JOIN seasons s
        ON pss.season_id = s.id
       AND s.start_year >= COALESCE((q.template_params->>'start_year')::integer, 2000)
    JOIN players p
        ON pss.player_id = p.id
    WHERE q.status = 'draft'
    GROUP BY q.id, q.metric_key, p.normalized_name
),
computed AS (
    SELECT
        question_id,
        answer_key,
        display_text,
        CAST(
            CASE metric_key
                WHEN 'goals'           THEN g
                WHEN 'assists'         THEN a
                WHEN 'appearances'     THEN app
                WHEN 'goals_assists'   THEN g + a
                WHEN 'sub_appearances' THEN sub_app
            END
        AS INTEGER) AS score
    FROM raw
    WHERE metric_key IN ('goals','assists','appearances','goals_assists','sub_appearances')
),
final_answers AS (
    SELECT
        question_id,
        answer_key,
        display_text,
        score,
        (score BETWEEN 1 AND 180
            AND score NOT IN (163,166,169,172,173,175,176,178,179)) AS is_valid_darts,
        (score > 180)                                                AS is_bust
    FROM computed
    WHERE score > 0
)
INSERT INTO answers
    (id, question_id, answer_key, display_text, score, is_valid_darts, is_bust, metadata, created_at, materialized_at)
SELECT
    gen_random_uuid(),
    question_id,
    answer_key,
    display_text,
    score,
    is_valid_darts,
    is_bust,
    '{}'::jsonb,
    NOW(),
    NOW()
FROM final_answers
ON CONFLICT (question_id, answer_key) DO UPDATE SET
    score           = EXCLUDED.score,
    is_valid_darts  = EXCLUDED.is_valid_darts,
    is_bust         = EXCLUDED.is_bust,
    materialized_at = NOW();

-- ── B2. Update difficulty metrics and activate ────────────────────────────────
WITH zone_counts AS (
    SELECT
        a.question_id,
        COUNT(*) FILTER (WHERE a.is_valid_darts AND a.score BETWEEN 100 AND 180) AS high_value_count,
        COUNT(*) FILTER (WHERE a.is_valid_darts AND a.score BETWEEN 20  AND 99)  AS mid_range_count,
        COUNT(*) FILTER (WHERE a.is_valid_darts AND a.score BETWEEN 1   AND 19)  AS checkout_count,
        COUNT(*) FILTER (WHERE a.is_valid_darts)                                  AS total_valid_count,
        COALESCE(SUM(a.score) FILTER (WHERE a.is_valid_darts), 0)                AS total_score_pool
    FROM answers a
    JOIN questions q ON a.question_id = q.id
    JOIN question_templates qt ON q.template_id = qt.id
    WHERE qt.materializer_key = 'football.player_career_metric'
      AND q.status = 'draft'
    GROUP BY a.question_id
),
with_viability AS (
    SELECT *,
        (total_score_pool >= 501 AND total_valid_count >= 15) AS viable
    FROM zone_counts
),
with_ease AS (
    SELECT *,
        LEAST(high_value_count::float / 25.0, 1.0) * 0.50
      + LEAST(mid_range_count::float  / 40.0, 1.0) * 0.30
      + LEAST(checkout_count::float   / 12.0, 1.0) * 0.20  AS ease_score
    FROM with_viability
),
with_base AS (
    SELECT *,
        CASE WHEN checkout_count = 0
             THEN GREATEST(10.0 * (1.0 - ease_score), 7.0)
             ELSE 10.0 * (1.0 - ease_score)
        END AS base_difficulty
    FROM with_ease
),
final_metrics AS (
    SELECT
        question_id,
        high_value_count,
        mid_range_count,
        checkout_count,
        total_valid_count,
        total_score_pool,
        viable,
        GREATEST(0.0, LEAST(10.0,
            base_difficulty - LEAST(total_valid_count::float / 200.0, 1.0) * 1.5
        )) AS difficulty_score
    FROM with_base
)
UPDATE questions q SET
    high_value_count           = f.high_value_count,
    mid_range_count            = f.mid_range_count,
    checkout_count             = f.checkout_count,
    total_valid_count          = f.total_valid_count,
    total_score_pool           = f.total_score_pool,
    single_question_viable     = f.viable,
    viability_exclusion_reason = CASE
        WHEN f.viable THEN NULL
        ELSE CONCAT_WS('; ',
            CASE WHEN f.total_score_pool < 501
                 THEN 'insufficient_score_pool: ' || f.total_score_pool || ' < 501'
                 ELSE NULL END,
            CASE WHEN f.total_valid_count < 15
                 THEN 'insufficient_answer_count: ' || f.total_valid_count || ' < 15'
                 ELSE NULL END
        )
    END,
    difficulty_score           = f.difficulty_score,
    status                     = CASE WHEN f.viable THEN 'active' ELSE 'excluded' END,
    updated_at                 = NOW()
FROM final_metrics f
WHERE q.id = f.question_id;

-- ── B3. Ensure footballer entities exist for autocomplete ─────────────────────
INSERT INTO entities (entity_type, display_name, normalized_name)
SELECT DISTINCT
    'footballer',
    p.name,
    unaccent(lower(p.name))
FROM answers a
JOIN questions q  ON a.question_id = q.id
JOIN question_templates qt ON q.template_id = qt.id
JOIN players p    ON p.normalized_name = a.answer_key
WHERE qt.materializer_key = 'football.player_career_metric'
ON CONFLICT (entity_type, normalized_name) DO NOTHING;
