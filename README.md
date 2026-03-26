# MeetXRecorder - Meeting Recording & AI Summary App

A production-grade Android application that records meetings, transcribes audio in real-time using Google Gemini, and generates structured AI-powered summaries.

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose (100%) |
| Architecture | MVVM (UI → ViewModel → Repository → Room DAO / API) |
| DI | Hilt 2.59.2 |
| Database | Room 2.8.4 |
| Networking | Retrofit 2.11 + OkHttp 4.12 |
| Async | Coroutines + Flow |
| Background | WorkManager |
| AI | Google Gemini 2.5 Flash-Lite |
| Min SDK | API 24 |
| Target SDK | API 36 |
| Build | AGP 9.0.1, Gradle 9.2.1, KSP |

## Architecture

```
UI (Compose Screens)
      ↓
ViewModels (StateFlow, sealed UI states)
      ↓
Repositories (business logic, API orchestration)
      ↓
Room DAOs / Retrofit API
```

Clean modular package structure with strict separation of concerns:

```
com.example.twinmind/
├── data/
│   ├── local/          # Room database, DAOs, entities
│   ├── remote/         # Gemini API interface & DTOs
│   └── repository/     # Recording, Transcription, Summary repos
├── di/                 # Hilt modules (Database, Network)
├── service/
│   ├── recording/      # Foreground audio recording service
│   └── workers/        # WorkManager workers
├── ui/
│   ├── dashboard/      # Session list with swipe-to-delete
│   ├── recording/      # Live recording with timer & status
│   ├── summary/        # Transcript & AI summary tabs
│   ├── navigation/     # NavGraph
│   └── theme/          # Material 3 theming
└── utils/              # Audio, Storage, Constants
```

## Feature Implementation

### 1. Audio Recording System

**Foreground Service** (`AudioRecordingService`) handles the full recording lifecycle:

- **Audio capture**: `AudioRecord` API at 16kHz, 16-bit PCM mono for speech-optimized recording
- **30-second chunking** with 2-second overlap between chunks for seamless transcription boundaries
- **WAV file output**: Raw PCM data with manually written WAV headers, stored in app-internal storage
- **Persistent notification** displaying recording status, elapsed timer, and Stop/Resume actions
- **Session state** persisted in Room — survives process death

**Edge cases implemented:**

| Edge Case | Detection | Behavior |
|---|---|---|
| Phone calls | `TelephonyCallback` (API 31+) / `PhoneStateListener` (legacy) | Pause recording, update status to "Paused - Phone call", auto-resume when call ends |
| Audio focus loss | `AudioFocusRequest` + `OnAudioFocusChangeListener` | Pause recording, update status to "Paused - Audio focus lost", resume on focus gain |
| Microphone source change | `BroadcastReceiver` for `ACTION_HEADSET_PLUG` and `BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED` | Continue recording, show notification warning |
| Low storage | `StatFs` check every 50 read cycles | Stop recording gracefully, save final chunk, show error notification |
| Silent audio | RMS amplitude monitoring with configurable threshold | Warning after 10 seconds of silence: "No audio detected - Check microphone" |
| Process death | Session state in Room + `TerminationRecoveryWorker` | WorkManager reschedules pending transcriptions and incomplete summaries on app restart |

### 2. Transcription System

- Each audio chunk is immediately enqueued for transcription via **WorkManager** upon completion
- Chunks are sent to **Google Gemini 2.5 Flash-Lite** with base64-encoded WAV audio inline
- Transcripts are stored in Room with correct sequence ordering
- **Retry logic**: Exponential backoff, up to 5 attempts per chunk
- **No chunk loss**: Audio files persist on disk; Room tracks transcription status (`PENDING` → `IN_PROGRESS` → `COMPLETED` / `FAILED`)
- Room is the single source of truth — UI observes via `Flow`

### 3. Summary Generation

- Triggered by the user after transcript is ready
- Full transcript sent to **Gemini 2.5 Flash-Lite** with a structured prompt requesting JSON output
- Response parsed into 4 sections: **Title**, **Summary**, **Action Items**, **Key Points**
- Handles both string and array formats in the API response
- Persisted via **WorkManager** (`SummaryWorker`) — continues generating even if the app is killed
- Summary status tracked in Room: `GENERATING` → `COMPLETED` / `FAILED`
- UI shows loading spinner during generation with retry on failure

## Database Schema (Room)

| Table | Key Fields |
|---|---|
| `recording_sessions` | id, startTime, endTime, status, title, durationMs |
| `audio_chunks` | id, sessionId (FK CASCADE), filePath, sequenceNumber, transcriptionStatus |
| `transcripts` | id, sessionId (FK CASCADE), text, sequenceNumber |
| `summaries` | id, sessionId (FK CASCADE), title, summary, actionItems, keyPoints, status |

Foreign key cascading ensures deleting a session cleans up all associated chunks, transcripts, and summaries.

## UI Screens

**Dashboard** — List of recording sessions with title, date, duration, and status. FAB to start recording. Swipe-to-delete with confirmation dialog.

**Recording** — Live recording with animated pulse indicator, real-time timer (persists after stop), status display, silence warning, and live transcript preview as chunks are processed.

**Summary** — Tabbed view with Transcript and Summary tabs. Loading indicators show transcription progress (chunk counter + progress bar). Summary tab shows AI-generated structured content in cards, or a loading spinner during generation.

## Setup

1. Clone the repository
2. Add your Gemini API key to `gradle.properties`:
   ```
   GEMINI_API_KEY=your_api_key_here
   ```
   Get a free key from [Google AI Studio](https://aistudio.google.com/apikey)
3. Open in Android Studio and sync Gradle
4. Build and run on a device with API 24+

## Android Components Used

| Component | Purpose |
|---|---|
| `ForegroundService` | Reliable audio recording with persistent notification |
| `WorkManager` | Background transcription, summary generation, process death recovery |
| `BroadcastReceiver` | Headset connect/disconnect detection |
| `TelephonyCallback` | Phone call state monitoring |
| `AudioFocusRequest` | Audio focus management |
| `Room` | Local database with reactive `Flow` queries |
| `Hilt` | Dependency injection across all layers including WorkManager |
| `Navigation Compose` | Type-safe screen navigation |

## Key Design Decisions

- **`AudioRecord` over `MediaRecorder`**: Enables custom chunking, overlap, silence detection, and raw PCM access — essential for the 30-second chunk + 2-second overlap requirement
- **WAV format**: Universally supported by the Gemini API for audio transcription without requiring additional encoding libraries
- **Non-streaming summary API**: Uses `generateContent` (single response) instead of SSE streaming for reliability with the v1 API — the summary payload is small enough that streaming adds complexity without meaningful UX benefit
- **WorkManager for all background tasks**: Guarantees execution even after process death, with exponential backoff retry policies
- **Static `StateFlow` in Service companion**: Allows the UI to observe recording state without binding to the service, keeping the architecture clean
- **`AtomicBoolean` for capture loop**: Thread-safe control of the recording loop across coroutine contexts
