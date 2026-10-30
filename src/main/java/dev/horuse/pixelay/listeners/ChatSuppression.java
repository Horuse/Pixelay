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
 * Suppresses outgoing chat for players, including standard, system, and disguised chat.
 * This is used for full-screen canvas interfaces that chat can obscure.
 * Packets are stored in a bounded queue and sent to the player during {@link #restore}.
 * The queue stores rendered {@link Component} objects rather than packet buffers.
 */
public class ChatSuppression implements PacketListener {

    private static final Map<UUID, Queue<Component>> QUEUES = new ConcurrentHashMap<>();

    /**
     * Suppresses chat for the player and queues messages until {@link #restore} is called.
     * Does nothing when {@code chat-suppression.enabled} is false.
     */
    public static void suppress(Player player) {
        if (!Pixelay.settings().chatSuppression().enabled()) {
            return;
        }

        QUEUES.put(player.getUniqueId(), new ConcurrentLinkedQueue<>());
    }

    /**
     * Restores chat for the player and delivers queued messages in sequence.
     * Discards messages if the queue exceeds {@code chat-suppression.deliver-limit}.
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

        // Materialize the component while the packet buffer is valid.
        Component message = unwrap(event);
        queue.add(message);
        // Remove oldest messages when queue limit is exceeded.
        int limit = Pixelay.settings().chatSuppression().queueLimit();

        while (queue.size() > limit) {
            queue.poll();
        }

        event.setCancelled(true);
    }

    /** Extracts the message component from a chat packet. */
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
