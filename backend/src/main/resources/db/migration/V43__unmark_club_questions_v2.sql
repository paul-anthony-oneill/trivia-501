-- V43: Un-mark club-scope questions from daily challenge pool (v2).
-- V42 used q_scope = 'club' but many club questions have q_scope = NULL
-- with q_club set instead. This catches both cases.
UPDATE questions
   SET suitable_for_daily = false
 WHERE category_id = (SELECT id FROM categories WHERE slug = 'football')
   AND suitable_for_daily = true
   AND (q_scope = 'club' OR q_club IS NOT NULL);
