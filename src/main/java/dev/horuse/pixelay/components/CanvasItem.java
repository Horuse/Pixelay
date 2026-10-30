package dev.horuse.pixelay.components;

import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityType;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import dev.horuse.pixelay.Pixelay;
import dev.horuse.pixelay.config.PixelaySettings;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import java.util.List;
import java.util.function.Supplier;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Renders an item in GUI format within a square of {@code sizePx} canvas pixels.
 */
public class CanvasItem extends CanvasModel<ItemStack> {

    /** {@code ItemDisplayTransform.GUI} in the item display metadata. */
    private static final byte DISPLAY_GUI = 6;
    /**
     * Rotation offset around Y. Corrects the client model orientation so the front face points outward.
     */
    private static final Quaternionf FRONT = new Quaternionf().rotateY((float) Math.PI);

    protected CanvasItem(
        Player viewer,
        boolean fake,
        double pixelX,
        double pixelY,
        double sizePx,
        Supplier<ItemStack> item
    ) {
        super(viewer, fake, pixelX, pixelY, sizePx, sizePx, item);
    }

    /** Creates an item with a server entity visible to all players. The supplier re-reads {@code item} on refresh. */
    public CanvasItem(double pixelX, double pixelY, double sizePx, Supplier<ItemStack> item) {
        this(null, false, pixelX, pixelY, sizePx, item);
    }

    /** Creates an item with a server entity visible only to {@code viewer}. */
    public static CanvasItem perPlayer(
        Player viewer,
        double pixelX,
        double pixelY,
        double sizePx,
        Supplier<ItemStack> item
    ) {
        return new CanvasItem(viewer, false, pixelX, pixelY, sizePx, item);
    }

    /** Creates a packet-only item for {@code viewer}. No server entity spawns. */
    public static CanvasItem fake(
        Player viewer,
        double pixelX,
        double pixelY,
        double sizePx,
        Supplier<ItemStack> item
    ) {
        return new CanvasItem(viewer, true, pixelX, pixelY, sizePx, item);
    }

    @Override
    protected Class<? extends Display> displayClass() {
        return ItemDisplay.class;
    }

    @Override
    protected EntityType packetType() {
        return EntityTypes.ITEM_DISPLAY;
    }

    @Override
    protected void applyContent(Display display, ItemStack item) {
        ItemDisplay itemDisplay = (ItemDisplay) display;
        itemDisplay.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GUI);
        itemDisplay.setItemStack(item);
    }

    @Override
    protected List<EntityData<?>> contentData(ItemStack item) {
        PixelaySettings.MetadataIndexes idx = Pixelay.settings().packets().metadata();
        return List.of(
            new EntityData<>(idx.item(), EntityDataTypes.ITEMSTACK, SpigotConversionUtil.fromBukkitItemStack(item)),
            new EntityData<>(idx.itemDisplayTransform(), EntityDataTypes.BYTE, DISPLAY_GUI)
        );
    }

    /** Centers the item model on the entity within a 1x1 block square. */
    @Override
    protected Vector3f modelOffset(float width, float height, float thickness) {
        return new Vector3f(width / 2, height / 2, thickness / 2);
    }

    @Override
    protected Quaternionf modelRotation() {
        return new Quaternionf(FRONT);
    }

    @Override
    protected ItemStack empty() {
        return ItemStack.empty();
    }
}
