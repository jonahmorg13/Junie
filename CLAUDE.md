# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

**juni** is a native Android (Kotlin + Jetpack Compose) BYOA agent that operates on a local Obsidian vault on the phone. The user picks any of Claude / OpenAI / Gemini / Ollama, brings their own API key, points juni at a folder via the Storage Access Framework, and has agentic conversations that can read, search, create, edit, move, and attach to vault notes — with photos as input via CameraX. Approval-gated tool calls; conversations persist via Room.

## Build / install / run

The wrapper, `adb`, and the output APK path are identical on every OS — only the wrapper invocation and the log-filter pipe differ between shells. Pick the block for your shell.

macOS / Linux (bash/zsh):

```bash
./gradlew assembleDebug                              # produces app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.juni.app/.MainActivity
adb logcat -d | grep -E "AndroidRuntime|FATAL|com\.juni"   # crash traces
```

Windows (PowerShell):

```powershell
.\gradlew.bat assembleDebug                          # produces app\build\outputs\apk\debug\app-debug.apk
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n com.juni.app/.MainActivity
adb logcat -d | Select-String "AndroidRuntime|FATAL|com\.juni"   # crash traces
```

Notes on the Windows variant: PowerShell needs the leading `.\` to run a script from the current directory; the wrapper itself is `gradlew.bat` (the extensionless `gradlew` is the Unix shell script). `grep` isn't on Windows by default — `Select-String` is the native equivalent and accepts the same regex. `findstr /R` works too if you prefer a cmd-compatible filter, but its regex dialect is different.

`adb` must be on `PATH`. On Windows it ships in `%LOCALAPPDATA%\Android\Sdk\platform-tools\` when installed via Android Studio; either add that to `PATH` or invoke `adb.exe` by full path.

`local.properties` (gitignored) needs an `sdk.dir=...` pointing at the Android SDK. If gradle complains it can't find the SDK on a fresh clone, that's the file to create. On Windows, escape backslashes or use forward slashes: `sdk.dir=C:\\Users\\you\\AppData\\Local\\Android\\Sdk` or `sdk.dir=C:/Users/you/AppData/Local/Android/Sdk` — Java properties files treat `\` as an escape character.

`gradle.properties` pins `org.gradle.java.home` at the JDK gradle should use. Newer system JVMs (24+) break AGP 8.10.x, so the pinned JDK is intentional — typically the JBR shipped with Android Studio (JDK 21). Edit it if your toolchain is elsewhere; don't drop the property entirely. Same backslash-escaping rule applies if you set a Windows path here.

There are no unit tests in the repo. Verification is end-to-end via the device.

**Don't `adb uninstall`** to "fix" things during dev — it wipes the user's API keys (EncryptedSharedPreferences), DataStore settings, and the Room DB of saved conversations. `adb install -r` is enough for almost every change. Schema-incompatible Room edits would normally require uninstall, but no shipped change has triggered that yet.

## Architecture

Three layers, all in `app/src/main/java/com/juni/app/`:

### `data/` — sources of truth and external APIs

- `prefs/SecurePrefs.kt` — EncryptedSharedPreferences for API keys (Android Keystore-backed).
- `prefs/AppSettings.kt` — DataStore for everything else (provider id, per-provider model, vault tree URI, Ollama base URL, system prompt). Single `Settings` data class flowed via `Flow<Settings>`.
- `vault/VaultRepository.kt` — the *only* place that touches `DocumentFile` / `Uri`. Path-relative API: `list`, `read`, `write`, `move`, `delete`, `writeAttachment`, `search`. Skips dot-files (`.trash`, `.obsidian`, etc.) in both `list` and `search`.
- `db/` — Room: `ConversationEntity` + `MessageEntity` (FK + cascade delete). `MessageContent` is JSON-encoded into `messages.contentJson` via kotlinx-serialization. `ConversationRepository` exposes a Flow of conversations and per-conversation message loaders/appenders.
- `provider/` — one `AiProvider` interface, four implementations (`ClaudeProvider`, `OpenAiProvider`, `GeminiProvider`, `OllamaProvider`) and a `ProviderRegistry` that resolves the active provider from `Settings + SecurePrefs`. Each provider takes the canonical `Message` / `ToolSpec` model and translates it to its own wire format. Streaming is SSE for Claude/OpenAI/Gemini and NDJSON for Ollama. Tool-call ids are minted client-side for Gemini and Ollama (those APIs don't supply them).
- `image/ImageUtils.kt` — `ImageProxy.toUprightJpeg(...)`: rotation-correct, resizes to ≤1568px long side at JPEG-85.

### `domain/` — provider-agnostic agent

- `agent/Message.kt` — canonical `Message(role, content)` where `MessageContent` is a sealed interface (`Text` / `Image` / `ToolUse` / `ToolResult`). All `@Serializable` so they round-trip through Room.
- `agent/AgentLoop.kt` — `channelFlow<AgentEvent>` that drives the multi-turn loop: stream provider → collect text+tool calls → gate each tool through a suspending `ApprovalGate` → execute → feed results back → loop until end-of-turn or iteration cap. Crucially: emits `AgentEvent.ToolResultsBatch` after running tools so the UI can persist the user-role tool_result message that pairs with the prior assistant tool_use blocks (the API requires the pair on the next request).
- `agent/JuniSystemPrompt.kt` — default system prompt. **Never** ask juni to ask about *location* in the prompt — current direction is "best-fit folder, fall back to vault root or `notes/`". Never include emoji directives — juni's output is no-emoji by policy.
- `agent/ChatIntent.kt` — per-message intents (diagram / transcribe / daily note / summarize / expand-knowledge). Selected intent's `instruction()` is wrapped in `[…]` and prepended to the user's message at send time, then resets. Intents are *not* per-session modes.
- `tools/` — `Tool` interface + `ToolRegistry`. `VaultTools.all(...)` is the canonical list. Tools that need to call back into the chat (ask_clarifying_question, rename_chat) take a lambda parameter and emit a side-effect via the ChatViewModel.

### `ui/` — Compose presentation, terminal-aesthetic only

- `ui/terminal/` — primitives: `TermText`, `TermBox`, `TermInput`, `TermButton`, `TermDivider`, `TermSpinner`, `TermConfirm`, `TermPromptDialog`, `TermMenuSheet`, `Toaster`/`ToastHost`. **All UI is built from these — never `androidx.compose.material3`.** TermBox accepts an opaque `background` for floating elements (toasts/dialogs use `TermSurface`); inline cards leave it null.
- `ui/chat/` — `ChatScreen` + `ChatViewModel` + `ChatItem` + `ToolCallCard`. The ViewModel takes a `conversationId` from `SavedStateHandle` (nav arg), loads `Message`s from DB on init, persists user messages immediately on send, persists assistant + tool-result messages atomically after each `TurnComplete`. Approval gate is a `CompletableDeferred<ApprovalResult>` per tool_use id; approve/reject completes it.
- `ui/conversations/` — sidebar/list (the home screen). Tap anywhere on a row to open; row buttons handle rename/delete.
- `ui/settings/` — section-selector top, sub-sections below: ai provider, system prompt, vault, data (delete-all). Each destructive action goes through a `TermConfirm`.
- `ui/camera/` — CameraX wrapped in Compose. On capture, JPEG bytes go into `JuniApp.composerImages` (a process-wide `MutableStateFlow<List<ByteArray>>`), then `popBackStack()` to chat.

### Cross-cutting bits

- `JuniApp` (Application class) holds the singletons: `securePrefs`, `appSettings`, `conversationRepository`, plus the `composerImages` staging flow. Reach them via `JuniApp.get()` in ViewModels.
- `AppNavHost` is the only Navigation Compose host. Routes: `conversations` (start), `chat/{conversationId}` with a string nav-arg, `settings`, `camera`.
- The `Toaster` singleton is process-wide. Anywhere can call `Toaster.success(...)` / `Toaster.error(...)`. `ToastHost` is wrapped around `AppNavHost` in `MainActivity`, so toasts surface above everything.

## Conventions to respect

- **Custom terminal-y primitives, no Material 3.** The look is Noto Serif (`TermFont`) for body text, JetBrains Mono (`MonoFont`) reserved for code blocks / inline code / anything that needs cell alignment, on a dark palette inside rounded `TermBox` panels. Both font families live in `ui/theme/Theme.kt`. New screens reuse the `ui/terminal/` primitives. The chat composer's input intentionally has `showBorder = false`; every other input keeps its rounded border. (The "terminal" naming is historical — the body font is a serif now, not a monospace.)
- **No emoji in juni's output.** This is in the system prompt — keep it there if you edit the default.
- **Approval is a suspending lambda, not an event.** `AgentLoop.run()` takes an `ApprovalGate` that suspends until the UI completes a `CompletableDeferred`. This keeps the `AgentEvent` stream one-way. Auto-approved tools (`list_files`, `read_note`, `search_notes`, `ask_clarifying_question`, `rename_chat`) bypass the gate — write tools always require a tap.
- **All vault reads/writes go through `VaultRepository`.** Outside `data/vault/`, code speaks in vault-relative paths like `notes/foo.md`. Never construct a `Uri` elsewhere.
- **Per-provider message-format mapping is the bug-prone part.** Anthropic uses `tool_use` blocks inside an assistant message + `tool_result` blocks in the next user message. OpenAI uses `tool_calls` on the assistant and one tool-role message per result. Gemini uses `functionCall` / `functionResponse` parts. Ollama uses `tool_calls` on the assistant + `role: tool` results without ids. The conversion functions are at the bottom of each `*Provider.kt` file.
- **Image flow.** `JuniApp.composerImages` is the single staging point. ChatViewModel reads it on send (base64-encoding into the user `Message`), feeds the same bytes into `AttachmentStaging` so the agent's `save_attachment` tool can persist them later, then clears the staging.
