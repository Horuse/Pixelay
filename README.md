# Pixelay

Pixel-perfect text, buttons and cursors on flat in-world canvases for Paper servers.

Pixelay maps a 2D pixel grid onto a plane in the world and draws on it with `TextDisplay` entities.
You work in pixels or with a CSS-like layout DSL; Pixelay handles world transforms, exact font
measurement, alignment, backgrounds, click hitboxes and refreshes.

**Documentation: [Wiki](https://github.com/Horuse/Pixelay/wiki)** — guide, configuration and
[examples](https://github.com/Horuse/Pixelay/wiki#examples).

## Features

- **Canvas** — a rotatable pixel plane of any size.
- **Components** — text, buttons, cursors and click catchers.
- **Three render modes** — server entities, per-player entities, or packet-only (nothing on the server).
- **Layout DSL** — box model, flexbox rows/columns, CSS Grid, stacks, text wrapping, backgrounds.
- **Exact measurement** — built-in vanilla font table; resource-pack glyphs are measured automatically.
- **Live content** — suppliers and PlaceholderAPI, refreshed on a timer, with self-healing.
- **Full-screen screens** — spectator click routing, cursor, sneak blocking, chat suppression.
- **Debug mode** — `/pixelay debug layout` shows every box and overflow.

## Quick start

Requirements: Paper 1.21.11 (Java 21), [PacketEvents](https://github.com/retrooper/packetevents) 2.12+,
optionally [PlaceholderAPI](https://github.com/PlaceholderAPI/PlaceholderAPI).

Server: put the jar from [Releases](https://github.com/Horuse/Pixelay/releases) and PacketEvents into
`plugins/`. Resource packs with custom font glyphs go into `plugins/Pixelay/resourcepacks/`.

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

Produces the plugin jar (Configurate relocated) and a plain API jar.
`tools/fontgen/generate.py` regenerates the vanilla width table for a new Minecraft version.

## License

[MIT](LICENSE) © Horuse
