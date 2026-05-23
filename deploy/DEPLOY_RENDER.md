# Render Deployment Guide

This app is ready to deploy to Render as a Python web service with Render Postgres.

## What Was Added

- `render.yaml` defines the Render web service and PostgreSQL database.
- `/healthz` checks that the app can connect to the database.
- `.gitignore` keeps local `.env`, virtualenv files, Python cache files, and local backup dumps out of Git.

Render's Flask quickstart uses `pip install -r requirements.txt` for builds and Gunicorn for production startup. This project uses:

```bash
gunicorn wsgi:application --bind 0.0.0.0:$PORT
```

`wsgi.py` runs `init_db()` when the service starts, so the tables and first admin account are created automatically.

## 1. Prepare Your Local Project

Make sure your real local secrets stay only in `.env`. Do not commit `.env`.

Check Python syntax locally:

```powershell
python -m py_compile app.py wsgi.py
```

## 2. Push to GitHub

Render deploys from a Git provider, so push this folder to a GitHub, GitLab, or Bitbucket repository.

```powershell
git init
git add .
git commit -m "Prepare Render deployment"
git branch -M main
git remote add origin https://github.com/YOUR_USERNAME/YOUR_REPO.git
git push -u origin main
```

If this is already a Git repo, only run:

```powershell
git add .
git commit -m "Prepare Render deployment"
git push
```

## 3. Create the Render Services from Blueprint

1. Open the Render Dashboard.
2. Go to **Blueprints**.
3. Click **New Blueprint Instance**.
4. Connect the Git repository containing this project.
5. Render should detect `render.yaml` in the repository root.
6. When prompted for `ADMIN_PASSWORD`, enter a strong admin password.
7. Apply the blueprint.

Render will create:

- Web service: `driver-login`
- PostgreSQL database: `driver-login-db`

The `DATABASE_URL` environment variable is linked automatically from the database.

## 4. Manual Setup Alternative

Use this if you do not want to use `render.yaml`.

### Create PostgreSQL

1. In Render, click **New +**.
2. Select **Postgres**.
3. Name it `driver-login-db`.
4. Create the database.
5. Copy the **Internal Database URL**.

### Create Web Service

1. In Render, click **New +**.
2. Select **Web Service**.
3. Connect your repository.
4. Use these settings:

| Setting | Value |
| --- | --- |
| Runtime | Python |
| Build Command | `python --version && pip install --upgrade pip && pip install -r requirements.txt` |
| Start Command | `gunicorn wsgi:application --bind 0.0.0.0:$PORT` |
| Health Check Path | `/healthz` |

Add these environment variables:

| Key | Value |
| --- | --- |
| `DATABASE_URL` | Internal Database URL from Render Postgres |
| `SECRET_KEY` | A long random secret |
| `ADMIN_USERNAME` | `admin` or your preferred admin username |
| `ADMIN_PASSWORD` | A strong admin password |
| `PYTHON_VERSION` | `3.11.9` |

## 5. Verify the Deployment

After the deploy finishes:

1. Open the Render service URL.
2. Log in with `ADMIN_USERNAME` and `ADMIN_PASSWORD`.
3. Create a user from the admin dashboard.
4. Log in as that user.
5. Allow location permission when the browser asks.
6. Start and end one work entry.
7. Return to the admin dashboard and confirm the entry appears.

Health check URL:

```text
https://YOUR-SERVICE.onrender.com/healthz
```

Expected response:

```json
{"ok":true}
```

## 6. Important Notes

- Browser location access requires HTTPS. Render's `onrender.com` URL provides HTTPS.
- Local `.backup` files are not deployed. Production data lives in Render Postgres.
- The app attempts local `pg_dump` backups after important changes, but Render web service storage is ephemeral and `pg_dump` may not be installed. Use Render Postgres backups or a dedicated backup job for production backups.
- The first admin user is created only if it does not already exist. Changing `ADMIN_PASSWORD` later does not automatically reset an existing admin password in the database.
- If the deploy fails, check the Render logs for database connection errors first.

## 7. Fix: Render Uses Python 3.14 and psycopg2 Crashes

If the deploy log shows this:

```text
ImportError: ... psycopg2/_psycopg.cpython-314-...so: undefined symbol: _PyInterpreterState_Get
```

Render used Python 3.14, but the deployed psycopg2 package was too old for that interpreter.

This project now pins `psycopg2-binary==2.9.11`, which supports newer Python versions. The project also includes `.python-version` with:

```text
3.11.9
```

For an existing Render web service, do all of this:

1. Commit and push `.python-version`, `requirements.txt`, and `render.yaml`.
2. In Render, open the web service.
3. Go to **Environment**.
4. Add or update:

```text
PYTHON_VERSION=3.11.9
```

5. Go to **Manual Deploy**.
6. Select **Clear build cache & deploy**.

On the next build, the log should show Python 3.11.9 near the build command output. If it still shows Python 3.14.3, the service environment variable or committed `.python-version` is not being picked up.
