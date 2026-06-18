-- V42: Un-mark club-scope questions from daily challenge pool.
-- League-scope questions stay marked (V41). Club questions will be
-- hand-selected by admin on a case-by-case basis.
UPDATE questions
   SET suitable_for_daily = false
 WHERE q_scope = 'club'
   AND suitable_for_daily = true;
