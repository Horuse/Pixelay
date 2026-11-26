# Examples and Guides

This section provides code examples for building HUD elements with the Pixelay Screen HUD API.

## 1. Dynamic Status Widget with `LayoutHudElement`

Compose multi-line or flex elements with the Pixelay Layout engine and attach them to screen anchors:

```java
import dev.horuse.pixelay.hud.HudAnchor;
import dev.horuse.pixelay.hud.LayoutHudElement;
import dev.horuse.pixelay.layout.Layout;
import dev.horuse.pixelay.layout.LayoutItem;
import org.bukkit.entity.Player;

public class PlayerStatusWidget {

    public static LayoutHudElement createWidget() {
        return new LayoutHudElement(HudAnchor.TOP_LEFT, 10, 14, (Player player) -> {
            // Row 1: Player name and level
            LayoutItem title = Layout.text("<gold><bold>" + player.getName() + "</bold></gold> <gray>Lv." + player.getLevel() + "</gray>");

            // Row 2: Status badges
            LayoutItem status = Layout.row(
                Layout.text("<aqua>⚡ " + (int) player.getHealth() + " HP</aqua>"),
                Layout.text("<green>Ping: " + player.getPing() + "ms</green>")
            ).gap(6);

            // Container: Vertical column with 3px gap
            return Layout.column(title, status).gap(3);
        });
    }
}
```

## 2. Absolute Screen Coordinate Overlay

Position an Adventure text component at exact $(X, Y)$ screen coordinates with `HudPosition.at`:

```java
import dev.horuse.pixelay.hud.HudPosition;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public class CoordinateOverlay {

    public static Component renderCoordinates(int blockX, int blockY, int blockZ) {
        Component text = Component.text()
            .append(Component.text("XYZ: ", NamedTextColor.GRAY))
            .append(Component.text(blockX + " " + blockY + " " + blockZ, NamedTextColor.WHITE, TextDecoration.BOLD))
            .build();

        // Places the coordinate label 12 pixels right and 20 pixels down from the screen origin
        return HudPosition.at(text, 12, 20);
    }
}
```

## 3. Custom Scanned Glyph Display

When `include-scanned-glyphs: true` is active, custom glyphs scanned by `PackFontScanner` work at any vertical offset:

```java
import dev.horuse.pixelay.hud.HudPosition;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class CustomBadgeWidget {

    // Custom glyph scanned from an external pack (e.g., custom_pack:font/icons.json)
    private static final char SHIELD_ICON = '\uE001';
    private static final Key ICON_FONT = Key.key("custom_pack", "icons");

    public static Component renderBadge(int armorValue, int screenX, int screenY) {
        // Icon rendered with its custom font, shifted vertically with HudPosition
        Component icon = Component.text(String.valueOf(SHIELD_ICON))
            .font(HudPosition.font(screenY));

        // Text value rendered at the same vertical offset
        Component label = Component.text(" " + armorValue, NamedTextColor.BLUE)
            .font(HudPosition.font(screenY));

        Component badge = Component.text().append(icon).append(label).build();
        return HudPosition.at(badge, screenX, screenY);
    }
}
```

## 4. Floating Notification Stack

Display temporary stacked notifications on screen:

```java
import dev.horuse.pixelay.hud.HudPosition;
import net.kyori.adventure.text.Component;

public final class NotificationStack {

    private final Component message;
    private final long expireTimeMillis;

    public NotificationStack(Component message, long durationMillis) {
        this.message = message;
        this.expireTimeMillis = System.currentTimeMillis() + durationMillis;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > expireTimeMillis;
    }

    public Component render(int index, int startX, int startY, int spacingY) {
        int y = startY + index * spacingY;
        return HudPosition.at(message, startX, y);
    }
}
```
