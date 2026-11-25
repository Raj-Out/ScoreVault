-- ============================================================================
-- Project: ScoreVault - Secure Multi-Category Event Evaluation & Auditing Portal
-- Course: Integrated Full-Stack Project (RDBMS Component)
-- Authors: Raj Singh (25LBCS3205) & Prince (25LBCS3005)
-- Target RDBMS: Azure SQL Database / Microsoft SQL Server
-- Third Normal Form (3NF) Relational DDL Script
-- ============================================================================

-- 1. DROP EXISTING OBJECTS IN REVERSE DEPENDENCY ORDER
DROP PROCEDURE IF EXISTS sp_finalize_and_lock_round_scores;
DROP VIEW IF EXISTS vw_round_leaderboard;
DROP VIEW IF EXISTS vw_judge_scoring_variance;
DROP TRIGGER IF EXISTS trg_audit_score_changes;

DROP TABLE IF EXISTS audit_log;
DROP TABLE IF EXISTS scores;
DROP TABLE IF EXISTS judge_assignments;
DROP TABLE IF EXISTS submissions;
DROP TABLE IF EXISTS team_members;
DROP TABLE IF EXISTS teams;
DROP TABLE IF EXISTS rounds;
DROP TABLE IF EXISTS events;
DROP TABLE IF EXISTS criteria;
DROP TABLE IF EXISTS rubric_templates;
DROP TABLE IF EXISTS event_categories;
DROP TABLE IF EXISTS users;
DROP TABLE IF EXISTS roles;
GO

-- ============================================================================
-- 2. CREATE 13 CORE 3NF RELATIONAL TABLES
-- ============================================================================

-- Table 1: roles
CREATE TABLE roles (
    role_id INT IDENTITY(1,1) PRIMARY KEY,
    role_name VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255) NULL
);
GO

-- Table 2: users
CREATE TABLE users (
    user_id INT IDENTITY(1,1) PRIMARY KEY,
    role_id INT NOT NULL,
    full_name VARCHAR(150) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    affiliation VARCHAR(200) NOT NULL, -- College / University / Organization name
    is_active BIT NOT NULL DEFAULT 1,
    created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT fk_users_role FOREIGN KEY (role_id) REFERENCES roles(role_id)
);
GO

-- Table 3: event_categories
CREATE TABLE event_categories (
    category_id INT IDENTITY(1,1) PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500) NULL,
    CONSTRAINT chk_category_code CHECK (code IN ('HACKATHON', 'DANCE', 'LAW', 'LITERATURE'))
);
GO

-- Table 4: rubric_templates
CREATE TABLE rubric_templates (
    template_id INT IDENTITY(1,1) PRIMARY KEY,
    category_id INT NOT NULL,
    name VARCHAR(150) NOT NULL,
    description VARCHAR(500) NULL,
    created_by INT NOT NULL,
    created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT fk_rubric_category FOREIGN KEY (category_id) REFERENCES event_categories(category_id),
    CONSTRAINT fk_rubric_creator FOREIGN KEY (created_by) REFERENCES users(user_id)
);
GO

-- Table 5: criteria
CREATE TABLE criteria (
    criterion_id INT IDENTITY(1,1) PRIMARY KEY,
    template_id INT NOT NULL,
    name VARCHAR(150) NOT NULL,
    description VARCHAR(500) NULL,
    max_score DECIMAL(5,2) NOT NULL DEFAULT 10.00,
    weightage_percent DECIMAL(5,2) NOT NULL, -- e.g., 25.00 for 25%
    CONSTRAINT fk_criteria_template FOREIGN KEY (template_id) REFERENCES rubric_templates(template_id) ON DELETE CASCADE,
    CONSTRAINT chk_criteria_max_score CHECK (max_score > 0),
    CONSTRAINT chk_criteria_weightage CHECK (weightage_percent > 0 AND weightage_percent <= 100)
);
GO

-- Table 6: events
CREATE TABLE events (
    event_id INT IDENTITY(1,1) PRIMARY KEY,
    category_id INT NOT NULL,
    template_id INT NOT NULL,
    title VARCHAR(200) NOT NULL,
    description VARCHAR(1000) NULL,
    start_date DATETIME2 NOT NULL,
    end_date DATETIME2 NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'UPCOMING',
    created_by INT NOT NULL,
    created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT fk_events_category FOREIGN KEY (category_id) REFERENCES event_categories(category_id),
    CONSTRAINT fk_events_template FOREIGN KEY (template_id) REFERENCES rubric_templates(template_id),
    CONSTRAINT fk_events_creator FOREIGN KEY (created_by) REFERENCES users(user_id),
    CONSTRAINT chk_event_dates CHECK (end_date >= start_date),
    CONSTRAINT chk_event_status CHECK (status IN ('UPCOMING', 'ACTIVE', 'SCORING', 'COMPLETED', 'ARCHIVED'))
);
GO

-- Table 7: rounds
CREATE TABLE rounds (
    round_id INT IDENTITY(1,1) PRIMARY KEY,
    event_id INT NOT NULL,
    round_number INT NOT NULL,
    round_name VARCHAR(100) NOT NULL,
    is_final BIT NOT NULL DEFAULT 0,
    status VARCHAR(30) NOT NULL DEFAULT 'SCHEDULED',
    CONSTRAINT fk_rounds_event FOREIGN KEY (event_id) REFERENCES events(event_id) ON DELETE CASCADE,
    CONSTRAINT uq_event_round_number UNIQUE (event_id, round_number),
    CONSTRAINT chk_round_status CHECK (status IN ('SCHEDULED', 'IN_PROGRESS', 'EVALUATION', 'COMPLETED'))
);
GO

-- Table 8: teams
CREATE TABLE teams (
    team_id INT IDENTITY(1,1) PRIMARY KEY,
    event_id INT NOT NULL,
    team_name VARCHAR(150) NOT NULL,
    lead_user_id INT NOT NULL,
    created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT fk_teams_event FOREIGN KEY (event_id) REFERENCES events(event_id),
    CONSTRAINT fk_teams_lead FOREIGN KEY (lead_user_id) REFERENCES users(user_id),
    CONSTRAINT uq_event_team_name UNIQUE (event_id, team_name)
);
GO

-- Table 9: team_members
CREATE TABLE team_members (
    team_member_id INT IDENTITY(1,1) PRIMARY KEY,
    team_id INT NOT NULL,
    user_id INT NOT NULL,
    joined_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT fk_tm_team FOREIGN KEY (team_id) REFERENCES teams(team_id) ON DELETE CASCADE,
    CONSTRAINT fk_tm_user FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT uq_team_user UNIQUE (team_id, user_id)
);
GO

-- Table 10: submissions
CREATE TABLE submissions (
    submission_id INT IDENTITY(1,1) PRIMARY KEY,
    round_id INT NOT NULL,
    team_id INT NOT NULL,
    file_blob_url VARCHAR(1000) NOT NULL, -- Azure Blob Storage URI
    file_name VARCHAR(255) NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    file_sha256 CHAR(64) NOT NULL, -- Cryptographic integrity hash
    submission_notes VARCHAR(1000) NULL,
    submitted_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT fk_sub_round FOREIGN KEY (round_id) REFERENCES rounds(round_id),
    CONSTRAINT fk_sub_team FOREIGN KEY (team_id) REFERENCES teams(team_id),
    CONSTRAINT uq_round_team_submission UNIQUE (round_id, team_id)
);
GO

-- Table 11: judge_assignments
CREATE TABLE judge_assignments (
    assignment_id INT IDENTITY(1,1) PRIMARY KEY,
    round_id INT NOT NULL,
    judge_user_id INT NOT NULL,
    team_id INT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ASSIGNED',
    assigned_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT fk_ja_round FOREIGN KEY (round_id) REFERENCES rounds(round_id),
    CONSTRAINT fk_ja_judge FOREIGN KEY (judge_user_id) REFERENCES users(user_id),
    CONSTRAINT fk_ja_team FOREIGN KEY (team_id) REFERENCES teams(team_id),
    CONSTRAINT uq_round_judge_team UNIQUE (round_id, judge_user_id, team_id),
    CONSTRAINT chk_assignment_status CHECK (status IN ('ASSIGNED', 'IN_PROGRESS', 'SUBMITTED', 'VERIFIED'))
);
GO

-- Table 12: scores
CREATE TABLE scores (
    score_id INT IDENTITY(1,1) PRIMARY KEY,
    assignment_id INT NOT NULL,
    criterion_id INT NOT NULL,
    raw_score DECIMAL(5,2) NOT NULL,
    weighted_score DECIMAL(7,3) NOT NULL, -- Computed: (raw_score / max_score) * weightage
    feedback_json_url VARCHAR(1000) NULL, -- Azure Blob URI for detailed semi-structured feedback
    is_locked BIT NOT NULL DEFAULT 0,
    scored_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT fk_scores_assignment FOREIGN KEY (assignment_id) REFERENCES judge_assignments(assignment_id) ON DELETE CASCADE,
    CONSTRAINT fk_scores_criterion FOREIGN KEY (criterion_id) REFERENCES criteria(criterion_id),
    CONSTRAINT uq_assignment_criterion UNIQUE (assignment_id, criterion_id),
    CONSTRAINT chk_raw_score_positive CHECK (raw_score >= 0)
);
GO

-- Table 13: audit_log
CREATE TABLE audit_log (
    log_id BIGINT IDENTITY(1,1) PRIMARY KEY,
    entity_name VARCHAR(50) NOT NULL,
    entity_id VARCHAR(50) NOT NULL,
    action_type VARCHAR(50) NOT NULL, -- 'INSERT', 'UPDATE', 'SCORE_LOCKED', 'OVERRIDE'
    old_value NVARCHAR(MAX) NULL,
    new_value NVARCHAR(MAX) NULL,
    performed_by_user_id INT NULL,
    timestamp DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    ip_address VARCHAR(45) NULL,
    CONSTRAINT fk_audit_user FOREIGN KEY (performed_by_user_id) REFERENCES users(user_id)
);
GO

-- ============================================================================
-- 3. HIGH-PERFORMANCE NON-CLUSTERED INDEXES
-- ============================================================================
CREATE NONCLUSTERED INDEX idx_users_email ON users(email);
CREATE NONCLUSTERED INDEX idx_users_affiliation ON users(affiliation);
CREATE NONCLUSTERED INDEX idx_teams_event ON teams(event_id);
CREATE NONCLUSTERED INDEX idx_team_members_lookup ON team_members(team_id, user_id);
CREATE NONCLUSTERED INDEX idx_submissions_round ON submissions(round_id, team_id);
CREATE NONCLUSTERED INDEX idx_assignments_judge ON judge_assignments(round_id, judge_user_id);
CREATE NONCLUSTERED INDEX idx_scores_assignment ON scores(assignment_id);
CREATE NONCLUSTERED INDEX idx_audit_entity ON audit_log(entity_name, entity_id);
GO

-- ============================================================================
-- 4. VIEWS (DP-900 / RDBMS Requirement)
-- ============================================================================

-- View 1: vw_round_leaderboard
-- Joins 6 tables to compute weighted aggregated team totals per round with ranks
CREATE OR ALTER VIEW vw_round_leaderboard AS
SELECT 
    r.round_id,
    r.event_id,
    e.title AS event_title,
    r.round_number,
    r.round_name,
    t.team_id,
    t.team_name,
    u_lead.full_name AS team_lead,
    u_lead.affiliation AS institution,
    COUNT(DISTINCT ja.judge_user_id) AS judges_evaluated_count,
    CAST(SUM(s.weighted_score) / NULLIF(COUNT(DISTINCT ja.judge_user_id), 0) AS DECIMAL(6,2)) AS final_weighted_average_score,
    DENSE_RANK() OVER (
        PARTITION BY r.round_id 
        ORDER BY (SUM(s.weighted_score) / NULLIF(COUNT(DISTINCT ja.judge_user_id), 0)) DESC
    ) AS leaderboard_rank
FROM rounds r
INNER JOIN events e ON r.event_id = e.event_id
INNER JOIN teams t ON e.event_id = t.event_id
INNER JOIN users u_lead ON t.lead_user_id = u_lead.user_id
INNER JOIN judge_assignments ja ON r.round_id = ja.round_id AND t.team_id = ja.team_id
INNER JOIN scores s ON ja.assignment_id = s.assignment_id
WHERE ja.status = 'SUBMITTED' AND s.is_locked = 1
GROUP BY 
    r.round_id, r.event_id, e.title, r.round_number, r.round_name, 
    t.team_id, t.team_name, u_lead.full_name, u_lead.affiliation;
GO

-- View 2: vw_judge_scoring_variance (Powers Power BI Analytics on Judge Bias)
CREATE OR ALTER VIEW vw_judge_scoring_variance AS
SELECT 
    j.user_id AS judge_id,
    j.full_name AS judge_name,
    j.affiliation AS judge_affiliation,
    ec.code AS category_code,
    COUNT(DISTINCT s.score_id) AS total_evaluations_count,
    CAST(AVG(s.raw_score) AS DECIMAL(5,2)) AS judge_avg_raw_score,
    CAST(STDEV(s.raw_score) AS DECIMAL(5,2)) AS judge_score_std_dev
FROM users j
INNER JOIN judge_assignments ja ON j.user_id = ja.judge_user_id
INNER JOIN rounds r ON ja.round_id = r.round_id
INNER JOIN events e ON r.event_id = e.event_id
INNER JOIN event_categories ec ON e.category_id = ec.category_id
INNER JOIN scores s ON ja.assignment_id = s.assignment_id
WHERE s.is_locked = 1
GROUP BY j.user_id, j.full_name, j.affiliation, ec.code;
GO

-- ============================================================================
-- 5. DATABASE TRIGGER FOR AUDIT TRAIL (Tamper-Resistance)
-- ============================================================================
CREATE OR ALTER TRIGGER trg_audit_score_changes
ON scores
AFTER UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;

    -- Capture modified or deleted scores into audit_log
    IF EXISTS (SELECT 1 FROM deleted)
    BEGIN
        INSERT INTO audit_log (
            entity_name,
            entity_id,
            action_type,
            old_value,
            new_value,
            performed_by_user_id,
            timestamp,
            ip_address
        )
        SELECT 
            'scores',
            CAST(d.score_id AS VARCHAR(50)),
            CASE 
                WHEN EXISTS (SELECT 1 FROM inserted) THEN 'UPDATE_SCORE'
                ELSE 'DELETE_SCORE'
            END,
            (SELECT d.raw_score AS raw, d.weighted_score AS weighted, d.is_locked AS locked FOR JSON PATH, WITHOUT_ARRAY_WRAPPER),
            (SELECT i.raw_score AS raw, i.weighted_score AS weighted, i.is_locked AS locked FROM inserted i WHERE i.score_id = d.score_id FOR JSON PATH, WITHOUT_ARRAY_WRAPPER),
            NULL, -- Injected via connection context or NULL for system trigger
            SYSUTCDATETIME(),
            'SYSTEM_DB_TRIGGER'
        FROM deleted d;
    END
END;
GO

-- ============================================================================
-- 6. STORED PROCEDURE WITH ACID TRANSACTION (Atomic Round Finalization)
-- ============================================================================
CREATE OR ALTER PROCEDURE sp_finalize_and_lock_round_scores
    @p_round_id INT,
    @p_admin_user_id INT,
    @p_status_message VARCHAR(255) OUTPUT
AS
BEGIN
    SET NOCOUNT ON;
    SET TRANSACTION ISOLATION LEVEL SERIALIZABLE;

    BEGIN TRY
        BEGIN TRANSACTION;

        -- 1. Check if the round exists and is active
        IF NOT EXISTS (SELECT 1 FROM rounds WHERE round_id = @p_round_id)
        BEGIN
            SET @p_status_message = 'ERROR: Round does not exist.';
            ROLLBACK TRANSACTION;
            RETURN;
        END

        -- 2. Verify all assignments for this round have been submitted
        IF EXISTS (
            SELECT 1 
            FROM judge_assignments 
            WHERE round_id = @p_round_id AND status != 'SUBMITTED'
        )
        BEGIN
            SET @p_status_message = 'ERROR: Cannot finalize. Some judge evaluations are pending.';
            ROLLBACK TRANSACTION;
            RETURN;
        END

        -- 3. Lock all scores belonging to this round
        UPDATE s
        SET s.is_locked = 1
        FROM scores s
        INNER JOIN judge_assignments ja ON s.assignment_id = ja.assignment_id
        WHERE ja.round_id = @p_round_id AND s.is_locked = 0;

        -- 4. Mark round as COMPLETED
        UPDATE rounds
        SET status = 'COMPLETED'
        WHERE round_id = @p_round_id;

        -- 5. Record finalization event into audit_log
        INSERT INTO audit_log (
            entity_name,
            entity_id,
            action_type,
            old_value,
            new_value,
            performed_by_user_id,
            timestamp,
            ip_address
        ) VALUES (
            'rounds',
            CAST(@p_round_id AS VARCHAR(50)),
            'ROUND_FINALIZED_AND_LOCKED',
            '{"status": "EVALUATION"}',
            '{"status": "COMPLETED", "scores_locked": true}',
            @p_admin_user_id,
            SYSUTCDATETIME(),
            'STORED_PROCEDURE'
        );

        COMMIT TRANSACTION;
        SET @p_status_message = 'SUCCESS: Round successfully finalized and all scores locked.';
    END TRY
    BEGIN CATCH
        IF @@TRANCOUNT > 0
            ROLLBACK TRANSACTION;

        SET @p_status_message = CONCAT('TRANSACTION FAILED: ', ERROR_MESSAGE());
    END CATCH
END;
GO
