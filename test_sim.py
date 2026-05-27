import psycopg2
from app import DATABASE_URL, save_location_point, calculate_distance_km
import datetime

def test():
    import psycopg2.extras
    conn = psycopg2.connect(DATABASE_URL)
    conn.autocommit = True
    cur = conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor)
    
    # create a dummy user
    cur.execute("INSERT INTO users (username, password_hash, role) VALUES ('test_driver3', 'hash', 'driver') RETURNING id")
    driver_id = cur.fetchone()[0]
    
    # Start work
    cur.execute(
        """
        INSERT INTO work_entries
        (user_id, work_given_person, vehicle_type, next_start_time, current_lat, current_lng, location_updated_at, tracking_token, remarks)
        VALUES (%s, 'test', 'car', CURRENT_TIMESTAMP, 10.0, 10.0, CURRENT_TIMESTAMP, 'token', 'test_remarks')
        RETURNING *
        """,
        (driver_id,)
    )
    entry = cur.fetchone()
    columns = [desc[0] for desc in cur.description]
    entry_dict = dict(zip(columns, entry))
    
    cur.execute(
        "INSERT INTO work_entry_locations (work_entry_id, lat, lng) VALUES (%s, %s, %s)",
        (entry_dict["id"], 10.0, 10.0)
    )
    
    print("Initial entry:", entry_dict['distance_km'])
    
    # send location 1 (100 meters away)
    lat, lng = 10.001, 10.000 # ~111 meters away
    dist, rows = save_location_point(cur, entry_dict, lat, lng, 10.0)
    print("After point 1: dist", dist, "rows inserted", rows)
    
    # Check what is in work_entry_locations
    cur.execute("SELECT lat, lng, recorded_at FROM work_entry_locations WHERE work_entry_id = %s ORDER BY id", (entry_dict["id"],))
    print("Locations:", cur.fetchall())

if __name__ == "__main__":
    test()
