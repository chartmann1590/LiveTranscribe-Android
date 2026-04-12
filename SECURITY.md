# Security Policy

## Supported Versions

| Version | Supported          |
| ------- | ------------------ |
| latest  | :white_check_mark: |
| older   | :x:                |

Only the most recent release receives security fixes. Update to the latest version from the [releases page](https://github.com/chartmann1590/LiveTranscribe-Android/releases/latest).

## Reporting a Vulnerability

**Do not open a public issue for security vulnerabilities.**

Instead, please use GitHub's private security advisory feature:

1. Go to the [Security tab](https://github.com/chartmann1590/LiveTranscribe-Android/security) of this repository.
2. Click **Report a vulnerability**.
3. Fill in the details and submit.

You will receive a response within 72 hours acknowledging the report. If the issue is confirmed, a fix will be developed privately and released as soon as possible, with credit given to the reporter (unless you prefer to remain anonymous).

## Scope

The following are in scope for security reports:

- Vulnerabilities in the LiveCaptionN Android application code
- Issues with how audio, transcripts, or settings are stored or transmitted
- Permission escalation or unintended data exposure
- Dependencies with known CVEs that affect this project

The following are **out of scope**:

- Vulnerabilities in third-party services (Google AdMob, Google ML Kit, Vosk, LibreTranslate) — report those to their respective maintainers
- Servers you self-host (LibreTranslate, Whisper) — their security is your responsibility
- Social engineering or phishing attacks
- Denial of service attacks

## Security Design

LiveCaptionN is designed with privacy in mind:

- **No accounts, no telemetry, no analytics** — the app phones home only to check for updates via the public GitHub API
- **On-device by default** — speech recognition (Vosk) and translation (ML Kit) both run locally
- **No cloud storage** — transcripts and settings are stored in app-private storage and removed on uninstall
- **Optional remote backends** — Whisper and LibreTranslate endpoints are user-configured; no data goes to developer-controlled servers
- **Minimal permissions** — only RECORD_AUDIO, SYSTEM_ALERT_WINDOW, FOREGROUND_SERVICE, POST_NOTIFICATIONS, and INTERNET
