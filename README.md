# hackertv-androidtv

A thin Android TV launcher for [HackerTV](https://hackertv.org/), the web app that plays
Hacker News videos as a schedule of TV channels. It is for Google TV and Android TV devices.

The app holds no HackerTV code. It is a full-screen WebView on `https://hackertv.org/`, so the
channel data, the schedule and the viewer count stay the author's. HackerTV's source is at
[dsalzman/hackertv](https://github.com/dsalzman/hackertv) and carries no license, so nothing from
it is copied here.

## Remote control

The page already handles up and down (channel), digits followed by Enter (direct channel) and
Escape. The app adds the keys a TV remote has and a browser does not.

| Remote key        | Action                                                 |
|-------------------|--------------------------------------------------------|
| Channel up / down | Next / previous channel                                |
| Red, Guide        | Channel guide                                          |
| Green             | Picture filter (CLEAN, CRT, VHS, B&W)                  |
| Yellow, Captions  | Captions                                               |
| Blue, Info        | Program info                                           |
| Play / Pause      | Theater mode (full window) on or off                   |
| Back              | Close the guide; twice within two seconds to exit      |

## Build and install

The project needs the Android SDK (platform 34, build-tools 34.0.0) and a JDK 17 or newer.
Android Studio is optional: the command-line SDK tools and the Gradle wrapper are enough. Point
`ANDROID_HOME` at the SDK, or put `sdk.dir` in `local.properties`.

```sh
./gradlew assembleDebug
adb connect <tv-ip>:5555        # enable Developer options and network debugging on the TV
adb install -r --user 0 app/build/outputs/apk/debug/app-debug.apk
```

The app appears in the launcher as HackerTV. `--user 0` installs for the owner profile: on a TV
with a second user, a plain `adb install` can report success while the app stays missing from the
launcher.

## Known limits

- Some videos disallow embedding and are skipped by the page itself.
- The CRT and VHS filters can stutter on slower TV chips. Use CLEAN there.
- The app needs Android 8.0 (API 26) or newer, for its adaptive launcher icon.
