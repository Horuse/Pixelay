# UI Components & State Styling

All Pixelay components live in `dev.horuse.pixelay.layout` and extend `Box<S>`, providing a unified HTML/CSS-like API with no dummy `(0, 0)` coordinates or manual `node(...)` wrappers required.

---

## 1. Text (`Text` — `new Text(...)`)

Represents an HTML `<p>` or `<span>` text node. Automatically measures and wraps MiniMessage text using pixel-accurate glyph metrics (`FontMetrics`).

```java
// Static MiniMessage text
Text title = new Text(28, "<#0F172A><b>Dashboard Overview</b>")
    .textAlign(HAlign.CENTER);

// Dynamic live-updating text (re-evaluated on every refresh)
Text counter = new Text(18, () -> "<#64748B>Clicks: <b>" + clicks[0] + "</b>");
```

- `.fontSize(px)` — updates font height in canvas pixels.
- `.text(miniMessage)` / `.text(() -> ...)` — updates text content dynamically.
- `.textAlign(HAlign)` / `.textAlign(Align)` — aligns text horizontally (`LEFT`, `CENTER`, `RIGHT`) inside the box independently of `justifySelf`.
- `.whiteSpace(WhiteSpace.NORMAL | NOWRAP)` — controls line wrapping.
- `.wordBreak(WordBreak.NORMAL | BREAK_WORD | BREAK_ALL)` — controls word breaking.
- `.textOverflow(TextOverflow.CLIP | ELLIPSIS)` — when set to `TextOverflow.ELLIPSIS`, automatically truncates single-line text that exceeds the box's available width and appends `"..."` while preserving MiniMessage styling.

---

## 2. Button (`Button` — `new Button(...)`)

Represents an HTML `<button>` element. Defaults to `padding(8, 16)`, `cursor("pointer")`, and centered text (`HAlign.CENTER`, `VAlign.MIDDLE`).

```java
Button saveBtn = new Button(20, "<white><b>Save Changes</b>", player -> {
    player.sendMessage("Saved!");
})
    .background(0xFF4F46E5)
    .state(State.HOVERED, s -> s.background(0xFF4338CA))
    .transition(150, Easing.EASE_OUT);
```

---

## 3. Checkbox (`Checkbox` — `new Checkbox(...)`)

Represents an HTML `<input type="checkbox">` with a font-independent geometric check box and optional label.

```java
Checkbox soundBox = new Checkbox(viewer, "Enable Sound FX", true, (player, checked) -> {
    player.sendMessage("Sound FX: " + checked);
})
    .boxSize(22)
    .activeColor(0xFF4F46E5)
    .inactiveColor(0xFFCBD5E1)
    .checkColor(0xFFFFFFFF);
```

---

## 4. Toggle Switch (`Toggle` — `new Toggle(...)`)

Represents an HTML `<input type="checkbox" role="switch">` pill switch with a sliding thumb knob and optional label.

```java
Toggle pvpSwitch = new Toggle(viewer, "PvP Combat Mode", false, (player, enabled) -> {
    player.sendMessage("PvP: " + enabled);
})
    .trackSize(42, 22)
    .activeColor(0xFF059669);
```

---

## 5. Progress Bar (`ProgressBar` — `new ProgressBar(...)`)

Represents an HTML `<progress>` meter supporting both static values (`0.0..1.0`) and live `DoubleSupplier`s.

```java
ProgressBar xpBar = new ProgressBar(() -> player.getExp())
    .trackHeight(10)
    .trackColor(0x1F000000)
    .fillColor(0xFF4F46E5)
    .width(400);
```

---

## 6. 2D Pixel Canvas (`Canvas` — `new Canvas(...)`)

Represents an HTML `<canvas>` element with a `gridWidth × gridHeight` 2D pixel buffer (`int[]` ARGB), programmatic 2D drawing primitives, and interactive player painting.

```java
Canvas pad = new Canvas(viewer, 64, 32)
    .size(640, 320)
    .background(0xFFF8FAFC)
    .drawable(true)
    .brushColor(0xFF4F46E5)
    .brushSize(2)
    .onDraw((player, gx, gy, color) -> {
        // Optional callback on every painted pixel/stroke
    });

// Programmatic 2D drawing API
pad.clear();
pad.fillRect(4, 4, 12, 8, 0xFFE0F2FE);
pad.strokeRect(4, 4, 12, 8, 0xFF0284C7);
pad.drawLine(0, 0, 63, 31, 0xFF10B981);
pad.fillCircle(32, 16, 6, 0xFFF43F5E);
```

- **2D Greedy Meshing**: Adjacent pixels of the same ARGB colour are automatically merged into minimal rectangles on each render pass and drawn using a pooled `List<TextBackground>`, minimizing entity/packet count.
- **Interactive Brush Painting**:
  - In `ScreenSession`: clicking toggles continuous brush stroke mode (`drawing = true`), dragging the cursor draws smooth Bresenham lines (`dragTo`), and clicking again or leaving the canvas stops the stroke.
  - On world canvases: clicking paints a brush dab at `(gx, gy)`.

---

## 7. 3D Models (`Item` & `Block` — `new Item(...)` & `new Block(...)`)

Embeds native 3D `ItemDisplay` and `BlockDisplay` entities directly into layouts:

```java
Item swordIcon = new Item(new ItemStack(Material.DIAMOND_SWORD), 64);
Block stoneTile = new Block(Material.STONE_BRICKS.createBlockData(), 128, 64);
```

---

## 8. States (`State.HOVERED`, `State.DISABLED`, `State.SELECTED`) & CSS Transitions

Every `Box` supports CSS pseudo-class states and custom class-like states via `.state(...)`:

```java
box
    .background(0xFFFFFFFF)
    .state(State.HOVERED, s -> s.background(0xFFF1F5F9).cursor("pointer"))
    .state(State.SELECTED, s -> s.background(0xFFEEF2FF))
    .state(State.DISABLED, s -> s.background(0xFFE2E8F0).cursor("not-allowed"))
    .state("danger", s -> s.background(0xFFFFE4E6))
    .transition(180, Easing.EASE_IN_OUT);
```

- Built-in states: `State.HOVERED`, `State.SELECTED`, `State.DISABLED`.
- Custom states: `.state("name", style -> ...)` toggled via `.toggle("name", boolean)`.
- Easing curves (`Easing`): `LINEAR`, `EASE`, `EASE_IN`, `EASE_OUT`, `EASE_IN_OUT`, `EASE_OUT_BACK`, `BOUNCE`, or custom `Easing.cubicBezier(x1, y1, x2, y2)`.
