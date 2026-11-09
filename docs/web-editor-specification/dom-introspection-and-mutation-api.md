# DOM Introspection & Mutation API

Every layout node in Pixelay extends `Box<S>` and provides accessors and mutators for introspection.

## Core Properties

| Property | Getter | Setter | Description |
| :--- | :--- | :--- | :--- |
| `tagName` | `tagName()` | *(immutable)* | Element tag: `"div"`, `"row"`, `"column"`, `"grid"`, `"button"`, `"text"`, etc. |
| `id` | `id()` | `id(String)` | Optional element identifier. |
| `fixedWidth` / `fixedHeight` | `fixedWidth()` / `fixedHeight()` | `width(double)` / `height(double)` | Fixed pixel size (`-1` = auto). |
| `padding` | `padding()` | `padding(Insets)` | Inner edge insets. |
| `margin` | `margin()` | `margin(Insets)` | Outer edge insets. |
| `position` | `position()` | `position(Position)` | Positioning mode (`STATIC`, `RELATIVE`, `ABSOLUTE`). |
| `zIndex` | `zIndex()` | `zIndex(int)` | Explicit depth stack level. |
| `grow` / `shrink` | `grow()` / `shrink()` | `grow(double)` / `shrink(double)` | Flex expansion and shrinkage factors. |
| `alignSelf` | `alignSelf()` | `alignSelf(Align)` | Individual cross-axis alignment. |
| `background` | `background()` | `background(int)` | Packed ARGB background color. |
| `cursor` | `cursor()` | `cursor(String)` | Active cursor sprite name. |

## Container Tree Mutation

Containers (`Flex`, `Grid`, `Stack`, `ScrollView`) support dynamic child mutation:

- `children()`: Returns an immutable snapshot list of children.
- `addChild(LayoutItem)`: Appends a child box and marks the layout dirty.
- `removeChild(Box<?>)`: Removes a child and triggers a layout recount.
- `clearChildren()`: Clears all child elements.
- `gap(double)`: Sets inter-child spacing.

Call `mountedLayout.refresh()` to rebuild the spatial layout and update client display entities.
