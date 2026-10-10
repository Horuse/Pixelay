package dev.horuse.pixelay.clicks;

import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import dev.horuse.pixelay.Pixelay;
import dev.horuse.pixelay.components.FakePacketCanvasRenderer;
import dev.horuse.pixelay.data.Canvas;
import dev.horuse.pixelay.data.CanvasResolution;
import dev.horuse.pixelay.data.Layers;
import dev.horuse.pixelay.utils.PacketUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.joml.Vector3f;

/**
 * Invisible {@code Interaction} tiles laid over a canvas plane. They only catch that a player clicked
 * the canvas; where exactly is decided by the player's look ray, so hitboxes match the drawn boxes
 * exactly whatever the canvas rotation (an Interaction is an axis-aligned box with a square base and
 * can not follow a rotated or lying plane itself).
 * <p>
 * Without a viewer the tiles are server entities everyone can click; with one they are packet-only
 * entities of that player.
 */
final class HitArea {

    /** Thickness (blocks) of the tiles of a lying canvas. */
    private static final float FLAT_THICKNESS = 0.05f;
    /** Metadata indexes of Interaction width and height (1.21.11). */
    private static final int META_WIDTH = 8;
    private static final int META_HEIGHT = 9;

    final Canvas canvas;
    final Player viewer;
    final List<ClickTarget> targets = new ArrayList<>();
    private final List<Interaction> entities = new ArrayList<>();
    private final List<Integer> fakeIds = new ArrayList<>();

    HitArea(Canvas canvas, Player viewer) {
        this.canvas = canvas;
        this.viewer = viewer;
    }

    /** One tile: bottom-centre position and size of an Interaction box. */
    private record Tile(double x, double y, double z, float width, float height) {}

    void spawn() {
        if (!canvas.worldClicks()) {
            return;
        }
        for (Tile tile : tiles()) {
            if (viewer == null) {
                Location at = new Location(canvas.center().getWorld(), tile.x(), tile.y(), tile.z());
                Interaction interaction = at.getWorld().spawn(at, Interaction.class);
                interaction.setInteractionWidth(tile.width());
                interaction.setInteractionHeight(tile.height());
                interaction.setPersistent(false);
                interaction.getPersistentDataContainer().set(Pixelay.entityKey(), PersistentDataType.BYTE, (byte) 1);
                entities.add(interaction);
                CanvasClicks.bind(interaction.getEntityId(), this);
            } else {
                int id = FakePacketCanvasRenderer.nextFakeEntityId();
                PacketUtils.send(
                    viewer,
                    new WrapperPlayServerSpawnEntity(
                        id,
                        Optional.of(UUID.randomUUID()),
                        EntityTypes.INTERACTION,
                        new Vector3d(tile.x(), tile.y(), tile.z()),
                        0f,
                        0f,
                        0f,
                        0,
                        Optional.empty()
                    )
                );
                PacketUtils.send(
                    viewer,
                    new WrapperPlayServerEntityMetadata(
                        id,
                        List.of(
                            new EntityData<>(META_WIDTH, EntityDataTypes.FLOAT, tile.width()),
                            new EntityData<>(META_HEIGHT, EntityDataTypes.FLOAT, tile.height())
                        )
                    )
                );
                fakeIds.add(id);
                CanvasClicks.bind(id, this);
            }
        }
    }

    void despawn() {
        for (Interaction interaction : entities) {
            CanvasClicks.unbind(interaction.getEntityId());
            interaction.remove();
        }
        entities.clear();
        if (!fakeIds.isEmpty()) {
            fakeIds.forEach(CanvasClicks::unbind);
            if (viewer.isOnline()) {
                PacketUtils.send(
                    viewer,
                    new WrapperPlayServerDestroyEntities(fakeIds.stream().mapToInt(Integer::intValue).toArray())
                );
            }
            fakeIds.clear();
        }
    }

    boolean isValid() {
        return entities.stream().allMatch(Interaction::isValid);
    }

    /**
     * Covers the plane with as few boxes as its orientation allows: one box per square of a lying
     * canvas aligned with the world axes, a full-height column per step of a standing one, small cubes
     * along a tilted one. Boxes stick out of a standing plane by half a tile at most.
     */
    private List<Tile> tiles() {
        CanvasResolution res = canvas.resolution();
        double width = res.blockWidth();
        double height = res.blockHeight();
        double step = Math.max(0.05, Pixelay.settings().clicks().hitTileSize());
        Vector3f right = canvas.right();
        Vector3f up = canvas.up();
        Vector3f normal = canvas.normal();
        double offset = Layers.text();
        Location c = canvas.pixelToWorld(res.pixelWidth() / 2.0, res.pixelHeight() / 2.0, (float) offset);
        List<Tile> tiles = new ArrayList<>();

        boolean lying = Math.abs(normal.y) > 0.999f;
        boolean standing = Math.abs(normal.y) < 0.001f;
        boolean axisAligned = Math.abs(right.x) > 0.999f || Math.abs(right.z) > 0.999f;

        if (lying && axisAligned) {
            double side = Math.min(width, height);
            for (double[] p : grid(width, height, side, side)) {
                tiles.add(at(c, right, up, p[0], p[1], (float) side, FLAT_THICKNESS, FLAT_THICKNESS / 2));
            }
        } else if (standing) {
            for (double[] p : grid(width, 0, step, height)) {
                tiles.add(at(c, right, up, p[0], 0, (float) step, (float) height, height / 2));
            }
        } else {
            for (double[] p : grid(width, height, step, step)) {
                tiles.add(at(c, right, up, p[0], p[1], (float) step, (float) step, step / 2));
            }
        }
        return tiles;
    }

    /** Centres {@code (u, v)} of cells of size {@code du x dv} covering {@code width x height} around 0. */
    private static List<double[]> grid(double width, double height, double du, double dv) {
        List<double[]> points = new ArrayList<>();
        int columns = Math.max(1, (int) Math.ceil(width / du - 1e-9));
        int rows = height <= 0 ? 1 : Math.max(1, (int) Math.ceil(height / dv - 1e-9));
        for (int i = 0; i < columns; i++) {
            // The last cell is pulled back inside, so boxes do not stick out past the edge.
            double u = Math.min(-width / 2 + du / 2 + i * du, width / 2 - du / 2);
            for (int j = 0; j < rows; j++) {
                double v = height <= 0 ? 0 : Math.min(-height / 2 + dv / 2 + j * dv, height / 2 - dv / 2);
                points.add(new double[] { u, v });
            }
        }
        return points;
    }

    private static Tile at(
        Location c,
        Vector3f right,
        Vector3f up,
        double u,
        double v,
        float width,
        float height,
        double lift
    ) {
        double x = c.getX() + right.x * u + up.x * v;
        double y = c.getY() + right.y * u + up.y * v - lift;
        double z = c.getZ() + right.z * u + up.z * v;
        return new Tile(x, y, z, width, height);
    }
}
