# ScoreVault backend (Spring Boot 3, Java 17)

Mid-review feature: **login (JWT) + create / list rubric templates** (admin creates; weights must total 100).

## Run
1. Install Java once:  `winget install Microsoft.OpenJDK.17`  (then open a NEW PowerShell)
2. From this folder:  `powershell -ExecutionPolicy Bypass -File .\run.ps1`
3. Enter the Azure SQL password for `scoreadmin` when asked (it is never written to disk).
4. Open http://localhost:8080 and sign in with the demo accounts from the main README.

## Notes
- Secrets come from environment variables (`DB_PASSWORD`, `JWT_SECRET`); nothing secret is in the repository.
- All SQL uses bound parameters (no string concatenation).
- Login, failed logins and rubric creation are written to `audit_log`.
