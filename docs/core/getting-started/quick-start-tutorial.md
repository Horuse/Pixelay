# Quick Start Tutorial

This tutorial shows how to construct a user interface and mount it on a canvas.

## 1. Create a Canvas Plane

A `Canvas` defines a 2D pixel area mapped onto a 3D plane in the Minecraft world.

```java
import dev.horuse.pixelay.data.Canvas;
import dev.horuse.pixelay.data.CanvasResolution;
import org.bukkit.Location;

// Define a 1920x1080 canvas plane with 4.0 blocks width and 2.25 blocks height
CanvasResolution resolution = new CanvasResolution(1920, 1080, 4.0, 2.25);
Location center = player.getLocation().add(0, 1.5, 3);
float yaw = 0.0f; // Canvas normal points toward North

Canvas canvas = new Canvas(center, yaw, resolution);
```

## 2. Define a Layout with CSS Box DSL

Use static factory methods from `dev.horuse.pixelay.layout.Layout` to construct layout trees:

```java
import static dev.horuse.pixelay.layout.Layout.*;
import dev.horuse.pixelay.layout.*;
import org.bukkit.entity.Player;

public static Box<?> createWelcomeBox(Player viewer) {
    return column(
        text(viewer, 28, "<gradient:#38BDF8:#818CF8><b>Welcome to the Server</b></gradient>"),
        spacer(16),
        text(viewer, 18, "<white>Click the button below to join the game."),
        spacer(24),
        button(viewer, 22, "<white><b>[ Start Game ]</b>", player -> {
            player.sendMessage("Game started!");
        })
            .padding(12, 24)
            .background(0xFF2563EB)
            .state(State.HOVERED, s -> s.background(0xFF1D4ED8))
            .transition(150, Easing.EASE_OUT)
    )
        .padding(32)
        .background(0xF00F172A)
        .border(2, 0xFF38BDF8)
        .align(Align.CENTER);
}
```

## 3. Mount and Spawn the Interface

Call `mount()` to create a `MountedLayout`, then call `spawn(canvas)`:

```java
MountedLayout mounted = mount(createWelcomeBox(player));
mounted.spawn(canvas);
```

To remove the interface from the world, call:

```java
mounted.despawn();
```
