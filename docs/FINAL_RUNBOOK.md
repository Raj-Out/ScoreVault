# ScoreVault: Final Steps Runbook (before the 27 Nov evaluation)

Steps that need credentials (Azure, database) are done by hand. Do them in this order.

## 1. Merge the branch (before the demo if it runs from `main`)
```powershell
git checkout feature/participant-access
git pull
git merge main        # already done once; repeat if main changed
git push
```
Then on GitHub: Pull requests, New pull request, `feature/participant-access` into `main`, Merge.

## 2. Rotate the database password (old one is in git history, commit c7a243e)
1. In VS Code, open an SQL window connected to `scorevault-db` as `scoreadmin`. Do NOT save the file.
2. Run: `ALTER USER scorevault_app WITH PASSWORD = N'<NEW 16+ char password>';`
3. Azure Portal, App Service, Settings, Environment variables: set `DB_PASSWORD` to the same value, Apply, Restart.
4. Locally: `$env:DB_PASSWORD = "<new password>"` before `.\run.ps1`.
5. Never write the new password in any file in the repo.

## 3. Prepare demo data
Run `database/demo_setup.sql` as `scoreadmin`. It removes the personal test upload row, sets Round 2 to
EVALUATION and assigns the tech judge to Team AlphaBits. Also delete the matching file from `backend/uploads/`.

## 4. Redeploy to Azure App Service
```powershell
cd backend
.\mvnw.cmd -DskipTests clean package      # or: mvn -DskipTests clean package
az webapp deploy --resource-group <RG> --name <APP_NAME> --src-path target\<artifact>.jar --type jar
```
Replace `<RG>`, `<APP_NAME>` and the jar name with your own. Then test the live site (below).

## 5. Live-site smoke test
| Role | Check |
| :--- | :--- |
| Participant (`lead.alphabits@...`) | `/participant.html`: sign in, My Teams, My Results shows AlphaBits #1 (90.25), upload a small PDF |
| Judge (`judge.tech@...`) | `/judging.html`: shows Team AlphaBits, Round 2; move sliders; Finalize and lock; then it appears locked |
| Admin | `/admin.html`: assignments and audit log; `/leaderboard.html`: standings |
| Security | Judge token on `/api/v1/participant/teams` returns 403; no token on any `/api/v1/**` page returns 401 |

## 6. Viva talking points (be ready to explain)
- Uploads are stored on the server (local `uploads/`), not Azure Blob yet; the design describes SAS URLs as the production step.
- The login page has demo credential buttons for the role-switch demo; no passwords are in `scorevault-bridge.js` any more.
- `judging.html` still contains decorative badges (HSM / X.509 / RSA-PSS "seal") from the Stitch design. They are visual only; the real protection is the DB lock trigger and audit log.
