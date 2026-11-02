---
title: Install Beermat
description: Install Beermat on Windows, macOS or Linux, or use it in the browser.
---

Beermat is free. Download it from the [releases page](https://beermat.pub/download), or [try it in your browser](https://beermat.pub/try) first.

## Windows

- **Installer** (`Beermat-windows-setup.exe`): run it and follow the steps. It can install for you alone (no administrator needed) or for everyone on the computer, open `.bbm` files with Beermat, and put the `beermat` command on the PATH.
- **Portable** (`Beermat-windows-portable.zip`): unzip it anywhere and run `Beermat.exe`. Nothing is installed.

The installers aren't signed yet, so Windows may warn that it doesn't recognise the app: choose **More info**, then **Run anyway**.

## macOS

Open `Beermat-macos.dmg` and drag Beermat to Applications. The app isn't signed yet: the first time, right-click it in Applications, choose **Open**, then **Open** again.

## Linux

Install `beermat-linux.deb` (Debian, Ubuntu) or `beermat-linux.rpm` (Fedora, openSUSE) with your package manager. Beermat goes in `/opt/beermat` and in the applications menu.

## In the browser

[beermat.pub/try](https://beermat.pub/try) runs Beermat in the browser: the same page, toolbar and keys. Your sheet stays in the browser until you save it to your computer. Printing, PDF, `.xmcd` import, symbolic algebra beyond the basics, Python functions and AI assistants need the desktop app; the browser shows them greyed out.

## The `beermat` command

The desktop app brings the [`beermat` command](../../reference/command-line/) with it, for scripts and AI assistants.

- **Windows**: the installer adds it to the PATH (an option, on by default). Open a new terminal and type `beermat`.
- **macOS and Linux**: in the app, **Tools → Install the beermat command…**.

## Updates

Beermat checks for a new version when it starts and tells you if there is one; turn this off in **Help → Check for updates at startup**.
