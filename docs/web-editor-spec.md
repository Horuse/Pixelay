# Web Editor & DOM Serialization Specification

Pixelay's layout tree provides full read and write introspection. A visual Web Editor or JSON serializer can inspect, serialize, and modify live `MountedLayout` documents.

---

## 1. Unified DOM Introspection API

Every layout node extends `Box<S>` and exposes semantic getters and setters:

### Base `Box<S>` Properties
| Property | Getter | Setter | Type / Values |
| :--- | :--- | :--- | :--- |
| Tag Name | `tagName()` | *(per subclass)* | `"div"`, `"row"`, `"column"`, `"grid"`, `"stack"`, `"scroll-view"`, `"text"`, `"button"`, `"checkbox"`, `"toggle"`, `"progress"`, `"canvas"`, `"model"`, `"spacer"` |
| Element ID | `id()` | `id(String)` | `String` (nullable) |
| Width / Height | `fixedWidth()`, `fixedHeight()` | `width(double)`, `height(double)` | `double` (`-1` = `auto`) |
| Padding | `padding()` | `padding(Insets)` | `Insets(top, right, bottom, left)` |
| Margin | `margin()` | `margin(Insets)` | `Insets(top, right, bottom, left)` |
| Position | `position()` | `position(Position)` | `STATIC`, `RELATIVE`, `ABSOLUTE` |
| Edge Offsets | `top()`, `right()`, `bottom()`, `left()` | `top(px)`, `right(px)`, `bottom(px)`, `left(px)` | `double` (`NaN` = `auto`) |
| Z-Index | `zIndex()` | `zIndex(int)` | `int` (default `0`) |
| Flex Grow / Shrink | `grow()`, `shrink()` | `grow(double)`, `shrink(double)` | `double` |
| Self Alignment | `alignSelf()`, `justifySelf()` | `alignSelf(Align)`, `justifySelf(Align)` | `START`, `CENTER`, `END`, `STRETCH` |
| Grid Placement | `gridRow()`, `gridColumn()`, `rowSpan()`, `columnSpan()` | `gridRow(int)`, `gridColumn(int)`, `rowSpan(int)`, `columnSpan(int)` | `int` |
| Background | `background()` | `background(int)` | `int` (`0xAARRGGBB`) |
| Overflow | `overflowX()`, `overflowY()` | `overflowX(Overflow)`, `overflowY(Overflow)` | `VISIBLE`, `HIDDEN`, `SCROLL` |
| Cursor | `cursor()` | `cursor(String)` | `"default"`, `"pointer"`, `"text"`, `"crosshair"`, `"not-allowed"`, etc. |
| States & Styles | `stateStyles()`, `customStates()` | `state(State/String, Consumer<Style>)`, `toggle(String, boolean)` | `Map<Object, Style>`, `Set<String>` |
| Computed Layout | `rect()`, `fit()` | *(computed by `layout()`)* | `Rect(x, y, width, height)`, `Fit` |

### `Container<S>` Dynamic DOM Mutation
Every `Container` (`Flex`, `Grid`, `Stack`, `ScrollView`) supports live child manipulation:
- `children()` — returns an immutable snapshot `List<Box<?>>` of child nodes.
- `addChild(LayoutItem)` — appends a child node and invalidates layout caches.
- `removeChild(Box<?>)` — removes a child node and invalidates layout caches.
- `clearChildren()` — removes all children.
- `gap()` / `gap(double)` — gets or sets the inter-child spacing.

When `mountedLayout.refresh()` is called after mutating children or properties, `MountedLayout.layout(...)` automatically rebuilds the box order, `zIndex` stacking, parent pointers, and clip regions.

---

## 2. Canonical JSON Document Schema (for the Web Editor)

Below is the recommended JSON schema mapping 1:1 to Pixelay's `Box` hierarchy:

```json
{
  "version": 1,
  "resolution": { "width": 1920, "height": 1080 },
  "root": {
    "tag": "column",
    "id": "main-card",
    "width": 800,
    "padding": [24, 28, 24, 28],
    "gap": 16,
    "background": "#FFFFFFFF",
    "children": [
      {
        "tag": "row",
        "align": "CENTER",
        "justify": "SPACE_BETWEEN",
        "children": [
          {
            "tag": "text",
            "fontSize": 28,
            "text": "<#0F172A><b>Pixelay Studio</b>"
          },
          {
            "tag": "button",
            "id": "action-save",
            "fontSize": 20,
            "text": "<#FFFFFF><b>Save</b>",
            "padding": [8, 18, 8, 18],
            "background": "#FF4F46E5",
            "states": {
              "HOVERED": { "background": "#FF4338CA" }
            },
            "transition": { "durationMs": 150, "easing": "EASE_OUT" }
          }
        ]
      },
      {
        "tag": "canvas",
        "id": "pixel-pad",
        "gridWidth": 48,
        "gridHeight": 24,
        "width": 744,
        "height": 240,
        "drawable": true,
        "brushColor": "#FF4F46E5",
        "brushSize": 1
      },
      {
        "tag": "text",
        "position": "ABSOLUTE",
        "right": 16,
        "bottom": 12,
        "zIndex": 5,
        "fontSize": 14,
        "text": "<#94A3B8>Absolute Badge"
      }
    ]
  }
}
```

---

## 3. Event Binding by Element `id`

When loading a layout document created in the Web Editor, server-side Java code can bind business logic cleanly by `id()` without coupling layout structure to event handlers:

```java
// Find elements by id() and attach server actions
for (Box<?> box : collectAllBoxes(mounted.root())) {
    switch (box.id()) {
        case "action-save" -> box.onClick(player -> saveProfile(player));
        case "pixel-pad" -> {
            if (box instanceof Canvas canvas) {
                canvas.onDraw((player, x, y, argb) -> syncCanvasPixel(player, x, y, argb));
            }
        }
        case null, default -> {}
    }
}
```
