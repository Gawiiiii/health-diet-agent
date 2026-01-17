# Menu Analyzer Implementation Notes

This document summarizes the changes that enable the end-to-end flow:
capture or upload -> OCR -> analysis -> suggestions -> history.

## Overview

- Android app uses CameraX and ML Kit OCR to extract text.
- Android app sends OCR text and user preferences to a FastAPI backend.
- Backend returns structured JSON with risk hits and suggestions.
- Android app stores history in Room and preferences in DataStore.

## Architecture

- Android client (Kotlin + Compose)
  - CameraX for capture
  - ML Kit for OCR
  - Retrofit + Moshi for HTTP
  - Room for history
  - DataStore for preferences
- Backend (FastAPI + OpenAI)
  - Optional OpenAI call to structure menu items
  - Rule-based risk scoring

## Backend service (FastAPI)

### Key files

- `server/main.py`: API, parsing, rule-based scoring, OpenAI fallback.
- `server/requirements.txt`: Python dependencies.

### Endpoint

`POST /analyze`

Request JSON:
```json
{
  "text": "OCR text here",
  "preferences": {
    "allergies": ["peanut", "milk"],
    "dislikes": ["cilantro"],
    "health_goals": ["low_sugar"]
  }
}
```

Response JSON:
```json
{
  "menu_items": [
    { "name": "Spicy Noodles", "ingredients": ["peanut", "soy"] }
  ],
  "risk_level": "HIGH",
  "hits": [
    { "term": "peanut", "reason": "Allergy match", "level": "HIGH" }
  ],
  "suggestions": [
    "Avoid items containing 'peanut'."
  ]
}
```

### Environment variables

- `OPENAI_API_KEY`: optional. If missing, the service uses a naive parser.
- `OPENAI_MODEL`: optional. Default is `gpt-4o-mini`.

### Run locally

```bash
python -m venv .venv
source .venv/bin/activate
pip install -r server/requirements.txt
export OPENAI_API_KEY=your_key_here
uvicorn server.main:app --reload --host 0.0.0.0 --port 8000
```

## Android app

### Key files

- `app/src/main/java/com/example/myapplication/MainActivity.kt`
- `app/src/main/java/com/example/myapplication/MyApplication.kt`
- `app/src/main/java/com/example/myapplication/AppContainer.kt`
- `app/src/main/java/com/example/myapplication/data/*`
- `app/src/main/java/com/example/myapplication/ui/*`
- `app/src/main/java/com/example/myapplication/viewmodel/*`

### Data flow

1. Capture or pick an image.
2. Run ML Kit OCR and show editable text.
3. Send OCR text and preferences to `/analyze`.
4. Save response in Room and show the result screen.
5. Use History to reopen previous results.

### Permissions

Declared in `app/src/main/AndroidManifest.xml`:

- `android.permission.CAMERA`
- `android.permission.INTERNET`

### Configuration

Set the base URL in `app/src/main/res/values/strings.xml`:

- Emulator: `http://10.0.2.2:8000/`
- Physical device: `http://<your-machine-ip>:8000/`

### Run locally

1. Start the backend.
2. Open the project in Android Studio.
3. Run the app on an emulator or device.

## Storage

- Room database: `menu_analyzer.db` for analysis history.
- DataStore: `user_preferences` for allergies, dislikes, and health goals.

## Troubleshooting

- If the app cannot reach the backend, verify the base URL and device IP.
- If OCR is empty, test with a higher-contrast image.
- If OpenAI fails, the backend still returns a basic parse.
