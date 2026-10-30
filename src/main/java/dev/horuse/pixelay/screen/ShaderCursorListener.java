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
 * Synchronizes outgoing packets for players using a {@link ShaderCursor}.
 * Replaces world age with cursor state, masks spectator mode as adventure in tab lists, and preserves view rotation across teleports.
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
