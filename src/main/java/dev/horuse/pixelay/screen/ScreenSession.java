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
 * A full-screen UI for one player.
 * The player becomes a spectator locked to an invisible camera before a canvas.
 * A cursor follows view rotation and sends clicks to the layout.
 * Player state (game mode, location, visibility, and chat) restores on {@link #close}, quit, or shutdown.
 *
 * <pre>{@code
 * ScreenSession.builder(player)
 *     .camera(viewLocation)
 *     .content(canvas -> buildLayout())
 *     .onClose((session, reason) -> ...)
 *     .open();
 * }</pre>
 *
 * The canvas fills the view at scale 1.
 * With the default shader cursor, the pack renders with matching field of view.
 * All methods must run on the main server thread.
 */
public final class ScreenSession {

    /** Reason for session closure. */
    public enum CloseReason {
        /** {@link #close()} was called. */
        CLOSED,
        /** The player disconnected. */
        QUIT,
        /** Server or plugin shutdown occurred. */
        SHUTDOWN,
    }

    private static final Map<UUID, ScreenSession> SESSIONS = new ConcurrentHashMap<>();
    private static final int LOAD_RADIUS_CHUNKS = 1;
    /** Blocks above camera where player sits with a shader cursor. */
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
    /** Cursor drawn by core shader, or {@code null} for glyph cursor. */
    private ShaderCursor shaderCursor;
    /** Reference yaw for player look angle (0 for shader cursor, else camera yaw). */
    private float lookOrigin;
    /** Half height tangent of the canvas seen from camera to map angles to pixels. */
    private volatile double halfHeightTan;
    private CanvasClickCatcher clickCatcher;
    private BukkitTask task;
    private double scale;
    private volatile double cursorX;
    private volatile double cursorY;
    private String hovered;
    private boolean closed;
    private boolean closing;
    /** Active transition veil, or {@code null}. */
    private ScreenFader fader;
    /** True when closed behind an active veil so next session starts covered. */
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

    /** Returns active screen session of player if present. */
    public static Optional<ScreenSession> of(Player player) {
        return Optional.ofNullable(SESSIONS.get(player.getUniqueId()));
    }

    public static boolean isInSession(Player player) {
        return player != null && SESSIONS.containsKey(player.getUniqueId());
    }

    /** Checks if entity is the camera vehicle for a session player. */
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

    /** Current scale of the screen canvas. */
    public double scale() {
        return scale;
    }

    /** Coordinates of canvas pixel under cursor {x, y}. */
    public double[] cursor() {
        return new double[] { cursorX, cursorY };
    }

    /** ID of deepest box under cursor, or {@code null}. */
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
     * Executes opening sequence: covers world with veil, mounts player, and reveals screen.
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
            // Previous screen remained covered: keep black screen and reveal.
            performOpen(true);
            return;
        }
        if (ShaderTransition.available()) {
            ShaderTransition trans = new ShaderTransition(player, fadeIn, ShaderTransition.Mode.IN);
            trans.play(() -> {
                if (closed || !player.isOnline()) {
                    ShaderTransition.clear(player);
                    return;
                }
                performOpen(true);
            });
            return;
        }
        Location eye = player.getEyeLocation();
        ScreenFader cover = worldFader(eye, fadeIn, 0);
        fader = cover;
        cover.animate(0, 1, () -> {
            if (closed || !player.isOnline()) {
                cover.remove();
                return;
            }
            performOpen(true);
            cover.remove();
        });
    }

    private void performOpen(boolean covered) {
        World world = view.getWorld();
        forceLoad(world);
        if (options.shaderCursor() && ShaderCursor.available()) {
            shaderCursor = new ShaderCursor(player);
        }
        // Shader measures look from yaw 0: player starts at 0 while camera faces canvas.
        lookOrigin = shaderCursor != null ? 0 : facingYaw;
        Location start = shaderCursor != null ? view.clone().add(0, SHADER_SEAT_HEIGHT, 0) : view.clone();
        start.setYaw(lookOrigin);
        start.setPitch(0f);
        player.teleport(start);
        PacketUtils.sendLook(player, lookOrigin, 0f);
        lastHeldSlot = player.getInventory().getHeldItemSlot();
        if (shaderCursor != null) {
            // Start shader cursor before spectator switch so tab list shows adventure.
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
        if (covered && !ShaderTransition.available()) {
            // Spawn veil before content so screen is black on first frame.
            fader = screenFader(options.fadeIn(), 1);
        }
        content = options.content().apply(canvas);
        content.viewer(player);
        content.spawn(canvas);
        Pixelay.manager().register(surface);

        camera = new ScreenCamera(player, view, shaderCursor != null ? SHADER_SEAT_HEIGHT : 0, lookOrigin);
        ScreenFader veil = fader;
        camera.spawn(() -> {
            if (covered && !closing && !closed) {
                if (ShaderTransition.available()) {
                    Bukkit.getScheduler().runTaskLater(
                        Pixelay.plugin(),
                        () -> {
                            if (!closing && !closed && player.isOnline()) {
                                ShaderTransition out = new ShaderTransition(
                                    player,
                                    options.fadeIn(),
                                    ShaderTransition.Mode.OUT
                                );
                                out.play(() -> ShaderTransition.clear(player));
                            }
                        },
                        REVEAL_HOLD_TICKS
                    );
                } else if (veil == fader && fader != null) {
                    veil.revealAfter(REVEAL_HOLD_TICKS, null);
                }
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

        // Route spectator click packets from network thread to main server thread.
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
    /** Handles camera look updates from client movement packets. */
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
            // Shader renders cursor locally on client.
            return;
        }
        Bukkit.getScheduler().runTask(Pixelay.plugin(), () -> {
            if (!closed && cursor != null) {
                cursor.move(canvas, cursorX, cursorY);
            }
        });
    }

    /**
     * Calculates canvas pixel under camera look angles, clamped to canvas edges.
     */
    private double[] lookPixel(float yaw, float pitch) {
        // Relative angles to camera facing canvas center.
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
        // Update hover states and refresh layout on change.
        layout.pointerAt(cursorX, cursorY, player);
        if (shaderCursor != null) {
            // Select cursor sprite for layout element under pointer.
            PixelayPack.Built pack = PixelayPack.current();
            int sprite = pack != null ? pack.spriteIndex(layout.cursorAt(cursorX, cursorY)) : 0;
            halfHeightTan = shaderCursor.show(sprite, exactHalfHeightTan());
        }
        String id = layout.idAt(cursorX, cursorY);
        if (!Objects.equals(id, hovered)) {
            hovered = id;
            if (options.onHover() != null) {
                options.onHover().accept(this, id);
                // Callback execution can change text content.
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

        // Scroll one step on slot change between hotbar slots 8 and 0.
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

        // Fast multi-slot change preserves scroll momentum if within 300 ms.
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

    /** Re-evaluates layout components on canvas. */
    public void refresh() {
        if (!closed) {
            currentLayout().refresh();
        }
    }

    /** Replaces active layout with a new mounted layout. */
    public void setContent(MountedLayout layout) {
        content.pointerLeft(player);
        content.despawn();
        content = layout;
        content.viewer(player);
        content.spawn(canvas);
    }

    // --- scale -------------------------------------------------------------------------------------------

    /**
     * Changes canvas scale when shader cursor is disabled.
     */
    public void setScale(double value) {
        if (shaderCursor != null) {
            // Shader cursor automatically fits canvas to screen.
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

    /** Tangent of half canvas height at current scale. */
    private double exactHalfHeightTan() {
        return Math.tan(Math.toRadians(options.verticalFov() / 2)) * scale;
    }

    /**
     * Calculates scale required to match discrete FOV steps of shader cursor.
     */
    private double shaderScale() {
        double tan = Math.tan(Math.toRadians(options.verticalFov() / 2));
        return ShaderCursor.halfHeightTan(ShaderCursor.level(tan)) / tan;
    }

    /**
     * Returns canvas yaw angle facing back toward camera.
     */
    private float canvasYaw() {
        return 180 - facingYaw;
    }

    private double clampScale(double value) {
        return Math.max(options.minScale(), Math.min(options.maxScale(), value));
    }

    /**
     * Computes canvas pixel and world dimensions for given scale.
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

    /** Ticks veil remains closed after switch to let client stabilize. */
    private static final long REVEAL_HOLD_TICKS = 2;

    private static boolean animated(ScreenTransition transition) {
        return transition != null && transition.type() != ScreenTransition.Type.NONE && transition.durationMs() > 0;
    }

    /** Creates a veil over this canvas. */
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

    /** Creates a veil in front of player eyes. */
    private ScreenFader worldFader(Location at, ScreenTransition transition, double u) {
        float yaw = at.getYaw();
        float pitch = at.getPitch();
        Vector forward = at.getDirection().normalize();
        Location center = at.clone().add(forward.multiply(options.distance()));
        Canvas front = new Canvas(center, 180 - yaw, -pitch, 0f, 0f, resolution(1.0));
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

    /** Covers screen and closes session behind veil. */
    private void startFadeOut(CloseReason reason, Runnable onClosed) {
        closing = true;
        removeFader();
        ScreenTransition fadeOut = options.fadeOut();
        if (ShaderTransition.available()) {
            ShaderTransition trans = new ShaderTransition(player, fadeOut, ShaderTransition.Mode.IN);
            trans.play(() -> finalizeClose(reason, onClosed));
            return;
        }
        ScreenFader veil = screenFader(fadeOut, 0);
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
        boolean shaderTrans = ShaderTransition.available();
        boolean veiled = (veil != null || shaderTrans) && reason == CloseReason.CLOSED && player.isOnline();
        if (camera == null) {
            // Session closed during initial coverage before setup completed.
            if (veil != null) {
                veil.remove();
            }
            if (shaderTrans) {
                ShaderTransition.clear(player);
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
                // Screen is fully obscured: restore game mode and camera, then teleport.
                if (player.isOnline() && options.restore()) {
                    player.setGameMode(previousMode);
                }
                camera.remove(true);
                if (player.isOnline() && options.restore()) {
                    player.teleport(previousLocation);
                }
                if (shaderCursor != null) {
                    shaderCursor.stop();
                }
                showOthers();
                releaseChunks();

                if (shaderTrans) {
                    Bukkit.getScheduler().runTaskLater(
                        Pixelay.plugin(),
                        () -> {
                            if (!player.isOnline()) {
                                ShaderTransition.clear(player);
                                SESSIONS.remove(player.getUniqueId(), this);
                                if (options.onClose() != null) {
                                    options.onClose().accept(this, reason);
                                }
                                return;
                            }
                            ShaderTransition reveal = new ShaderTransition(
                                player,
                                options.fadeOut(),
                                ShaderTransition.Mode.OUT
                            );
                            reveal.play(() -> {
                                ShaderTransition.clear(player);
                                SESSIONS.remove(player.getUniqueId(), this);
                                if (options.onClose() != null) {
                                    options.onClose().accept(this, reason);
                                }
                            });
                        },
                        REVEAL_HOLD_TICKS
                    );
                } else {
                    Location eye = player.getEyeLocation();
                    ScreenFader reveal = worldFader(eye, options.fadeOut(), 1);
                    fader = reveal;
                    if (veil != null) {
                        veil.remove();
                    }
                    reveal.revealAfter(REVEAL_HOLD_TICKS, () -> {
                        fader = null;
                        SESSIONS.remove(player.getUniqueId(), this);
                        if (options.onClose() != null) {
                            options.onClose().accept(this, reason);
                        }
                    });
                }
            } else {
                if (veil != null) {
                    veil.remove();
                }
                if (shaderTrans) {
                    ShaderTransition.clear(player);
                }
                if (player.isOnline() && options.restore()) {
                    player.setGameMode(previousMode);
                }
                camera.remove(true);
                if (player.isOnline() && options.restore()) {
                    player.teleport(previousLocation);
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
            // Next session has camera and veil active: preserve player view.
            camera.remove(false);
            if (veil != null) {
                veil.remove();
            }
            if (options.onClose() != null) {
                options.onClose().accept(this, reason);
            }
        }
    }

    /** Closes all active screen sessions. */
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

    /** Hides connecting players from session viewers and session viewers from connecting players. */
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

    /** Keeps chunks force loaded to maintain camera entity visibility. */
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

    /** Surface interface for scheduler refresh cycles. */
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

    /** Frozen options snapshot for an active screen session. */
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

    /** Builder for {@link ScreenSession} instances. */
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

        /** Sets camera position and viewing yaw angle. */
        public Builder camera(Location eye) {
            this.camera = eye;
            return this;
        }

        /** Sets distance in blocks between camera and canvas (default 1.5). */
        public Builder distance(double blocks) {
            this.distance = blocks;
            return this;
        }

        /** Sets canvas pixel resolution (default 1920x1080). */
        public Builder resolution(double width, double height) {
            this.pixelWidth = width;
            this.pixelHeight = height;
            return this;
        }

        /** Sets vertical field of view in degrees that the canvas fills at scale 1 (default 70). */
        public Builder verticalFov(double degrees) {
            this.verticalFov = degrees;
            return this;
        }

        /** Sets UI content factory built for the session canvas. */
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

        /** Configures a custom resource pack glyph cursor. */
        public Builder cursor(String glyph, double heightPx, double hotspotX, double hotspotY) {
            this.cursorGlyph = glyph;
            this.cursorHeightPx = heightPx;
            this.hotspotX = hotspotX;
            this.hotspotY = hotspotY;
            return this;
        }

        /**
         * Enables or disables core shader rendering for the cursor.
         */
        public Builder shaderCursor(boolean shader) {
            this.shaderCursor = shader;
            return this;
        }

        /**
         * Sets initial canvas scale when shader cursor is disabled.
         */
        public Builder scale(double scale) {
            this.scale = scale;
            return this;
        }

        /** Sets minimum and maximum scale bounds (default 0.5 to 2.0). */
        public Builder scaleRange(double min, double max) {
            this.minScale = min;
            this.maxScale = max;
            return this;
        }

        /** Hides other players from the viewer and viewer from other players. */
        public Builder hideOthers(boolean hide) {
            this.hideOthers = hide;
            return this;
        }

        /** Suppresses incoming chat messages while the screen session is open. */
        public Builder suppressChat(boolean suppress) {
            this.suppressChat = suppress;
            return this;
        }

        /** Restores player game mode and location on session close. */
        public Builder restoreOnClose(boolean restore) {
            this.restore = restore;
            return this;
        }

        public Builder onOpen(Consumer<ScreenSession> callback) {
            this.onOpen = callback;
            return this;
        }

        /** Callback executed when session closes. */
        public Builder onClose(BiConsumer<ScreenSession, CloseReason> callback) {
            this.onClose = callback;
            return this;
        }

        /** Callback executed when element ID under cursor changes. */
        public Builder onHover(BiConsumer<ScreenSession, String> callback) {
            this.onHover = callback;
            return this;
        }

        /** Configures identical transitions for fade-in and fade-out. */
        public Builder transition(ScreenTransition transition) {
            this.fadeIn = transition != null ? transition : ScreenTransition.none();
            this.fadeOut = this.fadeIn;
            return this;
        }

        /** Configures transition for opening the session. */
        public Builder fadeIn(ScreenTransition transition) {
            this.fadeIn = Objects.requireNonNullElse(transition, ScreenTransition.none());
            return this;
        }

        /** Configures black fade-in transition with duration in milliseconds. */
        public Builder fadeIn(long durationMs) {
            return fadeIn(ScreenTransition.fade(durationMs));
        }

        /** Configures transition for closing the session. */
        public Builder fadeOut(ScreenTransition transition) {
            this.fadeOut = Objects.requireNonNullElse(transition, ScreenTransition.none());
            return this;
        }

        /** Configures black fade-out transition with duration in milliseconds. */
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
