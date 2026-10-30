package dev.horuse.pixelay.screen;

import dev.horuse.pixelay.CanvasSurface;
import dev.horuse.pixelay.Pixelay;
import dev.horuse.pixelay.clicks.CanvasClickCatcher;
import dev.horuse.pixelay.components.CanvasCursor;
import dev.horuse.pixelay.data.Canvas;
import dev.horuse.pixelay.data.CanvasResolution;
import dev.horuse.pixelay.data.Layers;
import dev.horuse.pixelay.layout.MountedLayout;
import dev.horuse.pixelay.layout.ScrollView;
import dev.horuse.pixelay.listeners.ChatSuppression;
import dev.horuse.pixelay.pack.PixelayPack;
import dev.horuse.pixelay.utils.PacketUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
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
 *     .onClose((session, reason) -> ...)
 *     .open();
 * }</pre>
 *
 * The canvas is sized so that it fills a {@code verticalFov} view at scale 1. With the shader cursor (the
 * default) the pack draws it with that field of view, so it fills every player's screen; without it, players
 * with another field of view need another scale ({@link Builder#scale}, {@link #setScale}), e.g. from a
 * screen of your own. All methods must be called on the main thread.
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
    /** Blocks above the camera the player sits with a shader cursor. */
    private static final double SHADER_SEAT_HEIGHT = 4;

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
    private boolean closing;
    /** The veil currently shown (cover or reveal), or {@code null}. */
    private ScreenFader fader;
    /** Set once this session closed behind a full veil: a session opened right after starts covered. */
    private boolean handedOff;
    private int lastHeldSlot = -1;
    private int lastScrollDirection;
    private long lastScrollTime;
    private final AtomicInteger pendingScroll = new AtomicInteger();

    private ScreenSession(Player player, Options options) {
        this.player = player;
        this.options = options;
        this.view = options.camera().clone();
        this.facingYaw = view.getYaw();
        this.view.setPitch(0);
        Vector forward = new Vector(-Math.sin(Math.toRadians(facingYaw)), 0, Math.cos(Math.toRadians(facingYaw)));
        this.canvasCenter = view.clone().add(forward.multiply(options.distance()));
        ScreenSession existing = SESSIONS.get(player.getUniqueId());
        this.previousMode = existing != null ? existing.previousMode : player.getGameMode();
        this.previousLocation = existing != null ? existing.previousLocation : player.getLocation().clone();
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

    public boolean isClosed() {
        return closed;
    }

    public Location view() {
        return view.clone();
    }

    // --- opening -----------------------------------------------------------------------------------------

    /**
     * Opening runs in three steps: the veil covers the world, the player is seated on the camera behind it
     * (kept fully black until the client has settled), then the veil reveals the screen.
     */
    private void open() {
        ScreenSession previous = SESSIONS.put(player.getUniqueId(), this);
        if (previous != null && !previous.closed) {
            previous.close(CloseReason.CLOSED);
            SESSIONS.put(player.getUniqueId(), this);
        }
        ScreenTransition fadeIn = options.fadeIn();
        if (!animated(fadeIn)) {
            performOpen(false);
            return;
        }
        if (previous != null && previous.handedOff) {
            // The previous screen already covered everything: stay black and only reveal.
            performOpen(true);
            return;
        }
        Location worldView = player.getLocation().clone();
        worldView.setPitch(0f);
        player.setGameMode(GameMode.SPECTATOR);
        ScreenCamera worldCam = new ScreenCamera(player, worldView, 0, worldView.getYaw());
        worldCam.spawn();
        ScreenFader cover = worldFader(worldView, fadeIn, 0);
        fader = cover;
        cover.animate(0, 1, () -> {
            if (closed || !player.isOnline()) {
                cover.remove();
                worldCam.remove(true);
                player.setGameMode(previousMode);
                return;
            }
            worldCam.remove(false);
            cover.remove();
            performOpen(true);
        });
    }

    private void performOpen(boolean covered) {
        World world = view.getWorld();
        forceLoad(world);
        if (options.shaderCursor() && ShaderCursor.available()) {
            shaderCursor = new ShaderCursor(player);
        }
        // The shader measures the look from yaw 0: the player starts there, the camera keeps facing the canvas.
        lookOrigin = shaderCursor != null ? 0 : facingYaw;
        Location start = shaderCursor != null ? view.clone().add(0, SHADER_SEAT_HEIGHT, 0) : view.clone();
        start.setYaw(lookOrigin);
        start.setPitch(0f);
        player.teleport(start);
        PacketUtils.sendLook(player, lookOrigin, 0f);
        lastHeldSlot = player.getInventory().getHeldItemSlot();
        if (shaderCursor != null) {
            // Before the game mode changes: the tab list must never tell the client it is a spectator.
            shaderCursor.start();
        }
        player.setGameMode(GameMode.SPECTATOR);
        if (options.hideOthers()) {
            hideOthers();
        }
        if (options.suppressChat()) {
            ChatSuppression.suppress(player);
        }

        scale = shaderCursor != null ? shaderScale() : clampScale(options.scale());
        canvas = new Canvas(canvasCenter, canvasYaw(), resolution(scale));
        if (covered) {
            // Before any content: the screen is black from the first frame the camera shows it.
            fader = screenFader(options.fadeIn(), 1);
        }
        content = options.content().apply(canvas);
        content.viewer(player);
        content.spawn(canvas);
        Pixelay.manager().register(surface);

        camera = new ScreenCamera(player, view, shaderCursor != null ? SHADER_SEAT_HEIGHT : 0, lookOrigin);
        ScreenFader veil = fader;
        camera.spawn(() -> {
            if (covered && veil == fader && !closing && !closed) {
                veil.revealAfter(REVEAL_HOLD_TICKS, null);
            }
        });
        if (shaderCursor != null) {
            shaderCursor.flood(camera.seatLocation());
        }

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

        int scroll = pendingScroll.getAndSet(0);
        MountedLayout layout = currentLayout();
        if (scroll != 0) {
            ScrollView target = layout.scrollableAt(cursorX, cursorY);
            if (target != null) {
                target.scrollBy(scroll * target.scrollStep());
            }
        }

        ScrollView dragging = layout.activeDraggingScrollView();
        if (dragging != null) {
            dragging.dragTo(cursorY);
        }

        dev.horuse.pixelay.layout.Canvas drawingCanvas = layout.activeDrawingCanvas();
        boolean painted = drawingCanvas != null && drawingCanvas.dragTo(cursorX, cursorY, player);

        boolean animatedScroll = layout.tickScroll();
        boolean animatedTransition = layout.tickTransitions();
        if (animatedScroll || animatedTransition || scroll != 0 || dragging != null || painted) {
            layout.refresh();
        }

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
            if (options.onHover() != null) {
                options.onHover().accept(this, id);
                // The callback may have changed texts.
                layout.refresh();
            }
        }
    }

    private void click(Player clicker) {
        if (!closed && !closing && clicker.isOnline()) {
            MountedLayout layout = currentLayout();
            ScrollView dragging = layout.activeDraggingScrollView();
            if (dragging != null) {
                dragging.stopDragging();
                layout.refresh();
                return;
            }
            layout.click(cursorX, cursorY, clicker);
        }
    }

    synchronized void heldItemChange(int slot) {
        int previous = lastHeldSlot;
        lastHeldSlot = slot;
        if (previous < 0 || previous == slot) {
            return;
        }

        // Exact single-step notches: (8 -> 0 is forward, 0 -> 8 is backward)
        if (slot == (previous + 1) % 9) {
            lastScrollDirection = 1;
            lastScrollTime = System.currentTimeMillis();
            scroll(1);
            return;
        }
        if (previous == (slot + 1) % 9) {
            lastScrollDirection = -1;
            lastScrollTime = System.currentTimeMillis();
            scroll(-1);
            return;
        }

        // Fast multi-notch jump: if user scrolled in a direction within 300ms, preserve that momentum
        long now = System.currentTimeMillis();
        int delta;
        if (now - lastScrollTime < 300 && lastScrollDirection != 0) {
            if (lastScrollDirection > 0) {
                delta = (slot - previous + 9) % 9;
            } else {
                delta = -((previous - slot + 9) % 9);
            }
        } else {
            int forward = (slot - previous + 9) % 9;
            int backward = (previous - slot + 9) % 9;
            delta = forward <= backward ? forward : -backward;
            lastScrollDirection = Integer.signum(delta);
        }
        lastScrollTime = now;
        if (delta != 0) {
            scroll(delta);
        }
    }

    void scroll(int notches) {
        pendingScroll.addAndGet(notches);
    }

    private MountedLayout currentLayout() {
        return content;
    }

    /** Re-runs the layout of what is shown (texts changed); the scheduler also does it every refresh period. */
    public void refresh() {
        if (!closed) {
            currentLayout().refresh();
        }
    }

    /** Replaces the UI with another layout built for this session's canvas. */
    public void setContent(MountedLayout layout) {
        content.pointerLeft(player);
        content.despawn();
        content = layout;
        content.viewer(player);
        content.spawn(canvas);
    }

    // --- scale -------------------------------------------------------------------------------------------

    /**
     * Resizes the canvas (clamped to {@link Builder#scaleRange}); everything shown is re-laid out on the new
     * size. Without the shader cursor, a player whose field of view is not {@code verticalFov} sees the canvas
     * fill the screen at another scale. With it the shader fits the canvas to the screen and this does nothing.
     */
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
        close(CloseReason.CLOSED, null);
    }

    public void close(Runnable onClosed) {
        close(CloseReason.CLOSED, onClosed);
    }

    void close(CloseReason reason) {
        close(reason, null);
    }

    void close(CloseReason reason, Runnable onClosed) {
        if (closed) {
            return;
        }
        if (
            !closing &&
            reason == CloseReason.CLOSED &&
            options.fadeOut() != null &&
            options.fadeOut().type() != ScreenTransition.Type.NONE &&
            options.fadeOut().durationMs() > 0 &&
            player.isOnline()
        ) {
            startFadeOut(reason, onClosed);
            return;
        }
        finalizeClose(reason, onClosed);
    }

    private static final double OVERLAY_MULT = 1.6;

    private double overlayWidth() {
        return options.pixelWidth() * OVERLAY_MULT;
    }

    private double overlayHeight() {
        return options.pixelHeight() * OVERLAY_MULT;
    }

    private double overlayX() {
        return (options.pixelWidth() - overlayWidth()) / 2.0;
    }

    private double overlayY() {
        return (options.pixelHeight() - overlayHeight()) / 2.0;
    }

    private float overlayDepth() {
        return Layers.at(Layers.MAX_STEP + 5, player);
    }

    /** Ticks the veil stays fully closed after the switch, so the client settles before the reveal. */
    private static final long REVEAL_HOLD_TICKS = 2;

    private static boolean animated(ScreenTransition transition) {
        return transition != null && transition.type() != ScreenTransition.Type.NONE && transition.durationMs() > 0;
    }

    /** A veil over this session's canvas. */
    private ScreenFader screenFader(ScreenTransition transition, double u) {
        return new ScreenFader(
            player,
            canvas,
            transition,
            overlayX(),
            overlayY(),
            overlayWidth(),
            overlayHeight(),
            overlayDepth(),
            u
        );
    }

    /** A veil in the world, right in front of the world camera view. */
    private ScreenFader worldFader(Location at, ScreenTransition transition, double u) {
        float yaw = at.getYaw();
        Vector forward = new Vector(-Math.sin(Math.toRadians(yaw)), 0, Math.cos(Math.toRadians(yaw)));
        Canvas front = new Canvas(at.clone().add(forward.multiply(options.distance())), 180 - yaw, resolution(1.0));
        return new ScreenFader(
            player,
            front,
            transition,
            overlayX(),
            overlayY(),
            overlayWidth(),
            overlayHeight(),
            Layers.at(Layers.MAX_STEP + 5, player),
            u
        );
    }

    private void removeFader() {
        if (fader != null) {
            fader.remove();
            fader = null;
        }
    }

    /** Covers the screen, then closes behind the veil. */
    private void startFadeOut(CloseReason reason, Runnable onClosed) {
        closing = true;
        removeFader();
        ScreenFader veil = screenFader(options.fadeOut(), 0);
        fader = veil;
        veil.animate(0, 1, () -> finalizeClose(reason, onClosed));
    }

    private void finalizeClose(CloseReason reason, Runnable onClosed) {
        if (closed) {
            return;
        }
        closed = true;
        closing = false;
        ScreenFader veil = fader;
        fader = null;
        boolean veiled = veil != null && reason == CloseReason.CLOSED && player.isOnline();
        if (camera == null) {
            // Closed while the world was still being covered: nothing was set up yet.
            if (veil != null) {
                veil.remove();
            }
            SESSIONS.remove(player.getUniqueId(), this);
            if (options.onClose() != null) {
                options.onClose().accept(this, reason);
            }
            return;
        }

        if (task != null) {
            task.cancel();
            task = null;
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

        handedOff = veiled;
        if (onClosed != null) {
            onClosed.run();
        }

        ScreenSession nextSession = SESSIONS.get(player.getUniqueId());
        boolean hasNextSession = nextSession != null && nextSession != this;

        if (!hasNextSession) {
            if (veiled) {
                if (veil != null) {
                    veil.remove();
                }
                camera.remove(false);
                Location worldView = previousLocation.clone();
                worldView.setPitch(0f);
                player.teleport(worldView);
                ScreenCamera worldCam = new ScreenCamera(player, worldView, 0, worldView.getYaw());
                worldCam.spawn();
                ScreenFader reveal = worldFader(worldView, options.fadeOut(), 1);
                fader = reveal;
                reveal.revealAfter(REVEAL_HOLD_TICKS, () -> {
                    fader = null;
                    worldCam.remove(true);
                    if (player.isOnline() && options.restore()) {
                        player.teleport(previousLocation);
                        player.setGameMode(previousMode);
                    }
                    if (shaderCursor != null) {
                        shaderCursor.stop();
                    }
                    showOthers();
                    releaseChunks();
                    SESSIONS.remove(player.getUniqueId(), this);
                    if (options.onClose() != null) {
                        options.onClose().accept(this, reason);
                    }
                });
            } else {
                if (veil != null) {
                    veil.remove();
                }
                camera.remove(true);
                if (player.isOnline() && options.restore()) {
                    player.teleport(previousLocation);
                    player.setGameMode(previousMode);
                }
                if (shaderCursor != null) {
                    shaderCursor.stop();
                }
                showOthers();
                releaseChunks();
                SESSIONS.remove(player.getUniqueId(), this);
                if (options.onClose() != null) {
                    options.onClose().accept(this, reason);
                }
            }
        } else {
            // The next session already has its camera and its own veil: keep the player's view on it.
            camera.remove(false);
            if (veil != null) {
                veil.remove();
            }
            if (options.onClose() != null) {
                options.onClose().accept(this, reason);
            }
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
        double scale,
        double minScale,
        double maxScale,
        boolean hideOthers,
        boolean suppressChat,
        boolean restore,
        ScreenTransition fadeIn,
        ScreenTransition fadeOut,
        Consumer<ScreenSession> onOpen,
        BiConsumer<ScreenSession, CloseReason> onClose,
        BiConsumer<ScreenSession, String> onHover
    ) {}

    // --- options -----------------------------------------------------------------------------------------

    /** Options of a session; {@link #open()} starts it. */
    public static final class Builder {

        private final Player player;
        private Location camera;
        private double distance = 1.5;
        private double pixelWidth = 1920;
        private double pixelHeight = 1080;
        private double verticalFov = 70;
        private Function<Canvas, MountedLayout> content;
        private String cursorGlyph;
        private double cursorHeightPx;
        private double hotspotX;
        private double hotspotY;
        private boolean shaderCursor = Pixelay.settings().cursor().shader();
        private double scale = 1.0;
        private double minScale = 0.5;
        private double maxScale = 2.0;
        private boolean hideOthers = true;
        private boolean suppressChat = true;
        private boolean restore = true;
        private ScreenTransition fadeIn = ScreenTransition.none();
        private ScreenTransition fadeOut = ScreenTransition.none();
        private Consumer<ScreenSession> onOpen;
        private BiConsumer<ScreenSession, CloseReason> onClose;
        private BiConsumer<ScreenSession, String> onHover;

        private Builder(Player player) {
            this.player = player;
        }

        /** Where the camera stands and the way it looks (its yaw; the screen is always level). */
        public Builder camera(Location eye) {
            this.camera = eye;
            return this;
        }

        /** Blocks between the camera and the canvas (default 1.5). Keep the space between them free of blocks. */
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

        /** The UI, built once for the session's canvas (accepts any {@link dev.horuse.pixelay.layout.Box} or {@link MountedLayout}). */
        public Builder content(Function<Canvas, ? extends dev.horuse.pixelay.components.CanvasComponent> factory) {
            this.content = canvas -> {
                dev.horuse.pixelay.components.CanvasComponent comp = factory.apply(canvas);
                if (comp instanceof MountedLayout mounted) {
                    return mounted;
                }
                if (comp instanceof dev.horuse.pixelay.layout.Box<?> box) {
                    return dev.horuse.pixelay.layout.Layout.mount(box);
                }
                throw new IllegalArgumentException("Unsupported ScreenSession content: " + comp);
            };
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

        /**
         * The scale the screen opens with (default 1: the canvas fills a {@code verticalFov} view). Only without
         * the shader cursor: e.g. a scale the player picked on a screen of your own (see {@link ScreenSession#setScale}).
         */
        public Builder scale(double scale) {
            this.scale = scale;
            return this;
        }

        /** Limits of {@link #scale} and {@link ScreenSession#setScale} (default 0.5 to 2). */
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

        /** Configures matching fade in and fade out screen transitions. */
        public Builder transition(ScreenTransition transition) {
            this.fadeIn = transition != null ? transition : ScreenTransition.none();
            this.fadeOut = this.fadeIn;
            return this;
        }

        /** Configures a screen transition when opening the session. */
        public Builder fadeIn(ScreenTransition transition) {
            this.fadeIn = Objects.requireNonNullElse(transition, ScreenTransition.none());
            return this;
        }

        /** Configures a simple black fade-in with the given duration in milliseconds. */
        public Builder fadeIn(long durationMs) {
            return fadeIn(ScreenTransition.fade(durationMs));
        }

        /** Configures a screen transition when closing the session. */
        public Builder fadeOut(ScreenTransition transition) {
            this.fadeOut = Objects.requireNonNullElse(transition, ScreenTransition.none());
            return this;
        }

        /** Configures a simple black fade-out with the given duration in milliseconds. */
        public Builder fadeOut(long durationMs) {
            return fadeOut(ScreenTransition.fade(durationMs));
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
                scale,
                minScale,
                maxScale,
                hideOthers,
                suppressChat,
                restore,
                fadeIn,
                fadeOut,
                onOpen,
                onClose,
                onHover
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
