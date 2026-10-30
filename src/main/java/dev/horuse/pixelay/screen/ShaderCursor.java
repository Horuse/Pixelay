package dev.horuse.pixelay.screen;

import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTimeUpdate;
import dev.horuse.pixelay.pack.PixelayPack;
import dev.horuse.pixelay.utils.PacketUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.SoundCategory;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

/**
 * Renders the cursor of a {@link ScreenSession} with the Pixelay core shader ({@code pixelay_cursor.glsl}).
 * The client renders the cursor every frame matching the player look direction.
 * <p>
 * Operational details:
 * <ul>
 *   <li>The client renders a water overlay while player eyes are in water, following player yaw and pitch.
 *       The server places packet-only water blocks at eye height above the camera.</li>
 *   <li>The client disables this overlay for spectators. The server sends tab list updates setting mode to ADVENTURE.</li>
 *   <li>Active sprite and canvas field of view encode as negative world age ticks.</li>
 * </ul>
 * Look direction begins at yaw 0 and pitch 0. Server teleports preserve player rotation during the session.
 */
final class ShaderCursor {

    /** Sprite index representing hidden state. */
    static final int HIDDEN_SPRITE = 7;
    /** Tick delay before teleports stop modifying player rotation. */
    private static final int KEEP_LOOK_AFTER_TICKS = 2;
    private static final int WATER_RESEND_TICKS = 40;

    private static final Map<UUID, ShaderCursor> ACTIVE = new ConcurrentHashMap<>();

    private final Player player;
    private final List<Location> water = new ArrayList<>();
    private volatile int state;
    private volatile boolean keepsLook;
    private int ticks;

    ShaderCursor(Player player) {
        this.player = player;
    }

    /** Returns active shader cursor for player if present. */
    static ShaderCursor of(Player player) {
        return player != null ? ACTIVE.get(player.getUniqueId()) : null;
    }

    /** Checks if shader cursor is available with current resource pack. */
    static boolean available() {
        return PixelayPack.current() != null;
    }

    /**
     * Starts packet modifications and updates tab list game mode to adventure.
     */
    void start() {
        ACTIVE.put(player.getUniqueId(), this);
        sendTabListMode(GameMode.ADVENTURE);
    }

    /**
     * Vertical bounds for client-side water blocks relative to seat position.
     */
    private static final double WATER_FROM = 0.4;

    private static final double WATER_TO = 2.4;

    /**
     * Sends client-side water blocks around player eyes to trigger the underwater overlay.
     */
    void flood(Location seat) {
        clearWater();
        int bottom = (int) Math.floor(seat.getY() + WATER_FROM);
        int top = (int) Math.floor(seat.getY() + WATER_TO);
        for (int y = bottom; y <= top; y++) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    water.add(new Location(seat.getWorld(), seat.getBlockX() + x, y, seat.getBlockZ() + z));
                }
            }
        }
        sendWater();
    }

    /**
     * Displays sprite for given half height tangent.
     *
     * @return Quantized half height tangent used by the shader.
     */
    double show(int sprite, double halfHeightTan) {
        int level = level(halfHeightTan);
        state = 1 + Math.min(sprite, HIDDEN_SPRITE) + 8 * level;
        return halfHeightTan(level);
    }

    /** Hides the cursor until next {@link #show} call. */
    void hide() {
        state = state == 0 ? 0 : 1 + HIDDEN_SPRITE + 8 * ((state - 1) / 8);
    }

    /** Resends state each tick and periodically refreshes water blocks. */
    void tick() {
        ticks++;
        if (ticks == KEEP_LOOK_AFTER_TICKS) {
            keepsLook = true;
        }
        PacketUtils.send(player, new WrapperPlayServerTimeUpdate(worldAge(), player.getPlayerTime(), true));
        if (ticks % WATER_RESEND_TICKS == 0) {
            // Periodic chunk updates can clear client-side water blocks.
            sendWater();
        }
        // Suppress underwater ambient sound effect.
        player.stopSound(SoundCategory.AMBIENT);
    }

    /** Restores real world blocks, world age, and tab list game mode. */
    void stop() {
        ACTIVE.remove(player.getUniqueId(), this);
        if (!player.isOnline()) {
            return;
        }
        clearWater();
        player.stopSound(SoundCategory.AMBIENT);
        PacketUtils.send(
            player,
            new WrapperPlayServerTimeUpdate(player.getWorld().getGameTime(), player.getPlayerTime(), true)
        );
        sendTabListMode(GameMode.valueOf(player.getGameMode().name()));
    }

    /** Sends tab list packet with specified game mode. */
    private void sendTabListMode(GameMode mode) {
        WrapperPlayServerPlayerInfoUpdate.PlayerInfo entry = new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(
            new UserProfile(player.getUniqueId(), player.getName()),
            true,
            player.getPing(),
            mode,
            null,
            null
        );
        PacketUtils.send(
            player,
            new WrapperPlayServerPlayerInfoUpdate(WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_GAME_MODE, entry)
        );
    }

    /**
     * Computes encoded negative world age for current cursor state.
     */
    long worldAge() {
        int step = PixelayPack.STATE_STEP;
        return state == 0 ? 0 : -((long) state * step + step / 2 - 2);
    }

    /** Returns true if teleports must preserve current look angles. */
    boolean keepsLook() {
        return keepsLook;
    }

    private void sendWater() {
        BlockData data = Material.WATER.createBlockData();
        for (Location block : water) {
            player.sendBlockChange(block, data);
        }
    }

    private void clearWater() {
        for (Location block : water) {
            player.sendBlockChange(block, block.getBlock().getBlockData());
        }
        water.clear();
    }

    /** Returns state level for half height tangent on logarithmic scale. */
    static int level(double halfHeightTan) {
        double t = Math.log(halfHeightTan / PixelayPack.TAN_MIN) / Math.log(PixelayPack.TAN_MAX / PixelayPack.TAN_MIN);
        return (int) Math.max(0, Math.min(PixelayPack.LEVELS - 1, Math.round(t * (PixelayPack.LEVELS - 1))));
    }

    static double halfHeightTan(int level) {
        double t = level / (double) (PixelayPack.LEVELS - 1);
        return PixelayPack.TAN_MIN * Math.pow(PixelayPack.TAN_MAX / PixelayPack.TAN_MIN, t);
    }
}
