package dev.horuse.pixelay.components;

import dev.horuse.pixelay.Pixelay;
import dev.horuse.pixelay.data.Canvas;
import dev.horuse.pixelay.utils.DisplayUtils;
import org.bukkit.Location;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;

/**
 * Draws a {@link CanvasDrawable} as per-player entities: the backing {@link TextDisplay} and {@link
 * Interaction} are spawned in the world but made invisible by default and shown only to a single
 * {@link Player} viewer through Paper's per-player entity visibility (which sends the
 * spawn/metadata packets to that viewer only). This gives the same look as the server renderer
 * while keeping the canvas fully private to one player — e.g. for a menu screen where concurrent
 * viewers must not see each other's interface.
 */
public class PacketCanvasRenderer implements CanvasRenderer {

    private final Player viewer;
    private final CanvasDrawable drawable;

    private Canvas canvas;
    private TextDisplay display;
    private Interaction interaction;
    private String lastRendered;
    private int lastLayoutVersion;
    private double[] measuredHitbox;

    public PacketCanvasRenderer(Player viewer, CanvasDrawable drawable) {
        this.viewer = viewer;
        this.drawable = drawable;
    }

    @Override
    public void spawn(Canvas canvas) {
        if (isValid()) {
            return;
        }

        this.canvas = canvas;

        String text = drawable.renderText();
        measuredHitbox = CanvasLayout.hitboxSizePx(drawable, text);
        double[] pos = CanvasLayout.displayPosition(drawable, text);
        Location loc = canvas.pixelToWorld(pos[0], pos[1]);
        lastRendered = text;
        lastLayoutVersion = drawable.layoutVersion();
        display = DisplayUtils.spawnText(
            loc,
            CanvasText.parse(text),
            canvas.contentRotation(),
            canvas.scaleForHeightPx(drawable.heightPx()),
            drawable.hAlign().textAlignment()
        );
        display.setVisibleByDefault(false);
        viewer.showEntity(Pixelay.plugin(), display);
        AbstractCanvasComponent.applyTextStyle(display);
        AbstractCanvasComponent.tag(display);

        if (drawable.onClick() != null) {
            spawnInteraction(text);
        }
    }

    private void spawnInteraction(String text) {
        double[] size = CanvasLayout.hitboxSizePx(drawable, text);
        double[] pos = CanvasLayout.boxPosition(drawable, size[0], size[1]);
        Location loc = canvas.pixelToWorld(pos[0], pos[1]);

        interaction = loc.getWorld().spawn(loc, Interaction.class);
        interaction.setInteractionWidth(canvas.blocksForHeightPx(size[0]));
        interaction.setInteractionHeight(canvas.blocksForHeightPx(size[1]));
        interaction.setVisibleByDefault(false);
        viewer.showEntity(Pixelay.plugin(), interaction);
        AbstractCanvasComponent.tag(interaction);

        CanvasButton.registerClickById(interaction.getEntityId(), viewer.getUniqueId(), drawable.onClick());
    }

    @Override
    public void despawn() {
        if (display != null) {
            display.remove();
            display = null;
        }

        if (interaction != null) {
            CanvasButton.unregisterClickById(interaction.getEntityId());
            interaction.remove();
            interaction = null;
        }
    }

    @Override
    public boolean isValid() {
        if (display == null || !display.isValid()) {
            return false;
        }

        return drawable.onClick() == null || (interaction != null && interaction.isValid());
    }

    @Override
    public void refresh() {
        if (display == null || !display.isValid()) {
            return;
        }

        String rendered = drawable.renderText();

        if (rendered.equals(lastRendered) && drawable.layoutVersion() == lastLayoutVersion) {
            return;
        }

        lastRendered = rendered;
        lastLayoutVersion = drawable.layoutVersion();
        // Position first, then text: so the block doesn't blink
        // with the old position under the new text.
        measuredHitbox = CanvasLayout.hitboxSizePx(drawable, rendered);
        double[] pos = CanvasLayout.displayPosition(drawable, rendered);
        display.teleport(canvas.pixelToWorld(pos[0], pos[1]));
        display.text(CanvasText.parse(rendered));
        if (interaction != null) {
            double[] size = measuredHitbox;
            double[] box = CanvasLayout.boxPosition(drawable, size[0], size[1]);
            interaction.teleport(canvas.pixelToWorld(box[0], box[1]));
            interaction.setInteractionWidth(canvas.blocksForHeightPx(size[0]));
            interaction.setInteractionHeight(canvas.blocksForHeightPx(size[1]));
        }
    }

    @Override
    public double[] measuredHitboxPx() {
        return measuredHitbox;
    }
}
