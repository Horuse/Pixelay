package dev.horuse.pixelay.screen;

import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.teleport.RelativeFlag;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerPositionAndLook;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTimeUpdate;
import org.bukkit.entity.Player;

/**
 * Keeps the packets of a player with a {@link ShaderCursor} in line with it: every world age they get is the
 * cursor state, their own tab list entry says adventure instead of spectator (the client draws the water
 * overlay only then), and teleports leave their look alone.
 */
public class ShaderCursorListener implements PacketListener {

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        ShaderCursor cursor = ShaderCursor.of(player);
        if (cursor == null) {
            return;
        }
        if (event.getPacketType() == PacketType.Play.Server.TIME_UPDATE) {
            WrapperPlayServerTimeUpdate packet = new WrapperPlayServerTimeUpdate(event);
            if (packet.getWorldAge() != cursor.worldAge()) {
                packet.setWorldAge(cursor.worldAge());
                event.markForReEncode(true);
            }
        } else if (event.getPacketType() == PacketType.Play.Server.PLAYER_INFO_UPDATE) {
            WrapperPlayServerPlayerInfoUpdate packet = new WrapperPlayServerPlayerInfoUpdate(event);
            for (WrapperPlayServerPlayerInfoUpdate.PlayerInfo entry : packet.getEntries()) {
                if (player.getUniqueId().equals(entry.getProfileId()) && entry.getGameMode() == GameMode.SPECTATOR) {
                    entry.setGameMode(GameMode.ADVENTURE);
                    event.markForReEncode(true);
                }
            }
        } else if (event.getPacketType() == PacketType.Play.Server.PLAYER_POSITION_AND_LOOK && cursor.keepsLook()) {
            WrapperPlayServerPlayerPositionAndLook packet = new WrapperPlayServerPlayerPositionAndLook(event);
            packet.setYaw(0);
            packet.setPitch(0);
            packet.setRelativeFlags(packet.getRelativeFlags().or(RelativeFlag.YAW).or(RelativeFlag.PITCH));
            event.markForReEncode(true);
        }
    }
}
