-- ============================================================================
-- Project: ScoreVault - Query File
-- Course: Integrated Full-Stack Project (RDBMS Component)
-- Authors: Raj Singh (25LBCS3205) & Prince (25LBCS3005)
-- Demonstrating: Multi-table Joins, Aggregations, Window Functions,
-- Conflict-of-Interest Validation, and Audit Tracing
-- ============================================================================

-- ----------------------------------------------------------------------------
-- QUERY 1: Round Leaderboard with 6-Table Join & Window Ranking Function
-- Joins: rounds, events, teams, users, judge_assignments, scores
-- ----------------------------------------------------------------------------
SELECT 
    r.round_id,
    e.title AS competition_name,
    r.round_name,
    t.team_id,
    t.team_name,
    u_lead.full_name AS team_lead,
    u_lead.affiliation AS institution,
    COUNT(DISTINCT ja.judge_user_id) AS total_judges_evaluated,
    CAST(SUM(s.weighted_score) / NULLIF(COUNT(DISTINCT ja.judge_user_id), 0) AS DECIMAL(6,3)) AS aggregate_weighted_score,
    DENSE_RANK() OVER (
        PARTITION BY r.round_id 
        ORDER BY (SUM(s.weighted_score) / NULLIF(COUNT(DISTINCT ja.judge_user_id), 0)) DESC
    ) AS competition_rank
FROM rounds r
INNER JOIN events e ON r.event_id = e.event_id
INNER JOIN teams t ON e.event_id = t.event_id
INNER JOIN users u_lead ON t.lead_user_id = u_lead.user_id
INNER JOIN judge_assignments ja ON r.round_id = ja.round_id AND t.team_id = ja.team_id
INNER JOIN scores s ON ja.assignment_id = s.assignment_id
WHERE ja.status = 'SUBMITTED' AND s.is_locked = 1
GROUP BY 
    r.round_id, e.title, r.round_name, t.team_id, t.team_name, 
    u_lead.full_name, u_lead.affiliation
ORDER BY aggregate_weighted_score DESC;

-- ----------------------------------------------------------------------------
-- QUERY 2: Conflict-of-Interest (COI) Detection Query
-- Used by Spring Boot backend before allowing judge assignment.
-- Checks if target judge shares an affiliation with ANY member of the team.
-- ----------------------------------------------------------------------------
DECLARE @target_judge_id INT = 3;  -- Dr. Arvind Sharma (IIT Delhi)
DECLARE @target_team_id INT = 1;   -- Team AlphaBits (BITS Pilani)

SELECT 
    CASE 
        WHEN COUNT(*) > 0 THEN 1 -- CONFLICT DETECTED
        ELSE 0                   -- SAFE TO ASSIGN
    END AS has_conflict,
    j.user_id AS judge_id,
    j.full_name AS judge_name,
    j.affiliation AS judge_affiliation,
    STRING_AGG(CONCAT(u.full_name, ' (', u.affiliation, ')'), ', ') AS conflicting_team_members
FROM users j
CROSS JOIN teams t
INNER JOIN team_members tm ON t.team_id = tm.team_id
INNER JOIN users u ON tm.user_id = u.user_id
WHERE j.user_id = @target_judge_id
  AND t.team_id = @target_team_id
  AND LOWER(LTRIM(RTRIM(j.affiliation))) = LOWER(LTRIM(RTRIM(u.affiliation)))
GROUP BY j.user_id, j.full_name, j.affiliation;

-- ----------------------------------------------------------------------------
-- QUERY 3: Detailed Criterion-by-Criterion Breakdown for an Entry
-- Joins: teams, judge_assignments, users (judge), scores, criteria
-- ----------------------------------------------------------------------------
SELECT 
    t.team_name,
    j.full_name AS judge_name,
    c.name AS criterion_name,
    c.weightage_percent,
    s.raw_score,
    c.max_score,
    s.weighted_score,
    s.is_locked,
    s.feedback_json_url
FROM teams t
INNER JOIN judge_assignments ja ON t.team_id = ja.team_id
INNER JOIN users j ON ja.judge_user_id = j.user_id
INNER JOIN scores s ON ja.assignment_id = s.assignment_id
INNER JOIN criteria c ON s.criterion_id = c.criterion_id
WHERE t.team_id = 1
ORDER BY c.criterion_id ASC;

-- ----------------------------------------------------------------------------
-- QUERY 4: Judge Scoring Variance & Statistical Outlier Analysis (Power BI View)
-- Identifies judges scoring significantly above or below the event average
-- ----------------------------------------------------------------------------
SELECT 
    j.user_id AS judge_id,
    j.full_name AS judge_name,
    j.affiliation AS judge_institution,
    ec.name AS category_name,
    COUNT(s.score_id) AS criteria_scored_count,
    CAST(AVG(s.raw_score) AS DECIMAL(5,2)) AS average_raw_score,
    CAST(STDEV(s.raw_score) AS DECIMAL(5,2)) AS score_standard_deviation,
    CAST(MIN(s.raw_score) AS DECIMAL(5,2)) AS lowest_score_given,
    CAST(MAX(s.raw_score) AS DECIMAL(5,2)) AS highest_score_given
FROM users j
INNER JOIN judge_assignments ja ON j.user_id = ja.judge_user_id
INNER JOIN rounds r ON ja.round_id = r.round_id
INNER JOIN events e ON r.event_id = e.event_id
INNER JOIN event_categories ec ON e.category_id = ec.category_id
INNER JOIN scores s ON ja.assignment_id = s.assignment_id
WHERE s.is_locked = 1
GROUP BY j.user_id, j.full_name, j.affiliation, ec.name;

-- ----------------------------------------------------------------------------
-- QUERY 5: Complete Audit Trail Query for Tamper Verification
-- Retrieves temporal history of actions on a specific entity
-- ----------------------------------------------------------------------------
SELECT 
    al.log_id,
    al.entity_name,
    al.entity_id,
    al.action_type,
    al.old_value,
    al.new_value,
    u.full_name AS operator_name,
    u.email AS operator_email,
    al.timestamp,
    al.ip_address
FROM audit_log al
LEFT JOIN users u ON al.performed_by_user_id = u.user_id
ORDER BY al.timestamp DESC;
GO
