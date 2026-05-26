want to convert my existing Flask web-based driver tracking system into a native Android application (Kotlin) while keeping my current Flask backend, admin dashboard, and database unchanged.

Project goal

Build a production-ready Android driver tracking app similar to logistics/fleet apps.

The app must continuously track the driver’s live location and send updates to the Flask backend even when:

app is minimized
app is in background
screen is locked
user switches to another app
phone sleeps
internet temporarily disconnects
device reboots
Core Functional Requirements
1. Login

Create a native Android login screen.

Driver logs in using existing Flask API.

Example:

POST /api/login

Store auth token securely.

Use:

EncryptedSharedPreferences / Jetpack Security
2. Start Tracking Button

App must have:

START TRACKING
STOP TRACKING

When START is clicked:

request runtime permissions
validate permissions
start continuous GPS tracking service
start persistent foreground notification
enable floating bubble overlay
send first location immediately

When STOP is clicked:

stop tracking
stop foreground service
remove floating bubble
stop sending location
Permissions Required

Request and implement:

ACCESS_FINE_LOCATION
ACCESS_COARSE_LOCATION
ACCESS_BACKGROUND_LOCATION
FOREGROUND_SERVICE
FOREGROUND_SERVICE_LOCATION
POST_NOTIFICATIONS
WAKE_LOCK
INTERNET
RECEIVE_BOOT_COMPLETED
REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
SYSTEM_ALERT_WINDOW

Permission flow:

Fine location
Background location (“Allow all the time”)
Notification permission
Ignore battery optimization
Display over other apps
Auto-start instructions for OEM devices

Handle Android 10–14 properly.

3. Continuous Background Tracking

Implement a robust background GPS tracking system.

Requirements:

continues when app minimized
continues when screen locked
continues when another app opened
continues when app UI swiped away
resumes after process death
resumes after reboot
works reliably on Xiaomi/Oppo/Vivo/Samsung

Implementation:

Use:

Kotlin
Foreground Service
START_STICKY
FusedLocationProviderClient
BootReceiver
WorkManager fallback

Tracking settings:

Moving:

every 1–3 seconds

Idle:

every 5–10 seconds

High accuracy GPS.

Use adaptive intervals to reduce battery drain.

4. Floating Bubble Overlay (Messenger-style)

When app is minimized, show a floating draggable bubble icon on screen.

Requirements:

visible above other apps
draggable anywhere
persists while tracking active
tapping bubble opens main app
bubble disappears when STOP clicked
bubble survives app UI close
bubble managed by service

Use:

SYSTEM_ALERT_WINDOW
WindowManager
TYPE_APPLICATION_OVERLAY

Behavior:

If app minimized:

show floating icon

If user taps bubble:

open app dashboard

If tracking stopped:

remove bubble

Bubble should resemble app icon.

5. Foreground Notification

Tracking must show persistent notification:

Example:

Driver Tracking Active
Live location tracking in progress

Notification actions:

Open app
Stop tracking

Foreground service required.

6. Live Location Upload

Continuously send GPS to Flask backend.

Example API:

POST /api/driver/location

Payload:

{
  "driver_id": 101,
  "latitude": 13.0827,
  "longitude": 80.2707,
  "speed": 42,
  "accuracy": 8,
  "bearing": 120,
  "timestamp": "2026-05-26T10:00:00Z"
}

Requirements:

retry on failure
queue unsent data
batch sync when online

Use:

Retrofit
OkHttp
7. Offline Storage

If internet unavailable:

store location points locally.

Use:

Room Database

Queue:

timestamp
lat
lng
speed
accuracy

When internet restored:

auto sync pending records.

No data loss.

8. Distance Calculation

Distance must be calculated accurately.

Backend should handle distance calculation.

Algorithm:

Haversine formula.

Rules:

Ignore bad GPS points:

accuracy > 50 meters
distance jump > unrealistic threshold
tiny noise movements < 10 meters

Track:

current trip distance
9. Live Admin Dashboard Updates

Current Flask admin dashboard must show live moving driver.

Implement:

Flask-SocketIO / WebSockets

Flow:

Driver app -> Flask API -> database -> Socket.IO -> admin dashboard

Admin should see:

live moving marker
driver online/offline
last updated timestamp
trip distance
10. Reboot Recovery

If phone restarts while tracking enabled:

tracking resumes automatically.

Use:

BOOT_COMPLETED receiver

Restore:

tracking state
pending queue
foreground service
floating bubble
11. App Close Handling

If user swipes app UI away:

tracking must continue.

If process killed:

service restarts.

If force stop from Android settings:

accept Android limitation.

Implement:

onTaskRemoved()
START_STICKY
12. OEM Battery Handling

Handle aggressive manufacturers:

Xiaomi
Oppo
Vivo
Realme
Samsung

Provide setup screen guiding users to:

disable battery restrictions
enable auto-start
lock app in recents
13. Security

Secure API communication.

Implement:

HTTPS only
token authentication
encrypted local storage
secure session management
14. Architecture

Use clean architecture.

Tech stack:

Android:

Kotlin
MVVM
Jetpack Compose (preferred)
Hilt DI
Retrofit
Room
WorkManager
Foreground Service
Socket.IO client

Backend:

Existing Flask backend
Existing DB
Flask-SocketIO
15. Deliverables

Generate complete production-ready code including:

AndroidManifest.xml
permission handling
login screen
dashboard screen
tracking service
floating bubble overlay service
notification manager
GPS manager
Room database
Retrofit API layer
WebSocket integration
Boot receiver
battery optimization helper
overlay permission helper
offline sync worker
stop/start tracking flow
error handling
logging
16. Existing System

Existing backend already exists.

Do NOT rebuild backend from scratch.

Reuse current Flask APIs and admin dashboard.

Only create Android app frontend + required API integration.
Build this as a real deployable Android Studio project.