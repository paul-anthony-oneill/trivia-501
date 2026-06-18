-- Mark all league-scope football questions as suitable for daily challenges.
-- Club-scope questions are hand-selected by admin (not auto-marked).
UPDATE questions
   SET suitable_for_daily = true
 WHERE q_scope = 'league'
   AND status = 'active'
   AND suitable_for_daily = false;
