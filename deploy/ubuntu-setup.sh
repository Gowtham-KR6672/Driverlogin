#!/usr/bin/env bash
set -euo pipefail

APP_DIR="/opt/driver-login"
APP_USER="driverlogin"
DB_NAME="Driver"
DB_USER="driver_login_user"

echo "Installing Ubuntu packages..."
sudo apt update
sudo apt install -y python3 python3-venv python3-pip postgresql postgresql-contrib nginx curl

echo "Creating app user..."
if ! id "$APP_USER" >/dev/null 2>&1; then
  sudo useradd --system --create-home --shell /usr/sbin/nologin "$APP_USER"
fi

echo "Creating app directory..."
sudo mkdir -p "$APP_DIR" /var/log/driver-login
sudo chown -R "$APP_USER:www-data" "$APP_DIR" /var/log/driver-login
sudo chmod 775 /var/log/driver-login

echo "Copy your project files into $APP_DIR before continuing."
echo "Example from your project folder:"
echo "  sudo rsync -av --exclude .venv --exclude __pycache__ ./ $APP_DIR/"

echo "After copying files, run:"
echo "  sudo chown -R $APP_USER:www-data $APP_DIR"
echo "  cd $APP_DIR"
echo "  sudo -u $APP_USER python3 -m venv .venv"
echo "  sudo -u $APP_USER .venv/bin/pip install -r requirements.txt"
echo ""
echo "PostgreSQL setup example:"
echo "  sudo -u postgres psql"
echo "  CREATE DATABASE \"$DB_NAME\";"
echo "  CREATE USER $DB_USER WITH PASSWORD 'CHANGE_PASSWORD';"
echo "  GRANT ALL PRIVILEGES ON DATABASE \"$DB_NAME\" TO $DB_USER;"
echo "  \\c \"$DB_NAME\""
echo "  GRANT ALL ON SCHEMA public TO $DB_USER;"
echo "  \\q"
echo ""
echo "Then create /etc/driver-login.env from deploy/driver-login.env.example."
