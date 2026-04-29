# Elder Care App

A关怀 App for elderly family members. Installs on their Android phone to periodically report device status (location, battery, network, volume, etc.) so family members can check remotely.

## Architecture

```
elder-care-server/     # Node.js backend (Express)
ElderCareApp/          # Android client (Kotlin + Jetpack Compose)
```

## Features (MVP)

- **Heartbeat reporting**: Device status uploaded every 30 minutes
- **Event reporting**: State changes (WiFi, volume, airplane mode, etc.) reported immediately
- **Offline cache**: Room database caches data when offline, syncs when network recovers
- **Foreground service**: Keeps monitoring alive in background
- **Family web dashboard**: View device status at `http://localhost:3000`

## Tech Stack

### Android Client
- Kotlin, Jetpack Compose
- Room (local cache)
- OkHttp (HTTP upload)
- Foreground Service + WorkManager
- Broadcast Receivers for system events

### Backend Server
- Node.js + Express
- JSON file storage (lightweight, no database needed)

## Getting Started

### Backend

```bash
cd elder-care-server
npm install
node server.js
# Server runs at http://localhost:3000
```

### Android

1. Open `ElderCareApp/` in Android Studio
2. Sync Gradle
3. Run on device or emulator

### API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/device/heartbeat` | Report heartbeat |
| POST | `/api/device/event` | Report event |
| GET | `/api/device/:id/latest` | Get latest status |
| GET | `/api/device/:id/heartbeats` | Get heartbeat history |
| GET | `/api/device/:id/events` | Get event history |
| GET | `/api/devices` | List all devices |
| POST | `/api/clear` | Clear all data |

## Project Status

MVP stage - basic heartbeat reporting and status display are working. Next steps:

- [ ] Background service stability on Chinese ROM devices (Xiaomi, Huawei, OPPO, etc.)
- [ ] Location permission handling and background location
- [ ] Push notifications for abnormal states
- [ ] Historical data charts
- [ ] Multi-device support
