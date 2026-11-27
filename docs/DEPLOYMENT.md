# ScoreVault deployment guide

ScoreVault is one Spring Boot application: it serves the HTML/CSS/JavaScript frontend and the REST API from the same origin. The app needs Java 17 and an Azure SQL database. Login is email/password only for now; the demo account records remain available in the optional seed script.

## Recommended production setup

Use Azure App Service (Java SE 17) in the same Azure region as Azure SQL. Use GitHub Actions for continuous integration and deployment; Actions builds the app and sends it to Azure, but it is not the web host. Azure's free App Service plan is for trials and learning, has no SLA, and is not supported for production workloads. Budget for a paid App Service plan for a public, real-life event.

1. Build and verify locally with `cd backend` then `.\.tools\apache-maven-3.9.9\bin\mvn.cmd --batch-mode verify` (or run `mvn --batch-mode verify` if Maven is on PATH; GitHub Actions does this automatically).
2. Create an Azure App Service for Java SE 17. Set its health check path to `/actuator/health`.
3. Add these App Service application settings. Do not commit any values to Git:
   - `DB_URL` — `jdbc:sqlserver://<server>.database.windows.net:1433;database=<database>;encrypt=true;trustServerCertificate=false;hostNameInCertificate=*.database.windows.net;loginTimeout=120`
   - `DB_USERNAME` — `scorevault_app`
   - `DB_PASSWORD` — the password for the SQL application account
   - `JWT_SECRET` — a stable, random secret of at least 32 bytes. Keep the same value across deploys and restarts so active sessions remain valid. Store it in App Service settings or Key Vault.
   - `AZURE_STORAGE_ACCOUNT_URL` — `https://scorevaultfiles1127raj.blob.core.windows.net`
   - `AZURE_STORAGE_CONTAINER` — `participant-submissions`
   - Enable the App Service **system-assigned managed identity** and grant it **Storage Blob Data Contributor** on the storage account. The application creates the container as private on its first upload. Do not add a storage account key or connection string.
4. In Azure SQL networking, allow the App Service's outbound IP addresses to reach the SQL server. Recheck this list after changing the App Service plan or region. Avoid enabling the broad “Allow Azure services and resources to access this server” rule for production when a narrower firewall rule or private networking is available.
5. For a new, empty database, run `database/schema.sql`, `database/integrity_triggers.sql`, and `database/leaderboard_tiebreak.sql`, then configure the limited application user with `database/app_login.sql` (read its instructions first). Run `database/seed_data.sql` only when you intentionally want sample data. **Never run `schema.sql` against an existing database:** it drops and recreates tables.
6. For a public production site, replace or disable seeded demo users and their published default passwords before launch. Keep demo accounts only in a development/demo database. Create your actual admin, judge, and participant accounts through the app's admin user management.
7. For manual deployment, download the App Service publish profile and add its contents to the GitHub repository secret `AZURE_WEBAPP_PUBLISH_PROFILE` (Repository Settings -> Secrets and variables -> Actions). Never commit or share the profile. The manual workflow at `.github/workflows/deploy-azure.yml` verifies, packages, and deploys the JAR when run from GitHub Actions; ordinary pushes do not deploy. The separate `ci.yml` workflow continues to verify pushes and pull requests.
8. After deploying, configure the App Service health check path as `/actuator/health`, then check `https://<app-host>/actuator/health`, sign in with each role, and check the API pages below.

## Participant file submissions

Participant uploads are stored in private Azure Blob Storage and indexed in the existing SQL `submissions` table. The accepted formats are PDF, DOCX, PPTX, XLSX, ZIP, and MP4, up to 50 MB each. Each team can submit one file per round; assigned judges download it through the authenticated app. The app checks file signatures, stores a SHA-256 digest, and never exposes a public blob URL. The web app's managed identity needs Blob data access before uploads can succeed.

## Free preview option

Render can run the included `backend/Dockerfile` on a free web-service plan. In Render, set the repository root directory to `backend`, choose the Docker runtime, and set `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, and `JWT_SECRET`. Set its health check path to `/actuator/health`. The app honors Render's `PORT` setting.

Treat this as a demo environment, not production: Render free services sleep after 15 minutes idle, can take about a minute to wake, may restart, and Render says free instances are not for production. Connecting Render to Azure SQL also means allowing Render's region-specific shared outbound IP ranges through the Azure SQL firewall; those ranges are shared with other Render services and can change. This is a less isolated database boundary than hosting the app beside the database in Azure.

GitHub Actions is useful with either host for build checks. For this project, **Azure App Service + Azure SQL + GitHub Actions** is the better production deployment path; **Render free + Azure SQL** is only a no-cost preview tradeoff.

## Live smoke checks

With the app running, verify:

- Public: `/`, `/login.html`, `/api/v1/rounds`, `/api/v1/categories`, and `/api/v1/leaderboard/1`.
- Admin: `GET /api/v1/admin/events`, `/api/v1/admin/users`, and `/api/v1/judging/assignments`.
- Judge: `GET /api/v1/scores/my-tasks` and `/api/v1/rubrics`.
- Participant: `GET /api/v1/teams/events` and `/api/v1/teams/mine`.
- Unauthenticated access to admin endpoints returns `401`; a logged-in non-admin receives `403`.

The database integrity SQL checks are in `database/integrity_tests.sql`. Each test is wrapped in a transaction and rolled back so the checks do not alter application rows.
