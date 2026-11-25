/* ============================================================================
   ScoreVault: demo_setup.sql
   One-off demo preparation. Run as scoreadmin (server admin) on scorevault-db,
   because the app account scorevault_app has no DELETE right on submissions.
   Safe to re-run. Looks rows up by name/email, so it does not depend on ids.
   ============================================================================ */

-- 1. Remove the personal test upload from the participant testing.
DELETE FROM submissions WHERE file_name = 'OS_Unit-1to5_Notes_Handwritten.pdf';
-- (Also delete the stored file from the backend uploads/ folder on the machine
--  or App Service that received it. The SQL row and the file are separate.)
GO

-- 2. Make Round 2 ("Grand Finale Live Demo") ready for live scoring.
--    Seed data has Round 1 in EVALUATION; use the same state for Round 2.
UPDATE rounds SET status = 'EVALUATION'
WHERE round_name = 'Grand Finale Live Demo' AND status = 'SCHEDULED';
GO

-- 3. Assign the technical judge to Team AlphaBits in that round.
--    The conflict-of-interest trigger still applies: if the judge and a team
--    member share an affiliation, this INSERT fails with error 50001.
INSERT INTO judge_assignments (round_id, judge_user_id, team_id, status)
SELECT r.round_id, j.user_id, t.team_id, 'ASSIGNED'
FROM rounds r
JOIN teams t ON t.event_id = r.event_id AND t.team_name = 'Team AlphaBits'
JOIN users j ON j.email = 'judge.tech@scorevault.culkomail.in'
WHERE r.round_name = 'Grand Finale Live Demo'
  AND NOT EXISTS (SELECT 1 FROM judge_assignments ja
                  WHERE ja.round_id = r.round_id
                    AND ja.judge_user_id = j.user_id
                    AND ja.team_id = t.team_id);
GO

-- 4. Check: should show one ASSIGNED row for Round 2.
SELECT ja.assignment_id, r.round_name, r.status AS round_status,
       u.full_name AS judge, t.team_name, ja.status
FROM judge_assignments ja
JOIN rounds r ON r.round_id = ja.round_id
JOIN users u ON u.user_id = ja.judge_user_id
JOIN teams t ON t.team_id = ja.team_id
WHERE r.round_name = 'Grand Finale Live Demo';
GO
