# Pori

AI voice assistant for Android. Step A: wake-word loop, speech in/out in any language, Claude tool-calling loop, sample tools (time, battery).

## Build
Push to `main`: GitHub Actions builds the debug APK (Actions tab -> latest run -> Artifacts -> `pori-debug-apk`).

## Wake word "Hey Pori"
Put three files in `app/src/main/assets/wakeword/`: `melspectrogram.tflite` and `embedding_model.tflite` (from the openWakeWord project) and `hey_pori.tflite` (train it with the openWakeWord notebook). Without them, tap the orb to talk.

## Architecture
- `core/PoriController` - loop: wake -> listen -> AI -> speak
- `core/PoriForegroundService` - microphone foreground service
- `voice/` - WakeWordDetector, SpeechInput, SpeechOutput
- `brain/` - LlmClient (Claude API), Agent (tool loop), tools/ (Tool + registry)
- A new feature = a new `Tool` class registered in `ToolRegistry`.

## Security note
The API key is stored unencrypted in DataStore. Put it behind your own proxy before sharing the app.
