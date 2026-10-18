# CleanTube

A native Android YouTube client (Kotlin + Jetpack Compose) with a NewPipe-style layout and a black "Nothing" look.

- **Browse, search, channels, playlists, comments:** official YouTube Data API v3
- **Playback:** official YouTube player (IFrame, via [android-youtube-player](https://github.com/PierfrancescoSoffritti/android-youtube-player))
- **Your data stays on the phone:** subscriptions, feed, history and playlists are stored locally, no account needed

## First run

The app needs your own free API key (Settings, or the card on the Home tab explains it):

1. Google Cloud Console > create a project
2. Enable **YouTube Data API v3**
3. Credentials > Create credentials > **API key**, paste it into the app

Free quota is 10,000 units a day. A search costs 100 units (about 100 searches a day); everything else costs 1.
The quota resets around 12:30 PM IST.

## Features

| Tab | What it does |
|---|---|
| Home | Trending for your region, with category chips |
| Feed | Newest videos from your subscriptions (about 1 quota unit per channel) |
| Library | Subscriptions and local playlists |
| History | Videos you opened |
| Search (round button) | Videos / channels / playlists, local search history |

Also: video page with description, comments, "more from channel"; channel pages; save to playlist; share;
YouTube links and "Share to CleanTube" open in the app; import NewPipe subscriptions (`.json`); export / import backup.

## What the official engine cannot do

- No ad blocking, downloads or background / screen-off playback (the official player does not allow them)
- Some videos disallow embedding; the app then offers "Open in YouTube"
- No related-videos list (YouTube removed it from the API), so the video page shows "more from channel"
- Sign-in / likes / sync with your Google account are not available

## Build

GitHub Actions builds a signed release APK on every push to `main` and publishes it under **Releases**.
The APK is signed with the debug key in `app/debug.keystore`, the same on every build, so new versions install over old ones.

Local build: open in Android Studio (JDK 17) or run `./gradlew assembleRelease`.

## Credits

Layout and design tokens follow the "mistpipe" fork of [NewPipe](https://github.com/TeamNewPipe/NewPipe).
Fonts: Geist, NType 82 and Ndot 77, copied from that fork.
Not affiliated with YouTube, Google or NewPipe.
