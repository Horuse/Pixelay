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
 * An item drawn as in an inventory slot (the {@code GUI} display of its model: flat items flat, blocks as a
 * small isometric cube), filling a square of {@code sizePx}.
 */
public class CanvasItem extends CanvasModel<ItemStack> {

    /** {@code ItemDisplayTransform.GUI} in the item display metadata. */
    private static final byte DISPLAY_GUI = 6;
    /**
     * The client turns an item display's model half a turn around Y before drawing it; turned back here, so
     * the item's front (what an inventory shows) faces out of the canvas.
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

    /** An item drawn with a real entity seen by everyone; {@code item} is re-read on every refresh. */
    public CanvasItem(double pixelX, double pixelY, double sizePx, Supplier<ItemStack> item) {
        this(null, false, pixelX, pixelY, sizePx, item);
    }

    /** An item drawn with a real entity that only {@code viewer} sees. */
    public static CanvasItem perPlayer(
        Player viewer,
        double pixelX,
        double pixelY,
        double sizePx,
        Supplier<ItemStack> item
    ) {
        return new CanvasItem(viewer, false, pixelX, pixelY, sizePx, item);
    }

    /** An item made of packets only, for {@code viewer}: nothing is spawned on the server. */
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

    /** An item model is centred on its entity and fills a 1x1 block square in the GUI display, like a slot. */
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
