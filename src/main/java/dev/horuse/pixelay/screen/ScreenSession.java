package dev.horuse.pixelay.screen;

import dev.horuse.pixelay.CanvasSurface;
import dev.horuse.pixelay.Pixelay;
import dev.horuse.pixelay.clicks.CanvasClickCatcher;
import dev.horuse.pixelay.components.CanvasCursor;
import dev.horuse.pixelay.data.Canvas;
import dev.horuse.pixelay.data.CanvasResolution;
import dev.horuse.pixelay.data.Layers;
import dev.horuse.pixelay.layout.MountedLayout;
import dev.horuse.pixelay.listeners.ChatSuppression;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/**
 * A full-screen UI for one player: the player is turned into a spectator locked onto an invisible camera
 * in front of a canvas that fills their view, a cursor follows their look and clicks go to the layout
 * under it. Everything the player saw before (game mode, position, other players, chat) is restored on
 * {@link #close}, on quit and when Pixelay is disabled.
 *
 * <pre>{@code
 * ScreenSession.builder(player)
 *     .camera(viewLocation)                 // where the camera stands and which way it looks
 *     .content(canvas -> buildLayout())     // the UI, built for the player's canvas
 *     .calibration(CalibrationScreen.options().build())
 *     .onClose((session, reason) -> ...)
 *     .open();
 * }</pre>
 *
 * The canvas is sized so that it fills a {@code verticalFov} view at scale 1; players with another field
 * of view fix it once on the calibration screen, and the scale is kept by a {@link ScreenScaleStore}.
 * All methods must be called on the main thread.
 */
public final class ScreenSession {

    /** Why a session ended. */
    public enum CloseReason {
        /** {@link #close()} was called. */
        CLOSED,
        /** The player left the server. */
        QUIT,
        /** Pixelay (or the server) is shutting down. */
        SHUTDOWN,
    }

    private static final Map<UUID, ScreenSession> SESSIONS = new ConcurrentHashMap<>();
    private static final int LOAD_RADIUS_CHUNKS = 1;

    private final Player player;
    private final Options options;
    private final Location view;
    private final Location canvasCenter;
    private final float facingYaw;
    private final GameMode previousMode;
    private final Location previousLocation;
    private final Set<Long> forcedChunks = new HashSet<>();
    private final List<Player> hiddenFrom = new ArrayList<>();

    private ScreenCamera camera;
    private Canvas canvas;
    private MountedLayout content;
    private MountedLayout calibrationLayout;
    private CalibrationScreen calibration;
    private boolean calibrating;
    private CanvasCursor cursor;
    private CanvasClickCatcher clickCatcher;
    private BukkitTask task;
    private double scale;
    private volatile double cursorX;
    private volatile double cursorY;
    private String hovered;
    private boolean closed;

    private ScreenSession(Player player, Options options) {
        this.player = player;
        this.options = options;
        this.view = options.camera().clone();
        this.facingYaw = view.getYaw();
        this.view.setPitch(0);
        Vector forward = new Vector(-Math.sin(Math.toRadians(facingYaw)), 0, Math.cos(Math.toRadians(facingYaw)));
        this.canvasCenter = view.clone().add(forward.multiply(options.distance()));
        this.previousMode = player.getGameMode();
        this.previousLocation = player.getLocation().clone();
    }

    public static Builder builder(Player player) {
        return new Builder(player);
    }

    /** The open session of a player, if any. */
    public static Optional<ScreenSession> of(Player player) {
        return Optional.ofNullable(SESSIONS.get(player.getUniqueId()));
    }

    public static boolean isInSession(Player player) {
        return player != null && SESSIONS.containsKey(player.getUniqueId());
    }

    /** Whether {@code entity} is the camera a player in a session rides (it must never be dismounted). */
    public static boolean isCamera(Player player, Entity entity) {
        ScreenSession session = SESSIONS.get(player.getUniqueId());
        return session != null && session.camera != null && session.camera.is(entity);
    }

    public Player player() {
        return player;
    }

    public Canvas canvas() {
        return canvas;
    }

    public MountedLayout content() {
        return content;
    }

    /** The current screen scale (1 = the canvas fills a {@code verticalFov} view). */
    public double scale() {
        return scale;
    }

    /** The canvas pixel under the cursor, {@code {x, y}}. */
    public double[] cursor() {
        return new double[] { cursorX, cursorY };
    }

    /** The id of the deepest box under the cursor (see {@code Box.id}), or {@code null}. */
    public String hovered() {
        return hovered;
    }

    public boolean isCalibrating() {
        return calibrating;
    }

    // --- opening -----------------------------------------------------------------------------------------

    private void open() {
        ScreenSession previous = SESSIONS.put(player.getUniqueId(), this);
        if (previous != null) {
            previous.close(CloseReason.CLOSED);
            SESSIONS.put(player.getUniqueId(), this);
        }
        World world = view.getWorld();
        forceLoad(world);
        player.teleport(view);
        player.setGameMode(GameMode.SPECTATOR);
        if (options.hideOthers()) {
            hideOthers();
        }
        if (options.suppressChat()) {
            ChatSuppression.suppress(player);
        }

        camera = new ScreenCamera(player, view);
        camera.spawn();
        Double saved = options.scaleStore().load(player.getUniqueId());
        scale = clampScale(saved != null ? saved : 1.0);
        canvas = new Canvas(canvasCenter, facingYaw + 180, resolution(scale));
        content = options.content().apply(canvas);
        if (options.calibration() != null) {
            calibration = new CalibrationScreen(this, options.calibration());
        }
        calibrating = calibration != null && saved == null;
        currentLayout().spawn(canvas);
        Pixelay.manager().register(surface);

        cursor =
            options.cursorGlyph() != null
                ? new CanvasCursor(
                      player,
                      options.cursorGlyph(),
                      options.cursorHeightPx(),
                      options.hotspotX(),
                      options.hotspotY()
                  )
                : new CanvasCursor(player);
        cursorX = options.pixelWidth() / 2;
        cursorY = options.pixelHeight() / 2;
        cursor.spawn(canvas);

        // Spectator clicks arrive as packets on the network thread; the layout is used on the main thread.
        clickCatcher = new CanvasClickCatcher(
            player,
            clicker -> Bukkit.getScheduler().runTask(Pixelay.plugin(), () -> click(clicker)),
            camera::reset
        );
        clickCatcher.spawn(canvas);

        task = Bukkit.getScheduler().runTaskTimer(Pixelay.plugin(), this::tick, 1, 1);
        if (options.onOpen() != null) {
            options.onOpen().accept(this);
        }
    }

    // --- running -----------------------------------------------------------------------------------------

    private void tick() {
        if (!player.isOnline()) {
            close(CloseReason.QUIT);
            return;
        }
        camera.tick();
        updateHover();
    }

    /** Called with the camera look from the client's movement packets (network thread). */
    void look(float yaw, float pitch) {
        if (closed || canvas == null) {
            return;
        }
        double[] pixel = lookPixel(yaw, pitch);
        if (pixel == null) {
            return;
        }
        cursorX = pixel[0];
        cursorY = pixel[1];
        Bukkit.getScheduler().runTask(Pixelay.plugin(), () -> {
            if (!closed && cursor != null) {
                cursor.move(canvas, cursorX, cursorY);
            }
        });
    }

    /** Where the camera ray (from the camera eye, with this look) hits the canvas, clamped to its edges. */
    private double[] lookPixel(float yaw, float pitch) {
        double y = Math.toRadians(yaw);
        double p = Math.toRadians(pitch);
        Vector direction = new Vector(-Math.sin(y) * Math.cos(p), -Math.sin(p), Math.cos(y) * Math.cos(p));
        double[] hit = canvas.rayHit(view.toVector(), direction);
        if (hit == null) {
            return null;
        }
        CanvasResolution res = canvas.resolution();
        return new double[] {
            Math.max(0, Math.min(res.pixelWidth(), hit[0])),
            Math.max(0, Math.min(res.pixelHeight(), hit[1])),
        };
    }

    private void updateHover() {
        MountedLayout layout = currentLayout();
        // Hover states and styles; the layout refreshes itself when they change.
        layout.pointerAt(cursorX, cursorY, player);
        String id = layout.idAt(cursorX, cursorY);
        if (!Objects.equals(id, hovered)) {
            hovered = id;
            if (options.onHover() != null && !calibrating) {
                options.onHover().accept(this, id);
                // The callback may have changed texts.
                layout.refresh();
            }
        }
    }

    private void click(Player clicker) {
        if (!closed && clicker.isOnline()) {
            currentLayout().click(cursorX, cursorY, clicker);
        }
    }

    private MountedLayout currentLayout() {
        return calibrating ? calibrationLayout() : content;
    }

    private MountedLayout calibrationLayout() {
        if (calibrationLayout == null) {
            calibrationLayout = calibration.build();
        }
        return calibrationLayout;
    }

    /** Re-runs the layout of what is shown (texts changed); the scheduler also does it every refresh period. */
    public void refresh() {
        if (!closed) {
            currentLayout().refresh();
        }
    }

    /** Replaces the UI with another layout built for this session's canvas. */
    public void setContent(MountedLayout layout) {
        if (!calibrating) {
            content.pointerLeft(player);
            content.despawn();
        }
        content = layout;
        if (!calibrating) {
            content.spawn(canvas);
        }
    }

    // --- calibration -------------------------------------------------------------------------------------

    /** Shows the calibration screen (if the session has one). */
    public void calibrate() {
        if (calibration == null || calibrating) {
            return;
        }
        content.pointerLeft(player);
        content.despawn();
        calibrating = true;
        calibrationLayout().spawn(canvas);
    }

    /** Leaves the calibration screen, saving the scale. */
    public void finishCalibration() {
        if (!calibrating) {
            return;
        }
        options.scaleStore().save(player.getUniqueId(), scale);
        if (options.onScaleChanged() != null) {
            options.onScaleChanged().accept(this, scale);
        }
        calibrationLayout.pointerLeft(player);
        calibrationLayout.despawn();
        calibrating = false;
        content.spawn(canvas);
    }

    /** Changes the screen scale by {@code steps} calibration steps (negative = smaller). */
    public void stepScale(int steps) {
        setScale(scale + steps * options.calibration().step());
    }

    /** Resizes the canvas; everything shown is re-laid out on the new size. */
    public void setScale(double value) {
        double next = clampScale(Math.round(value * 1000) / 1000.0);
        if (next == scale) {
            return;
        }
        scale = next;
        MountedLayout shown = currentLayout();
        shown.despawn();
        cursor.despawn();
        canvas = new Canvas(canvasCenter, facingYaw + 180, resolution(scale));
        shown.spawn(canvas);
        cursor.spawn(canvas);
    }

    private double clampScale(double value) {
        return Math.max(options.minScale(), Math.min(options.maxScale(), value));
    }

    /**
     * Canvas size for a scale: at scale 1 it exactly fills a {@code verticalFov} view of the camera at the
     * text layer, so the screen edges are the canvas edges.
     */
    private CanvasResolution resolution(double value) {
        double toText = options.distance() - Layers.text();
        double height = 2 * toText * Math.tan(Math.toRadians(options.verticalFov() / 2)) * value;
        double width = (height * options.pixelWidth()) / options.pixelHeight();
        return new CanvasResolution(options.pixelWidth(), options.pixelHeight(), width, height);
    }

    // --- closing -----------------------------------------------------------------------------------------

    public void close() {
        close(CloseReason.CLOSED);
    }

    void close(CloseReason reason) {
        if (closed) {
            return;
        }
        closed = true;
        SESSIONS.remove(player.getUniqueId(), this);
        if (task != null) {
            task.cancel();
        }
        Pixelay.manager().unregister(surface);
        currentLayout().pointerLeft(player);
        currentLayout().despawn();
        if (cursor != null) {
            cursor.despawn();
        }
        if (clickCatcher != null) {
            clickCatcher.despawn();
        }
        if (options.suppressChat()) {
            ChatSuppression.restore(player);
        }
        camera.remove();
        showOthers();
        releaseChunks();
        if (player.isOnline() && options.restore()) {
            player.setGameMode(previousMode);
            player.teleport(previousLocation);
        }
        if (options.onClose() != null) {
            options.onClose().accept(this, reason);
        }
    }

    /** Closes every session (Pixelay disable). */
    public static void closeAll() {
        for (ScreenSession session : new ArrayList<>(SESSIONS.values())) {
            session.close(CloseReason.SHUTDOWN);
        }
    }

    static void onQuit(Player player) {
        ScreenSession session = SESSIONS.get(player.getUniqueId());
        if (session != null) {
            session.close(CloseReason.QUIT);
        }
    }

    /** A player who joins while others are in sessions must not see them, and they must not see him. */
    static void onJoin(Player joined) {
        for (ScreenSession session : SESSIONS.values()) {
            if (session.options.hideOthers() && session.player != joined) {
                session.hide(joined);
            }
        }
    }

    // --- surroundings ------------------------------------------------------------------------------------

    private void hideOthers() {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other != player) {
                hide(other);
            }
        }
    }

    private void hide(Player other) {
        player.hidePlayer(Pixelay.plugin(), other);
        other.hidePlayer(Pixelay.plugin(), player);
        hiddenFrom.add(other);
    }

    private void showOthers() {
        for (Player other : hiddenFrom) {
            if (other.isOnline()) {
                player.showPlayer(Pixelay.plugin(), other);
                other.showPlayer(Pixelay.plugin(), player);
            }
        }
        hiddenFrom.clear();
    }

    /** Keeps the screen's chunks loaded: an unloaded camera entity would drop the player's view. */
    private void forceLoad(World world) {
        int cx = canvasCenter.getBlockX() >> 4;
        int cz = canvasCenter.getBlockZ() >> 4;
        int vx = view.getBlockX() >> 4;
        int vz = view.getBlockZ() >> 4;
        for (int x = Math.min(cx, vx) - LOAD_RADIUS_CHUNKS; x <= Math.max(cx, vx) + LOAD_RADIUS_CHUNKS; x++) {
            for (int z = Math.min(cz, vz) - LOAD_RADIUS_CHUNKS; z <= Math.max(cz, vz) + LOAD_RADIUS_CHUNKS; z++) {
                if (!world.isChunkForceLoaded(x, z)) {
                    world.setChunkForceLoaded(x, z, true);
                    forcedChunks.add(((long) x << 32) | (z & 0xFFFFFFFFL));
                }
            }
        }
    }

    private void releaseChunks() {
        World world = view.getWorld();
        for (long key : forcedChunks) {
            world.setChunkForceLoaded((int) (key >> 32), (int) key, false);
        }
        forcedChunks.clear();
    }

    /** Lets the scheduler refresh texts and respawn what died. */
    private final CanvasSurface surface = new CanvasSurface() {
        @Override
        public void refresh() {
            ScreenSession.this.refresh();
        }

        @Override
        public boolean isValid() {
            return closed || currentLayout().isValid();
        }

        @Override
        public void respawn() {
            if (!closed) {
                MountedLayout shown = currentLayout();
                shown.despawn();
                shown.spawn(canvas);
            }
        }
    };

    /** The session options, frozen at open(): changing the builder afterwards can not affect a session. */
    private record Options(
        Location camera,
        double distance,
        double pixelWidth,
        double pixelHeight,
        double verticalFov,
        Function<Canvas, MountedLayout> content,
        String cursorGlyph,
        double cursorHeightPx,
        double hotspotX,
        double hotspotY,
        CalibrationScreen.Options calibration,
        ScreenScaleStore scaleStore,
        double minScale,
        double maxScale,
        boolean hideOthers,
        boolean suppressChat,
        boolean restore,
        Consumer<ScreenSession> onOpen,
        BiConsumer<ScreenSession, CloseReason> onClose,
        BiConsumer<ScreenSession, String> onHover,
        BiConsumer<ScreenSession, Double> onScaleChanged
    ) {}

    // --- options -----------------------------------------------------------------------------------------

    /** Options of a session; {@link #open()} starts it. */
    public static final class Builder {

        private final Player player;
        private Location camera;
        private double distance = 3;
        private double pixelWidth = 1920;
        private double pixelHeight = 1080;
        private double verticalFov = 70;
        private Function<Canvas, MountedLayout> content;
        private String cursorGlyph;
        private double cursorHeightPx;
        private double hotspotX;
        private double hotspotY;
        private CalibrationScreen.Options calibration;
        private ScreenScaleStore scaleStore = YamlScaleStore.shared();
        private double minScale = 0.5;
        private double maxScale = 2.0;
        private boolean hideOthers = true;
        private boolean suppressChat = true;
        private boolean restore = true;
        private Consumer<ScreenSession> onOpen;
        private BiConsumer<ScreenSession, CloseReason> onClose;
        private BiConsumer<ScreenSession, String> onHover;
        private BiConsumer<ScreenSession, Double> onScaleChanged;

        private Builder(Player player) {
            this.player = player;
        }

        /** Where the camera stands and the way it looks (its yaw; the screen is always level). */
        public Builder camera(Location eye) {
            this.camera = eye;
            return this;
        }

        /** Blocks between the camera and the canvas (default 3). Keep the space between them free of blocks. */
        public Builder distance(double blocks) {
            this.distance = blocks;
            return this;
        }

        /** The canvas pixel grid (default 1920x1080). Its aspect ratio should match the players' screens. */
        public Builder resolution(double width, double height) {
            this.pixelWidth = width;
            this.pixelHeight = height;
            return this;
        }

        /** The field of view (degrees, vertical) the canvas fills at scale 1 (default 70, Minecraft's default). */
        public Builder verticalFov(double degrees) {
            this.verticalFov = degrees;
            return this;
        }

        /** The UI, built once for the session's canvas (use fake components for {@code player}). */
        public Builder content(Function<Canvas, MountedLayout> factory) {
            this.content = factory;
            return this;
        }

        /** A resource-pack cursor: glyph, height in canvas px, hot spot in font px from its top-left. */
        public Builder cursor(String glyph, double heightPx, double hotspotX, double hotspotY) {
            this.cursorGlyph = glyph;
            this.cursorHeightPx = heightPx;
            this.hotspotX = hotspotX;
            this.hotspotY = hotspotY;
            return this;
        }

        /** Adds a calibration screen, shown first to players with no saved scale. */
        public Builder calibration(CalibrationScreen.Options options) {
            this.calibration = options;
            return this;
        }

        /** Where screen scales are kept (default: {@code plugins/Pixelay/screen-scales.yml}). */
        public Builder scaleStore(ScreenScaleStore store) {
            this.scaleStore = store;
            return this;
        }

        public Builder scaleRange(double min, double max) {
            this.minScale = min;
            this.maxScale = max;
            return this;
        }

        /** Hide other players from the viewer and the viewer from them (default true). */
        public Builder hideOthers(boolean hide) {
            this.hideOthers = hide;
            return this;
        }

        /** Hold back chat while the screen is open (default true). */
        public Builder suppressChat(boolean suppress) {
            this.suppressChat = suppress;
            return this;
        }

        /** Give back the game mode and position on close (default true). */
        public Builder restoreOnClose(boolean restore) {
            this.restore = restore;
            return this;
        }

        public Builder onOpen(Consumer<ScreenSession> callback) {
            this.onOpen = callback;
            return this;
        }

        /** Called once when the session ends; with {@code QUIT} the player is already leaving. */
        public Builder onClose(BiConsumer<ScreenSession, CloseReason> callback) {
            this.onClose = callback;
            return this;
        }

        /** Called when the box id under the cursor changes (see {@code Box.id}), e.g. to highlight it. */
        public Builder onHover(BiConsumer<ScreenSession, String> callback) {
            this.onHover = callback;
            return this;
        }

        /** Called when the player finishes calibrating, after the scale is saved. */
        public Builder onScaleChanged(BiConsumer<ScreenSession, Double> callback) {
            this.onScaleChanged = callback;
            return this;
        }

        private Options snapshot() {
            return new Options(
                camera.clone(),
                distance,
                pixelWidth,
                pixelHeight,
                verticalFov,
                content,
                cursorGlyph,
                cursorHeightPx,
                hotspotX,
                hotspotY,
                calibration,
                scaleStore,
                minScale,
                maxScale,
                hideOthers,
                suppressChat,
                restore,
                onOpen,
                onClose,
                onHover,
                onScaleChanged
            );
        }

        public ScreenSession open() {
            Objects.requireNonNull(camera, "camera");
            Objects.requireNonNull(content, "content");
            ScreenSession session = new ScreenSession(player, snapshot());
            session.open();
            return session;
        }
    }
}
