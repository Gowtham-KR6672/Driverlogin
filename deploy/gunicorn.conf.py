bind = "127.0.0.1:8000"
workers = 3
worker_class = "sync"
timeout = 60
accesslog = "/var/log/driver-login/gunicorn-access.log"
errorlog = "/var/log/driver-login/gunicorn-error.log"
capture_output = True
