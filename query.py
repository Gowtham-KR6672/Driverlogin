import os
import psycopg2
from app import DATABASE_URL

def main():
    conn = psycopg2.connect(DATABASE_URL)
    cur = conn.cursor()
    cur.execute("SELECT id FROM work_entries ORDER BY id DESC LIMIT 1")
    entry_id = cur.fetchone()[0]
    
    cur.execute("SELECT id, lat, lng, accuracy, recorded_at FROM work_entry_locations WHERE work_entry_id = %s ORDER BY id", (entry_id,))
    rows = cur.fetchall()
    print(f"Total points: {len(rows)}")
    for row in rows:
        print(row)

if __name__ == "__main__":
    main()
