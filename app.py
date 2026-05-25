import csv
import io
import math
import os
import secrets
import shutil
import subprocess
import threading
from contextlib import contextmanager
from datetime import datetime, timedelta
from functools import wraps
from pathlib import Path
from urllib.parse import urlparse, unquote

import psycopg2
import psycopg2.extras
import psycopg2.pool
from psycopg2 import OperationalError
from flask import Flask, Response, flash, g, jsonify, redirect, render_template, request, session, url_for
from dotenv import load_dotenv
from werkzeug.security import check_password_hash, generate_password_hash


load_dotenv()

app = Flask(__name__)
app.secret_key = os.environ.get("SECRET_KEY", "dev-secret-key-change-me")

DATABASE_URL = os.environ.get(
    "DATABASE_URL",
    "postgresql://postgres:postgres@localhost:5432/driver_login",
)
APP_TIMEZONE = os.environ.get("APP_TIMEZONE", "Asia/Kolkata")
BASE_DIR = Path(__file__).resolve().parent
BACKUP_DIR = BASE_DIR / "backups"
PG_DUMP_PATH = os.environ.get("PG_DUMP_PATH") or shutil.which("pg_dump")
ADMIN_ENTRY_LIMIT = int(os.environ.get("ADMIN_ENTRY_LIMIT", "25"))
DB_POOL_MIN = int(os.environ.get("DB_POOL_MIN", "1"))
DB_POOL_MAX = int(os.environ.get("DB_POOL_MAX", "5"))
DB_CONNECT_TIMEOUT = int(os.environ.get("DB_CONNECT_TIMEOUT", "8"))
if PG_DUMP_PATH is None:
    default_pg_dump = Path(r"C:\Program Files\PostgreSQL\18\bin\pg_dump.exe")
    if default_pg_dump.exists():
        PG_DUMP_PATH = str(default_pg_dump)

_db_pool = None
_db_pool_lock = threading.Lock()
_timezone_ready_connections = set()


def get_db_pool():
    global _db_pool

    if _db_pool is None:
        with _db_pool_lock:
            if _db_pool is None:
                _db_pool = psycopg2.pool.ThreadedConnectionPool(
                    DB_POOL_MIN,
                    DB_POOL_MAX,
                    DATABASE_URL,
                    cursor_factory=psycopg2.extras.RealDictCursor,
                    connect_timeout=DB_CONNECT_TIMEOUT,
                )

    return _db_pool


@contextmanager
def get_db():
    pool = get_db_pool()
    conn = pool.getconn()
    close_connection = False

    try:
        conn_id = id(conn)
        if conn_id not in _timezone_ready_connections:
            with conn.cursor() as cur:
                cur.execute("SET TIME ZONE %s", (APP_TIMEZONE,))
            conn.commit()
            _timezone_ready_connections.add(conn_id)

        yield conn
    except Exception:
        close_connection = bool(getattr(conn, "closed", False))
        if not close_connection:
            conn.rollback()
        raise
    finally:
        if not getattr(conn, "closed", False):
            conn.rollback()
        pool.putconn(conn, close=close_connection)


def create_database_backup(label="auto"):
    if not PG_DUMP_PATH:
        app.logger.warning("pg_dump was not found. Database backup skipped.")
        return None

    parsed = urlparse(DATABASE_URL)
    database_name = parsed.path.lstrip("/")
    month_name = datetime.now().strftime("%Y_%m")
    backup_file = BACKUP_DIR / f"{database_name}_{month_name}.backup"
    latest_file = BACKUP_DIR / f"{database_name}_latest.backup"
    BACKUP_DIR.mkdir(exist_ok=True)

    env = os.environ.copy()
    if parsed.password:
        env["PGPASSWORD"] = unquote(parsed.password)

    command = [
        PG_DUMP_PATH,
        "-h",
        parsed.hostname or "localhost",
        "-p",
        str(parsed.port or 5432),
        "-U",
        unquote(parsed.username or "postgres"),
        "-d",
        database_name,
        "-F",
        "c",
        "-b",
        "-f",
        str(backup_file),
    ]

    try:
        subprocess.run(command, check=True, env=env, capture_output=True, text=True)
        shutil.copy2(backup_file, latest_file)
        return backup_file
    except subprocess.CalledProcessError as exc:
        app.logger.warning("Database backup failed: %s", exc.stderr or exc)
        return None


def queue_database_backup(label="auto"):
    thread = threading.Thread(target=create_database_backup, args=(label,), daemon=True)
    thread.start()


def parse_location(lat, lng):
    try:
        lat = float(lat)
        lng = float(lng)
    except (TypeError, ValueError):
        return None

    if lat < -90 or lat > 90 or lng < -180 or lng > 180:
        return None

    return lat, lng


def format_duration(start_time, end_time):
    if not start_time or not end_time:
        return "In progress"

    total_seconds = int((end_time - start_time).total_seconds())
    if total_seconds < 0:
        return "Invalid time"

    hours, remainder = divmod(total_seconds, 3600)
    minutes, seconds = divmod(remainder, 60)
    return f"{hours:02d}:{minutes:02d}:{seconds:02d}"


def calculate_distance_km(lat1, lng1, lat2, lng2):
    radius_km = 6371.0088
    phi1 = math.radians(float(lat1))
    phi2 = math.radians(float(lat2))
    delta_phi = math.radians(float(lat2) - float(lat1))
    delta_lambda = math.radians(float(lng2) - float(lng1))

    a = (
        math.sin(delta_phi / 2) ** 2
        + math.cos(phi1) * math.cos(phi2) * math.sin(delta_lambda / 2) ** 2
    )
    return radius_km * 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))


def serialize_route_points(points):
    return [
        {
            "lat": float(point["lat"]),
            "lng": float(point["lng"]),
            "recorded_at": point["recorded_at"].strftime("%Y-%m-%d %H:%M:%S"),
        }
        for point in points
    ]


MAX_ACCEPTABLE_ACCURACY_M = 100.0
MIN_SAVE_DISTANCE_M = 3.0
MIN_COUNT_DISTANCE_M = 15.0
FALLBACK_ACCURACY_M = 15.0
MAX_REASONABLE_SPEED_MPS = 45.0
MAX_SHORT_INTERVAL_JUMP_M = 400.0
SHORT_INTERVAL_SECONDS = 15.0
MIN_SEGMENT_SECONDS = 1.0


def save_location_point(cur, entry, lat, lng, accuracy):
    try:
        accuracy_value = float(accuracy) if accuracy is not None else None
    except (TypeError, ValueError):
        accuracy_value = None

    existing_distance = float(entry["distance_km"] or 0)

    if accuracy_value is not None and accuracy_value > MAX_ACCEPTABLE_ACCURACY_M:
        return existing_distance, 1

    cur.execute(
        """
        SELECT lat, lng, accuracy, recorded_at
        FROM work_entry_locations
        WHERE work_entry_id = %s
        ORDER BY recorded_at DESC, id DESC
        LIMIT 1
        """,
        (entry["id"],),
    )
    previous_point = cur.fetchone()
    if not previous_point and entry.get("current_lat") is not None and entry.get("current_lng") is not None:
        previous_point = {
            "lat": entry["current_lat"],
            "lng": entry["current_lng"],
            "accuracy": accuracy_value,
            "recorded_at": entry.get("location_updated_at") or entry.get("next_start_time"),
        }

    segment_km = 0
    if previous_point:
        segment_km = calculate_distance_km(
            previous_point["lat"],
            previous_point["lng"],
            lat,
            lng,
        )
        segment_m = segment_km * 1000
        previous_recorded_at = previous_point.get("recorded_at")
        if previous_recorded_at:
            elapsed_seconds = max(
                MIN_SEGMENT_SECONDS,
                (datetime.now(previous_recorded_at.tzinfo) - previous_recorded_at).total_seconds(),
            )
            speed_mps = segment_m / elapsed_seconds

            if (
                speed_mps > MAX_REASONABLE_SPEED_MPS
                or (elapsed_seconds <= SHORT_INTERVAL_SECONDS and segment_m > MAX_SHORT_INTERVAL_JUMP_M)
            ):
                return existing_distance, 1

        prev_accuracy = float(previous_point["accuracy"] or FALLBACK_ACCURACY_M)
        this_accuracy = accuracy_value if accuracy_value is not None else FALLBACK_ACCURACY_M
        accuracy_floor = (prev_accuracy + this_accuracy) / 2

        save_threshold = max(MIN_SAVE_DISTANCE_M, accuracy_floor * 0.6)
        if segment_m < save_threshold:
            return existing_distance, 1

        count_threshold = max(MIN_COUNT_DISTANCE_M, accuracy_floor)
        if segment_m < count_threshold:
            segment_km = 0

    distance_km = existing_distance + segment_km
    cur.execute(
        """
        INSERT INTO work_entry_locations (work_entry_id, lat, lng, accuracy)
        VALUES (%s, %s, %s, %s)
        """,
        (entry["id"], lat, lng, accuracy_value),
    )
    cur.execute(
        """
        UPDATE work_entries
        SET current_lat = %s,
            current_lng = %s,
            location_updated_at = CURRENT_TIMESTAMP,
            distance_km = %s
        WHERE id = %s
        """,
        (lat, lng, distance_km, entry["id"]),
    )
    return distance_km, cur.rowcount


def add_duration(entries):
    for entry in entries:
        entry["duration"] = format_duration(entry["next_start_time"], entry["end_time"])
        entry["distance_display"] = f"{float(entry.get('distance_km') or 0):.2f} km"
    return entries


def total_duration(entries):
    total_seconds = 0
    for entry in entries:
        start_time = entry["next_start_time"]
        end_time = entry["end_time"]
        if start_time and end_time:
            seconds = int((end_time - start_time).total_seconds())
            if seconds > 0:
                total_seconds += seconds

    hours, remainder = divmod(total_seconds, 3600)
    minutes, seconds = divmod(remainder, 60)
    return {
        "display": f"{hours:02d}:{minutes:02d}:{seconds:02d}",
        "hours": round(total_seconds / 3600, 2),
    }


def format_seconds(total_seconds):
    total_seconds = max(int(total_seconds or 0), 0)
    hours, remainder = divmod(total_seconds, 3600)
    minutes, seconds = divmod(remainder, 60)
    return f"{hours:02d}:{minutes:02d}:{seconds:02d}"


def current_month_bounds():
    now = datetime.now()
    month_start = now.replace(day=1, hour=0, minute=0, second=0, microsecond=0)
    if month_start.month == 12:
        next_month = month_start.replace(year=month_start.year + 1, month=1)
    else:
        next_month = month_start.replace(month=month_start.month + 1)
    return month_start, next_month


def get_home_chart_data(user_id):
    today = datetime.now().date()
    start_day = today - timedelta(days=6)

    with get_db() as conn:
        with conn.cursor() as cur:
            cur.execute(
                """
                SELECT
                    DATE(end_time) AS day,
                    COALESCE(SUM(EXTRACT(EPOCH FROM (end_time - next_start_time))) / 3600.0, 0) AS hours,
                    COALESCE(SUM(distance_km), 0) AS km
                FROM work_entries
                WHERE user_id = %s
                    AND end_time IS NOT NULL
                    AND end_time::date >= %s
                    AND end_time::date <= %s
                GROUP BY DATE(end_time)
                """,
                (user_id, start_day, today),
            )
            day_rows = {row["day"]: row for row in cur.fetchall()}

            cur.execute(
                """
                SELECT vehicle_type, COUNT(*) AS count
                FROM work_entries
                WHERE user_id = %s
                GROUP BY vehicle_type
                """,
                (user_id,),
            )
            vehicle_rows = cur.fetchall()

    daily = []
    max_hours = 0.0
    for offset in range(7):
        day = start_day + timedelta(days=offset)
        row = day_rows.get(day)
        hours = float(row["hours"]) if row else 0.0
        km = float(row["km"]) if row else 0.0
        if hours > max_hours:
            max_hours = hours
        daily.append(
            {
                "day_label": day.strftime("%a"),
                "date_label": day.strftime("%d %b"),
                "hours": hours,
                "km": km,
                "hours_label": f"{hours:.1f}h" if hours else "",
            }
        )

    scale = max_hours if max_hours > 0 else 1
    for item in daily:
        item["height_pct"] = round((item["hours"] / scale) * 100, 1)

    vehicle_counts = {"car": 0, "bike": 0}
    for row in vehicle_rows:
        key = (row["vehicle_type"] or "").lower()
        if key in vehicle_counts:
            vehicle_counts[key] = int(row["count"])

    vehicle_total = vehicle_counts["car"] + vehicle_counts["bike"]
    if vehicle_total > 0:
        car_pct = vehicle_counts["car"] / vehicle_total * 100
        bike_pct = 100 - car_pct
    else:
        car_pct = 0
        bike_pct = 0

    circumference = 2 * math.pi * 42
    car_dash = round(circumference * (car_pct / 100), 2)

    return {
        "daily": daily,
        "max_hours_label": f"{max_hours:.1f}h" if max_hours else "0h",
        "vehicles": {
            "car_count": vehicle_counts["car"],
            "bike_count": vehicle_counts["bike"],
            "total": vehicle_total,
            "car_pct": round(car_pct, 1),
            "bike_pct": round(bike_pct, 1),
            "circumference": round(circumference, 2),
            "car_dash": car_dash,
        },
    }


def get_history_entries(user_id, from_date="", to_date=""):
    filters = ["user_id = %s", "end_time IS NOT NULL"]
    params = [user_id]

    if from_date:
        filters.append("end_time::date >= %s")
        params.append(from_date)

    if to_date:
        filters.append("end_time::date <= %s")
        params.append(to_date)

    with get_db() as conn:
        with conn.cursor() as cur:
            cur.execute(
                f"""
                SELECT *
                FROM work_entries
                WHERE {' AND '.join(filters)}
                ORDER BY end_time DESC
                """,
                params,
            )
            return add_duration(cur.fetchall())


def get_admin_users():
    with get_db() as conn:
        with conn.cursor() as cur:
            cur.execute(
                """
                SELECT id, username, role, created_at
                FROM users
                ORDER BY created_at DESC
                """
            )
            return cur.fetchall()


def set_admin_sidebar_users(users=None):
    if users is None:
        users = get_admin_users()
    g.admin_sidebar_users = sorted(
        [user for user in users if user["role"] == "user"],
        key=lambda user: user["username"].lower(),
    )
    return users


def get_admin_home_stats():
    month_start, next_month = current_month_bounds()

    with get_db() as conn:
        with conn.cursor() as cur:
            cur.execute("SELECT COUNT(*) AS count FROM users WHERE role = 'user'")
            employee_count = int(cur.fetchone()["count"] or 0)

            cur.execute(
                """
                SELECT
                    COALESCE(SUM(EXTRACT(EPOCH FROM (COALESCE(end_time, CURRENT_TIMESTAMP) - next_start_time))), 0) AS total_seconds,
                    COALESCE(SUM(distance_km), 0) AS total_km
                FROM work_entries
                WHERE next_start_time >= %s
                    AND next_start_time < %s
                """,
                (month_start, next_month),
            )
            month_summary = cur.fetchone()

            cur.execute(
                """
                SELECT e.*, u.username
                FROM work_entries e
                JOIN users u ON u.id = e.user_id
                WHERE e.end_time IS NULL
                ORDER BY e.location_updated_at DESC NULLS LAST, e.created_at DESC
                """
            )
            active_entries = add_duration(cur.fetchall())

    total_seconds = int(month_summary["total_seconds"] or 0)
    return {
        "employee_count": employee_count,
        "month_label": month_start.strftime("%B %Y"),
        "travel_hours_display": format_seconds(total_seconds),
        "travel_hours": round(total_seconds / 3600, 2),
        "travel_km": round(float(month_summary["total_km"] or 0), 2),
        "active_entries": active_entries,
    }


def get_admin_entries(selected_user_id="", limit=None):
    entry_params = []
    user_filter = ""
    limit_clause = ""

    if selected_user_id:
        user_filter = "WHERE e.user_id = %s"
        entry_params.append(selected_user_id)

    if limit:
        limit_clause = "LIMIT %s"
        entry_params.append(limit)

    with get_db() as conn:
        with conn.cursor() as cur:
            cur.execute(
                f"""
                SELECT e.*, u.username
                FROM work_entries e
                JOIN users u ON u.id = e.user_id
                {user_filter}
                ORDER BY e.created_at DESC
                {limit_clause}
                """,
                entry_params,
            )
            return add_duration(cur.fetchall())


def get_admin_entry_summary(user_id):
    with get_db() as conn:
        with conn.cursor() as cur:
            cur.execute(
                """
                SELECT
                    COUNT(*) AS entry_count,
                    COALESCE(
                        SUM(
                            CASE
                                WHEN end_time IS NOT NULL
                                THEN EXTRACT(EPOCH FROM (end_time - next_start_time))
                                ELSE 0
                            END
                        ),
                        0
                    ) AS total_seconds
                FROM work_entries
                WHERE user_id = %s
                """,
                (user_id,),
            )
            summary = cur.fetchone()

    seconds = int(summary["total_seconds"] or 0)
    hours, remainder = divmod(seconds, 3600)
    minutes, seconds = divmod(remainder, 60)
    return {
        "entry_count": int(summary["entry_count"] or 0),
        "display": f"{hours:02d}:{minutes:02d}:{seconds:02d}",
        "hours": round(int(summary["total_seconds"] or 0) / 3600, 2),
    }


def get_admin_user_summaries():
    with get_db() as conn:
        with conn.cursor() as cur:
            cur.execute(
                """
                SELECT
                    u.id,
                    u.username,
                    COUNT(e.id) AS entry_count,
                    COALESCE(
                        SUM(
                            CASE
                                WHEN e.end_time IS NOT NULL
                                THEN EXTRACT(EPOCH FROM (e.end_time - e.next_start_time))
                                ELSE 0
                            END
                        ),
                        0
                    ) AS total_seconds
                FROM users u
                LEFT JOIN work_entries e ON e.user_id = u.id
                WHERE u.role = 'user'
                GROUP BY u.id, u.username
                ORDER BY u.username
                """
            )
            user_summaries = cur.fetchall()

    for summary in user_summaries:
        seconds = int(summary["total_seconds"])
        hours, remainder = divmod(seconds, 3600)
        minutes, seconds = divmod(remainder, 60)
        summary["total_time"] = f"{hours:02d}:{minutes:02d}:{seconds:02d}"
        summary["total_hours"] = round(int(summary["total_seconds"]) / 3600, 2)

    return user_summaries


def init_db():
    with get_db() as conn:
        with conn.cursor() as cur:
            cur.execute(
                """
                CREATE TABLE IF NOT EXISTS users (
                    id SERIAL PRIMARY KEY,
                    username VARCHAR(80) UNIQUE NOT NULL,
                    password_hash TEXT NOT NULL,
                    role VARCHAR(20) NOT NULL DEFAULT 'user',
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                );
                """
            )
            cur.execute(
                """
                CREATE TABLE IF NOT EXISTS work_entries (
                    id SERIAL PRIMARY KEY,
                    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                    work_given_person VARCHAR(120) NOT NULL,
                    vehicle_type VARCHAR(20) NOT NULL DEFAULT 'car',
                    next_start_time TIMESTAMP NOT NULL,
                    end_time TIMESTAMP,
                    current_lat NUMERIC(10, 7),
                    current_lng NUMERIC(10, 7),
                    location_updated_at TIMESTAMP,
                    distance_km NUMERIC(10, 3) NOT NULL DEFAULT 0,
                    tracking_token TEXT UNIQUE,
                    remarks TEXT NOT NULL,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                );
                """
            )
            cur.execute(
                """
                CREATE TABLE IF NOT EXISTS work_entry_locations (
                    id SERIAL PRIMARY KEY,
                    work_entry_id INTEGER NOT NULL REFERENCES work_entries(id) ON DELETE CASCADE,
                    lat NUMERIC(10, 7) NOT NULL,
                    lng NUMERIC(10, 7) NOT NULL,
                    accuracy NUMERIC(10, 2),
                    recorded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                );
                """
            )
            cur.execute(
                """
                ALTER TABLE work_entries
                ALTER COLUMN end_time DROP NOT NULL;
                """
            )
            cur.execute(
                """
                ALTER TABLE work_entries
                ADD COLUMN IF NOT EXISTS vehicle_type VARCHAR(20) NOT NULL DEFAULT 'car';
                """
            )
            cur.execute(
                """
                UPDATE work_entries
                SET vehicle_type = 'car'
                WHERE vehicle_type IS NULL;
                """
            )
            cur.execute(
                """
                ALTER TABLE work_entries
                ADD COLUMN IF NOT EXISTS current_lat NUMERIC(10, 7);
                """
            )
            cur.execute(
                """
                ALTER TABLE work_entries
                ADD COLUMN IF NOT EXISTS current_lng NUMERIC(10, 7);
                """
            )
            cur.execute(
                """
                ALTER TABLE work_entries
                ADD COLUMN IF NOT EXISTS location_updated_at TIMESTAMP;
                """
            )
            cur.execute(
                """
                ALTER TABLE work_entries
                ADD COLUMN IF NOT EXISTS distance_km NUMERIC(10, 3) NOT NULL DEFAULT 0;
                """
            )
            cur.execute(
                """
                ALTER TABLE work_entries
                ADD COLUMN IF NOT EXISTS tracking_token TEXT UNIQUE;
                """
            )
            cur.execute(
                """
                CREATE INDEX IF NOT EXISTS idx_work_entry_locations_entry_time
                ON work_entry_locations (work_entry_id, recorded_at);
                """
            )
            cur.execute(
                """
                CREATE INDEX IF NOT EXISTS idx_work_entries_user_created
                ON work_entries (user_id, created_at DESC);
                """
            )
            cur.execute(
                """
                CREATE INDEX IF NOT EXISTS idx_work_entries_user_active
                ON work_entries (user_id, end_time, location_updated_at DESC, created_at DESC);
                """
            )
            cur.execute(
                """
                UPDATE work_entries
                SET remarks = ''
                WHERE remarks IS NULL;
                """
            )
            cur.execute(
                """
                ALTER TABLE work_entries
                ALTER COLUMN remarks SET NOT NULL;
                """
            )

            admin_username = os.environ.get("ADMIN_USERNAME", "admin")
            admin_password = os.environ.get("ADMIN_PASSWORD", "admin123")
            cur.execute("SELECT id FROM users WHERE username = %s", (admin_username,))
            if cur.fetchone() is None:
                cur.execute(
                    """
                    INSERT INTO users (username, password_hash, role)
                    VALUES (%s, %s, 'admin')
                    """,
                    (admin_username, generate_password_hash(admin_password)),
                )
        conn.commit()


def current_user():
    if hasattr(g, "current_user"):
        return g.current_user

    user_id = session.get("user_id")
    if not user_id:
        g.current_user = None
        return None
    with get_db() as conn:
        with conn.cursor() as cur:
            cur.execute("SELECT id, username, role, created_at FROM users WHERE id = %s", (user_id,))
            g.current_user = cur.fetchone()
            return g.current_user


def login_required(view):
    @wraps(view)
    def wrapped(*args, **kwargs):
        if "user_id" not in session:
            return redirect(url_for("login"))

        if current_user() is None:
            session.clear()
            flash("Your login session expired. Please login again.", "error")
            return redirect(url_for("login"))

        return view(*args, **kwargs)

    return wrapped


def admin_required(view):
    @wraps(view)
    def wrapped(*args, **kwargs):
        user = current_user()
        if not user or user["role"] != "admin":
            flash("Admin access required.", "error")
            return redirect(url_for("dashboard"))
        return view(*args, **kwargs)

    return wrapped


@app.context_processor
def inject_user():
    user = current_user()
    admin_sidebar_users = []

    if user and user["role"] == "admin":
        admin_sidebar_users = getattr(g, "admin_sidebar_users", None)
        if admin_sidebar_users is None:
            with get_db() as conn:
                with conn.cursor() as cur:
                    cur.execute(
                        """
                        SELECT id, username, role
                        FROM users
                        WHERE role = 'user'
                        ORDER BY username
                        """
                    )
                    admin_sidebar_users = cur.fetchall()

    return {
        "logged_in_user": user,
        "admin_sidebar_users": admin_sidebar_users,
        "app_timezone": APP_TIMEZONE,
    }


@app.route("/")
def index():
    if "user_id" in session:
        return redirect(url_for("dashboard"))
    return redirect(url_for("login"))


@app.get("/healthz")
def healthz():
    try:
        with get_db() as conn:
            with conn.cursor() as cur:
                cur.execute("SELECT 1")
    except OperationalError:
        return jsonify({"ok": False}), 503

    return jsonify({"ok": True})


@app.route("/login", methods=["GET", "POST"])
def login():
    if request.method == "POST":
        username = request.form.get("username", "").strip()
        password = request.form.get("password", "")
        login_location = parse_location(
            request.form.get("login_lat"),
            request.form.get("login_lng"),
        )

        with get_db() as conn:
            with conn.cursor() as cur:
                cur.execute("SELECT * FROM users WHERE username = %s", (username,))
                user = cur.fetchone()

        if user and check_password_hash(user["password_hash"], password):
            if user["role"] == "user" and not login_location:
                flash("Please allow location permission to login.", "error")
                return redirect(url_for("login"))

            session.clear()
            session["user_id"] = user["id"]
            if login_location:
                session["login_lat"] = login_location[0]
                session["login_lng"] = login_location[1]
            flash("Logged in successfully.", "success")
            return redirect(url_for("dashboard"))

        flash("Invalid username or password.", "error")

    return render_template("login.html")


@app.route("/logout")
def logout():
    session.clear()
    flash("Logged out successfully.", "success")
    return redirect(url_for("login"))


@app.route("/dashboard")
@login_required
def dashboard():
    user = current_user()
    if user is None:
        session.clear()
        flash("Your login session expired. Please login again.", "error")
        return redirect(url_for("login"))

    if user["role"] == "admin":
        return redirect(url_for("admin_dashboard"))
    return redirect(url_for("home"))


@app.route("/admin", methods=["GET", "POST"])
@login_required
@admin_required
def admin_dashboard():
    if request.method == "POST":
        return admin_users()

    users = set_admin_sidebar_users()
    return render_template(
        "admin.html",
        admin_tab="home",
        users=users,
        home_stats=get_admin_home_stats(),
        entries=[],
        selected_user=None,
        total={"display": "00:00:00", "hours": 0, "entry_count": 0},
        user_summaries=[],
        active_location=None,
    )


@app.route("/admin/users", methods=["GET", "POST"])
@login_required
@admin_required
def admin_users():
    if request.method == "POST":
        username = request.form.get("username", "").strip()
        password = request.form.get("password", "")

        if not username or not password:
            flash("Username and password are required.", "error")
            return redirect(url_for("admin_users"))

        try:
            with get_db() as conn:
                with conn.cursor() as cur:
                    cur.execute(
                        """
                        INSERT INTO users (username, password_hash, role)
                        VALUES (%s, %s, 'user')
                        """,
                        (username, generate_password_hash(password)),
                    )
                conn.commit()
            queue_database_backup("create_user")
            flash("User created successfully.", "success")
        except psycopg2.errors.UniqueViolation:
            flash("Username already exists.", "error")

        return redirect(url_for("admin_users"))

    users = set_admin_sidebar_users()
    return render_template(
        "admin.html",
        admin_tab="users",
        users=users,
        entries=[],
        selected_user=None,
        total={"display": "00:00:00", "hours": 0, "entry_count": 0},
        user_summaries=[],
        active_location=None,
        home_stats=None,
    )


@app.route("/admin/work")
@login_required
@admin_required
def admin_work():
    selected_user_id = request.args.get("user_id", "").strip()
    with get_db() as conn:
        with conn.cursor() as cur:
            cur.execute(
                """
                SELECT id, username, role, created_at
                FROM users
                ORDER BY created_at DESC
                """
            )
            users = cur.fetchall()
            set_admin_sidebar_users(users)
            selected_user = None
            user_summaries = []

            if selected_user_id:
                cur.execute(
                    "SELECT id, username, role FROM users WHERE id = %s AND role = 'user'",
                    (selected_user_id,),
                )
                selected_user = cur.fetchone()

            if selected_user:
                cur.execute(
                    """
                    SELECT e.*, u.username
                    FROM work_entries e
                    JOIN users u ON u.id = e.user_id
                    WHERE e.user_id = %s
                    ORDER BY e.created_at DESC
                    LIMIT %s
                    """,
                    (selected_user["id"], ADMIN_ENTRY_LIMIT),
                )
                entries = add_duration(cur.fetchall())
                cur.execute(
                    """
                    SELECT
                        COUNT(*) AS entry_count,
                        COALESCE(
                            SUM(
                                CASE
                                    WHEN end_time IS NOT NULL
                                    THEN EXTRACT(EPOCH FROM (end_time - next_start_time))
                                    ELSE 0
                                END
                            ),
                            0
                        ) AS total_seconds
                    FROM work_entries
                    WHERE user_id = %s
                    """,
                    (selected_user["id"],),
                )
                summary = cur.fetchone()
                seconds = int(summary["total_seconds"] or 0)
                hours, remainder = divmod(seconds, 3600)
                minutes, seconds = divmod(remainder, 60)
                total = {
                    "display": f"{hours:02d}:{minutes:02d}:{seconds:02d}",
                    "hours": round(int(summary["total_seconds"] or 0) / 3600, 2),
                    "entry_count": int(summary["entry_count"] or 0),
                }
            else:
                entries = []
                total = {"display": "00:00:00", "hours": 0, "entry_count": 0}
            active_location = None

            if selected_user:
                cur.execute(
                    """
                    SELECT id, current_lat, current_lng, location_updated_at, distance_km
                    FROM work_entries
                    WHERE user_id = %s
                        AND end_time IS NULL
                        AND current_lat IS NOT NULL
                        AND current_lng IS NOT NULL
                    ORDER BY location_updated_at DESC NULLS LAST, created_at DESC
                    LIMIT 1
                    """,
                    (selected_user["id"],),
                )
                active_location = cur.fetchone()

    return render_template(
        "admin.html",
        admin_tab="work",
        users=users,
        entries=entries,
        selected_user=selected_user,
        total=total,
        user_summaries=user_summaries,
        active_location=active_location,
        home_stats=None,
    )


@app.route("/admin/users/<int:user_id>/reset-password", methods=["POST"])
@login_required
@admin_required
def reset_user_password(user_id):
    new_password = request.form.get("new_password", "").strip()

    if not new_password:
        flash("New password is required.", "error")
        return redirect(url_for("admin_users"))

    with get_db() as conn:
        with conn.cursor() as cur:
            cur.execute(
                """
                UPDATE users
                SET password_hash = %s
                WHERE id = %s AND role = 'user'
                """,
                (generate_password_hash(new_password), user_id),
            )
            updated = cur.rowcount
        conn.commit()

    if updated:
        queue_database_backup("reset_password")
        flash("Password reset successfully. Share the new password with the user.", "success")
    else:
        flash("User not found or password cannot be reset.", "error")

    return redirect(url_for("admin_users"))


@app.route("/admin/users/<int:user_id>/delete", methods=["POST"])
@login_required
@admin_required
def delete_user(user_id):
    with get_db() as conn:
        with conn.cursor() as cur:
            cur.execute(
                """
                DELETE FROM users
                WHERE id = %s AND role = 'user'
                RETURNING username
                """,
                (user_id,),
            )
            deleted_user = cur.fetchone()
        conn.commit()

    if deleted_user:
        queue_database_backup("delete_user")
        flash(f"Deleted user {deleted_user['username']} and their work entries.", "success")
    else:
        flash("User not found or cannot be deleted.", "error")

    return redirect(url_for("admin_users"))


@app.route("/admin/download")
@login_required
@admin_required
def download_admin_data():
    selected_user_id = request.args.get("user_id", "").strip()
    output = io.StringIO()
    writer = csv.writer(output)

    if selected_user_id:
        entries = get_admin_entries(selected_user_id)
        writer.writerow(["User", "Work Given Person", "Vehicle", "Start Time", "End Time", "Total Time", "Travel Distance", "Remarks", "Submitted"])

        for entry in entries:
            writer.writerow(
                [
                    entry["username"],
                    entry["work_given_person"],
                    entry["vehicle_type"].title(),
                    entry["next_start_time"].strftime("%Y-%m-%d %H:%M:%S"),
                    entry["end_time"].strftime("%Y-%m-%d %H:%M:%S") if entry["end_time"] else "In progress",
                    entry["duration"],
                    entry["distance_display"],
                    entry["remarks"],
                    entry["created_at"].strftime("%Y-%m-%d %H:%M:%S"),
                ]
            )
        filename = f"admin_user_{selected_user_id}_entries.csv"
    else:
        summaries = get_admin_user_summaries()
        writer.writerow(["User Name", "Total Time", "Total Hours", "Entry Count"])

        for summary in summaries:
            writer.writerow(
                [
                    summary["username"],
                    summary["total_time"],
                    summary["total_hours"],
                    summary["entry_count"],
                ]
            )
        filename = "admin_all_entry_summary.csv"

    return Response(
        output.getvalue(),
        mimetype="text/csv",
        headers={"Content-Disposition": f"attachment; filename={filename}"},
    )


@app.route("/admin/users/<int:user_id>/location")
@login_required
@admin_required
def admin_user_location(user_id):
    with get_db() as conn:
        with conn.cursor() as cur:
            cur.execute(
                """
                SELECT id, current_lat, current_lng, location_updated_at, distance_km
                FROM work_entries
                WHERE user_id = %s
                    AND end_time IS NULL
                    AND current_lat IS NOT NULL
                    AND current_lng IS NOT NULL
                ORDER BY location_updated_at DESC NULLS LAST, created_at DESC
                LIMIT 1
                """,
                (user_id,),
            )
            location = cur.fetchone()

    if not location:
        return jsonify({"has_location": False})

    route_points = get_entry_route_points(location["id"])

    return jsonify(
        {
            "has_location": True,
            "lat": float(location["current_lat"]),
            "lng": float(location["current_lng"]),
            "distance_km": float(location["distance_km"] or 0),
            "route": serialize_route_points(route_points),
            "updated_at": location["location_updated_at"].strftime("%Y-%m-%d %H:%M:%S")
            if location["location_updated_at"]
            else "",
        }
    )


@app.route("/home")
@login_required
def home():
    user = current_user()
    if user is None:
        session.clear()
        flash("Your login session expired. Please login again.", "error")
        return redirect(url_for("login"))

    if user["role"] == "admin":
        return redirect(url_for("admin_dashboard"))

    month_start, month_end = current_month_bounds()
    entries = get_history_entries(user["id"], month_start.strftime("%Y-%m-%d"), month_end.strftime("%Y-%m-%d"))

    total_hours = total_duration(entries)
    total_km = 0.0
    for entry in entries:
        total_km += float(entry.get("distance_km") or 0)

    current_month_stats = {
        "total_hours_display": total_hours["display"],
        "total_hours": total_hours["hours"],
        "total_km": f"{total_km:.2f}",
        "total_entries": len(entries),
    }

    chart_data = get_home_chart_data(user["id"])

    return render_template(
        "home.html",
        current_month_stats=current_month_stats,
        chart_data=chart_data,
    )


@app.route("/work-entry", methods=["GET", "POST"])
@login_required
def work_entry():
    user = current_user()
    if user["role"] == "admin":
        return redirect(url_for("admin_dashboard"))

    work_people = ["Vijay Anand P", "Shajahan S", "Prem Anand Y", "HR Department", "Manager", "IT Department", "Other"]

    if request.method == "POST":
        work_given_person = request.form.get("work_given_person", "").strip()
        other_work_given_person = request.form.get("other_work_given_person", "").strip()
        vehicle_type = request.form.get("vehicle_type", "").strip().lower()
        remarks = request.form.get("remarks", "").strip()
        start_location = parse_location(
            request.form.get("start_lat") or session.get("login_lat"),
            request.form.get("start_lng") or session.get("login_lng"),
        )

        if work_given_person == "Other":
            work_given_person = other_work_given_person

        if vehicle_type not in {"car", "bike"}:
            flash("Please select car or bike.", "error")
            return redirect(url_for("work_entry"))

        if not work_given_person or not remarks:
            flash("Please fill all required fields.", "error")
            return redirect(url_for("work_entry"))

        if not start_location:
            flash("Please allow location permission before starting work.", "error")
            return redirect(url_for("work_entry"))

        with get_db() as conn:
            with conn.cursor() as cur:
                cur.execute(
                    """
                    SELECT id
                    FROM work_entries
                    WHERE user_id = %s AND end_time IS NULL
                    LIMIT 1
                    """,
                    (user["id"],),
                )
                if cur.fetchone():
                    flash("Please end the current work before starting a new one.", "error")
                    return redirect(url_for("work_entry"))

                cur.execute(
                    """
                    INSERT INTO work_entries
                    (user_id, work_given_person, vehicle_type, next_start_time, current_lat, current_lng, location_updated_at, tracking_token, remarks)
                    VALUES (%s, %s, %s, CURRENT_TIMESTAMP, %s, %s, CURRENT_TIMESTAMP, %s, %s)
                    RETURNING id
                    """,
                    (
                        user["id"],
                        work_given_person,
                        vehicle_type,
                        start_location[0],
                        start_location[1],
                        secrets.token_urlsafe(32),
                        remarks,
                    ),
                )
                entry = cur.fetchone()
                cur.execute(
                    """
                    INSERT INTO work_entry_locations (work_entry_id, lat, lng)
                    VALUES (%s, %s, %s)
                    """,
                    (entry["id"], start_location[0], start_location[1]),
                )
            conn.commit()

        queue_database_backup("start_work")
        flash("Work started successfully.", "success")
        return redirect(url_for("work_entry"))

    with get_db() as conn:
        with conn.cursor() as cur:
            cur.execute(
                """
                SELECT *
                FROM work_entries
                WHERE user_id = %s AND end_time IS NULL
                ORDER BY created_at DESC
                """,
                (user["id"],),
            )
            entries = add_duration(cur.fetchall())

    active_entry = entries[0] if entries else None
    if active_entry and not active_entry.get("tracking_token"):
        active_entry["tracking_token"] = secrets.token_urlsafe(32)
        with get_db() as conn:
            with conn.cursor() as cur:
                cur.execute(
                    """
                    UPDATE work_entries
                    SET tracking_token = %s
                    WHERE id = %s
                    """,
                    (active_entry["tracking_token"], active_entry["id"]),
                )
            conn.commit()
    active_route = get_entry_route_points(active_entry["id"]) if active_entry else []

    return render_template(
        "work_entry.html",
        work_people=work_people,
        entries=entries,
        active_route=serialize_route_points(active_route),
        background_tracking={
            "enabled": bool(active_entry),
            "endpoint": url_for("background_location_update", _external=True),
            "token": active_entry["tracking_token"] if active_entry else "",
        },
    )


@app.route("/location/update", methods=["POST"])
@login_required
def update_location():
    user = current_user()
    if user["role"] == "admin":
        return jsonify({"ok": False, "message": "Admin location is not tracked."}), 403

    data = request.get_json(silent=True) or {}
    lat = data.get("lat")
    lng = data.get("lng")
    accuracy = data.get("accuracy")

    location = parse_location(lat, lng)
    if not location:
        return jsonify({"ok": False, "message": "Invalid location."}), 400

    lat, lng = location
    try:
        accuracy = float(accuracy) if accuracy is not None else None
    except (TypeError, ValueError):
        accuracy = None

    with get_db() as conn:
        with conn.cursor() as cur:
            cur.execute(
                """
                SELECT id, distance_km, current_lat, current_lng, location_updated_at, next_start_time
                FROM work_entries
                WHERE user_id = %s AND end_time IS NULL
                ORDER BY created_at DESC
                LIMIT 1
                FOR UPDATE
                """,
                (user["id"],),
            )
            entry = cur.fetchone()
            if not entry:
                conn.commit()
                return jsonify({"ok": False, "message": "No active work entry."}), 404

            distance_km, updated = save_location_point(cur, entry, lat, lng, accuracy)
        conn.commit()

    return jsonify({"ok": bool(updated), "distance_km": round(distance_km, 3)})


@app.route("/location/background-update", methods=["POST"])
def background_location_update():
    data = request.get_json(silent=True) or {}
    token = data.get("token")
    lat = data.get("lat")
    lng = data.get("lng")
    accuracy = data.get("accuracy")

    if not token:
        return jsonify({"ok": False, "message": "Tracking token is required."}), 401

    location = parse_location(lat, lng)
    if not location:
        return jsonify({"ok": False, "message": "Invalid location."}), 400

    lat, lng = location
    try:
        accuracy = float(accuracy) if accuracy is not None else None
    except (TypeError, ValueError):
        accuracy = None

    with get_db() as conn:
        with conn.cursor() as cur:
            cur.execute(
                """
                SELECT id, distance_km, current_lat, current_lng, location_updated_at, next_start_time
                FROM work_entries
                WHERE tracking_token = %s AND end_time IS NULL
                LIMIT 1
                FOR UPDATE
                """,
                (token,),
            )
            entry = cur.fetchone()
            if not entry:
                conn.commit()
                return jsonify({"ok": False, "message": "No active work entry."}), 404

            distance_km, updated = save_location_point(cur, entry, lat, lng, accuracy)
        conn.commit()

    return jsonify({"ok": bool(updated), "distance_km": round(distance_km, 3)})


def get_entry_route_points(entry_id):
    with get_db() as conn:
        with conn.cursor() as cur:
            cur.execute(
                """
                SELECT lat, lng, recorded_at
                FROM work_entry_locations
                WHERE work_entry_id = %s
                ORDER BY recorded_at, id
                """,
                (entry_id,),
            )
            return cur.fetchall()


@app.route("/work-entry/<int:entry_id>/route")
@login_required
def work_entry_route(entry_id):
    user = current_user()
    params = [entry_id]
    user_filter = ""

    if user["role"] != "admin":
        user_filter = "AND user_id = %s"
        params.append(user["id"])

    with get_db() as conn:
        with conn.cursor() as cur:
            cur.execute(
                f"""
                SELECT id, distance_km, current_lat, current_lng, location_updated_at
                FROM work_entries
                WHERE id = %s {user_filter}
                """,
                params,
            )
            entry = cur.fetchone()

    if not entry:
        return jsonify({"ok": False, "message": "Entry not found."}), 404

    route_points = get_entry_route_points(entry_id)
    return jsonify(
        {
            "ok": True,
            "has_route": bool(route_points),
            "distance_km": float(entry["distance_km"] or 0),
            "route": serialize_route_points(route_points),
        }
    )


@app.route("/history")
@login_required
def history():
    user = current_user()
    if user["role"] == "admin":
        return redirect(url_for("admin_dashboard"))

    from_date = request.args.get("from_date", "").strip()
    to_date = request.args.get("to_date", "").strip()

    entries = get_history_entries(user["id"], from_date, to_date)
    total = total_duration(entries)

    return render_template(
        "history.html",
        entries=entries,
        from_date=from_date,
        to_date=to_date,
        total=total,
    )


@app.route("/history/download")
@login_required
def download_history():
    user = current_user()
    if user["role"] == "admin":
        return redirect(url_for("admin_dashboard"))

    from_date = request.args.get("from_date", "").strip()
    to_date = request.args.get("to_date", "").strip()
    entries = get_history_entries(user["id"], from_date, to_date)

    output = io.StringIO()
    writer = csv.writer(output)
    writer.writerow(["Work Given Person", "Vehicle", "Start Time", "End Time", "Total Time", "Travel Distance", "Remarks"])

    for entry in entries:
        writer.writerow(
            [
                entry["work_given_person"],
                entry["vehicle_type"].title(),
                entry["next_start_time"].strftime("%Y-%m-%d %H:%M:%S"),
                entry["end_time"].strftime("%Y-%m-%d %H:%M:%S"),
                entry["duration"],
                entry["distance_display"],
                entry["remarks"],
            ]
        )

    filename_parts = ["work_history"]
    if from_date:
        filename_parts.append(f"from_{from_date}")
    if to_date:
        filename_parts.append(f"to_{to_date}")
    filename = "_".join(filename_parts) + ".csv"

    return Response(
        output.getvalue(),
        mimetype="text/csv",
        headers={"Content-Disposition": f"attachment; filename={filename}"},
    )


@app.route("/work-entry/<int:entry_id>/end", methods=["POST"])
@login_required
def end_work_entry(entry_id):
    user = current_user()
    if user["role"] == "admin":
        return redirect(url_for("admin_dashboard"))

    with get_db() as conn:
        with conn.cursor() as cur:
            cur.execute(
                """
                UPDATE work_entries
                SET end_time = CURRENT_TIMESTAMP,
                    location_updated_at = COALESCE(location_updated_at, CURRENT_TIMESTAMP),
                    tracking_token = NULL
                WHERE id = %s
                    AND user_id = %s
                    AND end_time IS NULL
                """,
                (entry_id, user["id"]),
            )
            updated = cur.rowcount
        conn.commit()

    if updated:
        queue_database_backup("end_work")
        flash("Work ended successfully.", "success")
    else:
        flash("Work entry was already ended or not found.", "error")

    return redirect(url_for("work_entry"))


if __name__ == "__main__":
    try:
        init_db()
    except OperationalError as exc:
        print("\nPostgreSQL connection failed.")
        print("Please make sure PostgreSQL is installed, running, and DATABASE_URL is correct.")
        print(f"Current DATABASE_URL: {DATABASE_URL}")
        print("\nExample:")
        print('$env:DATABASE_URL="postgresql://postgres:your_password@localhost:5432/driver_login"')
        print("\nOriginal error:")
        print(exc)
        raise SystemExit(1)

    host = os.environ.get("FLASK_RUN_HOST", "127.0.0.1")
    port = int(os.environ.get("PORT", "5001"))
    print(f"\nDriver Login running at http://{host}:{port}")
    app.run(host=host, port=port, debug=True)
