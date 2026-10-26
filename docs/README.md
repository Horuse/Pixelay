# Pixelay Documentation

**Pixelay** is an HTML/CSS-inspired UI layout, rendering, and interactivity engine for Minecraft servers (Paper 1.21+ & PacketEvents). It enables developers and future visual editors to build responsive, multi-layered 2D/3D interfaces rendered either in the 3D world or as full-screen interactive sessions (`ScreenSession`) with hardware-smooth cursor tracking.

---

## Documentation Index

1. [**Architecture & Rendering Pipeline**](./architecture.md)
   - How Pixelay projects 2D pixel coordinate spaces onto 3D Minecraft planes
   - Packet-based fake entities vs. server entities
   - Automatic Porter-Duff alpha compositing (solving Minecraft's translucent `TextDisplay` depth-occlusion bug)
   - `ScreenSession` & Core Shader Cursor (`rendertype_text`)

2. [**Layout Engine (HTML/CSS Box Model, Flexbox, Grid, Positioning)**](./layout-engine.md)
   - Two-pass layout engine (`measure` bottom-up, `arrange` top-down)
   - Box Model (`width`, `height`, `padding`, `margin`, `overflow`, `clip`)
   - CSS Flexbox (`row`, `column`, `gap`, `grow`, `shrink`, `align`, `justify` including `SPACE_BETWEEN`, `SPACE_AROUND`, `SPACE_EVENLY`)
   - CSS Grid (`grid`, `px`, `fr`, `auto`, `repeat`, `rowSpan`, `columnSpan`, sparse auto-placement)
   - CSS Positioning (`Position.STATIC`, `Position.RELATIVE`, `Position.ABSOLUTE`, `left`, `top`, `right`, `bottom`, `inset`, `zIndex`)
   - `Stack` & `ScrollView` (smooth inertial scrolling, scrollbar track/thumb dragging)

3. [**UI Components & State Styling**](./components.md)
   - Structural elements: `Box` (`box()` / `div()`), `Leaf` (`text()`), `Spacer` (`spacer()` / `space()`)
   - Interactive controls: `Button` (`button()`), `Checkbox` (`checkbox()`), `Toggle` (`toggle()`), `ProgressBar` (`progressBar()`)
   - 2D Drawing & Pixel Art: `Canvas` (`canvas(viewer, gridW, gridH)`) — programmatic 2D drawing API + interactive player brush painting with 2D greedy meshing
   - 3D Models: `ModelLeaf` (`item()` and `block()`)
   - Pseudo-states (`:hover`, `:disabled`, `:selected`, custom states) & CSS `Transition` / `Easing`

4. [**Web Editor & DOM Serialization Specification**](./web-editor-spec.md)
   - SOLID introspection & live mutation API across all `Box` and `Container` subclasses
   - Canonical JSON Document Schema (`tagName`, box model, layout props, state styles, component props)
   - Bidirectional mapping between the future Web Editor and `MountedLayout`

---

## Quick Start

```java
import static dev.horuse.pixelay.layout.Layout.*;

import dev.horuse.pixelay.layout.Align;
import dev.horuse.pixelay.layout.Canvas;
import dev.horuse.pixelay.layout.Easing;
import dev.horuse.pixelay.layout.Justify;
import dev.horuse.pixelay.layout.MountedLayout;
import dev.horuse.pixelay.layout.Position;
import org.bukkit.entity.Player;

public final class QuickExample {

    public static MountedLayout createHud(Player viewer) {
        Canvas miniPad = canvas(viewer, 32, 16)
            .size(320, 160)
            .background(0xFF1E293B)
            .drawable(true)
            .brushColor(0xFF38BDF8);

        return mount(
            viewer,
            column(
                row(
                    text(viewer, 28, "<gradient:#38BDF8:#818CF8><b>PIXELAY STUDIO</b></gradient>"),
                    spacer(),
                    button(viewer, 20, "<white><b>Close ✕</b>", p -> p.closeInventory())
                        .background(0xFFE11D48)
                        .hover(s -> s.background(0xFFBE123C))
                        .transition(150, Easing.EASE_OUT)
                ).align(Align.CENTER),
                space(16),
                toggle(viewer, "Enable Live Brush Preview", true, (p, on) -> miniPad.drawable(on)),
                space(12),
                miniPad,
                // Floating badge pinned to the bottom-right corner via CSS position: absolute
                text(viewer, 14, "<#94A3B8>v1.0 • CSS Absolute")
                    .position(Position.ABSOLUTE)
                    .right(24)
                    .bottom(16)
            )
                .padding(24)
                .background(0xFF0F172A)
        );
    }
}
```
