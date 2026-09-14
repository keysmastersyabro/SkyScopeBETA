# SkyScope

Open-source Minecraft client for SkyScope, built for **Minecraft 26.1.2 / Fabric**.

[**Download SkyScope 3.9.16**](https://github.com/keysmastersyabro/SkyScopeBETA/releases/download/v3.9.16/skyscope-3.9.16.jar) · [Commands](COMMANDS.md)

## Features

- Link your SkyScope account and sync your filters.
- Receive flip alerts and browse, sort, blacklist or dismiss items.
- Configure chat alerts and optional Quick Buy / Auto Buy.

Flip discovery and pricing run on SkyScope's servers. This repository contains the Minecraft client, its tests and build files. It does not contain the server's discovery or valuation implementation. Client-side ranking only sorts opportunities already received from the service.

## Install

1. Use **Minecraft 26.1.2**, **Fabric Loader 0.19.3+**, **Fabric API** and **Java 25+**. Fabric API **0.154.0+26.1.2** is the tested version.
2. Download `skyscope-3.9.16.jar` and put it in your Minecraft `mods` folder. Remove any older SkyScope JAR first; install only one SkyScope JAR.
3. Sign in at [skyscope.dev](https://skyscope.dev) and generate a Minecraft linking code.
4. Run `/skyscope link <code>` in Minecraft.
5. Press **Right Shift** for the dashboard or **B** for the flip browser. `/skyscope help` lists the commands.

Quick Buy and Auto Buy are off on a fresh install. Auto Buy pauses if it cannot verify the auction identity, item and exact price; you can inspect and buy manually instead. Existing settings remain in `config/skyscope-public/` for compatibility with the previous release; the mod is named SkyScope.

## Instant median alerts

Use `/skyscope instantmedian on` to enable this optional mode, or `/skyscope instantmedian off` to disable it. It is OFF by default; the dashboard also has a toggle.

Listings with a buy price **below 100,000 coins** and a seven-day **item-ID median above 3,000,000 coins** can appear before pricing finishes. The alert shows **profit pending**. Item-ID medians pool variants and are not build-specific resale targets.

If **Auto Buy is also ON**, these alerts can start an immediate purchase **before profit is confirmed**. Current purchase limits, blacklist and auction verification still apply. Later pricing updates show the calculated target/profit or a rejection/unresolved reason; they do not themselves create another purchase intent. Pricing may be unresolved if the auction becomes terminal first.

Alerts require an online connection. Results are tracked for up to 180 seconds. Normal validated flips retain their usual filters and purchase controls.

## Build from source

Install a Java 25 JDK, clone this repository and run:

```sh
git clone https://github.com/keysmastersyabro/SkyScopeBETA.git
cd SkyScopeBETA
./gradlew --no-daemon build
```

On Windows, use `gradlew.bat --no-daemon build`. The first build downloads Gradle, Minecraft, Fabric and test dependencies; later cached builds can use `--offline`.

The installable JAR is `build/libs/skyscope-3.9.16.jar`. The `-sources.jar` contains source, not an installable mod. `./gradlew test` runs the client tests. The build verifies the explicit client source list and packaged files and writes a SHA-256 checksum beside the JAR.

## Data and permissions

Each user links their own account. No credentials or local configuration are included. The client sends account-linking/profile requests, normal feed subscriptions and receipt/display acknowledgements to SkyScope's approved TLS endpoints. General chat, inventory and competitor alerts are not uploaded by this client. Purchase-related chat is handled locally when using the buy controls. Estimated profit is not verified resale profit.

## License

[MIT](LICENSE). This license covers the client source distributed in this repository.
