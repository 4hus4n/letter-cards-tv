# Little Letter Cards TV

Android TV app for Little Letter Cards (Alif Baa, ABC, 123, Haa Shaviyani, Shapes, 99 Names of Allah, Solar System).
Works offline: the app, voices and fonts are inside the APK.

## Build
Push to `main` (or run the workflow by hand). GitHub Actions builds `LittleLetterCardsTV.apk`
and publishes it under **Releases** as `v1.<run number>`.

## Updates
On start the app checks the latest release of the repo in `app/build.gradle.kts` (`UPDATE_REPO`).
If a newer version exists it offers to download and install it.
Keep `app/release.jks` in the repo: every build must be signed with the same key or updates will not install.

## Remote
- Left / Right: previous / next card
- OK: listen (on 123 cards, OK counts the next item)
- Up: menu (Home, Auto play, All cards); Down: back to the card
- Play/Pause: auto play
- Back: close menu, then Home, then exit

## Voices
- English: Kokoro (af_heart), recorded into the app.
- Arabic (Alif Baa, 99 Names): Piper voice ar_JO "kareem" (medium), from the sherpa-onnx
  GitHub release of Piper voices, recorded into the app. Text is read with full vowel marks;
  a final ة is read in its pause form (…ah).
- No text-to-speech is needed on the TV.

## Your own voices
Put `dhivehi-voices.json` and/or `names-voices.json` (saved from the phone version) into
`app/src/main/assets/` and push. The TV app will use them.

## Updating the cards
`app/src/main/assets/index.html` is the full app. Replace it with a new build and push.
