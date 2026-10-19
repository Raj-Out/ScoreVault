-- ============================================================================
-- Project: ScoreVault - Sample Seed Data
-- Domains: Hackathon, Dance, Law/Moot Court, Literature
-- Prepopulates all 13 tables for demo, testing, and faculty review
-- ============================================================================

-- 1. Insert Roles
SET IDENTITY_INSERT roles ON;
INSERT INTO roles (role_id, role_name, description) VALUES
(1, 'ADMIN', 'Event Organiser / Superuser with rubric, assignment, and override controls'),
(2, 'JUDGE', 'Subject-matter expert scoring entries against weighted criteria'),
(3, 'PARTICIPANT', 'Contestant or team lead uploading project submissions');
SET IDENTITY_INSERT roles OFF;

-- 2. Insert Users (Password: 'Pass#2026' bcrypt hashed: $2a$12$e8Z4NkWgW7n8B7... dummy hash)
SET IDENTITY_INSERT users ON;
INSERT INTO users (user_id, role_id, full_name, email, password_hash, affiliation, is_active) VALUES
-- Admins
(1, 1, 'Raj Singh', '25LBCS3205@culkomail.in', '$2a$12$K1G6xX8K9Uv1tX8.X5Rwe.yU2jQ48W9N1B6.y2K0C8fN1qX9bV2uC', 'Chandigarh University', 1),
(2, 1, 'Prince', '25lbcs3005@culkomail.in', '$2a$12$K1G6xX8K9Uv1tX8.X5Rwe.yU2jQ48W9N1B6.y2K0C8fN1qX9bV2uC', 'Chandigarh University', 1),

-- Judges
(3, 2, 'Dr. Arvind Sharma (Tech)', 'judge.tech@scorevault.culkomail.in', '$2a$12$K1G6xX8K9Uv1tX8.X5Rwe.yU2jQ48W9N1B6.y2K0C8fN1qX9bV2uC', 'IIT Delhi', 1),
(4, 2, 'Prof. Meenakshi Sundaram (Dance)', 'judge.dance@scorevault.culkomail.in', '$2a$12$K1G6xX8K9Uv1tX8.X5Rwe.yU2jQ48W9N1B6.y2K0C8fN1qX9bV2uC', 'Kalakshetra Foundation', 1),
(5, 2, 'Advocate Rohan Verma (Law)', 'judge.law@scorevault.culkomail.in', '$2a$12$K1G6xX8K9Uv1tX8.X5Rwe.yU2jQ48W9N1B6.y2K0C8fN1qX9bV2uC', 'Delhi High Court Bar', 1),
(6, 2, 'Dr. Kavita Sen (Literature)', 'judge.lit@scorevault.culkomail.in', '$2a$12$K1G6xX8K9Uv1tX8.X5Rwe.yU2jQ48W9N1B6.y2K0C8fN1qX9bV2uC', 'Jadavpur University', 1),

-- Participants
(7, 3, 'Aman Gupta (AlphaBits Lead)', 'lead.alphabits@culkomail.in', '$2a$12$K1G6xX8K9Uv1tX8.X5Rwe.yU2jQ48W9N1B6.y2K0C8fN1qX9bV2uC', 'BITS Pilani', 1),
(8, 3, 'Sneha Rao (AlphaBits Member)', 'sneha.rao@bits.edu', '$2a$12$K1G6xX8K9Uv1tX8.X5Rwe.yU2jQ48W9N1B6.y2K0C8fN1qX9bV2uC', 'BITS Pilani', 1),
(9, 3, 'Kabir Mehta (Rhythm Crew Lead)', 'kabir.rhythm@culkomail.in', '$2a$12$K1G6xX8K9Uv1tX8.X5Rwe.yU2jQ48W9N1B6.y2K0C8fN1qX9bV2uC', 'Delhi University', 1),
(10, 3, 'Ananya Desai (Lex Justitia Lead)', 'ananya.lex@culkomail.in', '$2a$12$K1G6xX8K9Uv1tX8.X5Rwe.yU2jQ48W9N1B6.y2K0C8fN1qX9bV2uC', 'National Law School Bangalore', 1),
(11, 3, 'Vikramaditya (Solo Bard)', 'vikram.bard@culkomail.in', '$2a$12$K1G6xX8K9Uv1tX8.X5Rwe.yU2jQ48W9N1B6.y2K0C8fN1qX9bV2uC', 'Ashoka University', 1);
SET IDENTITY_INSERT users OFF;

-- 3. Insert Categories
SET IDENTITY_INSERT event_categories ON;
INSERT INTO event_categories (category_id, code, name, description) VALUES
(1, 'HACKATHON', 'Hackathon & Software Innovation', 'Technical problem solving, software engineering, and prototype construction'),
(2, 'DANCE', 'Classical & Contemporary Dance', 'Solo and group choreography, rhythm, sync, and stage expression'),
(3, 'LAW', 'Moot Court & Legal Advocacy', 'Appellate argument, legal research, statutory interpretation, and court demeanor'),
(4, 'LITERATURE', 'Creative Writing & Poetry Slam', 'Originality of thought, thematic depth, linguistic command, and stylistic delivery');
SET IDENTITY_INSERT event_categories OFF;

-- 4. Insert Rubric Templates
SET IDENTITY_INSERT rubric_templates ON;
INSERT INTO rubric_templates (template_id, category_id, name, description, created_by) VALUES
(1, 1, 'National Hackathon Evaluation Matrix', 'Standard 4-point criteria weighted for innovation and execution', 1),
(2, 2, 'Inter-Varsity Dance Scoring Rubric', 'Choreography, musicality, precision, and costume/expression', 1),
(3, 3, 'Appellate Moot Court Scoring System', 'Legal analysis, questions from the bench, memorial quality', 2),
(4, 4, 'Literary Festival Adjudication Rubric', 'Thematic coherence, poetic devices, vocabulary, audience impact', 2);
SET IDENTITY_INSERT rubric_templates OFF;

-- 5. Insert Criteria (Weights sum to exactly 100% per template)
SET IDENTITY_INSERT criteria ON;
-- Hackathon (Template 1)
INSERT INTO criteria (criterion_id, template_id, name, description, max_score, weightage_percent) VALUES
(1, 1, 'Innovation & Originality', 'Uniqueness of the solution and creative use of technology', 10.00, 30.00),
(2, 1, 'Technical Architecture & Code Quality', 'System design, scalability, modularity, and clean code', 10.00, 30.00),
(3, 1, 'Working Prototype Execution', 'Completeness of feature demo and functional viability', 10.00, 25.00),
(4, 1, 'Presentation & Pitch', 'Clarity of problem formulation and delivery', 10.00, 15.00),

-- Dance (Template 2)
(5, 2, 'Rhythm & Synchronization', 'Adherence to tempo and unison among performers', 10.00, 35.00),
(6, 2, 'Choreography & Creativity', 'Formation transitions, floor use, and movement variation', 10.00, 35.00),
(7, 2, 'Stage Presence & Expressions', 'Facial emotive expression (Abhinaya) and confidence', 10.00, 30.00),

-- Moot Court / Law (Template 3)
(8, 3, 'Knowledge of Law & Jurisprudence', 'Application of precedents, constitutional provisions, statutes', 10.00, 40.00),
(9, 3, 'Response to Judicial Interventions', 'Deference, clarity, and quickness under bench grilling', 10.00, 35.00),
(10, 3, 'Court Mannerisms & Ethics', 'Proper courtroom address, timing, and respectful advocacy', 10.00, 25.00),

-- Literature (Template 4)
(11, 4, 'Thematic Depth & Originality', 'Freshness of perspective and intellectual resonance', 10.00, 40.00),
(12, 4, 'Stylistic Command & Diction', 'Grammar, metaphoric density, structure, voice', 10.00, 35.00),
(13, 4, 'Audience Resonance & Impact', 'Emotional delivery and structural resolution', 10.00, 25.00);
SET IDENTITY_INSERT criteria OFF;

-- 6. Insert Events
SET IDENTITY_INSERT events ON;
INSERT INTO events (event_id, category_id, template_id, title, description, start_date, end_date, status, created_by) VALUES
(1, 1, 1, 'CodeGenesis National Hackathon 2026', '36-hour cloud innovation sprint', '2026-10-25 09:00:00', '2026-10-26 21:00:00', 'ACTIVE', 1),
(2, 2, 2, 'Tarang Classical Dance Championship', 'All-India inter-collegiate classical dance festival', '2026-10-27 10:00:00', '2026-10-28 18:00:00', 'UPCOMING', 1),
(3, 3, 3, 'National Moot Court Competition 2026', 'Appellate advocacy on constitutional data privacy', '2026-10-29 09:00:00', '2026-10-30 17:00:00', 'UPCOMING', 2),
(4, 4, 4, 'Kavya Renaissance Literary Meet', 'Poetry and critical discourse symposium', '2026-11-01 10:00:00', '2026-11-01 16:00:00', 'UPCOMING', 2);
SET IDENTITY_INSERT events OFF;

-- 7. Insert Rounds
SET IDENTITY_INSERT rounds ON;
INSERT INTO rounds (round_id, event_id, round_number, round_name, is_final, status) VALUES
(1, 1, 1, 'Preliminary Prototype Screening', 0, 'EVALUATION'),
(2, 1, 2, 'Grand Finale Live Demo', 1, 'SCHEDULED'),
(3, 2, 1, 'Solo Classical Dance Prelims', 1, 'SCHEDULED'),
(4, 3, 1, 'Quarter-Final Memorial Bench', 1, 'SCHEDULED'),
(5, 4, 1, 'Manuscript Evaluation Round', 1, 'SCHEDULED');
SET IDENTITY_INSERT rounds OFF;

-- 8. Insert Teams
SET IDENTITY_INSERT teams ON;
INSERT INTO teams (team_id, event_id, team_name, lead_user_id) VALUES
(1, 1, 'Team AlphaBits', 7),
(2, 2, 'Rhythm Beats', 9),
(3, 3, 'Lex Justitia Advocates', 10),
(4, 4, 'The Solitary Scribes', 11);
SET IDENTITY_INSERT teams OFF;

-- 9. Insert Team Members
SET IDENTITY_INSERT team_members ON;
INSERT INTO team_members (team_member_id, team_id, user_id) VALUES
(1, 1, 7), -- Aman Gupta in AlphaBits
(2, 1, 8), -- Sneha Rao in AlphaBits
(3, 2, 9), -- Kabir Mehta
(4, 3, 10), -- Ananya Desai
(5, 4, 11); -- Vikramaditya
SET IDENTITY_INSERT team_members OFF;

-- 10. Insert Submissions (Pointing to Azure Blob Storage URLs)
SET IDENTITY_INSERT submissions ON;
INSERT INTO submissions (submission_id, round_id, team_id, file_blob_url, file_name, mime_type, file_size_bytes, file_sha256, submission_notes) VALUES
(1, 1, 1, 'https://scorevaultstorage.blob.core.windows.net/submissions/round-1/team-1/source-code.zip', 'source-code.zip', 'application/zip', 14580214, 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855', 'Full-stack AI health assistant code repo with docker-compose'),
(2, 3, 2, 'https://scorevaultstorage.blob.core.windows.net/submissions/round-3/team-2/performance.mp4', 'performance.mp4', 'video/mp4', 214580214, '5d41402abc4b2a76b9719d911017c592b0c3677b47b4d1c1cf604085cbbe0702', 'Bharatanatyam varnam entry video'),
(3, 4, 3, 'https://scorevaultstorage.blob.core.windows.net/submissions/round-4/team-3/memorial-appellant.pdf', 'memorial-appellant.pdf', 'application/pdf', 3450124, 'a665a45920422f9d417e4867efdc4fb8a04a1f3fff1fa07e998e86f7f7a27ae3', 'Written submissions on behalf of the Appellants'),
(4, 5, 4, 'https://scorevaultstorage.blob.core.windows.net/submissions/round-5/team-4/kavya-manuscript.pdf', 'kavya-manuscript.pdf', 'application/pdf', 1245012, '7bc1e26b776112d3d927a4d538e785d0d1e564d2576b8822003c2009a7b9db83', 'Original poetry anthology');
SET IDENTITY_INSERT submissions OFF;

-- 11. Insert Conflict-Free Judge Assignments
-- (Judge 3 is from IIT Delhi; Team 1 is from BITS Pilani -> No conflict!)
SET IDENTITY_INSERT judge_assignments ON;
INSERT INTO judge_assignments (assignment_id, round_id, judge_user_id, team_id, status) VALUES
(1, 1, 3, 1, 'SUBMITTED');
SET IDENTITY_INSERT judge_assignments OFF;

-- 12. Insert Evaluated & Locked Scores
SET IDENTITY_INSERT scores ON;
INSERT INTO scores (score_id, assignment_id, criterion_id, raw_score, weighted_score, feedback_json_url, is_locked) VALUES
(1, 1, 1, 9.50, 2.850, 'https://scorevaultstorage.blob.core.windows.net/feedback-json/round-1/score-1.json', 1), -- Innovation (9.5/10 * 30% = 2.85)
(2, 1, 2, 9.00, 2.700, 'https://scorevaultstorage.blob.core.windows.net/feedback-json/round-1/score-2.json', 1), -- Architecture (9.0/10 * 30% = 2.70)
(3, 1, 3, 8.50, 2.125, 'https://scorevaultstorage.blob.core.windows.net/feedback-json/round-1/score-3.json', 1), -- Working Demo (8.5/10 * 25% = 2.125)
(4, 1, 4, 9.00, 1.350, 'https://scorevaultstorage.blob.core.windows.net/feedback-json/round-1/score-4.json', 1); -- Pitch (9.0/10 * 15% = 1.35)
-- Total Weighted Score = 2.85 + 2.70 + 2.125 + 1.35 = 9.025 out of 10.00
SET IDENTITY_INSERT scores OFF;

-- 13. Audit Log Initial Records
INSERT INTO audit_log (entity_name, entity_id, action_type, old_value, new_value, performed_by_user_id, ip_address) VALUES
('events', '1', 'CREATE_EVENT', NULL, '{"title": "CodeGenesis National Hackathon 2026"}', 1, '192.168.1.10'),
('submissions', '1', 'UPLOAD_SUBMISSION', NULL, '{"file_name": "source-code.zip", "size": 14580214}', 7, '103.21.244.2'),
('scores', '1', 'SCORE_LOCKED', '{"is_locked": 0}', '{"is_locked": 1, "total_weighted": 9.025}', 3, '14.139.45.130');
GO
