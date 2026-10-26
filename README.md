# ScoreVault: Secure Multi-Category Event Evaluation & Auditing Portal

> **Integrated Full-Stack Project**  
> **Course:** System Design, RDBMS, DP-900 (Azure Data Fundamentals), Cloud Security  
> **Milestone:** Design Review (October 19, 2026)  
> **Domain:** Education / Competition Management  

---

## 👥 Team Information

| Member | Full Name | UID | Section | University Email |
| :--- | :--- | :--- | :--- | :--- |
| **Member 1** | Raj Singh | `25LBCS3205` | B | `25LBCS3205@culkomail.in` |
| **Member 2** | Prince | `25LBCS3005` | B | `25lbcs3005@culkomail.in` |

**Team Type:** Pair (Requires minimum 5 core features; ScoreVault implements 6)

---

## 📌 Project Overview

**One-line Summary:**  
*A secure portal where organisers run hackathon, dance, law, and literature events, judges score entries on weighted rubrics, and results are audited.*

### Problem Statement
College fests, hackathons, and academic competitions typically evaluate participants using paper scoring sheets or shared spreadsheets. This traditional approach introduces severe risks:
1. **Calculation & Human Errors:** Manual computation of weighted scores and tie-breakers.
2. **Inconsistent Rubrics:** Ad-hoc criteria with no standardization across rounds or categories.
3. **Score Tampering & Retroactive Edits:** Unaudited changes to scores post-evaluation.
4. **Conflicts of Interest:** Judges evaluating teams from their own institute, department, or circle.
5. **Delayed Results & Zero Auditability:** Lack of transparent audit trails indicating who scored, changed, or locked results.

### Solution: ScoreVault
**ScoreVault** is a cloud-native, secure, three-tier platform providing:
- **Reusable Weighted Rubric Templates:** Specialized rubrics for Hackathons (code/innovation), Dance (choreography/rhythm), Moot Court (legal reasoning/presentation), and Literature (originality/style).
- **Automated Conflict-of-Interest Blocking:** Prevents judges from being assigned to participants from matching affiliations.
- **Transactional Score Locking & Tamper Prevention:** Immutable score finalization backed by database triggers and audit logs.
- **Multi-Modal Submissions via Cloud Storage:** Secure upload of code ZIPs, performance videos, and legal/literary PDFs to Azure Blob Storage with SAS token authorization.
- **Cached Live Leaderboards & Power BI Analytics:** Sub-second result views on peak event days and deep insights into judge scoring tendencies (harsh vs. lenient).

---

## 🎯 6 Core Features

1. **Event & Reusable Weighted Rubric Template Management:** Multi-round event creation with customizable criteria weights ($100\%$ validation).
2. **Role-Based Access Control (RBAC):** Distinct interfaces, permissions, and API guards for `ADMIN`, `JUDGE`, and `PARTICIPANT`.
3. **Team/Solo Registration & Cloud Submissions:** Team assembly, lead management, and direct-to-cloud file uploads (ZIP, MP4, PDF).
4. **Conflict-of-Interest Aware Judge Assignment:** Validation engine guaranteeing impartial evaluations based on user affiliation.
5. **Rubric-Based Locked Scoring with Audit Trail:** Criterion-level scoring with mandatory locking and immutable database trigger audit logs.
6. **Cached Live Leaderboard & Power BI Analytics:** Automated ranking with configurable tie-break rules and judge scoring variance analytics.

---

## 🛠️ Planned Tech Stack

| Layer | Technology | Details / Role |
| :--- | :--- | :--- |
| **Frontend** | React 18, Tailwind CSS, Vite | Responsive UI, dynamic rubric sliders, live leaderboard, PDF/video preview |
| **Backend** | Java 17, Spring Boot 3.x, Spring Security | REST API, JWT authentication, BCrypt password hashing, RBAC filters |
| **Relational Database** | Azure SQL Database | 13 related tables in 3NF, ACID transactions, triggers, indexes, views |
| **Unstructured / Non-Relational**| Azure Blob Storage | Private containers for code ZIPs, MP4 videos, and PDF briefs |
| **Cloud Hosting & Secrets** | Azure App Service, Azure Key Vault | HTTPS encryption, zero credentials in code, IP firewall rules |
| **Analytics & BI** | Microsoft Power BI | DirectQuery / Azure SQL connector for judge bias and score distribution |
| **Dev Tools** | GitHub, Maven, Postman, draw.io | Version control, dependency management, API testing, UML diagrams |

---

## 📚 Subject Requirements Mapping

| Subject | ScoreVault Implementation | Key Deliverable |
| :--- | :--- | :--- |
| **System Design** (20 pts) | Three-tier architecture, C4 container diagram, sequence flows, REST API spec (`/api/v1/...`), Trade-off: Monolith vs Microservices & Leaderboard Caching. | [docs/DESIGN_DOCUMENT.md](docs/DESIGN_DOCUMENT.md) |
| **RDBMS** (20 pts) | 13-table 3NF schema, PK/FK constraints, `vw_round_leaderboard`, `trg_audit_score_changes` plus 5 integrity triggers, `sp_finalize_and_lock_round_scores`, ACID transactions, 13 automated integrity tests. | [docs/DATABASE_DESIGN.md](docs/DATABASE_DESIGN.md)<br>[database/schema.sql](database/schema.sql) |
| **DP-900** (20 pts) | 3-way data classification (Structured: Azure SQL, Semi-structured: JSON feedback, Unstructured: Azure Blob Storage), Power BI analytics models. | [docs/DP900_DATA_CLASSIFICATION.md](docs/DP900_DATA_CLASSIFICATION.md) |
| **Cloud Security** (20 pts) | BCrypt hashing, JWT RBAC, parameterized queries (SQLi defense), XSS escaping, Azure SQL firewall, DB trigger audit logs, STRIDE Threat Model. | [docs/THREAT_MODEL.md](docs/THREAT_MODEL.md) |
| **Integration** (10 pts) | End-to-end integration of Spring Boot, React, Azure SQL, Azure Blob, and Power BI. | Running Cloud App & Git Repository |
| **Report, Demo & Viva** (10 pts)| Final report, live demonstration, clear commit history, test accounts per role. | Final submission bundle |

---

## 📂 Repository Structure

```plaintext
ScoreVault/
├── README.md                               # Project documentation & team credentials
├── docs/
│   ├── DESIGN_DOCUMENT.md                  # System Design: requirements, architecture, APIs, trade-offs
│   ├── DATABASE_DESIGN.md                  # RDBMS: 3NF justification, ER diagram, views, procs, triggers
│   ├── THREAT_MODEL.md                     # Cloud Security: STRIDE threat model, mitigations, checklist
│   └── DP900_DATA_CLASSIFICATION.md        # DP-900: Data classification, Azure architecture, Power BI
├── database/
│   ├── schema.sql                          # DDL: 13 tables, constraints, indexes, views, stored procedure, audit trigger
│   ├── integrity_triggers.sql              # 6 triggers: conflict of interest, score locking, validation, immutable audit log
│   ├── leaderboard_tiebreak.sql            # Leaderboard view with tie-break rules
│   ├── seed_data.sql                       # Sample data for hackathon, dance, law, and literature (dummy data only)
│   ├── integrity_tests.sql                 # 13 automated tests (expect T1 to T13 PASS)
│   └── queries.sql                         # Complex joins, leaderboard queries, aggregation & audit checks
└── [source code directories to be scaffolded for Mid Review]
```

---

## 🗄️ Database Setup (Azure SQL Database)

Run these files, in this order, on an empty database (VS Code with the SQL Server (mssql) extension, or SSMS):

1. `database/schema.sql`
2. `database/integrity_triggers.sql`
3. `database/leaderboard_tiebreak.sql`
4. `database/seed_data.sql`
5. `database/integrity_tests.sql` (the Messages tab should show T1 to T13 all PASS)

`schema.sql` begins with `DROP` statements, so use it only on a new or test database.

## 🔑 Test Credentials (Preview for Evaluation)

| Role | Email | Default Password | Target Portal View |
| :--- | :--- | :--- | :--- |
| **Admin / Organiser** | `admin@scorevault.culkomail.in` | `AdminSecurePass#2026` | Event creator, Rubric designer, Assignment dashboard |
| **Judge (Tech)** | `judge.tech@scorevault.culkomail.in` | `JudgeSecurePass#2026` | Assigned submissions list, Rubric scoring slider |
| **Judge (Law/Moot)**| `judge.law@scorevault.culkomail.in` | `JudgeSecurePass#2026` | Moot court memorial evaluation & oral argument rubric |
| **Participant (Team Lead)**| `lead.alphabits@culkomail.in` | `TeamSecurePass#2026` | Team management, File submission, Live score tracking |
