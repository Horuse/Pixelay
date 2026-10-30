package dev.horuse.pixelay.components;

import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityType;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import dev.horuse.pixelay.Pixelay;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import java.util.List;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Renders a block model over a box of canvas pixels. Shows the south face without repeating textures.
 */
public class CanvasBlock extends CanvasModel<BlockData> {

    protected CanvasBlock(
        Player viewer,
        boolean fake,
        double pixelX,
        double pixelY,
        double widthPx,
        double heightPx,
        Supplier<BlockData> block
    ) {
        super(viewer, fake, pixelX, pixelY, widthPx, heightPx, block);
    }

    /** Creates a block with a server entity visible to all players. The supplier re-reads {@code block} on refresh. */
    public CanvasBlock(double pixelX, double pixelY, double widthPx, double heightPx, Supplier<BlockData> block) {
        this(null, false, pixelX, pixelY, widthPx, heightPx, block);
    }

    /** Creates a block with a server entity visible only to {@code viewer}. */
    public static CanvasBlock perPlayer(
        Player viewer,
        double pixelX,
        double pixelY,
        double widthPx,
        double heightPx,
        Supplier<BlockData> block
    ) {
        return new CanvasBlock(viewer, false, pixelX, pixelY, widthPx, heightPx, block);
    }

    /** Creates a packet-only block for {@code viewer}. No server entity spawns. */
    public static CanvasBlock fake(
        Player viewer,
        double pixelX,
        double pixelY,
        double widthPx,
        double heightPx,
        Supplier<BlockData> block
    ) {
        return new CanvasBlock(viewer, true, pixelX, pixelY, widthPx, heightPx, block);
    }

    @Override
    protected Class<? extends Display> displayClass() {
        return BlockDisplay.class;
    }

    @Override
    protected EntityType packetType() {
        return EntityTypes.BLOCK_DISPLAY;
    }

    @Override
    protected void applyContent(Display display, BlockData block) {
        ((BlockDisplay) display).setBlock(block);
    }

    @Override
    protected List<EntityData<?>> contentData(BlockData block) {
        int state = SpigotConversionUtil.fromBukkitBlockData(block).getGlobalId();
        return List.of(
            new EntityData<>(Pixelay.settings().packets().metadata().blockState(), EntityDataTypes.BLOCK_STATE, state)
        );
    }

    /** A block model extends from 0 to 1 from its entity origin, which marks the box corner. */
    @Override
    protected Vector3f modelOffset(float width, float height, float thickness) {
        return new Vector3f();
    }

    @Override
    protected Quaternionf modelRotation() {
        return new Quaternionf();
    }

    @Override
    protected BlockData empty() {
        return Material.AIR.createBlockData();
    }
}
