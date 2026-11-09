# Flexbox Layouts

Pixelay provides flex containers for linear horizontal and vertical layouts via `row()` and `column()`.

## Basic Usage

```java
import static dev.horuse.pixelay.layout.Layout.*;
import dev.horuse.pixelay.layout.*;

Box<?> navBar = row(
    text(viewer, 22, "<b>Pixelay Dashboard</b>"),
    spacer(),
    button(viewer, 18, "Profile", p -> {}),
    space(12),
    button(viewer, 18, "Settings", p -> {})
)
    .gap(16)
    .align(Align.CENTER)
    .padding(16, 24)
    .background(0xFF1E293B);
```

## Alignment & Justification

- **`.align(Align)`** (`align-items`): Controls cross-axis alignment of children:
  - `Align.START`: Aligns children to the start of the cross axis.
  - `Align.CENTER`: Centers children along the cross axis.
  - `Align.END`: Aligns children to the end of the cross axis.
  - `Align.STRETCH`: Expands children to match container cross-axis size.

- **`.justify(Justify)`** (`justify-content`): Controls main-axis space distribution:
  - `Justify.START`: Packs items at the beginning of the container.
  - `Justify.CENTER`: Centers items along the main axis.
  - `Justify.END`: Packs items at the end of the container.
  - `Justify.SPACE_BETWEEN`: Distributes spare space evenly between items.
  - `Justify.SPACE_AROUND`: Distributes spare space with half-size outer gaps.
  - `Justify.SPACE_EVENLY`: Distributes equal space between all items and edges.

## Flex Sizing

- **`.grow(factor)`** (`flex-grow`): Specifies how much spare space the child takes along the main axis.
- **`.shrink(factor)`** (`flex-shrink`): Specifies how the child shrinks when total size exceeds container space.
- **`.alignSelf(Align)`** (`align-self`): Overrides cross-axis alignment for an individual child.
