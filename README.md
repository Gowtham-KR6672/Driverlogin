# Driver Login Work Entry

Simple HTML + Python Flask + PostgreSQL website.

## Features

- Admin login
- Admin can create user logins
- Users can login and submit:
  - Work given person
  - Next start time
  - End time
  - Remarks
- Admin can view all submitted work entries

## Setup

1. Create PostgreSQL database:

```sql
CREATE DATABASE driver_login;
```

2. Create and activate a Python virtual environment:

```powershell
python -m venv .venv
.\.venv\Scripts\Activate.ps1
```

3. Install dependencies:

```powershell
pip install -r requirements.txt
```

4. Set environment variables:

```powershell
$env:DATABASE_URL="postgresql://postgres:your_password@localhost:5432/driver_login"
$env:SECRET_KEY="change-this-secret-key"
$env:ADMIN_USERNAME="admin"
$env:ADMIN_PASSWORD="admin123"
```

5. Run the website:

```powershell
python app.py
```

Open: http://127.0.0.1:5001

The admin account is created automatically the first time the app starts.

## Deploy to Render

This project includes a Render blueprint at `render.yaml`.

Quick path:

1. Push the project to GitHub/GitLab/Bitbucket.
2. In Render, create a **New Blueprint Instance** from the repo.
3. Enter a strong `ADMIN_PASSWORD` when Render prompts for it.
4. Deploy.

The blueprint creates a Python web service and a Render Postgres database. The production start command is:

```bash
gunicorn -w 1 --threads 100 wsgi:application --bind 0.0.0.0:$PORT
```

Full guide: `deploy/DEPLOY_RENDER.md`
