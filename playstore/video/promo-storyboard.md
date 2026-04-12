# LiveCaptionN — 30 second Play Store promo video

Google Play shows the promo video on the listing as a YouTube link, so the
deliverable is a YouTube URL, not a file attachment. This storyboard is
scoped to 30 seconds because shorter promos auto-play better in listings.

## Technical requirements (Play Console)

- Hosted on YouTube (public or unlisted).
- 16:9 aspect, 1920x1080 at 30 fps recommended.
- No in-video "download now" CTAs, no misleading framing, no ads.
- Audio bed should be copyright-cleared (YouTube Audio Library is fine).

## Recording setup

1. Put the phone in Do Not Disturb so no notifications show up during capture.
2. Set the phone wallpaper to something quiet — a dark solid or the default
   Pixel wallpaper — so the brand gradient reads cleanly.
3. Install the release build of LiveCaptionN.
4. Grant Microphone, Display-over-other-apps, and Notifications permissions.
5. Pre-download a Vosk English model and let ML Kit fetch EN->VI (or EN->ES,
   whichever looks better on camera).
6. Capture the screen with `scrcpy --max-size 1920 --record promo-raw.mp4`
   from a laptop, or with an OBS scene that mirrors the phone. Avoid the
   phone's built-in screen recorder — it draws a red status bar.
7. Record a separate audio track speaking the lines in section "VO script"
   so you can dub a clean version over the raw capture audio.

## Shot list (00:00 to 00:30)

| Time | Duration | Shot | On-screen text overlay | VO |
| --- | --- | --- | --- | --- |
| 00:00 | 2s  | Purple brand title card. Icon centered, "LiveCaptionN" below. Fade in from black. | — | "Real-time speech captions…" |
| 00:02 | 3s  | Main settings screen. Fingertip taps Start Captioning. | "One tap to caption any app." | "…and translation, floating over any Android app." |
| 00:05 | 4s  | Switch to YouTube (or any video player) playing a foreign-language clip. Overlay appears and starts filling with live translated captions. | "Streaming on-device Vosk + Google ML Kit" | "Streaming speech recognition runs entirely on-device." |
| 00:09 | 4s  | Close-up of the overlay text updating word-by-word as a speaker talks. | — | "Captions arrive word-by-word — not in two-second chunks." |
| 00:13 | 4s  | User drags the overlay to a new position, then pinch-resizes it. | "Drag, resize, pause." | "Drag it anywhere. Pause any time." |
| 00:17 | 4s  | Switch to Manage On-device Models sheet. Scroll past the Large section showing 15 languages. | "15 languages, server-grade accuracy" | "Large server-grade speech models for 15 languages." |
| 00:21 | 3s  | Show the Translation section with the ML Kit option highlighted. | "Offline translation, ~59 languages" | "On-device translation in about 59 languages." |
| 00:24 | 4s  | Return to the main screen. History list populated with a few finalized lines. | "Private by default. No accounts. No telemetry." | "Nothing leaves your phone. No accounts. No telemetry." |
| 00:28 | 2s  | Brand outro card: icon + "LiveCaptionN" wordmark + "Available on Google Play". Fade to black. | "Available on Google Play" | "LiveCaptionN. Free. Open source." |

## VO script (single take)

> Real-time speech captions and translation, floating over any Android app.
> Streaming speech recognition runs entirely on-device — captions arrive
> word-by-word, not in two-second chunks. Drag the overlay anywhere, pause
> any time. Large server-grade speech models for 15 languages and on-device
> translation in about 59 languages. Nothing leaves your phone. No accounts,
> no telemetry. LiveCaptionN. Free. Open source.

Total read time at a natural pace: ~26 seconds, leaving two seconds of
breathing room for the outro card.

## Assets to hand off to the editor

- `playstore/graphics/icon-512.png` — for the title and outro cards.
- `playstore/graphics/feature-graphic-1024x500.png` — for the thumbnail.
- Raw screen capture from scrcpy.
- Clean VO track recorded separately.
- Lower-third overlay color: `#1A1330` at 70% opacity. Text: `#F5F3FF`.

## Upload checklist

- [ ] Export at 1920x1080, H.264, CRF 18, AAC 192 kbps.
- [ ] Upload to YouTube as "unlisted" first for review.
- [ ] Thumbnail: `feature-graphic-1024x500.png` resized to 1280x720.
- [ ] Video title: "LiveCaptionN — live captions and translation for any Android app"
- [ ] Video description: short description + full description + GitHub URL.
- [ ] Flip to "public" once happy, paste the URL into Play Console → Main store listing → Video.
