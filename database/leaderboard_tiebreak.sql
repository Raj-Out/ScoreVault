/* ============================================================================
   ScoreVault: leaderboard_tiebreak.sql
   Target : Azure SQL Database (T-SQL)
   Run    : schema.sql -> integrity_triggers.sql -> leaderboard_tiebreak.sql
            -> seed_data.sql -> integrity_tests.sql
   Safe   : CREATE OR ALTER, so it can be re-run. It REPLACES the view that
            schema.sql creates, keeping every existing column name and adding
            two columns at the end.

   TIE-BREAK RULE (applies per round, applied in this order)
     1. Higher final_weighted_average_score (rounded to 2 decimals, as shown)
     2. Higher average score on the rubric's PRIORITY criterion
        (the criterion with the highest weight in the rubric; if two criteria
         have the same weight, the one listed first)
     3. Earlier first submission time
   Teams that are equal on all three share the same rank (DENSE_RANK).
   Example: Innovation (30%) in a hackathon rubric, Legal Argument (35%) in a
   moot court rubric.
   ============================================================================ */

CREATE OR ALTER VIEW vw_round_leaderboard
AS
WITH team_totals AS (
    SELECT
        r.round_id,
        r.event_id,
        e.title AS event_title,
        e.template_id,
        r.round_number,
        r.round_name,
        t.team_id,
        t.team_name,
        u_lead.full_name   AS team_lead,
        u_lead.affiliation AS institution,
        COUNT(DISTINCT ja.judge_user_id) AS judges_evaluated_count,
        CAST(SUM(s.weighted_score) / NULLIF(COUNT(DISTINCT ja.judge_user_id), 0) AS DECIMAL(6,2))
            AS final_weighted_average_score
    FROM rounds r
    INNER JOIN events e            ON r.event_id = e.event_id
    INNER JOIN teams t             ON e.event_id = t.event_id
    INNER JOIN users u_lead        ON t.lead_user_id = u_lead.user_id
    INNER JOIN judge_assignments ja ON r.round_id = ja.round_id AND t.team_id = ja.team_id
    INNER JOIN scores s            ON ja.assignment_id = s.assignment_id
    WHERE ja.status = 'SUBMITTED' AND s.is_locked = 1
    GROUP BY r.round_id, r.event_id, e.title, e.template_id, r.round_number, r.round_name,
             t.team_id, t.team_name, u_lead.full_name, u_lead.affiliation
),
priority_criterion AS (          -- one row per rubric: its highest-weight criterion
    SELECT template_id, criterion_id
    FROM (SELECT template_id,
                 criterion_id,
                 ROW_NUMBER() OVER (PARTITION BY template_id
                                    ORDER BY weightage_percent DESC, criterion_id ASC) AS rn
          FROM criteria) ranked
    WHERE rn = 1
),
priority_scores AS (             -- each team's average score on that criterion
    SELECT ja.round_id,
           ja.team_id,
           CAST(SUM(s.weighted_score) / NULLIF(COUNT(DISTINCT ja.judge_user_id), 0) AS DECIMAL(6,2))
               AS priority_criterion_score
    FROM judge_assignments ja
    INNER JOIN rounds r             ON r.round_id = ja.round_id
    INNER JOIN events e             ON e.event_id = r.event_id
    INNER JOIN priority_criterion pc ON pc.template_id = e.template_id
    INNER JOIN scores s             ON s.assignment_id = ja.assignment_id
                                   AND s.criterion_id  = pc.criterion_id
    WHERE ja.status = 'SUBMITTED' AND s.is_locked = 1
    GROUP BY ja.round_id, ja.team_id
),
first_submission AS (
    SELECT round_id, team_id, MIN(submitted_at) AS first_submitted_at
    FROM submissions
    GROUP BY round_id, team_id
)
SELECT
    tt.round_id,
    tt.event_id,
    tt.event_title,
    tt.round_number,
    tt.round_name,
    tt.team_id,
    tt.team_name,
    tt.team_lead,
    tt.institution,
    tt.judges_evaluated_count,
    tt.final_weighted_average_score,
    DENSE_RANK() OVER (
        PARTITION BY tt.round_id
        ORDER BY tt.final_weighted_average_score DESC,
                 ISNULL(ps.priority_criterion_score, 0) DESC,
                 ISNULL(fs.first_submitted_at, CAST('9999-12-31' AS DATETIME2)) ASC
    ) AS leaderboard_rank,
    ps.priority_criterion_score,
    fs.first_submitted_at
FROM team_totals tt
LEFT JOIN priority_scores ps ON ps.round_id = tt.round_id AND ps.team_id = tt.team_id
LEFT JOIN first_submission fs ON fs.round_id = tt.round_id AND fs.team_id = tt.team_id;
GO


/* ----------------------------------------------------------------------------
   CHECK 1: the ranking rule on sample numbers (needs no table data)
   Expected result:
     Team D   rank 1   (highest score)
     Team C   rank 2   (same score as A, B; better priority score than A; earlier than B)
     Team E   rank 2   (identical to C on all three, so it shares the rank)
     Team B   rank 3   (same priority score as C, later submission)
     Team A   rank 4   (lower priority score)
   ---------------------------------------------------------------------------- */
SELECT team, final_score, priority_score, first_submitted,
       DENSE_RANK() OVER (ORDER BY final_score DESC, priority_score DESC, first_submitted ASC) AS leaderboard_rank
FROM (VALUES
        ('Team A', CAST(85.00 AS DECIMAL(6,2)), CAST(27.00 AS DECIMAL(6,2)), CAST('2026-11-01 10:00' AS DATETIME2)),
        ('Team B', 85.00, 28.50, '2026-11-01 10:30'),
        ('Team C', 85.00, 28.50, '2026-11-01 09:00'),
        ('Team D', 90.00, 20.00, '2026-11-01 11:00'),
        ('Team E', 85.00, 28.50, '2026-11-01 09:00')
     ) v(team, final_score, priority_score, first_submitted)
ORDER BY leaderboard_rank, team;
GO

/* ----------------------------------------------------------------------------
   CHECK 2: run this AFTER seed_data.sql has loaded (it is empty before that)
   Expected: one row, the seeded team, score 90.25, priority_criterion_score 28.50,
   leaderboard_rank 1.

   SELECT round_id, team_name, final_weighted_average_score,
          priority_criterion_score, first_submitted_at, leaderboard_rank
   FROM vw_round_leaderboard
   ORDER BY round_id, leaderboard_rank;
   ---------------------------------------------------------------------------- */
