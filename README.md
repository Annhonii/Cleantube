# CleanTube

A native Android YouTube client (Kotlin + Jetpack Compose) with a NewPipe-style layout and a Material You (Monet) look: wallpaper colours on Android 12+, light / dark with the system, system font.

- **Browse, search, channels, playlists, comments:** YouTube's own internal (InnerTube) API, the same one the website uses. No API key, no Google Cloud Console.
- **Playback:** official YouTube player (IFrame, via [android-youtube-player](https://github.com/PierfrancescoSoffritti/android-youtube-player))
- **Sign in with Google (optional):** Home becomes YouTube's real recommendations for you, Feed is your real subscriptions feed, History can show your YouTube history.

## How it works

A hidden WebView sits on youtube.com and `assets/yt.js` makes the InnerTube requests and parses the answers into plain lists
(the parsers are tested against recorded YouTube responses). The Kotlin side only asks it for things.
Login happens in a normal Google sign-in page inside the app; only the cookies stay on your phone.

## Honest warnings

- It is **unofficial**. YouTube can change its responses any day and a screen may stop loading until `yt.js` is updated.
- Google's terms don't allow unofficial clients. Use an account you can afford to risk.
- Google may refuse sign-in inside an app WebView. If it does, the app still works signed out.
- Videos opened in CleanTube are not added to your YouTube watch history (the embedded player isn't signed in); the History tab keeps a local list and can also show your YouTube one.
- No ad blocking or background play (the official player doesn't allow them). Some videos block embedding; the app then offers "Open in YouTube".

## Tabs

| Tab | What it does |
|---|---|
| Home | YouTube's home feed (personalized when signed in) |
| Shorts | Full-screen vertical Shorts feed (swipe up for the next one, tap to pause) with topic chips, Download / Save / Share on every Short |
| Feed | Your subscriptions feed (signed in) or newest videos from locally saved subscriptions |
| Library | Subscriptions and local playlists |
| Downloads | Live download progress (percent, size, speed, time left), finished files, retry / cancel / delete, "Paste YouTube link" |
| History | Videos you opened, plus your YouTube history when signed in |
| Search (magnifier in the top bar) | Videos / channels / playlists, local search history |

Downloads: stream links now come from the **NewPipe engine** ([NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor)); the old built-in method is only a fallback. A stale / refused link is refreshed automatically once. Progress, speed and time left are shown in the Downloads tab and in the notification (with a Cancel button); a separate notification announces finished / failed downloads. When YouTube changes something and downloads break again, bump the `NewPipeExtractor` version in `app/build.gradle.kts` to the newest release.

The Download button on a video offers every H.264 quality (picture and sound are downloaded separately, then joined on the phone), ready-made files when YouTube gives any, and audio-only files. 1 to 32 parallel connections can be chosen, saved either **in the app** (private folder) or to **phone storage** (Movies/CleanTube, Music/CleanTube). Links come from YouTube's app clients and can stop working when YouTube changes them; VP9 / AV1 qualities (usually 1440p and up) are not offered.

Also: video page with description, comments and "Up next"; channel pages; save to playlist; share;
YouTube links and "Share to CleanTube" open in the app; import NewPipe subscriptions (`.json`); export / import backup.

## Build

GitHub Actions builds a signed release APK on every push to `main` and publishes it under **Releases**.
The APK is signed with the debug key in `app/debug.keystore`, the same on every build, so new versions install over old ones.

Local build: open in Android Studio (JDK 17) or run `./gradlew assembleRelease`.

## Credits

Layout and design tokens follow the "mistpipe" fork of [NewPipe](https://github.com/TeamNewPipe/NewPipe).
Fonts: Geist, NType 82 and Ndot 77, copied from that fork.
Not affiliated with YouTube, Google or NewPipe.
