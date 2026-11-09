# In-World Wall Displays

Pixelay can mount user interfaces directly onto walls and billboards in the 3D Minecraft world.

## Projecting onto a Block Face

When a player selects a wall surface, Pixelay determines the plane location and normal vector:

```java
Block block = player.getTargetBlockExact(16);
BlockFace face = player.getTargetBlockFace(16);
Vector normal = face.getDirection();

// Center the canvas 0.01 blocks in front of the wall face to avoid block z-fighting
Location canvasCenter = block.getLocation()
    .add(0.5, 1.5, 0.5)
    .add(normal.clone().multiply(0.51));

// Calculate canvas yaw from the face normal vector
float wallYaw = (float) Math.toDegrees(Math.atan2(normal.getX(), normal.getZ()));
canvasCenter.setYaw(wallYaw);

Canvas canvas = new Canvas(canvasCenter, wallYaw, resolution);
mountedLayout.spawn(canvas);
```

## Multi-Player Visibility

In-world wall displays can be seen by any player standing nearby.
Interactive buttons continue to support raycast clicks from observers in the area.
