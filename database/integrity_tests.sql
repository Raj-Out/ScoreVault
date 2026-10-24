/* ============================================================================
   ScoreVault: integrity_tests.sql
   Run order : schema.sql -> integrity_triggers.sql -> seed_data.sql -> THIS FILE
   Result    : every test prints PASS or FAIL. Nothing is changed permanently:
               each test runs inside a transaction that is rolled back.
   ============================================================================ */
SET NOCOUNT ON;
GO

-- T1: judge must have the JUDGE role  (expect 50040)
BEGIN TRY
    BEGIN TRANSACTION;
    INSERT INTO judge_assignments (round_id, judge_user_id, team_id) VALUES (3, 7, 2);
    PRINT 'T1  FAIL: a participant was accepted as judge';
    ROLLBACK TRANSACTION;
END TRY
BEGIN CATCH
    PRINT CASE WHEN ERROR_NUMBER() = 50040 THEN 'T1  PASS: ' ELSE 'T1  FAIL (wrong error): ' END + ERROR_MESSAGE();
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
END CATCH;
GO

-- T2: team must belong to the round's event  (expect 50041)
BEGIN TRY
    BEGIN TRANSACTION;
    INSERT INTO judge_assignments (round_id, judge_user_id, team_id) VALUES (1, 3, 2);
    PRINT 'T2  FAIL: team from another event was accepted';
    ROLLBACK TRANSACTION;
END TRY
BEGIN CATCH
    PRINT CASE WHEN ERROR_NUMBER() = 50041 THEN 'T2  PASS: ' ELSE 'T2  FAIL (wrong error): ' END + ERROR_MESSAGE();
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
END CATCH;
GO

-- T3: judge who is a member of the team  (expect 50042)
BEGIN TRY
    BEGIN TRANSACTION;
    INSERT INTO team_members (team_id, user_id) VALUES (1, 4);
    INSERT INTO judge_assignments (round_id, judge_user_id, team_id) VALUES (1, 4, 1);
    PRINT 'T3  FAIL: a team member was accepted as judge';
    ROLLBACK TRANSACTION;
END TRY
BEGIN CATCH
    PRINT CASE WHEN ERROR_NUMBER() = 50042 THEN 'T3  PASS: ' ELSE 'T3  FAIL (wrong error): ' END + ERROR_MESSAGE();
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
END CATCH;
GO

-- T4: judge with the same affiliation as the team  (expect 50043)
BEGIN TRY
    BEGIN TRANSACTION;
    UPDATE users SET affiliation = 'BITS Pilani' WHERE user_id = 4;
    INSERT INTO judge_assignments (round_id, judge_user_id, team_id) VALUES (1, 4, 1);
    PRINT 'T4  FAIL: conflicting judge was accepted';
    ROLLBACK TRANSACTION;
END TRY
BEGIN CATCH
    PRINT CASE WHEN ERROR_NUMBER() = 50043 THEN 'T4  PASS: ' ELSE 'T4  FAIL (wrong error): ' END + ERROR_MESSAGE();
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
END CATCH;
GO

-- T5: a locked score cannot be edited  (expect 50030)
BEGIN TRY
    UPDATE scores SET feedback_json_url = feedback_json_url + '?edited' WHERE score_id = 1;
    PRINT 'T5  FAIL: a locked score was edited';
END TRY
BEGIN CATCH
    PRINT CASE WHEN ERROR_NUMBER() = 50030 THEN 'T5  PASS: ' ELSE 'T5  FAIL (wrong error): ' END + ERROR_MESSAGE();
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
END CATCH;
GO

-- T6: a locked score cannot be deleted  (expect 50030)
BEGIN TRY
    DELETE FROM scores WHERE score_id = 1;
    PRINT 'T6  FAIL: a locked score was deleted';
END TRY
BEGIN CATCH
    PRINT CASE WHEN ERROR_NUMBER() = 50030 THEN 'T6  PASS: ' ELSE 'T6  FAIL (wrong error): ' END + ERROR_MESSAGE();
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
END CATCH;
GO

-- T7: raw_score above the criterion maximum  (expect 50031)
BEGIN TRY
    BEGIN TRANSACTION;
    INSERT INTO judge_assignments (round_id, judge_user_id, team_id) VALUES (1, 4, 1);
    INSERT INTO scores (assignment_id, criterion_id, raw_score, weighted_score)
    VALUES (SCOPE_IDENTITY(), 1, 11.00, 33.000);
    PRINT 'T7  FAIL: score above the maximum was accepted';
    ROLLBACK TRANSACTION;
END TRY
BEGIN CATCH
    PRINT CASE WHEN ERROR_NUMBER() = 50031 THEN 'T7  PASS: ' ELSE 'T7  FAIL (wrong error): ' END + ERROR_MESSAGE();
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
END CATCH;
GO

-- T8: criterion from another event's rubric  (expect 50032)
BEGIN TRY
    BEGIN TRANSACTION;
    INSERT INTO judge_assignments (round_id, judge_user_id, team_id) VALUES (1, 4, 1);
    INSERT INTO scores (assignment_id, criterion_id, raw_score, weighted_score)
    VALUES (SCOPE_IDENTITY(), (SELECT TOP 1 criterion_id FROM criteria WHERE template_id <> 1 ORDER BY criterion_id), 1.00, 1.000);
    PRINT 'T8  FAIL: criterion from another rubric was accepted';
    ROLLBACK TRANSACTION;
END TRY
BEGIN CATCH
    PRINT CASE WHEN ERROR_NUMBER() = 50032 THEN 'T8  PASS: ' ELSE 'T8  FAIL (wrong error): ' END + ERROR_MESSAGE();
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
END CATCH;
GO

-- T9: weighted_score that does not match raw_score  (expect 50033)
BEGIN TRY
    BEGIN TRANSACTION;
    INSERT INTO judge_assignments (round_id, judge_user_id, team_id) VALUES (1, 4, 1);
    INSERT INTO scores (assignment_id, criterion_id, raw_score, weighted_score)
    VALUES (SCOPE_IDENTITY(), 1, 5.00, 99.000);
    PRINT 'T9  FAIL: inconsistent weighted_score was accepted';
    ROLLBACK TRANSACTION;
END TRY
BEGIN CATCH
    PRINT CASE WHEN ERROR_NUMBER() = 50033 THEN 'T9  PASS: ' ELSE 'T9  FAIL (wrong error): ' END + ERROR_MESSAGE();
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
END CATCH;
GO

-- T10: submission whose team and round belong to different events  (expect 50050)
BEGIN TRY
    BEGIN TRANSACTION;
    INSERT INTO submissions (round_id, team_id, file_blob_url, file_name, mime_type, file_size_bytes, file_sha256)
    VALUES (2, 2, 'https://example.blob.core.windows.net/x/y.zip', 'y.zip', 'application/zip', 1000, REPLICATE('a', 64));
    PRINT 'T10 FAIL: mismatched team and round were accepted';
    ROLLBACK TRANSACTION;
END TRY
BEGIN CATCH
    PRINT CASE WHEN ERROR_NUMBER() = 50050 THEN 'T10 PASS: ' ELSE 'T10 FAIL (wrong error): ' END + ERROR_MESSAGE();
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
END CATCH;
GO

-- T11: audit_log cannot be updated  (expect 50060)
BEGIN TRY
    UPDATE audit_log SET action_type = 'TAMPERED';
    PRINT 'T11 FAIL: audit_log was updated';
END TRY
BEGIN CATCH
    PRINT CASE WHEN ERROR_NUMBER() = 50060 THEN 'T11 PASS: ' ELSE 'T11 FAIL (wrong error): ' END + ERROR_MESSAGE();
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
END CATCH;
GO

-- T12: audit_log cannot be deleted  (expect 50060)
BEGIN TRY
    DELETE FROM audit_log;
    PRINT 'T12 FAIL: audit_log rows were deleted';
END TRY
BEGIN CATCH
    PRINT CASE WHEN ERROR_NUMBER() = 50060 THEN 'T12 PASS: ' ELSE 'T12 FAIL (wrong error): ' END + ERROR_MESSAGE();
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
END CATCH;
GO

-- T13: a logged admin override CAN touch a locked score, and the audit row names the actor
BEGIN TRY
    BEGIN TRANSACTION;
    EXEC sp_set_session_context @key = N'admin_override', @value = 1;
    EXEC sp_set_session_context @key = N'user_id',        @value = 1;
    EXEC sp_set_session_context @key = N'client_ip',      @value = '203.0.113.7';
    UPDATE scores SET feedback_json_url = feedback_json_url WHERE score_id = 1;
    IF EXISTS (SELECT 1 FROM audit_log
               WHERE action_type = 'UPDATE_SCORE' AND entity_id = '1'
                 AND performed_by_user_id = 1 AND ip_address = '203.0.113.7')
        PRINT 'T13 PASS: override allowed and the audit row records user 1 and the client IP';
    ELSE
        PRINT 'T13 FAIL: override worked but the audit row has no actor';
    ROLLBACK TRANSACTION;
END TRY
BEGIN CATCH
    PRINT 'T13 FAIL: ' + ERROR_MESSAGE();
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
END CATCH;
EXEC sp_set_session_context @key = N'admin_override', @value = NULL;
EXEC sp_set_session_context @key = N'user_id',        @value = NULL;
EXEC sp_set_session_context @key = N'client_ip',      @value = NULL;
GO

PRINT '--- done: every line above should say PASS ---';
GO