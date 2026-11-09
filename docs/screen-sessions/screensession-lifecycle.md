# ScreenSession Lifecycle

The `ScreenSession` class manages the full lifecycle of a dedicated UI screen for a player.

## Opening a Screen Session

Use `ScreenSession.builder(player)` to configure and open a session:

```java
import dev.horuse.pixelay.screen.ScreenSession;
import org.bukkit.Location;
import org.bukkit.entity.Player;

Location cameraLocation = player.getEyeLocation();

ScreenSession session = ScreenSession.builder(player)
    .camera(cameraLocation)
    .content(canvas -> createMenuBox(player))
    .onClose((sess, reason) -> {
        player.sendMessage("Interface closed.");
    })
    .open();
```

## Session Cleanup & Safety

- **Player Disconnect**: If a player quits the server, Pixelay automatically closes the session and clears all fake entities.
- **Player Damage or Teleport**: Server events can safely close the session via `session.close()`.
- **Spectator State Recovery**: On session close, Pixelay restores the original game mode and camera location of the player.
