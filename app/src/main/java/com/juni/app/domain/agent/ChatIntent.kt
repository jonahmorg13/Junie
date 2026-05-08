package com.juni.app.domain.agent

import java.time.LocalDate

/**
 * One-shot per-message biases the user can attach to their next prompt via
 * the composer's [ + ] menu. Each intent supplies a short instruction that
 * gets prepended (in brackets) to the user's text before it goes to the
 * model. The intent resets after a successful send — modes don't persist
 * across turns by design.
 */
enum class ChatIntent(val label: String, val short: String) {
    DIAGRAM(
        label = "diagram",
        short = "Convert attached photo to a Mermaid diagram in a new note.",
    ),
    TRANSCRIBE(
        label = "transcribe",
        short = "Verbatim transcription of the attached photo.",
    ),
    DAILY_NOTE(
        label = "daily note",
        short = "Open or create today's daily note.",
    ),
    SUMMARIZE_NOTE(
        label = "summarize note",
        short = "Summarize a vault note I'll specify.",
    ),
    EXPAND_KNOWLEDGE(
        label = "expand knowledge",
        short = "Add the attached info to existing related notes, or start a new one.",
    ),
    ;

    /** Instruction text prepended to the user message; resolved at send time. */
    fun instruction(): String = when (this) {
        DIAGRAM ->
            "Convert the attached image to a Mermaid fenced block in a new vault note. " +
                "Capture any title, date, or surrounding context from the photo as headings " +
                "or YAML at the top of the note. Do not embed the source image."
        TRANSCRIBE ->
            "Transcribe the attached photo verbatim into a new vault note. Preserve the " +
                "user's voice, idioms, and minor errors exactly — do not correct anything. " +
                "Place it in the journal folder if one exists; otherwise ask. Do not embed " +
                "the source image."
        DAILY_NOTE -> {
            val today = LocalDate.now().toString()
            "Open or create today's daily note for $today (use the existing daily-note " +
                "folder if one is already in the vault; otherwise ask where it should live). " +
                "Append what follows to that note as new content under a timestamp heading."
        }
        SUMMARIZE_NOTE ->
            "Read the vault note I describe below (use search_notes if you need to find it), " +
                "then produce a concise summary. Show me the summary first; do not write it " +
                "back to the note unless I confirm."
        EXPAND_KNOWLEDGE ->
            "Take what's attached (image and/or text below) and integrate it into my vault. " +
                "Identify the topic(s) the input covers. Use search_notes and list_files to " +
                "find existing notes on those topics. If a relevant note exists, propose an " +
                "edit_note that appends the new information in a clearly labelled section " +
                "(e.g. a date heading or 'Added from <source>') without destroying existing " +
                "content. If no relevant note exists, create a new note in the most fitting " +
                "folder (ask if uncertain). Tell me what you found and what you plan to do " +
                "before each tool call."
    }
}
