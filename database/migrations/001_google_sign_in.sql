-- Run once against an existing ScoreVault Azure SQL database before enabling Google sign-in.
IF COL_LENGTH('dbo.users', 'google_subject') IS NULL
    ALTER TABLE dbo.users ADD google_subject VARCHAR(255) NULL;
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ux_users_google_subject' AND object_id = OBJECT_ID('dbo.users'))
    CREATE UNIQUE INDEX ux_users_google_subject ON dbo.users(google_subject) WHERE google_subject IS NOT NULL;
GO
