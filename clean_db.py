import psycopg2
from app import DATABASE_URL

def clean_db():
    conn = psycopg2.connect(DATABASE_URL)
    conn.autocommit = True
    cur = conn.cursor()
    cur.execute("DELETE FROM users WHERE username IN ('test_driver', 'test_driver2', 'test_driver3')")
    print("Deleted test users.")

if __name__ == "__main__":
    clean_db()
