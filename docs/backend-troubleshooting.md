# Backend Troubleshooting Runbook

## Fast Triage

1. Check status badge (`AI: OK`, `AI: Degraded`, `AI: Offline`).
2. Run **Test connection** for selected backend.
3. Check Burp extension output/errors tab.

## CLI Backends

1. Run configured command in terminal exactly as in settings.
2. Use full path if GUI-launched Burp misses PATH entries.
3. Re-check auth env vars in Burp runtime context.

## HTTP Backends

1. Verify service is listening on configured URL.
2. Verify model exists and is loadable.
3. Confirm API key/header settings.
4. Increase timeout for large models or long prompts.

## Long Session/Prompt Issues

1. Start a fresh chat session.
2. Reduce manual context body caps.
3. Verify history trimming settings and runtime defaults.

## Escalation Data

When reporting an issue, include:

* backend type and command/URL,
* status badge state,
* extension output error snippet,
* minimal reproducible prompt/action.
