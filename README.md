# Pixelay

Pixel-perfect text, buttons, and cursors on flat in-world canvases for Paper servers.

Pixelay maps a 2D pixel grid onto a plane in the world and draws on it with `TextDisplay` entities.
You can design with pixels or a layout DSL similar to CSS.
Pixelay calculates world transforms, font measurements, alignment, backgrounds, click hitboxes, and updates.

**Documentation: [Wiki](https://github.com/Horuse/Pixelay/wiki)** — guide, configuration, and
[examples](https://github.com/Horuse/Pixelay/wiki#examples).

## Features

- **Canvas** — A rotatable pixel plane of any size.
- **Components** — Text, buttons, cursors, and click catchers.
- **Three render modes** — Server entities, per-player entities, or packet-only (no server entities).
- **Layout DSL** — Box model, flexbox rows and columns, CSS Grid, stacks, text wrapping, and backgrounds.
- **Exact measurement** — Built-in vanilla font table. Pixelay measures resource-pack glyphs automatically.
- **Live content** — Suppliers and PlaceholderAPI, updated on a timer with automatic recovery.
- **Full-screen screens** — Spectator click routing, cursor, sneak blocking, and chat suppression.
- **Debug mode** — `/pixelay debug layout` shows every box and overflow.

## Quick start

Requirements: Paper 1.21.11 (Java 21), [PacketEvents](https://github.com/retrooper/packetevents) 2.12+,
and optional [PlaceholderAPI](https://github.com/PlaceholderAPI/PlaceholderAPI).

Server installation:
1. Save the jar file from [Releases](https://github.com/Horuse/Pixelay/releases) and PacketEvents into `plugins/`.
2. Copy resource packs with custom font glyphs to `plugins/Pixelay/resourcepacks/`.

Developers ([JitPack](https://jitpack.io/#Horuse/Pixelay)):

```gradle
repositories { maven { url = 'https://jitpack.io' } }
dependencies { compileOnly 'com.github.Horuse:Pixelay:<tag>' }
```

```yaml
# plugin.yml
depend: [ Pixelay ]
```

```java
import static dev.horuse.pixelay.layout.Layout.*;

Canvas canvas = new Canvas(location, 0f, new CanvasResolution(320, 180, 4, 2.25));

MountedLayout ui = mount(
    column(
        new CanvasText(0, 0, 24, TextContent.of("<gold><b>Welcome")),
        spacer(),
        node(new CanvasButton(0, 0, 12, TextContent.of("<green>[ Play ]"), p -> p.sendMessage("Click!")))
    ).align(Align.CENTER).padding(16).background(0xC0101820)
);
ui.spawn(canvas);
```

More in [Getting Started](https://github.com/Horuse/Pixelay/wiki/Getting-Started).

## Building

```sh
./gradlew build
```

The build creates the plugin jar with relocated Configurate and a separate API jar.
`tools/fontgen/generate.py` regenerates the vanilla width table for a new Minecraft version.

## License

[MIT](LICENSE) © Horuse
