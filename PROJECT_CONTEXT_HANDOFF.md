# ScoreVault: AI Context Transfer & Project Handoff Document

> **Target Audience:** Any AI model, pair programmer, or developer taking over or continuing work on this project.  
> **Last Updated:** November 11, 2026  
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
