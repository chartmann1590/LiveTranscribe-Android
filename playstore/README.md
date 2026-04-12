# Play Store submission pack

Everything needed to push a LiveCaptionN release to the Google Play Store, in
one folder. Nothing in here is consumed by the build — it is a curated set of
artifacts for the Play Console upload forms.

## Layout

```
playstore/
├── README.md                          this file
├── generate_graphics.py               regenerates every PNG in graphics/
├── keystore.properties.example        template for signing config (never commit real secrets)
├── metadata/
│   └── en-US/
│       ├── title.txt                  Play Console → App details → App name
│       ├── short_description.txt      Play Console → App details → Short description
│       ├── full_description.txt       Play Console → App details → Full description
│       ├── release_notes.txt          Play Console → Release → Release notes
│       ├── contact_website.txt        Play Console → App details → Website
│       ├── privacy_policy_url.txt     Play Console → App content → Privacy policy
│       └── app_category.txt           Play Console → App details → Category (reference only)
├── graphics/
│   ├── icon-512.png                   Play Console → Store listing → App icon (512×512 PNG, 32-bit RGB)
│   ├── feature-graphic-1024x500.png   Play Console → Store listing → Feature graphic (1024×500 PNG, 24-bit RGB)
│   └── phone-screenshots/
│       ├── 01_overlay_home.png        Play Console → Store listing → Phone screenshots
│       ├── 02_overlay_listening.png
│       ├── 03_main.png
│       └── 04_history.png
└── video/
    └── promo-storyboard.md            Shot list + VO script for the 30 second YouTube promo
```

All four phone screenshots are padded to exactly 1122×2244 (a 1:2 aspect) so
they satisfy Play Store's "aspect ratio must not exceed 2:1" requirement with
no cropping. They are 24-bit PNG (no alpha) so the Play Console accepts them
without re-encoding.

## Regenerating the graphics

```bash
python playstore/generate_graphics.py
```

This reads `docs/assets/screenshots/*.png` and rewrites everything under
`playstore/graphics/`. Run it whenever the screenshots or brand colors
change. Requires Python 3.10+ and Pillow.

## Building a signed Play Store bundle

The Play Store only accepts Android App Bundles (`.aab`). The project is
already configured to produce one:

```bash
./gradlew bundleRelease
```

The unsigned output lands at:

```
app/build/outputs/bundle/release/app-release.aab
```

### Signing

Google Play now manages the upload key via Play App Signing, but you still
need an *upload key* of your own. Create one once and reuse it forever:

```bash
keytool -genkey -v \
  -keystore livecaptionn-upload.keystore \
  -alias livecaptionn \
  -keyalg RSA -keysize 2048 -validity 36500
```

Copy `playstore/keystore.properties.example` to `keystore.properties` in the
**repo root** (which is already gitignored) and fill in the four values:

```
storeFile=H:/secure/livecaptionn-upload.keystore
storePassword=...
keyAlias=livecaptionn
keyPassword=...
```

Then rebuild:

```bash
./gradlew clean bundleRelease
```

Gradle will automatically sign the bundle with your upload key when the
properties file is present. The resulting `app-release.aab` is what you
upload to Play Console → Production → Create new release → Upload.

### First-time Play Console checklist

1. Create the application in Play Console under the **Tools** category.
2. Fill in **App details** from `metadata/en-US/*.txt`.
3. Upload `graphics/icon-512.png`, `graphics/feature-graphic-1024x500.png`,
   and every PNG in `graphics/phone-screenshots/`.
4. Paste the privacy policy URL from `metadata/en-US/privacy_policy_url.txt`
   into **App content → Privacy policy**.
5. Complete the **Data safety** form. LiveCaptionN collects and shares no
   user data (no analytics, no advertising, speech never leaves the device
   under the default pipeline). See `../docs/privacy.html` for the exact
   language to mirror.
6. Complete the **Content rating** questionnaire — the app has no user
   generated content, no ads, and no in-app purchases, so it lands in the
   lowest rating tier for every region.
7. Upload the signed `.aab` under **Production → Create new release**.
8. Record the promo video per `video/promo-storyboard.md`, upload to
   YouTube, paste the URL into **Store listing → Video**.
9. Submit for review.

## Permissions justification (Play Console: App access)

- `RECORD_AUDIO` — required for microphone speech recognition. Only used
  while a captioning session is actively running and a foreground
  notification is visible.
- `SYSTEM_ALERT_WINDOW` — required for the floating caption window that
  sits on top of other apps. The core feature of the app is unusable
  without it.
- `FOREGROUND_SERVICE_MICROPHONE` and
  `FOREGROUND_SERVICE_MEDIA_PROJECTION` — required by Android 14+ so the
  captioning service can keep recording after the user switches apps.
- `POST_NOTIFICATIONS` — captioning session notification and optional
  update notifications.
- `INTERNET` — only used to download on-device Vosk / ML Kit models when
  the user asks, and to check GitHub for new release builds.
