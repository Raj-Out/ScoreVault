/* ============================================================================
   ScoreVault: app_login.sql
   Creates a LIMITED database account for the application (least privilege),
   so the running app no longer uses the server admin login (scoreadmin).

   Target : Azure SQL Database (T-SQL)
   Run as : scoreadmin, connected to the scorevault-db database (not master)
   Run    : after schema.sql, integrity_triggers.sql, leaderboard_tiebreak.sql
            and seed_data.sql.  Safe to re-run.

   !!  DO NOT PUT A REAL PASSWORD IN THIS FILE OR COMMIT ONE.  !!
   How to use it safely:
     1. Open this file in VS Code.
     2. Replace REPLACE_WITH_A_STRONG_PASSWORD in the editor with a strong password
        (at least 16 characters, with upper case, lower case, digits and symbols).
     3. Run the file with Ctrl+Shift+E WITHOUT saving it.
     4. Close the file and choose "Don't Save", so the repository copy keeps the placeholder.
   The app then reads this password from the DB_PASSWORD environment variable.
   ============================================================================ */

IF NOT EXISTS (SELECT 1 FROM sys.database_principals WHERE name = 'scorevault_app')
BEGIN
    -- A contained database user: it exists only inside this database and cannot log in to the server.
    CREATE USER scorevault_app WITH PASSWORD = N'AdminSecurePass#2026';  -- REPLACE WITH A STRONG PASSWORD
END;
GO

-- The app may read everything, write rows, and run the stored procedures.
GRANT SELECT, INSERT, UPDATE ON SCHEMA::dbo TO scorevault_app;
GRANT EXECUTE ON SCHEMA::dbo TO scorevault_app;

-- Only team membership is ever removed by the application.
GRANT DELETE ON dbo.team_members TO scorevault_app;

-- The audit log is append-only for the app (the trigger already enforces this; this is a second layer).
DENY UPDATE, DELETE ON dbo.audit_log TO scorevault_app;

-- Reference data is fixed by the administrator.
DENY INSERT, UPDATE, DELETE ON dbo.roles TO scorevault_app;
DENY INSERT, UPDATE, DELETE ON dbo.event_categories TO scorevault_app;

-- No schema changes: the app has no ALTER, CREATE or DROP rights (none were granted).
GO

/* ----------------------------------------------------------------------------
   VERIFY (optional): connect to scorevault-db AS scorevault_app, then run these.
   Each statement marked "denied" should fail with a permission error.

   SELECT USER_NAME();                               -- scorevault_app
   SELECT COUNT(*) FROM users;                       -- works
   UPDATE audit_log SET action_type = 'x';           -- denied
   DELETE FROM audit_log;                            -- denied
   DROP TABLE team_members;                          -- denied
   CREATE TABLE test_table (id INT);                 -- denied
   ---------------------------------------------------------------------------- */
