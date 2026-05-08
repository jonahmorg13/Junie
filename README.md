# juni

A bring-your-own-API-key Android agent for your Obsidian vault. Take a photo of a whiteboard and get a Mermaid diagram in a new note. Photograph a journal page and get it transcribed verbatim. Talk through a topic and have juni file your notes for you. The vault stays local on the phone; juni only ever calls the AI provider you've configured.

Pick **Claude**, **OpenAI**, **Gemini**, or **Ollama** — all four work. Every write to your vault is gated by an explicit approve/reject tap, with a diff preview before you commit.

## Highlights

- **Local Obsidian vault** — pick the folder via Android's Storage Access Framework. juni reads, searches, creates, edits, moves, and embeds attachments inside it. Hidden folders (`.trash`, `.obsidian`) are skipped.
- **Multi-provider** — Claude (Anthropic Messages API), OpenAI (Chat Completions), Gemini (`streamGenerateContent`), Ollama (local `http://host:11434`). Switch any time in Settings.
- **Vision in** — attach a photo from the camera or library; juni uses the provider's native vision API. Source images are *not* embedded in notes by default — they're treated as transient input. Ask explicitly if you want to keep one.
- **Approval-gated tools** — destructive tool calls (`create_note`, `edit_note`, `move_note`, `save_attachment`) show as a card with a diff preview and `[ approve ]` / `[ reject ]` buttons. Read-only tools (`list_files`, `read_note`, `search_notes`) auto-approve and collapse to a single line; tap to expand.
- **Per-message intents** — a `[ + ]` button in the composer offers: *diagram* (whiteboard → Mermaid), *transcribe* (verbatim from photo), *daily note* (today), *summarize note*, *expand knowledge* (integrate input into related notes or start a new one). The selected intent prepends an instruction to your next message and resets after send.
- **Persistent conversations** — every chat is saved (Room) with messages, images, tool calls, and results. Resume any time; rename via the chat header title or the row in Conversations.
- **Open-in-Obsidian** — successful `create_note` / `edit_note` / `move_note` cards expose an `[ open in obsidian ]` button that launches Obsidian to that note via `obsidian://open`.
- **Editable system prompt** — Settings → system prompt. Reset-to-default button included.
- **Terminal aesthetic** — JetBrains Mono, rounded `TermBox` panels, ASCII tool cards, no Material 3 widgets anywhere.

## Requirements

- Android phone running Android 8.0 (API 26) or later. Tested on a Pixel 9a (Android 16).
- Obsidian installed on the same device for the open-in-Obsidian flow (the vault folder must be registered in Obsidian — usually automatic on first open).
- An API key for whichever provider you want to use (Anthropic / OpenAI / Google AI Studio / a reachable Ollama instance).

## Build

The project ships with the gradle wrapper and a pinned JBR. Open a terminal in the repo root:

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.juni.app/.MainActivity
```

You'll need `local.properties` pointing to your Android SDK:

```properties
sdk.dir=/path/to/Android/Sdk
```

Java is provided by Android Studio's bundled JBR (JDK 21) via `gradle.properties`'s `org.gradle.java.home`. Edit that if your Android Studio lives elsewhere or you want to use a different JDK.

## First-run setup

1. Launch the app — you land on the **Conversations** list (empty initially).
2. Tap **`[ settings ]`** at the top.
3. Open the **ai provider** section — pick your provider, paste the API key (tap `[ show ]` first if you want to see what you're typing), and confirm the model name.
4. Open the **vault** section — tap `[ pick folder ]` and choose your Obsidian vault root. The vault URI persists across launches via SAF's persistable URI permission.
5. Optional: open **system prompt** to tune juni's behavior.
6. Back to Conversations, tap `[ + new chat ]` and start.

## What does juni do well?

The four canonical use cases:

1. **Whiteboard photo → Mermaid diagram in a new note.** Aim the camera at a hand-drawn diagram, hit `[ + ] → diagram`, send. juni studies the shapes and arrows, drafts a `mermaid` fenced block in a fresh markdown file, and shows you a diff to approve.
2. **Handwritten journal page → verbatim transcription.** `[ + ] → transcribe`. Idioms and minor errors are preserved on purpose. juni picks a folder (your `journal/` if it exists, else best fit) and titles by the date on the page or today.
3. **Stream-of-consciousness cleanup.** Ask juni to read a messy note and tighten it. juni proposes an `edit_note` with a clear before/after diff before touching anything.
4. **Collaborative new-note creation.** "Make me a note about monarch butterflies" → juni asks one or two clarifying questions about scope, drafts, and only commits to the vault after you approve.

There's also `[ + ] → expand knowledge`: photograph some text or a diagram, and juni searches existing notes for related material, proposes appending to the right one, or starts a new note if the topic is fresh.

## Tech stack

Native Android. Kotlin 2.0.21, AGP 8.10.1, compileSdk 36, minSdk 26. Jetpack Compose foundation only (no Material 3). CameraX for capture. Room + DataStore + EncryptedSharedPreferences for persistence. OkHttp + kotlinx-serialization for the four streaming AI clients (SSE for Claude/OpenAI/Gemini, NDJSON for Ollama).

For internal architecture and conventions, see [CLAUDE.md](CLAUDE.md).
