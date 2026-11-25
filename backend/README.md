# ScoreVault backend (Spring Boot 3, Java 17)

Mid-review feature: **login (JWT) + create / list rubric templates** (admin creates; weights must total 100).

## Run
1. Install Java once:  `winget install Microsoft.OpenJDK.17`  (then open a NEW PowerShell)
2. From this folder:  `powershell -ExecutionPolicy Bypass -File .\run.ps1`
3. Enter your own Azure SQL JDBC URL when asked, for example `jdbc:sqlserver://<your-server>.database.windows.net:1433;database=<your-database>;encrypt=true;trustServerCertificate=false;hostNameInCertificate=*.database.windows.net;loginTimeout=120`.
4. Enter the database user (press Enter for `scorevault_app`, the limited account created in your own database) and its password when asked. The password is never written to disk.
5. Open http://localhost:8080 and sign in with an existing account or the demo accounts from the main README.

## Notes
- The JDBC endpoint comes from `DB_URL`; credentials and the JWT key come from environment variables (`DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`). Nothing secret is stored in the repository.
- All SQL uses bound parameters (no string concatenation).
- Login, failed logins and rubric creation are written to `audit_log`.
- For a production deployment, environment variables, database setup, SQL firewall, health check, and host options, see [the deployment guide](../docs/DEPLOYMENT.md).
