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
 * A block stretched over a box of canvas px, showing its south (front) face: a textured background, a tile or
 * a picture of a block. The texture is stretched, not repeated.
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

    /** A block drawn with a real entity seen by everyone; {@code block} is re-read on every refresh. */
    public CanvasBlock(double pixelX, double pixelY, double widthPx, double heightPx, Supplier<BlockData> block) {
        this(null, false, pixelX, pixelY, widthPx, heightPx, block);
    }

    /** A block drawn with a real entity that only {@code viewer} sees. */
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

    /** A block made of packets only, for {@code viewer}: nothing is spawned on the server. */
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

    /** A block model spans 0..1 from its entity, so the entity is the box corner. */
    @Override
    protected Vector3f modelOffset(float width, float height, float thickness) {
        return new Vector3f();
    }

    /**
     * Tilts the block's front face slightly towards the sky (+Y) so {@code Light0_Direction} /
     * {@code Light1_Direction} illuminate it brightly in all cardinal directions, and compensates the Z shift
     * of the block's vertical midpoint so it stays on its canvas layer.
     */
    @Override
    protected float normalPitchTiltRad() {
        return Pixelay.settings().text().fullBrightness() ? (float) Math.toRadians(-35.0) : 0f;
    }

    @Override
    protected Vector3f pitchTiltOffset(float width, float height, float tiltRad) {
        if (tiltRad == 0f) {
            return new Vector3f();
        }
        return new Vector3f(0f, 0f, -0.5f * height * (float) Math.tan(tiltRad));
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
