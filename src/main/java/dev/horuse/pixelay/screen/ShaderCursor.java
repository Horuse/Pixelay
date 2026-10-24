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
 * The cursor of a {@link ScreenSession} drawn by the core shader of Pixelay's pack ({@code pixelay_cursor.glsl}):
 * the client draws it every frame where the player looks, so it moves exactly as smoothly as the camera.
 * <p>
 * How the shader learns what it needs:
 * <ul>
 *   <li>the look: the client draws its water overlay while the player's eyes are in water, with texture
 *       coordinates that follow the player's own yaw and pitch (not the camera's, which stays locked). The
 *       player gets client-side water at eye height, above the camera, out of its view;</li>
 *   <li>the overlay is never drawn for spectators, as the client reads the game mode from the tab list: the
 *       player's own tab list entry says adventure while the session lasts (they stay a spectator);</li>
 *   <li>which sprite and how wide the canvas looks: a state number sent as a negative world age every tick
 *       (the client counts the age on between packets, the shader rounds that away).</li>
 * </ul>
 * The look starts at yaw 0 and pitch 0 (the camera's direction for the shader); teleports the server sends
 * during the session no longer turn the player's look, so it never jumps.
 */
final class ShaderCursor {

    /** The sprite number the shader draws nothing for. */
    static final int HIDDEN_SPRITE = 7;
    /** Ticks after the start before teleports stop turning the look: the session's own teleport must turn it. */
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

    /** The cursor shown to a player, if any (packet listeners, network thread). */
    static ShaderCursor of(Player player) {
        return player != null ? ACTIVE.get(player.getUniqueId()) : null;
    }

    /** Whether this player can see a shader cursor: Pixelay's pack was built (they need it loaded). */
    static boolean available() {
        return PixelayPack.current() != null;
    }

    /**
     * Starts rewriting the player's packets and tells the client its tab list game mode is adventure. Sent here,
     * not only when the game mode changes: a player who goes from one screen straight to the next stays a
     * spectator, and the server sends nothing then.
     */
    void start() {
        ACTIVE.put(player.getUniqueId(), this);
        sendTabListMode(GameMode.ADVENTURE);
    }

    /**
     * Rows of client-side water around the eyes of a player riding a vehicle at {@code seat}, from this much
     * above the seat to this much (block bottoms): a rider sits 0.6 below the seat, its eyes 1.62 above that,
     * with room to spare either way.
     */
    private static final double WATER_FROM = 0.4;

    private static final double WATER_TO = 2.4;

    /**
     * Puts client-side water around the eyes of the player sitting at {@code seat}, so the overlay is drawn: a
     * 3x3 column, so the eyes are in it even on a block edge, and one block more above (water under water is
     * full height, so the eyes are in it at any height).
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
     * Shows sprite {@code sprite} for a canvas whose half height is seen at {@code tan(halfHeight)}.
     *
     * @return the half height tangent the shader works with (quantized): map looks to pixels with it
     */
    double show(int sprite, double halfHeightTan) {
        int level = level(halfHeightTan);
        state = 1 + Math.min(sprite, HIDDEN_SPRITE) + 8 * level;
        return halfHeightTan(level);
    }

    /** Hides the cursor (the overlay draws nothing) until the next {@link #show}. */
    void hide() {
        state = state == 0 ? 0 : 1 + HIDDEN_SPRITE + 8 * ((state - 1) / 8);
    }

    /** Every tick: re-sends the state (the client counts the age on), the water now and then. */
    void tick() {
        ticks++;
        if (ticks == KEEP_LOOK_AFTER_TICKS) {
            keepsLook = true;
        }
        PacketUtils.send(player, new WrapperPlayServerTimeUpdate(worldAge(), player.getPlayerTime(), true));
        if (ticks % WATER_RESEND_TICKS == 0) {
            // A chunk re-sent by the server would wash the client-side water away.
            sendWater();
        }
        // The client plays the underwater ambience for eyes in water: cut it before it is heard.
        player.stopSound(SoundCategory.AMBIENT);
    }

    /** Gives back the real blocks, world age and tab list game mode. */
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

    /** The game mode of the player's own tab list entry, as their client sees it. */
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
     * The world age the client must have, negative (0 = no cursor): near the top of the state's range, as the
     * client counts it down (the age goes up) until the next packet.
     */
    long worldAge() {
        int step = PixelayPack.STATE_STEP;
        return state == 0 ? 0 : -((long) state * step + step / 2 - 2);
    }

    /** Whether teleports must leave the player's look alone. */
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

    /** The state level of a half height tangent (log scale, see {@link PixelayPack#LEVELS}). */
    static int level(double halfHeightTan) {
        double t = Math.log(halfHeightTan / PixelayPack.TAN_MIN) / Math.log(PixelayPack.TAN_MAX / PixelayPack.TAN_MIN);
        return (int) Math.max(0, Math.min(PixelayPack.LEVELS - 1, Math.round(t * (PixelayPack.LEVELS - 1))));
    }

    static double halfHeightTan(int level) {
        double t = level / (double) (PixelayPack.LEVELS - 1);
        return PixelayPack.TAN_MIN * Math.pow(PixelayPack.TAN_MAX / PixelayPack.TAN_MIN, t);
    }
}
