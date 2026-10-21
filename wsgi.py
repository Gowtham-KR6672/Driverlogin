from app import app, init_db, start_keep_alive


init_db()
start_keep_alive()

application = app
