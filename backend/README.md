# ScoreVault backend (Spring Boot 3, Java 17)

Mid-review feature: **login (JWT) + create / list rubric templates** (admin creates; weights must total 100).

## Run
1. Install Java once:  `winget install Microsoft.OpenJDK.17`  (then open a NEW PowerShell)
2. From this folder:  `powershell -ExecutionPolicy Bypass -File .\run.ps1`
3. Enter your own Azure SQL JDBC URL when asked, for example `jdbc:sqlserver://<your-server>.database.windows.net:1433;database=<your-database>;encrypt=true;trustServerCertificate=false;hostNameInCertificate=*.database.windows.net;loginTimeout=120`.
4. Enter the database user (press Enter for `scorevault_app`, the limited account created in your own database) and its password when asked. The password is never written to disk.
5. (Optional) Enter your Google OAuth Web client ID when prompted. Press Enter to skip and use the existing email/password login.
6. Open http://localhost:8080. Demo email/password accounts remain available; Google is an additional sign-in method.

## Notes
- The JDBC endpoint comes from `DB_URL`; credentials and the JWT key come from environment variables (`DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`). Nothing secret is stored in the repository.
- All SQL uses bound parameters (no string concatenation).
- Login, failed logins and rubric creation are written to `audit_log`.
- To enable Google login on an existing database, first run `database/migrations/001_google_sign_in.sql` in Azure SQL Query editor. On a new database the column and filtered unique index are already in `database/schema.sql`.
- Create a Google OAuth 2.0 **Web application** client in Google Cloud Console. Add `http://localhost:8080` as an authorized JavaScript origin and set `GOOGLE_CLIENT_ID` to its client ID. For a hosted deployment, also add the exact HTTPS domain as an authorized origin and set the same app setting in Azure App Service. The client ID is public; never put a client secret in this frontend.
- Google verified email accounts link to existing ScoreVault accounts by email and keep their assigned role. New Google registrations are participants and must provide an affiliation; admins assign judge/admin roles through the existing admin tools. The demo email/password login stays enabled.
