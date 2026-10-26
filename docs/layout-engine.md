# Layout Engine (HTML/CSS Box Model, Flexbox, Grid, Positioning)

Pixelay implements a two-pass layout engine modelled directly after CSS:
1. **Measure pass (`measure(maxWidth)`)**: Bottom-up calculation of natural and width-constrained sizes (cached per pass).
2. **Arrange pass (`arrange(rect)`)**: Top-down assignment of pixel rectangles (`Rect(x, y, width, height)`).

---

## 1. The CSS Box Model (`Box<S>`)

Every node in a Pixelay layout extends `Box<S>`. A `Box` can also be instantiated directly via `box()` as an empty block element (like an HTML `<div>` used as a panel, divider, or geometric shape).

### Sizing & Spacing
| Method | CSS Equivalent | Description |
| :--- | :--- | :--- |
| `.width(px)` / `.height(px)` | `width` / `height` | Fixed outer box size (including padding, excluding margin). |
| `.size(w, h)` | `width` + `height` | Sets both fixed width and fixed height. |
| `.padding(all)` | `padding: A` | Uniform inner padding on all four sides. |
| `.padding(v, h)` | `padding: V H` | Vertical (`top`/`bottom`) and horizontal (`left`/`right`) padding. |
| `.padding(t, r, b, l)` | `padding: T R B L` | Explicit padding per side. |
| `.margin(all)` | `margin: A` | Uniform outer margin on all four sides (may be negative). |
| `.margin(v, h)` | `margin: V H` | Vertical and horizontal outer margin. |
| `.margin(t, r, b, l)` | `margin: T R B L` | Explicit outer margin per side. |

### Overflow & Clipping
| Method | CSS Equivalent | Description |
| :--- | :--- | :--- |
| `.overflow(Overflow.HIDDEN)` | `overflow: hidden` | Clips child backgrounds, text glyphs, and pointer hit-testing to this box's edges. |
| `.overflowX(...)` / `.overflowY(...)` | `overflow-x` / `overflow-y` | Axis-specific clipping (`VISIBLE`, `HIDDEN`, `SCROLL`). |

---

## 2. Flexbox (`Flex`: `row`, `column`, `div`)

Created via `row(children...)`, `column(children...)`, or `div(children...)`:

```java
row(
    text(viewer, 20, "Left"),
    spacer(),
    text(viewer, 20, "Right")
)
    .gap(12)
    .align(Align.CENTER)
    .justify(Justify.SPACE_BETWEEN)
    .padding(16, 24);
```

- **Main-axis distribution (`justify(Justify)`)**:
  - `Justify.START` (default)
  - `Justify.CENTER`
  - `Justify.END`
  - `Justify.SPACE_BETWEEN`
  - `Justify.SPACE_AROUND`
  - `Justify.SPACE_EVENLY`
- **Cross-axis alignment (`align(Align)` & child `.alignSelf(Align)`)**:
  - `Align.STRETCH` (default — stretches children without fixed cross-size)
  - `Align.START`, `Align.CENTER`, `Align.END`
- **Flexibility (`.grow(double)` & `.shrink(double)`)**:
  - When a `row` overflows its available width, children shrink proportionally to `shrink * naturalWidth`, never shrinking below `minContentWidth()` (so words never break mid-word unless `wordBreak(BREAK_ALL)` is set).
  - Free space is distributed proportionally to `.grow(weight)`.

---

## 3. CSS Grid (`Grid`: `grid`)

Created via `grid(children...)`:

```java
grid(
    featuredCard.rowSpan(2),
    card2,
    card3,
    card4,
    card5
)
    .columns(px(260), fr(1), fr(1))
    .rows(auto(), auto())
    .gap(16);
```

- **Track sizing (`Track`)**:
  - `px(size)` — fixed track size in canvas pixels.
  - `fr(weight)` — share of remaining free space (`1fr`, `2fr`, etc.).
  - `auto()` — content-sized track (`fit-content`).
  - `repeat(count, track)` — repeats a track definition `count` times.
- **Item placement**:
  - `.gridRow(line)` / `.gridColumn(line)` (1-based start line, or `0` for auto-placement).
  - `.rowSpan(n)` / `.columnSpan(n)`.
  - `.justifySelf(Align)` / `.alignSelf(Align)` inside the assigned grid area.

---

## 4. CSS Positioning (`Position`: `STATIC`, `RELATIVE`, `ABSOLUTE`) & `z-index`

Any `Box` can be positioned using CSS positioning schemes:

```java
stack(
    mainContent,
    badge(viewer, "NEW", 0xFF4F46E5, "<white>")
        .position(Position.ABSOLUTE)
        .top(12)
        .right(12)
        .zIndex(10)
);
```

- **`Position.STATIC`** (default): Normal flow inside `Flex`, `Grid`, or `Stack`.
- **`Position.RELATIVE`**: Laid out in normal flow, then shifted by `.left(px)` / `.right(px)` and `.top(px)` / `.bottom(px)`.
- **`Position.ABSOLUTE`**: Removed from normal flow (`Flex`, `Grid`, and `Stack` ignore it when measuring and arranging in-flow children) and positioned relative to its parent container's box (or directly on the canvas if used on a root `Box`):
  - `.left(px)`, `.right(px)`, `.top(px)`, `.bottom(px)`
  - `.absolute(left, top)` — shorthand for `.position(Position.ABSOLUTE).left(left).top(top)`.
  - `.inset(all)` / `.inset(v, h)` / `.inset(t, r, b, l)` — sets all four edge offsets; if both `left` and `right` (or `top` and `bottom`) are set without a fixed width/height, the box stretches between those edges!
  - `.translate(percentX, percentY)` / `.translatePx(dx, dy)` — CSS `transform: translate(...)`, e.g. `.translate(-0.5, -0.5)` for `transform: translate(-50%, -50%)` to center a marker on `(left, top)`.
- **`.zIndex(int)`**: Controls stacking order for both depth layers and pointer hit-testing.

---

## 5. `Stack` & `ScrollView`

- **`stack(children...)`**: Overlays all in-flow children in the same area (like a single CSS grid cell).
- **`scrollView(children...)`**: Vertical scrollable viewport with automatic clipping (`overflow-y: scroll; overflow-x: hidden`), mouse wheel support, smooth inertial gliding, and an interactive scrollbar track & draggable thumb:
  - `.scrollbar(ScrollbarPolicy.AUTO | ALWAYS | NEVER)`
  - `.scrollbarWidth(px)`, `.scrollbarMargin(px)`, `.minThumbSize(px)`
  - `.trackColor(argb)`, `.thumbColor(argb)`, `.thumbHoverColor(argb)`, `.thumbDragColor(argb)`
  - `.scrollStep(px)`, `.smooth(boolean)`, `.scrollTo(px)`, `.scrollToPercent(pct)`, `.onScroll(listener)`
