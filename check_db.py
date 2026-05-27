import psycopg2
from app import DATABASE_URL

def check():
    conn = psycopg2.connect(DATABASE_URL)
    cur = conn.cursor()
    cur.execute("SELECT id, current_lat, current_lng FROM work_entries ORDER BY id DESC LIMIT 5")
    entries = cur.fetchall()
    
    for entry in entries:
        eid = entry[0]
        cur.execute("SELECT COUNT(*) FROM work_entry_locations WHERE work_entry_id = %s", (eid,))
        count = cur.fetchone()[0]
        print(f"Entry {eid}: {count} points, current_lat: {entry[1]}")

if __name__ == "__main__":
    check()
