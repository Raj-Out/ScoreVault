/* ============================================================================
   ScoreVault: integrity_tests.sql
   Run order : schema.sql -> integrity_triggers.sql -> seed_data.sql -> THIS FILE
   Result    : returns a PASS or FAIL row for every test. Nothing is changed permanently:
               each test runs inside a transaction that is rolled back.
   ============================================================================ */
SET NOCOUNT ON;
IF OBJECT_ID('tempdb..#ScoreVaultIntegrityResults') IS NOT NULL DROP TABLE #ScoreVaultIntegrityResults;
CREATE TABLE #ScoreVaultIntegrityResults (
    test_name VARCHAR(5) NOT NULL,
    outcome VARCHAR(4) NOT NULL,
    detail NVARCHAR(2000) NOT NULL
);
GO

-- T1: judge must have the JUDGE role  (expect 50040)
BEGIN TRY
    BEGIN TRANSACTION;
    INSERT INTO judge_assignments (round_id, judge_user_id, team_id) VALUES (3, 7, 2);
    INSERT INTO #ScoreVaultIntegrityResults VALUES ('T1', 'FAIL', 'A participant was accepted as a judge.');
    ROLLBACK TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    INSERT INTO #ScoreVaultIntegrityResults VALUES ('T1', CASE WHEN ERROR_NUMBER() = 50040 THEN 'PASS' ELSE 'FAIL' END, CONCAT('SQL error ', ERROR_NUMBER(), ': ', ERROR_MESSAGE()));
END CATCH;
GO

-- T2: team must belong to the round's event  (expect 50041)
BEGIN TRY
    BEGIN TRANSACTION;
    INSERT INTO judge_assignments (round_id, judge_user_id, team_id) VALUES (1, 3, 2);
    INSERT INTO #ScoreVaultIntegrityResults VALUES ('T2', 'FAIL', 'A team from another event was accepted.');
    ROLLBACK TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    INSERT INTO #ScoreVaultIntegrityResults VALUES ('T2', CASE WHEN ERROR_NUMBER() = 50041 THEN 'PASS' ELSE 'FAIL' END, CONCAT('SQL error ', ERROR_NUMBER(), ': ', ERROR_MESSAGE()));
END CATCH;
GO

-- T3: judge who is a member of the team  (expect 50042)
BEGIN TRY
    BEGIN TRANSACTION;
    INSERT INTO team_members (team_id, user_id) VALUES (1, 4);
    INSERT INTO judge_assignments (round_id, judge_user_id, team_id) VALUES (1, 4, 1);
    INSERT INTO #ScoreVaultIntegrityResults VALUES ('T3', 'FAIL', 'A team member was accepted as a judge.');
    ROLLBACK TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    INSERT INTO #ScoreVaultIntegrityResults VALUES ('T3', CASE WHEN ERROR_NUMBER() = 50042 THEN 'PASS' ELSE 'FAIL' END, CONCAT('SQL error ', ERROR_NUMBER(), ': ', ERROR_MESSAGE()));
END CATCH;
GO

-- T4: judge with the same affiliation as the team  (expect 50043)
BEGIN TRY
    BEGIN TRANSACTION;
    UPDATE users SET affiliation = 'BITS Pilani' WHERE user_id = 4;
    INSERT INTO judge_assignments (round_id, judge_user_id, team_id) VALUES (1, 4, 1);
    INSERT INTO #ScoreVaultIntegrityResults VALUES ('T4', 'FAIL', 'A judge with a conflicting affiliation was accepted.');
    ROLLBACK TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    INSERT INTO #ScoreVaultIntegrityResults VALUES ('T4', CASE WHEN ERROR_NUMBER() = 50043 THEN 'PASS' ELSE 'FAIL' END, CONCAT('SQL error ', ERROR_NUMBER(), ': ', ERROR_MESSAGE()));
END CATCH;
GO

-- T5: a locked score cannot be edited  (expect 50030)
BEGIN TRY
    BEGIN TRANSACTION;
    UPDATE scores SET feedback_json_url = feedback_json_url + '?edited' WHERE score_id = 1;
    INSERT INTO #ScoreVaultIntegrityResults VALUES ('T5', 'FAIL', 'A locked score was edited.');
    ROLLBACK TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    INSERT INTO #ScoreVaultIntegrityResults VALUES ('T5', CASE WHEN ERROR_NUMBER() = 50030 THEN 'PASS' ELSE 'FAIL' END, CONCAT('SQL error ', ERROR_NUMBER(), ': ', ERROR_MESSAGE()));
END CATCH;
GO

-- T6: a locked score cannot be deleted  (expect 50030)
BEGIN TRY
    BEGIN TRANSACTION;
    DELETE FROM scores WHERE score_id = 1;
    INSERT INTO #ScoreVaultIntegrityResults VALUES ('T6', 'FAIL', 'A locked score was deleted.');
    ROLLBACK TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    INSERT INTO #ScoreVaultIntegrityResults VALUES ('T6', CASE WHEN ERROR_NUMBER() = 50030 THEN 'PASS' ELSE 'FAIL' END, CONCAT('SQL error ', ERROR_NUMBER(), ': ', ERROR_MESSAGE()));
END CATCH;
GO

-- T7: raw_score above the criterion maximum  (expect 50031)
BEGIN TRY
    BEGIN TRANSACTION;
    INSERT INTO judge_assignments (round_id, judge_user_id, team_id) VALUES (1, 4, 1);
    INSERT INTO scores (assignment_id, criterion_id, raw_score, weighted_score)
    VALUES (SCOPE_IDENTITY(), 1, 11.00, 33.000);
    INSERT INTO #ScoreVaultIntegrityResults VALUES ('T7', 'FAIL', 'A score above the maximum was accepted.');
    ROLLBACK TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    INSERT INTO #ScoreVaultIntegrityResults VALUES ('T7', CASE WHEN ERROR_NUMBER() = 50031 THEN 'PASS' ELSE 'FAIL' END, CONCAT('SQL error ', ERROR_NUMBER(), ': ', ERROR_MESSAGE()));
END CATCH;
GO

-- T8: criterion from another event's rubric  (expect 50032)
BEGIN TRY
    BEGIN TRANSACTION;
    INSERT INTO judge_assignments (round_id, judge_user_id, team_id) VALUES (1, 4, 1);
    INSERT INTO scores (assignment_id, criterion_id, raw_score, weighted_score)
    VALUES (SCOPE_IDENTITY(), (SELECT TOP 1 criterion_id FROM criteria WHERE template_id <> 1 ORDER BY criterion_id), 1.00, 1.000);
    INSERT INTO #ScoreVaultIntegrityResults VALUES ('T8', 'FAIL', 'A criterion from another rubric was accepted.');
    ROLLBACK TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    INSERT INTO #ScoreVaultIntegrityResults VALUES ('T8', CASE WHEN ERROR_NUMBER() = 50032 THEN 'PASS' ELSE 'FAIL' END, CONCAT('SQL error ', ERROR_NUMBER(), ': ', ERROR_MESSAGE()));
END CATCH;
GO

-- T9: weighted_score that does not match raw_score  (expect 50033)
BEGIN TRY
    BEGIN TRANSACTION;
    INSERT INTO judge_assignments (round_id, judge_user_id, team_id) VALUES (1, 4, 1);
    INSERT INTO scores (assignment_id, criterion_id, raw_score, weighted_score)
    VALUES (SCOPE_IDENTITY(), 1, 5.00, 99.000);
    INSERT INTO #ScoreVaultIntegrityResults VALUES ('T9', 'FAIL', 'An inconsistent weighted score was accepted.');
    ROLLBACK TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    INSERT INTO #ScoreVaultIntegrityResults VALUES ('T9', CASE WHEN ERROR_NUMBER() = 50033 THEN 'PASS' ELSE 'FAIL' END, CONCAT('SQL error ', ERROR_NUMBER(), ': ', ERROR_MESSAGE()));
END CATCH;
GO

-- T10: submission whose team and round belong to different events  (expect 50050)
BEGIN TRY
    BEGIN TRANSACTION;
    INSERT INTO submissions (round_id, team_id, file_blob_url, file_name, mime_type, file_size_bytes, file_sha256)
    VALUES (2, 2, 'https://example.blob.core.windows.net/x/y.zip', 'y.zip', 'application/zip', 1000, REPLICATE('a', 64));
    INSERT INTO #ScoreVaultIntegrityResults VALUES ('T10', 'FAIL', 'A submission with mismatched team and round was accepted.');
    ROLLBACK TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    INSERT INTO #ScoreVaultIntegrityResults VALUES ('T10', CASE WHEN ERROR_NUMBER() = 50050 THEN 'PASS' ELSE 'FAIL' END, CONCAT('SQL error ', ERROR_NUMBER(), ': ', ERROR_MESSAGE()));
END CATCH;
GO

-- T11: audit_log cannot be updated  (expect 50060)
BEGIN TRY
    BEGIN TRANSACTION;
    UPDATE audit_log SET action_type = 'TAMPERED';
    INSERT INTO #ScoreVaultIntegrityResults VALUES ('T11', 'FAIL', 'The audit log was updated.');
    ROLLBACK TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    INSERT INTO #ScoreVaultIntegrityResults VALUES ('T11', CASE WHEN ERROR_NUMBER() = 50060 THEN 'PASS' ELSE 'FAIL' END, CONCAT('SQL error ', ERROR_NUMBER(), ': ', ERROR_MESSAGE()));
END CATCH;
GO

-- T12: audit_log cannot be deleted  (expect 50060)
BEGIN TRY
    BEGIN TRANSACTION;
    DELETE FROM audit_log;
    INSERT INTO #ScoreVaultIntegrityResults VALUES ('T12', 'FAIL', 'Audit log rows were deleted.');
    ROLLBACK TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    INSERT INTO #ScoreVaultIntegrityResults VALUES ('T12', CASE WHEN ERROR_NUMBER() = 50060 THEN 'PASS' ELSE 'FAIL' END, CONCAT('SQL error ', ERROR_NUMBER(), ': ', ERROR_MESSAGE()));
END CATCH;
GO

-- T13: a logged admin override CAN touch a locked score, and the audit row names the actor
DECLARE @T13Outcome VARCHAR(4);
DECLARE @T13Detail NVARCHAR(2000);
BEGIN TRY
    BEGIN TRANSACTION;
    EXEC sp_set_session_context @key = N'admin_override', @value = 1;
    EXEC sp_set_session_context @key = N'user_id',        @value = 1;
    EXEC sp_set_session_context @key = N'client_ip',      @value = '203.0.113.7';
    UPDATE scores SET feedback_json_url = feedback_json_url WHERE score_id = 1;
    IF EXISTS (SELECT 1 FROM audit_log
               WHERE action_type = 'UPDATE_SCORE' AND entity_id = '1'
                 AND performed_by_user_id = 1 AND ip_address = '203.0.113.7')
    BEGIN
        SET @T13Outcome = 'PASS';
        SET @T13Detail = 'Override worked and the audit row records user 1 and the client IP.';
    END
    ELSE
    BEGIN
        SET @T13Outcome = 'FAIL';
        SET @T13Detail = 'Override worked but the audit row has no actor.';
    END
    ROLLBACK TRANSACTION;
    INSERT INTO #ScoreVaultIntegrityResults VALUES ('T13', @T13Outcome, @T13Detail);
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    INSERT INTO #ScoreVaultIntegrityResults VALUES ('T13', 'FAIL', CONCAT('SQL error ', ERROR_NUMBER(), ': ', ERROR_MESSAGE()));
END CATCH;
EXEC sp_set_session_context @key = N'admin_override', @value = NULL;
EXEC sp_set_session_context @key = N'user_id',        @value = NULL;
EXEC sp_set_session_context @key = N'client_ip',      @value = NULL;
GO

SELECT test_name, outcome, detail FROM #ScoreVaultIntegrityResults ORDER BY test_name;
DROP TABLE #ScoreVaultIntegrityResults;
GO
