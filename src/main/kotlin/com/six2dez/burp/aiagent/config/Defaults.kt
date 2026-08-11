package com.six2dez.burp.aiagent.config

object Defaults {
    val DEFAULT_EXCLUDED_EXTENSIONS =
        setOf(
            "css",
            "jpg",
            "jpeg",
            "png",
            "gif",
            "svg",
            "ico",
            "woff",
            "woff2",
            "ttf",
            "eot",
            "otf",
            "mp4",
            "mp3",
            "avi",
            "mov",
            "webm",
            "webp",
            "pdf",
            "zip",
            "gz",
            "tar",
            "rar",
            "7z",
            "map",
            "bmp",
            "tif",
            "tiff",
        )
    val DEFAULT_EXCLUDED_EXTENSIONS_CSV = DEFAULT_EXCLUDED_EXTENSIONS.joinToString(",")

    const val FINDINGS_BUFFER_SIZE = 50
    const val MAX_HISTORY_MESSAGES = 20
    const val MAX_HISTORY_TOTAL_CHARS = 40_000
    const val LARGE_PROMPT_THRESHOLD = 32_000
    const val CLI_PROCESS_TIMEOUT_SECONDS = 120
    const val PASSIVE_SCAN_TIMEOUT_MS = 90_000L
    const val HEALTH_CHECK_INTERVAL_MS = 2_000L
    const val BACKEND_STARTUP_DELAY_MS = 2_000L
    const val DEDUP_WINDOW_MS = 3_600_000L
    const val ACTIVE_SCAN_MAX_QUEUE_SIZE = 2_000
    const val MAX_CONTEXT_TOTAL_CHARS = 40_000
    const val CHAT_MAX_OUTPUT_TOKENS = 4096
    const val SCANNER_MAX_OUTPUT_TOKENS = 2048
    const val SCANNER_BATCH_MAX_OUTPUT_TOKENS = 4096
    const val PAYLOAD_MAX_OUTPUT_TOKENS = 1024
    const val OPENCODE_IDLE_TIMEOUT_MS = 30_000L

    // (PRIV-06 / D-04) Window width for the body-redaction stage in Redaction.apply — a window
    // width, NOT a skip threshold. Input at or below this length is processed in a single pass
    // whose cost and behaviour are identical to the pre-Phase-21 implementation, which covers the
    // overwhelming majority of payloads (ContextCollector already truncates bodies to 4k/8k; the
    // larger strings come from the other callers — MCP tools, bounty resolver). Input above this
    // length is cut into windows at line boundaries and every window is scanned: nothing is
    // skipped, and unscanned bytes never reach a backend.
    // The name and the 1_000_000 value are kept deliberately. Renaming this to something like
    // REDACTION_WINDOW_CHARS would churn Defaults, Redaction, RedactionTest and the Phase 13
    // planning documents for no behavioural gain.
    const val MAX_REDACTION_BODY_CHARS = 1_000_000

    // (PRIV-06 / D-02) Total wall-clock budget for the body-redaction stage. Windows are processed
    // in order until it is spent; everything past that point is dropped behind a visible marker
    // rather than passed through — fail closed, so unscanned bytes never reach a backend. The
    // per-pattern deadline handed to SafeRegex is min(SafeRegex.DEFAULT_TIMEOUT_MS, remaining
    // budget), so a per-pattern deadline can never outlive the total. The MAX_ prefix matches
    // MAX_REDACTION_BODY_CHARS above.
    // Sized from measurement rather than from an external source: ~27 ms per 1 MB window for the
    // form plus JSON rules on Apple Silicon / JDK 21, so 2 000 ms covers tens of megabytes (the
    // reference implementation processed a 4.16 MB input in 849 ms).
    // Deliberately NOT user-configurable: D-04 rejected exposing the window and the budget in the
    // Privacy settings panel, because a user who set the budget to 0 would silently disable body
    // redaction — exactly the class of bug this phase exists to kill.
    const val MAX_REDACTION_BUDGET_MS = 2_000L

    const val PREPROCESS_PROXY_HISTORY_ENABLED = true
    const val PREPROCESS_MAX_RESPONSE_SIZE_KB = 20
    const val PREPROCESS_FILTER_BINARY_CONTENT = true
    const val MCP_PROXY_HISTORY_MAX_ITEMS_PER_REQUEST = 20
    const val MCP_PROXY_HISTORY_NEWEST_FIRST = true
    const val MCP_ALLOW_UNPREPROCESSED_PROXY_HISTORY = true
    val PREPROCESS_ALLOWED_CONTENT_TYPES: Set<String> =
        setOf(
            "text/",
            "application/json",
            "application/xml",
            "application/javascript",
            "application/x-www-form-urlencoded",
            "multipart/form-data",
        )
}
