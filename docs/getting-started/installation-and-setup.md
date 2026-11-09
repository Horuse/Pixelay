# Installation & Setup

This guide describes how to install Pixelay on your server and add it to your project.

## Server Requirements

- **Server Software**: Paper 1.21.1+ (or compatible fork).
- **Java Runtime**: Java 21 or higher.
- **Protocol Library**: [PacketEvents](https://github.com/retrooper/packetevents) version 2.12.0 or higher.
- **Optional Extensions**: [PlaceholderAPI](https://github.com/PlaceholderAPI/PlaceholderAPI) for dynamic placeholders.

## Server Installation

Follow these steps to install Pixelay on a Paper server:

1. Download the latest `Pixelay.jar` from GitHub Releases.
2. Download the latest `packetevents-spigot.jar`.
3. Put both files into the `plugins/` directory of your server.
4. Start the server to generate configuration files and font folders.
5. Optional: Put resource packs with custom font glyphs into `plugins/Pixelay/resourcepacks/`.

## Developer Setup (Gradle)

Pixelay publishes builds to JitPack. Add the repository and dependency to your `build.gradle`:

```groovy
repositories {
    mavenCentral()
    maven { url = 'https://jitpack.io' }
    maven { url = 'https://repo.codemc.io/repository/maven-releases/' }
}

dependencies {
    compileOnly 'com.github.Horuse:Pixelay:v1.0.0'
    compileOnly 'com.github.retrooper:packetevents-spigot:2.12.0'
}
```

Add Pixelay to your `plugin.yml` file:

```yaml
name: YourPlugin
version: 1.0.0
main: com.example.plugin.YourPlugin
depend:
  - Pixelay
  - packetevents
```
