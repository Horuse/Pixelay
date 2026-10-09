# Pixelay

Pixel-perfect text, buttons and cursors on flat in-world canvases for Paper servers.

Pixelay maps a 2D pixel grid onto a plane in the world and draws components on it with
`TextDisplay` entities. You place things in pixels (`x` right, `y` down, origin top-left),
Pixelay handles world transforms, rotation, font measurement, alignment and click hitboxes.

## Features

- **Canvas** — a rotatable (yaw/pitch) pixel plane with padding and anchor helpers.
- **Components** — `CanvasText`, `CanvasButton`, `CanvasCursor`, `CanvasClickCatcher`.
- **Three render modes**
  - *server* — real entities visible to everyone;
  - *packet* — real entities shown to a single viewer (per-player visibility);
  - *fake* — packet-only entities, nothing exists on the server.
- **Accurate text measurement** — vanilla font width table (bold-aware, MiniMessage-aware) plus
  overrides for resource-pack glyphs, used for CSS-like `HAlign`/`VAlign` anchoring and hitboxes.
- **Dynamic content** — constant text, suppliers or PlaceholderAPI templates, refreshed on a timer.
- **Self-healing** — registered surfaces are respawned automatically if an entity dies.
- **Spectator screens** — click routing via packets, sneak-dismount blocking, chat suppression.
- **Fully configurable** — timings, text style, cursor, chat suppression and even entity
  metadata indexes live in `config.yml`.

## Requirements

- Paper 1.21.11 (Java 21)
- [PacketEvents](https://github.com/retrooper/packetevents) 2.12+
- [PlaceholderAPI](https://github.com/PlaceholderAPI/PlaceholderAPI) — optional

## Installation

1. Download the latest jar from [Releases](https://github.com/Horuse/Pixelay/releases) (or build with `./gradlew build`).
2. Put `build/libs/pixelay-<version>.jar` and PacketEvents into `plugins/`.
3. Start the server, adjust `plugins/Pixelay/config.yml`, run `/pixelay reload`.

## Usage (API)

Pixelay is published through [JitPack](https://jitpack.io/#Horuse/Pixelay).

Gradle:

```gradle
repositories {
    maven { url = 'https://jitpack.io' }
}

dependencies {
    compileOnly 'com.github.Horuse:Pixelay:<tag>'
}
```

Maven:

```xml
<repository>
    <id>jitpack.io</id>
    <url>https://jitpack.io</url>
</repository>

<dependency>
    <groupId>com.github.Horuse</groupId>
    <artifactId>Pixelay</artifactId>
    <version>TAG</version>
    <scope>provided</scope>
</dependency>
```

Then declare the runtime dependency in your `plugin.yml`:

```yaml
depend: [ Pixelay ]
```

Server owners install the plugin jar from [Releases](https://github.com/Horuse/Pixelay/releases).

```java
// 256x144 px canvas, 4x2.25 blocks, facing south
Canvas canvas = new Canvas(location, 0f, new CanvasResolution(256, 144, 4, 2.25));

CanvasText title = new CanvasText(128, 12, 16, TextContent.of("<gold><b>Welcome"))
        .align(HAlign.CENTER, VAlign.TOP);
title.spawn(canvas);

CanvasButton play = new CanvasButton(128, 100, 12, TextContent.of("<green>[ Play ]"),
        player -> player.sendMessage("Click!"));
play.spawn(canvas);

// Live text through PlaceholderAPI
CanvasText online = new CanvasText(8, 136, 8, TextContent.placeholder("Online: %server_online%"))
        .align(HAlign.LEFT, VAlign.BOTTOM);
online.spawn(canvas);
```

> **Security:** all canvas text is parsed as MiniMessage, and `TextContent.placeholder(...)`
> expands every `%placeholder%`. Never pass player-supplied text (chat, names, signs) directly —
> escape it with `MiniMessage.miniMessage().escapeTags(input)` and strip `%` first.

Per-player and packet-only variants:

```java
CanvasText.packet(viewer, x, y, heightPx, content);   // visible to one player
CanvasButton.fake(viewer, x, y, heightPx, content, onClick); // packet-only
```

Fake components have no server-side hitbox: resolve clicks yourself with
`canvas.pixelFromRay(eye, direction)` and `CanvasLayout.contains(...)`, and use a
`CanvasClickCatcher` to receive the raw click from a spectator player.

To get automatic refresh and self-healing, implement `CanvasSurface` and register it:

```java
Pixelay.manager().register(surface);
Pixelay.manager().unregister(surface);
```

Chat suppression while a full-screen canvas is shown:

```java
ChatSuppression.suppress(player);
ChatSuppression.restore(player); // delivers queued messages
```

## Layout

Instead of computing pixel positions by hand, build a layout tree with a small CSS-like DSL
(box model + flexbox + grid). Components are placed into it directly:

```java
import static dev.horuse.pixelay.layout.Layout.*;

MountedLayout ui = mount(
    column(
        row(title, spacer(), balance),
        spacer(),
        row(
            grid(cards).columns(repeat(3, px(220))).rows(repeat(3, px(136))).autoFlow(Grid.Flow.COLUMN),
            spacer(),
            continueButton
        ).align(Align.END)
    ).padding(32)
);
ui.spawn(canvas);   // measure, arrange, spawn
ui.refresh();       // re-layout when texts change, moves only what shifted
ui.click(px, py, player);
```

| Building block | Meaning |
|----------------|---------|
| `row(...)`, `column(...)` | flexbox; `gap`, `align` (cross axis, default `STRETCH`), `justify` (`START`, `CENTER`, `END`, `SPACE_BETWEEN`) |
| `grid(...)` | CSS Grid: `columns`/`rows` tracks (`px`, `fr`, `auto`, `repeat`), `autoColumns`/`autoRows`, `autoFlow(ROW\|COLUMN)`, `gap`, `align`/`justifyItems`; items take `rowSpan`/`columnSpan` and `gridRow`/`gridColumn` |
| `stack(...)` | layers sharing one area, placed by `justifySelf` / `alignSelf` |
| `spacer()`, `space(px)` | flexible or fixed empty space along the main axis |
| `node(component)` | a component as a leaf, to set box properties on it |

Every box supports `padding`, `margin` (negative values allowed), `width`/`height`, `grow`, `shrink`,
`alignSelf` / `justifySelf`, `id` (hit testing via `idAt`) and `onClick`.

Alignment is one system, like CSS: `align` / `alignSelf` (vertical; the cross axis in a row or a column)
and `justifyItems` / `justifySelf` (horizontal). A component inside a layout is pinned by them within
its rectangle (top-left by default), so `component.align(...)` is not used there. In a row or a column
`justifySelf` does not move the box (as in CSS flexbox); a leaf still uses it to pin its text.

In a row children start at their natural width; when they do not fit, `shrink` (default 1) takes
the missing space from them, never below their min-content width (texts wrap, words stay whole).

### Text wrapping

Layouts pass the available width down like CSS: a column, a stack or a grid area gives its children
its own width, a row gives growing children what is left. Texts wrap to that width (the TextDisplay
`line_width` is set per element), so height accounts for the extra lines. Like CSS, a word is never
broken: a word wider than its space overflows instead (visible with `/pixelay debug layout`).
`text.line-width` in `config.yml` stays the upper limit.

### Debugging layouts

`/pixelay debug layout` (permission `pixelay.admin`) toggles a per-player debug mode: every component
gets a translucent background showing its text block — blue when it fits, red where an overflow starts
(the box itself is bigger than its space), orange when it sits inside an overflowing box. The
highlight follows the next canvas refresh. Fake and per-player components show it only to the debugging player; server-wide
components show it to everyone while anyone debugs.

## Configuration

| File | Purpose |
|------|---------|
| `config.yml` | Timings, text style, click debounce, cursor, chat suppression, packet metadata indexes. |
| `font-widths.yml` | Width overrides (in font pixels) for custom resource-pack glyphs. |

All options are documented inline in the generated files.

## Commands & permissions

| Command | Permission | Description |
|---------|------------|-------------|
| `/pixelay reload` | `pixelay.admin` (op) | Reloads `config.yml` and `font-widths.yml`. |
| `/pixelay debug layout` | `pixelay.admin` (op) | Highlights components that overflow their layout space. |

## Regenerating the font width table

`tools/fontgen/generate.py` rebuilds `FontWidths.java` from the vanilla font assets
(and optionally your resource pack) using only the Python standard library:

```sh
python3 tools/fontgen/generate.py \
  --assets path/to/vanilla-font-assets \
  --pack path/to/your-resource-pack \
  --output src/main/java/dev/horuse/pixelay/content/FontWidths.java
```

Paths can also be set in a `.env` file at the repository root
(`VANILLA_ASSETS_PATH`, `RESOURCE_PACK_PATH`). See the script header for details.

## Building

```sh
./gradlew build
```

Produces the shaded plugin jar (Configurate relocated to `dev.horuse.pixelay.libs`) and a plain
API jar for compiling against.

## License

[MIT](LICENSE) © Horuse
