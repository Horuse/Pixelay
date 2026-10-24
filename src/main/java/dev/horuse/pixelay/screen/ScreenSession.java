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
import dev.horuse.pixelay.pack.PixelayPack;
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
    /** The cursor drawn by the pack's core shader, or {@code null} for the glyph {@link #cursor}. */
    private ShaderCursor shaderCursor;
    /** Yaw the player's look is measured from: 0 with a shader cursor (the shader's origin), else the camera's. */
    private float lookOrigin;
    /** Tangent of half the canvas height as seen from the camera; looks map to pixels with it. */
    private volatile double halfHeightTan;
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
        if (options.shaderCursor() && ShaderCursor.available()) {
            shaderCursor = new ShaderCursor(player);
        }
        // The shader measures the look from yaw 0: the player starts there, the camera keeps facing the canvas.
        lookOrigin = shaderCursor != null ? 0 : facingYaw;
        Location start = view.clone();
        start.setYaw(lookOrigin);
        player.teleport(start);
        if (shaderCursor != null) {
            // Before the game mode changes: the tab list must never tell the client it is a spectator.
            shaderCursor.start();
            shaderCursor.flood(view);
        }
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
        scale = shaderCursor != null ? shaderScale() : clampScale(saved != null ? saved : 1.0);
        canvas = new Canvas(canvasCenter, canvasYaw(), resolution(scale));
        content = options.content().apply(canvas);
        if (options.calibration() != null) {
            calibration = new CalibrationScreen(this, options.calibration());
        }
        // The shader draws the canvas with its own field of view, so it always fills the screen: no calibration.
        calibrating = calibration != null && saved == null && shaderCursor == null;
        currentLayout().spawn(canvas);
        Pixelay.manager().register(surface);

        halfHeightTan = Math.tan(Math.toRadians(options.verticalFov() / 2)) * scale;
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
        if (shaderCursor == null) {
            cursor.spawn(canvas);
        }

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
        if (shaderCursor != null) {
            shaderCursor.tick();
        }
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
        if (shaderCursor != null) {
            // The client draws it there by itself.
            return;
        }
        Bukkit.getScheduler().runTask(Pixelay.plugin(), () -> {
            if (!closed && cursor != null) {
                cursor.move(canvas, cursorX, cursorY);
            }
        });
    }

    /**
     * The canvas pixel the look points at (where the camera ray through it hits the canvas), clamped to its
     * edges. The shader cursor works out the same point on the screen with the same formula, so the pixel
     * clicked is always the one under the drawn cursor.
     */
    private double[] lookPixel(float yaw, float pitch) {
        // Relative to the camera, which faces the canvas centre.
        double relative = Math.toRadians(((((yaw - lookOrigin + 180) % 360) + 360) % 360) - 180);
        double p = Math.toRadians(pitch);
        double forward = Math.cos(relative);
        double tangentX = forward > 1.0e-4 ? Math.tan(relative) : Math.signum(relative) * 1.0e4;
        double tangentY = -Math.tan(p) / Math.max(forward, 1.0e-4);
        CanvasResolution res = canvas.resolution();
        double aspect = res.pixelWidth() / res.pixelHeight();
        double t = halfHeightTan;
        return new double[] {
            Math.max(0, Math.min(res.pixelWidth(), ((1 + tangentX / (t * aspect)) * res.pixelWidth()) / 2)),
            Math.max(0, Math.min(res.pixelHeight(), ((1 - tangentY / t) * res.pixelHeight()) / 2)),
        };
    }

    private void updateHover() {
        MountedLayout layout = currentLayout();
        // Hover states and styles; the layout refreshes itself when they change.
        layout.pointerAt(cursorX, cursorY, player);
        if (shaderCursor != null) {
            // CSS cursor of what is under it; the pixels are then measured with the size the shader uses.
            PixelayPack.Built pack = PixelayPack.current();
            int sprite = pack != null ? pack.spriteIndex(layout.cursorAt(cursorX, cursorY)) : 0;
            halfHeightTan = shaderCursor.show(sprite, exactHalfHeightTan());
        }
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
        // A shader cursor's canvas always fills the screen: there is nothing to calibrate.
        if (calibration == null || calibrating || shaderCursor != null) {
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
        if (shaderCursor != null) {
            // The shader fits the canvas to the screen by itself.
            return;
        }
        double next = clampScale(Math.round(value * 1000) / 1000.0);
        if (next == scale) {
            return;
        }
        scale = next;
        MountedLayout shown = currentLayout();
        shown.despawn();
        cursor.despawn();
        canvas = new Canvas(canvasCenter, canvasYaw(), resolution(scale));
        halfHeightTan = exactHalfHeightTan();
        shown.spawn(canvas);
        if (shaderCursor == null) {
            cursor.spawn(canvas);
        }
    }

    /** {@code tan} of half the canvas height as the camera sees it, at the current scale. */
    private double exactHalfHeightTan() {
        return Math.tan(Math.toRadians(options.verticalFov() / 2)) * scale;
    }

    /**
     * With a shader cursor the canvas is drawn with the field of view the state carries (whatever the player's
     * FOV is), which comes in steps: the scale that makes the canvas exactly that size.
     */
    private double shaderScale() {
        double tan = Math.tan(Math.toRadians(options.verticalFov() / 2));
        return ShaderCursor.halfHeightTan(ShaderCursor.level(tan)) / tan;
    }

    /**
     * The canvas faces the camera. Minecraft yaw turns the other way than the canvas' one (a canvas with yaw
     * {@code c} faces {@code (sin c, 0, cos c)}, a Minecraft yaw {@code y} looks along {@code (-sin y, 0, cos y)}),
     * so facing back towards the camera is {@code 180 - y}, not {@code y + 180}: the two agree only for 0 and 180.
     */
    private float canvasYaw() {
        return 180 - facingYaw;
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
        if (shaderCursor != null) {
            // After the game mode is back: it also sends the real one to the tab list.
            shaderCursor.stop();
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
        boolean shaderCursor,
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
        private boolean shaderCursor = Pixelay.settings().cursor().shader();
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

        /**
         * Draw the cursor with the core shader of Pixelay's pack: smooth as the camera, and its sprite follows
         * CSS {@code cursor} of the boxes under it (default: {@code cursor.shader} in config.yml). The player
         * needs the pack; without the shader the glyph cursor ({@link #cursor}) is moved by the server.
         */
        public Builder shaderCursor(boolean shader) {
            this.shaderCursor = shader;
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
                shaderCursor,
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
