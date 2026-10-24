/* ============================================================================
   ScoreVault: integrity_triggers.sql
   Target : Azure SQL Database (T-SQL). Written for the repo's schema.sql
            (users.affiliation, judge_assignments(round_id, judge_user_id, team_id),
             scores(raw_score, weighted_score, is_locked), roles 'ADMIN'/'JUDGE'/'PARTICIPANT').
   Run    : schema.sql  ->  integrity_triggers.sql  ->  seed_data.sql
            (installing the triggers BEFORE the seed means the seed data is validated too).
   Safe   : CREATE OR ALTER, so it can be re-run.

   WHY THIS FILE EXISTS
   The design document and threat model promise controls at the DATABASE level:
     - conflict-of-interest blocking
     - scores cannot be edited after lock
     - an audit trail that records who changed what, and cannot itself be edited
   schema.sql alone only logs score updates (without the actor) and has a
   detection query for conflicts. This file adds the actual enforcement.

   Error numbers (THROW): 50030-50033 scores, 50040-50043 judge assignment,
                          50050-50051 submissions, 50060 audit log.

   APPLICATION CONTRACT (Spring Boot, once per request on the connection)
     EXEC sp_set_session_context @key = N'user_id',   @value = <logged-in user id>;
     EXEC sp_set_session_context @key = N'client_ip', @value = '<caller ip>';
     -- only for an explicit, logged admin unlock of a locked score:
     EXEC sp_set_session_context @key = N'admin_override', @value = 1;
   Session context lives as long as the connection, and pooled connections are
   reused, so the application must set or clear these values on EVERY request.
   ============================================================================ */


/* ----------------------------------------------------------------------------
   1. Judge assignment rules (conflict of interest)
   ---------------------------------------------------------------------------- */
CREATE OR ALTER TRIGGER trg_judge_conflict
ON judge_assignments
AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;

    -- 1a. the assigned user must be an active JUDGE
    IF EXISTS (SELECT 1
               FROM inserted i
               JOIN users u ON u.user_id = i.judge_user_id
               JOIN roles r ON r.role_id = u.role_id
               WHERE r.role_name <> 'JUDGE' OR u.is_active = 0)
    BEGIN
        ROLLBACK TRANSACTION;
        THROW 50040, 'Only active users with the JUDGE role can be assigned.', 1;
    END;

    -- 1b. the team must belong to the same event as the round
    IF EXISTS (SELECT 1
               FROM inserted i
               JOIN rounds r ON r.round_id = i.round_id
               JOIN teams  t ON t.team_id  = i.team_id
               WHERE t.event_id <> r.event_id)
    BEGIN
        ROLLBACK TRANSACTION;
        THROW 50041, 'The team does not belong to the event of this round.', 1;
    END;

    -- 1c. a judge cannot judge a team they lead or belong to
    IF EXISTS (SELECT 1
               FROM inserted i
               JOIN teams t ON t.team_id = i.team_id
               WHERE i.judge_user_id = t.lead_user_id
                  OR EXISTS (SELECT 1
                             FROM team_members tm
                             WHERE tm.team_id = i.team_id
                               AND tm.user_id = i.judge_user_id))
    BEGIN
        ROLLBACK TRANSACTION;
        THROW 50042, 'Conflict of interest: the judge is a member of this team.', 1;
    END;

    -- 1d. a judge cannot share an affiliation with the team lead or any member
    IF EXISTS (SELECT 1
               FROM inserted i
               JOIN users j ON j.user_id = i.judge_user_id
               JOIN (SELECT t.team_id, t.lead_user_id AS user_id FROM teams t
                     UNION
                     SELECT tm.team_id, tm.user_id FROM team_members tm) m
                    ON m.team_id = i.team_id
               JOIN users mu ON mu.user_id = m.user_id
               WHERE LOWER(LTRIM(RTRIM(j.affiliation))) = LOWER(LTRIM(RTRIM(mu.affiliation))))
    BEGIN
        ROLLBACK TRANSACTION;
        THROW 50043, 'Conflict of interest: the judge shares an affiliation with this team.', 1;
    END;
END;
GO


/* ----------------------------------------------------------------------------
   2. Score validation: range, rubric membership, weighted_score consistency
      weighted_score is a stored, derived value on a 0 to 100 scale:
          weighted_score = raw_score / max_score * weightage_percent
      It is kept for fast leaderboards and audit, and this trigger guarantees
      it can never disagree with raw_score.
   ---------------------------------------------------------------------------- */
CREATE OR ALTER TRIGGER trg_scores_validate
ON scores
AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;

    IF EXISTS (SELECT 1
               FROM inserted i
               JOIN criteria c ON c.criterion_id = i.criterion_id
               WHERE i.raw_score > c.max_score)
    BEGIN
        ROLLBACK TRANSACTION;
        THROW 50031, 'raw_score exceeds the maximum allowed for this criterion.', 1;
    END;

    IF EXISTS (SELECT 1
               FROM inserted i
               JOIN judge_assignments ja ON ja.assignment_id = i.assignment_id
               JOIN rounds   r ON r.round_id     = ja.round_id
               JOIN events   e ON e.event_id     = r.event_id
               JOIN criteria c ON c.criterion_id = i.criterion_id
               WHERE c.template_id <> e.template_id)
    BEGIN
        ROLLBACK TRANSACTION;
        THROW 50032, 'This criterion does not belong to the rubric used by the event.', 1;
    END;

    IF EXISTS (SELECT 1
               FROM inserted i
               JOIN criteria c ON c.criterion_id = i.criterion_id
               WHERE ABS(i.weighted_score
                         - (CAST(i.raw_score AS DECIMAL(12,6)) / c.max_score * c.weightage_percent)) > 0.01)
    BEGIN
        ROLLBACK TRANSACTION;
        THROW 50033, 'weighted_score must equal raw_score / max_score * weightage_percent.', 1;
    END;
END;
GO


/* ----------------------------------------------------------------------------
   3. Locked scores cannot be changed or deleted (unless a logged admin override)
   ---------------------------------------------------------------------------- */
CREATE OR ALTER TRIGGER trg_scores_locked_guard
ON scores
AFTER UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;

    DECLARE @override INT = TRY_CAST(SESSION_CONTEXT(N'admin_override') AS INT);

    IF ISNULL(@override, 0) <> 1
       AND EXISTS (SELECT 1 FROM deleted WHERE is_locked = 1)    -- 'deleted' holds the OLD rows
    BEGIN
        ROLLBACK TRANSACTION;
        THROW 50030, 'Scores are locked and cannot be changed or deleted.', 1;
    END;
END;
GO


/* ----------------------------------------------------------------------------
   4. Audit trail with the real actor (replaces the trigger in schema.sql)
      Same name, so CREATE OR ALTER simply upgrades it.
   ---------------------------------------------------------------------------- */
CREATE OR ALTER TRIGGER trg_audit_score_changes
ON scores
AFTER UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;

    DECLARE @actor INT         = TRY_CAST(SESSION_CONTEXT(N'user_id')   AS INT);
    DECLARE @ip    VARCHAR(45) = TRY_CAST(SESSION_CONTEXT(N'client_ip') AS VARCHAR(45));

    IF @actor IS NOT NULL AND NOT EXISTS (SELECT 1 FROM users WHERE user_id = @actor)
        SET @actor = NULL;

    INSERT INTO audit_log (entity_name, entity_id, action_type, old_value, new_value,
                           performed_by_user_id, [timestamp], ip_address)
    SELECT 'scores',
           CAST(d.score_id AS VARCHAR(50)),
           CASE WHEN EXISTS (SELECT 1 FROM inserted i WHERE i.score_id = d.score_id)
                THEN 'UPDATE_SCORE' ELSE 'DELETE_SCORE' END,
           (SELECT d.raw_score AS raw, d.weighted_score AS weighted, d.is_locked AS locked
            FOR JSON PATH, WITHOUT_ARRAY_WRAPPER),
           (SELECT i.raw_score AS raw, i.weighted_score AS weighted, i.is_locked AS locked
            FROM inserted i WHERE i.score_id = d.score_id
            FOR JSON PATH, WITHOUT_ARRAY_WRAPPER),
           @actor,
           SYSUTCDATETIME(),
           COALESCE(@ip, 'SYSTEM_DB_TRIGGER')
    FROM deleted d;
END;
GO


/* ----------------------------------------------------------------------------
   5. Submissions: team and round must belong to one event; no new entries once
      the round is COMPLETED
   ---------------------------------------------------------------------------- */
CREATE OR ALTER TRIGGER trg_submissions_check
ON submissions
AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;

    IF EXISTS (SELECT 1
               FROM inserted i
               JOIN teams  t ON t.team_id  = i.team_id
               JOIN rounds r ON r.round_id = i.round_id
               WHERE t.event_id <> r.event_id)
    BEGIN
        ROLLBACK TRANSACTION;
        THROW 50050, 'The team and the round belong to different events.', 1;
    END;

    IF EXISTS (SELECT 1
               FROM inserted i
               JOIN rounds r ON r.round_id = i.round_id
               LEFT JOIN deleted d ON d.submission_id = i.submission_id
               WHERE r.status = 'COMPLETED'
                 AND d.submission_id IS NULL)                    -- new rows only
    BEGIN
        ROLLBACK TRANSACTION;
        THROW 50051, 'This round is completed; new submissions are not accepted.', 1;
    END;
END;
GO


/* ----------------------------------------------------------------------------
   6. audit_log is append-only
   ---------------------------------------------------------------------------- */
CREATE OR ALTER TRIGGER trg_audit_log_immutable
ON audit_log
INSTEAD OF UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;
    THROW 50060, 'audit_log is append-only: records cannot be modified or deleted.', 1;
END;
GO

/* ============================ END OF integrity_triggers.sql ============================ */
