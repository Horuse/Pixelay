# 3D Item & Block Displays

Pixelay embeds 3D Minecraft items and blocks into 2D UI layouts using `ItemDisplay` and `BlockDisplay` entities.

## Item Display (`Item`)

Renders a Bukkit `ItemStack` inside the layout:

```java
import static dev.horuse.pixelay.layout.Layout.*;
import dev.horuse.pixelay.layout.Item;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

Item swordDisplay = item(new ItemStack(Material.DIAMOND_SWORD))
    .size(64, 64)
    .scale(1.2)
    .rotation(0, 45, 0); // Yaw, pitch, roll
```

## Block Display (`Block`)

Renders a Bukkit `BlockData` state inside the layout:

```java
import dev.horuse.pixelay.layout.Block;
import org.bukkit.Material;

Block chestDisplay = block(Material.CHEST.createBlockData())
    .size(80, 80)
    .rotation(15, 30, 0);
```

## Alignment with 2D Elements

Pixelay measures the 3D entity bounds and reserves exact rectangular space in parent rows, columns, or grids.
Hover effects and state styles can animate 3D model transforms seamlessly.
