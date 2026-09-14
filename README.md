# SkyScopeBETA

Public SkyScope mod for **Minecraft 26.1.2 / Fabric**.

## Download

[**Download SkyScope Public 3.9.12-public.1**](https://github.com/keysmastersyabro/SkyScopeBETA/raw/refs/heads/main/skyscope-public-3.9.12-public.1.jar)

This is the public edition. It includes account-linked flip alerts, filters and item blacklist, the flip browser, Quick Buy, and optional Auto Buy. Owner administration, collector controls, inventory-provider tools, COFL capture, and the owner-build updater are excluded.

## Install

1. Install Minecraft **26.1.2**, **Fabric Loader 0.19.3 or later**, **Fabric API**, and **Java 25 or later**. Fabric API **0.154.0+26.1.2** was tested.
2. Put the downloaded JAR in your Minecraft `mods` folder. Remove any other SkyScope JAR first; both editions use the same mod ID.
3. Sign in to your own account at [skyscope.dev](https://skyscope.dev) and generate a Minecraft linking code.
4. Run `/skyscope link <code>` in Minecraft.
5. Press **Right Shift** for the dashboard or **B** for the flip browser. Use `/skyscope help` for commands and `/skyscope filters` for filters.

Quick Buy and Auto Buy are **off on a fresh install**. The public edition uses its own `config/skyscope-public/` folder.

## File verification

The uploaded JAR passed the public-artifact safety check and **77 public-mod tests**. No account credentials or local configuration are bundled.

SHA-256:

```text
e168883501274ed60cda0b4c5b1c8be2fd22537c483749f6db03d073ee4891a1
```

[Download checksum file](skyscope-public-3.9.12-public.1.jar.sha256)
