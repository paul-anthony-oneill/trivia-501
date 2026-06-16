-- V27: Materialize answers for 4 combined-stat football question templates.
--
-- The Java materializer processes ~100 round-trips per question; at 876 questions
-- over a Supabase connection this takes ~10 hours. This migration does the same
-- work in 2 SQL statements (~10-30 seconds).
--
-- Templates handled:
--   a56031a7-da53-495b-a8c2-226208367a31  goals_assists
--   c2b35e68-8c4f-4042-93c4-8475a265b502  goals_appearances
--   9ba50f5e-cef9-4c2a-8bf8-04807be78111  assists_appearances
--   9225b97f-a736-4729-aeec-86ea020a4768  goals_assists_appearances
--
-- Idempotent: ON CONFLICT DO UPDATE on answers; UPDATE (not INSERT) on questions.

-- ── 1. Insert / refresh answers ───────────────────────────────────────────────
--
-- For each (question, player) pair: sum the relevant stats across all seasons
-- in the question's competition + team since start_year, then derive validity flags.

WITH raw AS (
    SELECT
        q.id                                AS question_id,
        q.template_id,
        p.normalized_name                   AS answer_key,
        -- If two player rows share the same normalized_name, pick one display name.
        MIN(p.name)                         AS display_text,
        SUM(pss.goals)                      AS g,
        SUM(pss.assists)                    AS a,
        SUM(pss.appearances)                AS app
    FROM questions q
    JOIN player_season_stints pss
        ON  pss.team_id        = (q.template_params->>'team_id')::uuid
        AND pss.competition_id = (q.template_params->>'competition_id')::uuid
    JOIN seasons s
        ON  pss.season_id = s.id
        AND s.start_year >= COALESCE((q.template_params->>'start_year')::integer, 2000)
    JOIN players p
        ON  pss.player_id = p.id
    WHERE q.template_id IN (
        'a56031a7-da53-495b-a8c2-226208367a31',
        'c2b35e68-8c4f-4042-93c4-8475a265b502',
        '9ba50f5e-cef9-4c2a-8bf8-04807be78111',
        '9225b97f-a736-4729-aeec-86ea020a4768'
    )
    AND q.status != 'retired'
    -- Group by normalized_name (not p.id) so duplicate player rows are merged.
    GROUP BY q.id, q.template_id, p.normalized_name
),
computed AS (
    SELECT
        question_id,
        answer_key,
        display_text,
        CAST(
            CASE template_id
                WHEN 'a56031a7-da53-495b-a8c2-226208367a31' THEN g   + a
                WHEN 'c2b35e68-8c4f-4042-93c4-8475a265b502' THEN g   + app
                WHEN '9ba50f5e-cef9-4c2a-8bf8-04807be78111' THEN a   + app
                WHEN '9225b97f-a736-4729-aeec-86ea020a4768' THEN g   + a + app
            END
        AS INTEGER) AS score
    FROM raw
),
final_answers AS (
    SELECT
        question_id,
        answer_key,
        display_text,
        score,
        (score BETWEEN 1 AND 180
            AND score NOT IN (163, 166, 169, 172, 173, 175, 176, 178, 179)) AS is_valid_darts,
        (score > 180)                                                         AS is_bust
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


-- ── 2. Update question difficulty metrics and set status ──────────────────────
--
-- Counts are computed from the freshly-upserted answers rows.
-- Difficulty formula matches DifficultyCalculator.java exactly.

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
    WHERE q.template_id IN (
        'a56031a7-da53-495b-a8c2-226208367a31',
        'c2b35e68-8c4f-4042-93c4-8475a265b502',
        '9ba50f5e-cef9-4c2a-8bf8-04807be78111',
        '9225b97f-a736-4729-aeec-86ea020a4768'
    )
    GROUP BY a.question_id
),
with_viability AS (
    SELECT
        question_id,
        high_value_count,
        mid_range_count,
        checkout_count,
        total_valid_count,
        total_score_pool,
        (total_score_pool >= 501 AND total_valid_count >= 15) AS viable
    FROM zone_counts
),
with_ease AS (
    SELECT
        *,
        LEAST(high_value_count::float / 25.0, 1.0) * 0.50
      + LEAST(mid_range_count::float  / 40.0, 1.0) * 0.30
      + LEAST(checkout_count::float   / 12.0, 1.0) * 0.20  AS ease_score
    FROM with_viability
),
with_base AS (
    SELECT
        *,
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


-- ── 3. Ensure footballer entities exist for autocomplete ──────────────────────
--
-- Players in the combined-stat answer pool should already be registered from
-- single-stat template materialization, but this upsert is idempotent.

INSERT INTO entities (entity_type, display_name, normalized_name)
SELECT DISTINCT
    'footballer',
    p.name,
    unaccent(lower(p.name))
FROM answers a
JOIN questions q  ON a.question_id = q.id
JOIN players p    ON p.normalized_name = a.answer_key
WHERE q.template_id IN (
    'a56031a7-da53-495b-a8c2-226208367a31',
    'c2b35e68-8c4f-4042-93c4-8475a265b502',
    '9ba50f5e-cef9-4c2a-8bf8-04807be78111',
    '9225b97f-a736-4729-aeec-86ea020a4768'
)
ON CONFLICT (entity_type, normalized_name) DO NOTHING;
