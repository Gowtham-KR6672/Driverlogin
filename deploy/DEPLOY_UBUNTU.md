# Ubuntu 24.04 Deployment

Target stack:

- Ubuntu Desktop 24.04 LTS
- Python + Flask
- PostgreSQL
- Gunicorn
- Nginx
- Cloudflare Tunnel

## 1. Install Packages

```bash
sudo apt update
sudo apt install -y python3 python3-venv python3-pip postgresql postgresql-contrib nginx curl
```

## 2. Copy Project

Recommended location:

```bash
sudo mkdir -p /opt/driver-login
sudo rsync -av --exclude .venv --exclude __pycache__ ./ /opt/driver-login/
sudo useradd --system --create-home --shell /usr/sbin/nologin driverlogin || true
sudo chown -R driverlogin:www-data /opt/driver-login
```

## 3. Python Environment

```bash
cd /opt/driver-login
sudo -u driverlogin python3 -m venv .venv
sudo -u driverlogin .venv/bin/pip install -r requirements.txt
```

## 4. PostgreSQL

```bash
sudo -u postgres psql
```

Inside `psql`:

```sql
CREATE DATABASE "Driver";
CREATE USER driver_login_user WITH PASSWORD 'CHANGE_PASSWORD';
GRANT ALL PRIVILEGES ON DATABASE "Driver" TO driver_login_user;
\c "Driver"
GRANT ALL ON SCHEMA public TO driver_login_user;
\q
```

## 5. Environment File

```bash
sudo cp /opt/driver-login/deploy/driver-login.env.example /etc/driver-login.env
sudo nano /etc/driver-login.env
sudo chmod 600 /etc/driver-login.env
sudo chown root:root /etc/driver-login.env
```

Set real values:

```text
DATABASE_URL=postgresql://driver_login_user:CHANGE_PASSWORD@localhost:5432/Driver
SECRET_KEY=CHANGE_TO_LONG_RANDOM_SECRET
ADMIN_USERNAME=admin
ADMIN_PASSWORD=CHANGE_ADMIN_PASSWORD
PG_DUMP_PATH=/usr/bin/pg_dump
```

## 6. Gunicorn Systemd Service

```bash
sudo mkdir -p /var/log/driver-login
sudo chown driverlogin:www-data /var/log/driver-login
sudo cp /opt/driver-login/deploy/driver-login.service /etc/systemd/system/driver-login.service
sudo systemctl daemon-reload
sudo systemctl enable --now driver-login
sudo systemctl status driver-login
```

## 7. Nginx

```bash
sudo cp /opt/driver-login/deploy/nginx-driver-login.conf /etc/nginx/sites-available/driver-login
sudo ln -sf /etc/nginx/sites-available/driver-login /etc/nginx/sites-enabled/driver-login
sudo nginx -t
sudo systemctl reload nginx
```

Local test:

```bash
curl http://localhost
```

## 8. Cloudflare Tunnel

Install `cloudflared`:

```bash
curl -L --output cloudflared.deb https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64.deb
sudo dpkg -i cloudflared.deb
```

Login:

```bash
cloudflared tunnel login
```

Create tunnel:

```bash
cloudflared tunnel create driver-login
cloudflared tunnel route dns driver-login your-domain.example.com
```

Copy and edit config:

```bash
sudo mkdir -p /etc/cloudflared
sudo cp /opt/driver-login/deploy/cloudflared-config.yml /etc/cloudflared/config.yml
sudo nano /etc/cloudflared/config.yml
```

Replace:

- `DRIVER_LOGIN_TUNNEL_ID`
- `your-domain.example.com`
- credentials JSON filename

Install service:

```bash
sudo cloudflared service install
sudo systemctl restart cloudflared
sudo systemctl status cloudflared
```

## 9. Notes

- Location permission and installable app features require `localhost` or HTTPS.
- Cloudflare Tunnel provides HTTPS for your public domain.
- Monthly backups are stored in `/opt/driver-login/backups`.
- `Driver_latest.backup` is overwritten after important data changes.
