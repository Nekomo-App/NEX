# Integration notes

These notes come from each project's source code, not guesses.

## Nekomo
- Packages: `app.nekomo`, `app.nekomo.tv`, plus `.beta` and `.debug` variants of each.
- Manifest: `{"nekomoRepository": 1, "repositories": [{type, name, url, description}], "apis": [...]}` where `type` is `cloudstream` or `zangetsu`. The official one is `Nekomo-App/neko-source/repo/index.json`.
- App updates: `neko-source/apks/latest.json` (builds per flavor/channel, with sha256). Changelogs come from the GitHub releases API.
- Nekomo handles `nekomo-repo://add?url=<repository url>` (login-gated, always shows a confirmation dialog; nothing is added silently). It accepts Nekomo manifests and CloudStream, Zangetsu, Aniyomi and Mihon repository addresses (address only; APK extensions stay manual). Older Nekomo versions get the URL copied.

## Mihon / Aniyomi
- Index: `index.min.json` array of `{name, pkg, apk, lang, code, version, nsfw, sources}`. APKs live at `<base>/apk/<apk>` and icons at `<base>/icon/<pkg>.png`.
- Extensions are APKs declaring `uses-feature` `tachiyomi.extension` (Mihon) or `tachiyomi.animeextension` (Aniyomi). Compatible library versions: Mihon 1.4–1.5, Aniyomi 12–16.
- Add-repo intents: `tachiyomi://add-repo?url=` and `aniyomi://add-repo?url=`.

## CloudStream
- `repo.json`: `{name, manifestVersion, pluginLists: [url]}`. Each plugin list is an array of `{internalName, name, url (.cs3), version, status, apiVersion, ...}`.
- Hand-off: `cloudstreamrepo://<url without scheme>`. Plugins are installed by CloudStream into its private files dir.

## Zangetsu
- Package `com.spyou.watch_app`. `index.json`: `{name, sources: [{id, name, version, type, lang, file, logo}]}`. Paths are relative to the index.
- Hand-off uses `nekomo-repo://add?url=`; when no Nekomo build handles it the URL is copied instead.

## Nekomo links (Nekomo 2.9.2+)
- `nekomo-repo://add?url=<repo>`: confirmation dialog for a Nekomo manifest or a CloudStream/Zangetsu/Aniyomi/Mihon repository address.
- `nekomo-repo://source?repo=<repo>&id=<id>&type=cloudstream|zangetsu`: shows the source and installs it after confirmation (Aniyomi/Mihon types only save the repository address; APKs are never installed by Nekomo).
- `nekomo-repo://api?id=anilist|kitsu|jikan|tvmaze|tmdb`: opens that metadata API in Nekomo's Extensions manager.
