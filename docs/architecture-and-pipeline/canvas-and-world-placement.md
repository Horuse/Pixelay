# Canvas & World Placement

The `Canvas` class defines where your user interface appears in the Minecraft world and how large it renders.

## Creating a Canvas

Construct a `Canvas` with a center location, orientation yaw, and resolution:

```java
import dev.horuse.pixelay.data.Canvas;
import dev.horuse.pixelay.data.CanvasResolution;
import org.bukkit.Location;

// Resolution: 1920x1080 pixels mapped onto 4.0 blocks width and 2.25 blocks height
CanvasResolution resolution = new CanvasResolution(1920, 1080, 4.0, 2.25);

Location center = player.getLocation().add(0, 1.5, 3);
float yaw = 0.0f; // 0 = Facing South, 90 = Facing West, 180 = Facing North, 270 = Facing East

Canvas canvas = new Canvas(center, yaw, resolution);
```

## Useful Canvas Methods

Use these methods to inspect or interact with the canvas plane:

| Method | Return Type | Description |
| :--- | :--- | :--- |
| `canvas.resolution()` | `CanvasResolution` | Returns pixel dimensions and world block size. |
| `canvas.center()` | `Location` | Returns the center location in the world. |
| `canvas.yaw()` | `float` | Returns the rotation angle in degrees. |
| `canvas.pixelToWorld(x, y)` | `Location` | Converts pixel coordinates `(x, y)` to an exact Bukkit world location. |
| `canvas.worldClicks(boolean)` | `void` | Enables or disables click interactions from players standing in the world. |

## Pixel Aspect Ratio Tip

Keep pixel proportions equal to block proportions:

```text
pixelWidth / pixelHeight == blockWidth / blockHeight
```

For example, 1920 / 1080 is 16:9, and 4.0 / 2.25 is 16:9. When these ratios match, squares and text render without distortion.
