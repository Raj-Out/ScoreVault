# ScoreVault: AI Context Transfer & Project Handoff Document

> **Target Audience:** Any AI model, pair programmer, or developer taking over or continuing work on this project.  
> **Last Updated:** November 23, 2026 
> **Project Directory:** `C:\SEM3 PROJECT`  
> **GitHub Repository:** [https://github.com/Raj-Out/ScoreVault](https://github.com/Raj-Out/ScoreVault)

---

## 1. Project Metadata & Team Profile

- **Project Title:** ScoreVault
- **One-Line Pitch:** A secure portal where organisers run hackathon, dance, law, and literature events, judges score entries on weighted rubrics, and results are audited.
- **Academic Context:** 3rd Semester Integrated Full-Stack Project combining **System Design**, **RDBMS**, **DP-900 (Azure Data Fundamentals)**, and **Cloud Security** (100 total marks).
- **Domain:** Education / Inter-Collegiate Competition Management.
- **Team Setup (Pair):**
  - **Member 1:** Raj Singh | UID: `25LBCS3205` | Section: B | Email: `25LBCS3205@culkomail.in` | GitHub: `Raj-Out`
  - **Member 2:** Prince | UID: `25LBCS3005` | Section: B | Email: `25lbcs3005@culkomail.in`
- **Grading Rule:** Pair members are graded separately via the viva and individual Git commit history.

---

## 2. Commit History & Work Distribution

```plaintext
* b75ab82 - Raj Singh (25LBCS3205@culkomail.in) : feat(backend & web): implement judge assignment, rubric scoring engine, and live leaderboard
* f59cfaa - Raj Singh (25LBCS3205@culkomail.in) : docs(security): add STRIDE threat model and cloud security verification checklist
* c7a243e - Prince (25LBCS3005@culkomail.in) : feat(security): least-privilege DB account, deployment settings, docs update
* b6db9ce - Prince (25LBCS3005@culkomail.in) : feat(backend): JWT login and rubric template API with web page
* 999c25e - Prince (25LBCS3005@culkomail.in) : feat(rdbms): leaderboard tie-break rule; update docs and run order
* 048c182 - Prince (25LBCS3005@culkomail.in) : docs(rdbms, system design): match documents to deployed database; use real bcrypt demo hashes
* 9e4a677 - Prince (25LBCS3005@culkomail.in) : feat(rdbms): add integrity triggers and tests; correct weighted_score scale to 0-100
* 720b21b - Raj Singh (25LBCS3205@culkomail.in) : feat(rdbms & dp-900): implement 13-table 3NF schema, stored procs, views, queries, and data classification
```

---

## 3. Implemented Core Features Status (All 6 Features)

1. **Event & Reusable Weighted Rubric Template Management:**
   - Backend API: `RubricController` & `RubricService` (`POST /api/v1/rubrics`, `GET /api/v1/rubrics`, `GET /api/v1/categories`).
   - Web UI: Create template form, dynamic criteria weights validation (sum = 100%).
2. **Role-Based Access Control (RBAC):**
   - Spring Security with JWT filter, BCrypt password checking, `@PreAuthorize` guards for `ADMIN`, `JUDGE`, and `PARTICIPANT`.
3. **Team/Solo Registration & Multi-Modal Submissions:**
   - Relational schema tables (`teams`, `team_members`, `submissions`). Azure Blob Storage classification for `.zip`, `.mp4`, `.pdf`.
4. **Conflict-of-Interest Aware Judge Assignment Engine:**
   - Backend API: `JudgeAssignmentController` & `JudgeAssignmentService` (`POST /api/v1/judging/assign`).
   - Database trigger: `trg_prevent_judge_conflict_of_interest` raises error 50001 if judge and team member share institutional affiliation; service maps to HTTP 409 Conflict.
5. **Rubric-Based Locked Scoring with Audit Trail:**
   - Backend API: `ScoringController` & `ScoringService` (`GET /api/v1/scores/my-tasks`, `POST /api/v1/scores/submit`).
   - Server-side calculation of weighted criteria scores, automatic score locking (`is_locked = 1`), and immutable trigger audit logging.
6. **Live Leaderboard & Tie-Break Rankings:**
   - Backend API: `LeaderboardController` & `LeaderboardService` (`GET /api/v1/rounds`, `GET /api/v1/leaderboard/{roundId}`).
   - View `vw_round_leaderboard` computing weighted averages and tie-breaks (priority criterion + submission timestamp).
   - Interactive web UI displaying live standings.

   ---

## 4. Update 23 Nov 2026 (Prince, branch feature/participant-access)

**New commits:**
- d754151 feat(backend): participant team registration API
- f092d79 feat(backend): participant results API (finalized rounds only)

**Done and tested in Postman (all under /api/v1/participant, PARTICIPANT role only):**
- POST /teams (create team), GET /teams (my teams), POST /teams/{id}/members (lead adds member by email)
- GET /results (only rounds with status COMPLETED; score, rank, per-criterion averages across judges)
- Identity always comes from the JWT; no team or user id is accepted from the client.
- A judge token gets 403 on these endpoints.

**Not done yet:**
1. Participant web pages (login, my teams, my results)
2. Submission upload (submissions table exists; Azure Blob SAS or local fallback)
3. Finalize one round (sp_finalize_and_lock_round_scores) so /results shows data in the demo
4. Redeploy backend to Azure App Service
5. Update DESIGN_DOCUMENT.md with participant endpoints; write project report and user manual
6. Reconcile schema.sql differences, then viva rehearsal
7. Change the scorevault_app database password and update DB_PASSWORD in Azure (old one is in git history)

**Run locally:** cd backend, set $env:SERVER_PORT = "8081" if 8080 is busy, run .\run.ps1. Never commit database passwords.

---

## 5. Update 24 Nov 2026 (Prince, branch feature/participant-access)

**New since section 4:**
- Participant web page: static/participant.html and participant.js (sign in, My Teams, create team, add member, My Results, My Submissions)
- File upload: POST/GET /api/v1/participant/submissions (.zip/.mp4/.pdf, max 50 MB, type + magic-byte check, SHA-256 stored, files saved locally in backend/uploads, which is git-ignored). Azure Blob is NOT wired in; the design says SAS links, the code stores locally.
- Round 1 (Preliminary Prototype Screening) was finalized with sp_finalize_and_lock_round_scores, so GET /results shows Team AlphaBits #1 with 90.25. Rounds 2 to 5 are still SCHEDULED with no judge assignments.

**Branch:** feature/participant-access (not merged into main yet). Run: git fetch, git checkout feature/participant-access, git pull.

**Still to do (in this order):**
1. Redeploy backend to Azure App Service (manual; no GitHub Actions workflow exists). Test the live site afterwards.
2. Change the scorevault_app database password (the old one is in git history of database/app_login.sql, commit c7a243e) and update DB_PASSWORD in the App Service settings to match.
3. Create one judge assignment in a SCHEDULED round so a judge can be shown scoring live (may need the round status changed first).
4. Remove the personal test upload (file_name OS_Unit-1to5_Notes_Handwritten.pdf) from the submissions table if still present.
5. Update docs/DESIGN_DOCUMENT.md with the /api/v1/participant/** endpoints; write the project report and user manual.
6. scorevault-bridge.js has demo passwords and an auto-login as admin; remove or be ready to explain it. Check that index.html shows the user's name (login returns "name", app.js reads "fullName").
7. Merge to main when tested (pull request on GitHub), then rehearse the viva. Evaluation is 27 Nov before 2:30.

**Run locally:** cd backend; $env:SERVER_PORT = "8081" if Steam or another program uses 8080; .\run.ps1. Never commit database passwords.









