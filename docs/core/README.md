# Pixelay Documentation

Pixelay provides a UI layout, rendering, and interaction system for Minecraft Paper 1.21+ servers. It uses concepts from HTML and CSS.
Developers can build responsive, multi-layered 2D and 3D interfaces.
Pixelay renders interfaces in the 3D world or as full-screen interactive sessions (`ScreenSession`) with client-side shader cursor tracking.

***

## Documentation Index

1. [**Getting Started**](getting-started/README.md)
   * Installation, setup, quick start tutorial, and configuration.
2. [**Architecture & Pipeline**](architecture-and-pipeline/README.md)
   * Spatial projection of 2D coordinates onto 3D world planes.
   * Packet-based fake entities and server entities.
   * Depth layering and colors guidelines.
3. [**Layout Engine**](layout-engine/README.md)
   * Two-pass layout lifecycle, box model, flexbox rows and columns.
   * CSS Grid, positioning, stacks, and scroll views.
4. [**UI Components**](components-and-styling/README.md)
   * Typography with MiniMessage and glyph metrics.
   * Buttons, form controls, progress bars, 2D drawing canvas, and 3D models.
5. [**Transitions & Styling**](transitions-and-styling/README.md)
   * Pseudo-states (`:hover`, `:selected`, `:disabled`), CSS transitions, and high-FPS GPU animations.
6. [**Screen Sessions**](screen-sessions/README.md)
   * Full-screen spectator camera, zero-latency shader cursors, and screen transitions.
7. [**Examples & Guides**](examples-and-guides/README.md)
   * Complete real-world code examples: menus, item shops, leaderboards, wall displays, and debug tools.
8. [**Web Editor Specification**](web-editor-specification/README.md)
   * DOM introspection API, container mutations, and canonical JSON document schema.

***

## Quick Code Example

```java
import static dev.horuse.pixelay.layout.Layout.*;
import dev.horuse.pixelay.data.Canvas;
import dev.horuse.pixelay.data.CanvasResolution;
import dev.horuse.pixelay.layout.*;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public final class QuickExample {

    public static void open(Player player) {
        Location center = player.getLocation().add(0, 1.5, 3);
        Canvas canvas = new Canvas(center, 0f, new CanvasResolution(1920, 1080, 4.0, 2.25));

        MountedLayout ui = mount(
            column(
                text(player, 28, "<gradient:#38BDF8:#818CF8><b>PIXELAY UI</b></gradient>"),
                spacer(16),
                button(player, 20, "<white><b>Click Me</b>", p -> p.sendMessage("Clicked!"))
                    .padding(10, 20)
                    .background(0xFF2563EB)
                    .state(State.HOVERED, s -> s.background(0xFF1D4ED8))
                    .transition(150, Easing.EASE_OUT)
            )
                .padding(24)
                .background(0xFF0F172A)
                .align(Align.CENTER)
        );

        ui.spawn(canvas);
    }
}
```
