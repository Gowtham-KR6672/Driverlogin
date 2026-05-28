import os
from app import get_db, recalculate_entry_distance

def fix_all_distances():
    with get_db() as conn:
        with conn.cursor() as cur:
            cur.execute("SELECT id FROM work_entries")
            entries = cur.fetchall()
            print(f"Found {len(entries)} work entries. Recalculating...")
            
            for entry in entries:
                entry_id = entry['id']
                # The recalculate_entry_distance function now includes the noise filtering logic
                # So this will automatically fix the huge distance numbers
                new_distance = recalculate_entry_distance(cur, entry_id)
                print(f"Entry #{entry_id} -> New Distance: {new_distance:.2f} km")
                
        conn.commit()
    print("All distances have been recalculated and fixed!")

if __name__ == "__main__":
    # Ensure DATABASE_URL is set in the environment or fallback to local
    print("Starting distance fix...")
    fix_all_distances()
