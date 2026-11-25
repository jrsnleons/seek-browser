# Seek

A small, quiet web browser for Android tablets. About 190 KB, no Google services, no account, no telemetry.

Seek is an Android port of [Search](https://github.com/driceroland/Search) by Office Commun, a minimalist browser for the Mac. It follows Search's design (a row of tabs, one field, and the page) and its ideas. It is renamed because Search's README asks forks to use another name, and it is not affiliated with Office Commun.

Search runs on the Mac's WebKit. Android has no WebKit, so Seek uses the tablet's built-in Android System WebView (Chromium).

## What it does

- **One field.** Type an address to go there, or words to search. It finishes addresses from your own history and sends nothing until you press Go (unless you turn on search suggestions).
- **Tabs** down the left or across the top, pinned tabs as small tiles, a tab switcher (long-press the field), and swipe a tab sideways to close it. Tabs come back at launch and cost nothing until you open them.
- **Split view:** up to three pages side by side, with dividers you can drag. Hold a tab and drag it onto the page, and Seek asks whether to split the screen or open it there.
- **Ad and tracker blocker**, on by default, with optional EasyList and EasyPrivacy.
- **Hide something:** tap a cookie banner or other clutter, and it stays hidden on that site.
- **Reading mode**, find on page, per-site text size, floating video (picture-in-picture), and private tabs.
- **Light, dark, or pure-black AMOLED**, a start page with your most-visited sites, an optional new-tab background, and a setting to darken bright sites.
- **Optional looks:** subtle motion (on by default), a frame tinted to match each site, a top bar that hides while you scroll, compact density, and the Inter typeface.
- **Fast on modest tablets:** ads hidden before a page is drawn, background tabs paused and put to sleep after 30 idle minutes, a background-tab budget set from the tablet's memory, addresses looked up while you type, site icons kept on disk, and an optional Lite mode with no autoplay video and no web fonts.
- **Bookmarks, history and downloads**, with bookmark import and export as HTML.
- **Gestures:** swipe two fingers right to go back and left to go forward, and pull down from the top to reload.

Private tabs keep no history, but they share cookies with normal tabs, because Android WebView allows one cookie store per app.

## Install

Download [apk/Seek-1.2.apk](apk/Seek-1.2.apk) on the tablet (press "Download raw file"), open it on the tablet, and allow installs from unknown sources. On Huawei tablets, turn off Pure Mode first. HarmonyOS NEXT (5.x) can't install APKs.

## Build

`build.sh` builds Seek without Gradle (aapt2 → javac → dx → zipalign → apksigner). See the top of the script for the tools it expects. Set `SEEK_KS_PASS` to choose your keystore password. Keep your keystore safe, because updates must be signed with the same key.

## License

MIT. See [LICENSE](LICENSE).

Seek includes a Latin subset of the [Inter](https://rsms.me/inter/) typeface by Rasmus Andersson, under the SIL Open Font License 1.1. See [THIRD_PARTY.md](THIRD_PARTY.md).
