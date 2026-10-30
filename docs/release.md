# Releases

> Status: **decided** by Claude on 2026-10-28 while Dan was away; approved by Dan on 2026-10-29. A trial build ran that night (all three platforms, about 7 minutes): Windows setup 46 MB, macOS DMG 67 MB, Linux deb 52 MB and rpm 61 MB, CLI zip 2.4 MB. With Python and SymPy bundled (trial runs on 2026-10-29, all three passing the Python check): Linux deb 79 MB, CLI zip 31–37 MB per system, Microsoft Store MSIX 89 MB; per system, the uploaded artifacts total 185 MB on Windows (setup, portable zip, CLI), 134 MB on macOS (DMG, CLI) and 214 MB on Linux (deb, rpm, CLI). Not yet run for a real release.

## Versions

- **Semantic versioning**, `MAJOR.MINOR.PATCH`: a new feature bumps MINOR, a fix PATCH; MAJOR changes only if old worksheets stop opening as before (the file format has its own number, `beermat: 1` in the frontmatter).
- **The first public version is 1.0.0.** Before it, pre-releases are `1.0.0-beta.1`, `1.0.0-beta.2`… (published as GitHub pre-releases). Starting at 1.0.0 also suits the installers: macOS packages can't have a version starting with 0.
- **One number, in one place**: `version=` in `gradle.properties`. Between releases it is the next version with `-dev` (`1.1.0-dev`). The app (Help → About), `beermat --version` and the MCP server all report it. A release build takes its version from the git tag instead (`-Pversion=1.0.0`).
- Native installers want a plain `X.Y.Z`, so `1.0.0-beta.2` installs as 1.0.0; the app still shows the full version.

## Making a release

1. Update `CHANGELOG.md`: a `## 1.2.0` section, short and in plain words (it becomes the release notes).
2. Commit, then tag and push: `git tag v1.2.0 && git push origin v1.2.0`.
3. **Release** (`.github/workflows/release.yml`) builds, on GitHub's machines:
   - Windows: `Beermat-windows-setup.exe` (Inno Setup, see below), and `Beermat-windows-portable.zip` (see [Portable](#portable-windows));
   - macOS: `Beermat-macos.dmg`;
   - Linux: `beermat-linux.deb` and `beermat-linux.rpm`;
   - the command line, with its Python: `beermat-cli-windows.zip`, `beermat-cli-macos.zip`, `beermat-cli-linux.zip` (need Java 21 for now);

   Each job first checks that the bundled Python answers: `import sympy` inside the app as packaged (on Linux, from the unpacked `.deb`), and `beermat eval examples/symbolic.bbm` from the unzipped command line, which fails if a symbolic result needs SymPy and doesn't get it.

   It publishes them as a GitHub Release, with the changelog section as notes. A tag with a `-` (`v1.0.0-beta.1`) is a pre-release. It also builds `Beermat-windows.msix` for the Microsoft Store, kept on the run's page only (see [Microsoft Store](#microsoft-store)).
4. For a full release, upload the `.msix` to Partner Center as a new submission. Once the Store has published it, set `packaging/windows/store-version.txt` to that version and push, so Store users get the update notice.
5. Set `gradle.properties` to the next `-dev` version.

To try the installers without publishing: Actions → Release → Run workflow (the artifacts appear on the run's page). The trial is stamped `1.0.0-trial` (macOS packages need a first number of at least 1); `only` builds one system (`windows`, `macos` or `linux`), to save minutes.

**CI** (`.github/workflows/ci.yml`) builds and tests every push to `main`, on Linux only while the repository is private (GitHub bills macOS minutes at 10× and Windows at 2× for private repositories; public ones are free).

## Downloads on the website

The release files keep the same names in every version, so the website links never change:

```
https://github.com/beermat-pub/beermat/releases/latest/download/Beermat-windows-setup.exe
https://github.com/beermat-pub/beermat/releases/latest/download/Beermat-windows-portable.zip
https://github.com/beermat-pub/beermat/releases/latest/download/Beermat-macos.dmg
https://github.com/beermat-pub/beermat/releases/latest/download/beermat-linux.deb
```

These only work once the repository is public (a private repository's releases need a login).

## Installers and branding

- **Every icon comes from `branding/logo.svg`**: `./gradlew :app:icons` renders `packaging/icons` (PNG sizes, the Windows `.ico`, the macOS `.icns`, the installer pictures), committed. Run it again when the logo changes. Sizes of 32 px and below use `logo-small.svg`, as `branding/README.md` says.
- **Windows: Inno Setup, not MSI.** Compose's MSI uses WiX's stock dialogs, with their red artwork; Inno Setup's modern wizard shows our cream-and-amber pictures instead. It installs for everyone or just the user (no admin needed), adds a Start menu entry, optionally a desktop icon, and opens `.bbm` files with Beermat. The script is `packaging/windows/beermat.iss`.
- **macOS**: a DMG from Compose, with the icon and bundle id `pub.beermat.app`.
- **Linux**: `.deb` and `.rpm` from Compose, with the icon and a menu entry.
- The app bundles its own Java runtime and Python with SymPy (about 210 MB installed on Windows, of which 87 MB is Python; [symbolic.md](symbolic.md#bundled-sympy)); nothing else to install.

## Portable (Windows)

Asked for by Dan on 2026-10-28: a version with no installation. The Microsoft Store can't offer one (it only lists apps that install, as MSIX or an installer; the MSIX is the lightest of those: per user, no admin, removed cleanly). So the portable version is on the GitHub release and the website: `Beermat-windows-portable.zip` is the app folder (`createDistributable`) zipped, with its own Java and Python (83 MB). Unzip it anywhere, a USB stick included, and run `Beermat.exe`.

What it doesn't do, being uninstalled: no Start menu entry, and `.bbm` files don't open with it on a double-click (open them from the app, or drag them onto `Beermat.exe`). The user's settings (zoom, toolbar) still go to Java's preferences in the registry, and `~/.beermat` is used as usual; making those follow the folder is possible later (a marker file next to `Beermat.exe`), if wanted. The update check works as in the installed version and points to the GitHub release.

## Microsoft Store

Asked for by Dan on 2026-10-28. Compose has no MSIX target, so `packaging/windows/msix.ps1` packs the app image (`createDistributable`) itself, with the Windows SDK's `makepri` and `makeappx`:

- **The manifest** is `packaging/windows/AppxManifest.xml`. It asks for `runFullTrust`, a restricted capability that the Store approves when the submission explains it. Note for certification: "Beermat is a desktop app built on the Java runtime (bundled); it runs as a full-trust Win32 process." It opens `.bbm` files.
- **The logos** come from the logo like every other icon (`./gradlew :app:icons` writes `packaging/icons/msix`). There are target-size versions for the taskbar, indexed by `resources.pri`.
- **The version** has four numbers with the last one 0, as the Store requires: `1.2.0` packs as `1.2.0.0`. A beta packs as its plain version, so only full releases go to the Store, each with a higher version than the last.
- **No signing:** the Store signs what it publishes. Because of that, the `.msix` isn't attached to the GitHub release (an unsigned package doesn't install from a download). The Release workflow uploads it as the run's `microsoft-store` artifact, to upload by hand to Partner Center.
- **The identity** (`Identity Name`, `Publisher`, publisher display name) comes from Partner Center once the name "Beermat" is reserved (App management → Product identity). Set it in the repository's Actions variables `MSIX_NAME`, `MSIX_PUBLISHER` and `MSIX_PUBLISHER_NAME`. Without them the package uses placeholders: it builds, but the Store rejects it.
- **The Store build has its own update check** (Dan, 2026-10-28). A release reaches GitHub days before it passes Store certification, so the Store build doesn't use GitHub's releases. Instead it reads `packaging/windows/store-version.txt` on `main` (through raw.githubusercontent.com), and a newer version there links to the app's Store page. The script adds `-Dbeermat.store=<Store ID>` to the launcher's options. The Store ID is the 12 characters on Partner Center's Product identity page, set in the Actions variable `MSIX_STORE_ID`; without it, the link searches the Store for "Beermat". The Store updates apps by itself too, so the notice mostly helps those who turned that off.
- The "buy us a beer" link stays: the Store allows non-game apps to take payments their own way, so a donation link should pass (†, a guess until the first certification).
- **Locally:** `.\gradlew :app:createDistributable`, then `.\packaging\windows\msix.ps1 -Version 1.0.0` writes `build\installers\Beermat-windows.msix`. To try it installed without signing, turn on Developer Mode (Settings → System → For developers) and run `Add-AppxPackage -Register build\msix\Beermat\AppxManifest.xml`; `Get-AppxPackage Beermat | Remove-AppxPackage` removes it.
- Later: the command line inside the Store package, as an app execution alias (`beermat` in any terminal).

## Update check

The app checks for a new version when it starts (Dan, 2026-10-28):

- one request, in the background, to GitHub's API for the latest release (`https://api.github.com/repos/beermat-pub/beermat/releases/latest`), so publishing a release is all it takes; no file on the website to keep up to date;
- when the release is newer than the running version (semantic versioning; pre-releases aren't offered, since GitHub's "latest" skips them), the status bar shows **"Beermat 1.2.0 is out"** in amber, linking to the release page. No dialog, no download in the background;
- Help → **Check for updates now** says so either way ("up to date", or "couldn't reach GitHub"); Help → **Check for updates at startup** turns the automatic check off, remembered between runs (Java preferences, node `pub/beermat`);
- nothing is sent but the request itself (a `User-Agent: Beermat/<version>` header); failures (offline, rate limit of 60 an hour per address, the repository still private) are silent;
- `BEERMAT_UPDATE_URL` points the check at another URL, for trying it before a release exists.

Code: `app/Updates.kt`. The bundled Java runtime includes `java.prefs` and `jdk.crypto.ec` for it.

## Signing

**Decided by Dan on 2026-10-28: no signing for now.**

Unsigned installers work, with a warning:

- **Windows** shows SmartScreen's "Windows protected your PC" until the installer builds a reputation; users click "More info → Run anyway". A code-signing certificate removes it: an OV certificate (about €200–400 a year) still needs reputation; Azure Trusted Signing (about $10 a month, for individuals with a verified identity) is the cheapest current option. The Microsoft Store version avoids the warning altogether, since the Store signs it.
- **macOS** refuses unsigned apps from the internet: users must right-click → Open (or allow it in System Settings). Signing and notarizing need an Apple Developer account ($99 a year).
- **Linux**: nothing needed.

Recommendation: release 1.0.0 unsigned with clear instructions on the download page, and add Azure Trusted Signing and an Apple Developer account if people take it up. The workflow has a place for both (secrets, then a signing step).

## Later

- The command line bundled with its own runtime (jlink), and inside the app installers.
- winget, Homebrew and Flathub listings.
