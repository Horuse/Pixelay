# Box Model

Every UI component in Pixelay extends `Box<S>`. You can also create empty boxes with `box()` or `div()` to build backgrounds, panels, and dividers.

## Sizing Methods

| Method | CSS Property | Description |
| :--- | :--- | :--- |
| `.width(px)` / `.height(px)` | `width` / `height` | Sets fixed width or height in layout pixels. |
| `.size(w, h)` | `width` + `height` | Sets both width and height simultaneously. |
| `.widthPercent(pct)` / `.heightPercent(pct)` | `width: %` / `height: %` | Sets dimension as a percentage of the parent inner size (`0.0 .. 1.0`). |
| `.minWidth(px)` / `.maxWidth(px)` | `min-width` / `max-width` | Clamps the resolved outer width of the box. |
| `.minHeight(px)` / `.maxHeight(px)` | `min-height` / `max-height` | Clamps the resolved outer height of the box. |

## Spacing & Borders

| Method | CSS Property | Description |
| :--- | :--- | :--- |
| `.padding(all)` | `padding` | Applies uniform inner padding on all four edges. |
| `.padding(v, h)` | `padding: V H` | Sets vertical (`top`/`bottom`) and horizontal (`left`/`right`) padding. |
| `.padding(t, r, b, l)` | `padding: T R B L` | Sets explicit padding per edge. |
| `.margin(all)` / `.margin(v, h)` | `margin` | Sets outer margin around the box (accepts negative values). |
| `.border(width, color)` | `border` | Sets border thickness and ARGB color or hex string (`"#38BDF8"`). |
| `.borderTop(w, c)` etc. | `border-top` etc. | Sets border thickness and color on specific edges. |

## Visibility & Clipping

| Method | CSS Property | Description |
| :--- | :--- | :--- |
| `.background(color)` | `background-color` | Sets packed ARGB int (`0xAARRGGBB`) or CSS string (`#RGB`, `#RRGGBB`, `rgba(...)`). |
| `.opacity(0.0 .. 1.0)` | `opacity` | Multiplies the alpha channel of the background and borders. |
| `.hidden(boolean)` | `display: none` | Removes the box and its subtree from layout, hit-testing, and rendering. |
| `.overflow(Overflow.HIDDEN)` | `overflow: hidden` | Clips child glyphs, panels, and pointer hitboxes to box boundaries. |
