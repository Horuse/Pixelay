package dev.horuse.pixelay.listeners;

import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChatMessage;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDisguisedChat;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSystemChatMessage;
import dev.horuse.pixelay.Pixelay;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * Suppresses outgoing chat for a set of players: normal, system and disguised. Needed for
 * full-screen canvas interfaces (menus, selection screens, …), which the chat overlaps. Packets are
 * not discarded, but accumulated in a queue (with a limit, old ones are evicted) and delivered to
 * the player at {@link #restore}. A ready Component is stored, not a packet-wrapper: the original
 * packet buffer is freed immediately after sending, sending the wrapper again throws
 * IllegalReferenceCountException and breaks the client thread (protocol crash).
 */
public class ChatSuppression implements PacketListener {

    private static final Map<UUID, Queue<Component>> QUEUES = new ConcurrentHashMap<>();

    /**
     * Hides chat for the player, queuing messages until {@link #restore} is called. No-op when
     * {@code chat-suppression.enabled} is false.
     */
    public static void suppress(Player player) {
        if (!Pixelay.settings().chatSuppression().enabled()) {
            return;
        }

        QUEUES.put(player.getUniqueId(), new ConcurrentLinkedQueue<>());
    }

    /**
     * Restores chat to the player: delivers the accumulated queue in the original order, if it is
     * not longer than {@code chat-suppression.deliver-limit} (no one needs a wall of old chat),
     * otherwise just discards it.
     */
    public static void restore(Player player) {
        Queue<Component> queue = QUEUES.remove(player.getUniqueId());

        if (queue == null || !player.isOnline() || queue.size() > Pixelay.settings().chatSuppression().deliverLimit()) {
            return;
        }

        Component message;
        while ((message = queue.poll()) != null) {
            player.sendMessage(message);
        }
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (QUEUES.isEmpty()) {
            return;
        }

        if (
            event.getPacketType() != PacketType.Play.Server.SYSTEM_CHAT_MESSAGE &&
            event.getPacketType() != PacketType.Play.Server.CHAT_MESSAGE &&
            event.getPacketType() != PacketType.Play.Server.DISGUISED_CHAT
        ) {
            return;
        }

        Player player = event.getPlayer();

        if (player == null) {
            return;
        }

        Queue<Component> queue = QUEUES.get(player.getUniqueId());

        if (queue == null) {
            return;
        }

        // The component is materialized right here, while the packet buffer is alive.
        Component message = unwrap(event);
        queue.add(message);
        // On overflow the oldest messages are lost.
        int limit = Pixelay.settings().chatSuppression().queueLimit();

        while (queue.size() > limit) {
            queue.poll();
        }

        event.setCancelled(true);
    }

    /** Extracts the ready message component from a chat packet. */
    private static Component unwrap(PacketSendEvent event) {
        PacketTypeCommon type = event.getPacketType();

        if (type == PacketType.Play.Server.SYSTEM_CHAT_MESSAGE) {
            return new WrapperPlayServerSystemChatMessage(event).getMessage();
        }

        if (type == PacketType.Play.Server.CHAT_MESSAGE) {
            return new WrapperPlayServerChatMessage(event).getMessage().getChatContent();
        }

        return new WrapperPlayServerDisguisedChat(event).getMessage();
    }
}
