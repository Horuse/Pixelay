package dev.horuse.pixelay.layout;

import dev.horuse.pixelay.components.TextBackground;
import dev.horuse.pixelay.data.Rect;
import dev.horuse.pixelay.data.Size;
import dev.horuse.pixelay.screen.ScreenSession;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.bukkit.entity.Player;

/**
 * An HTML {@code <canvas>}-like 2D pixel grid and interactive drawing surface inside a layout.
 * <p>
 * Provides a programmatic 2D drawing API ({@link #setPixel}, {@link #fillRect}, {@link #strokeRect},
 * {@link #drawLine}, {@link #drawCircle}, {@link #fillCircle}, {@link #clear}) as well as interactive
 * player painting via cursor clicks and continuous drag strokes when {@link #drawable(boolean)} is enabled.
 * <p>
 * Adjacent pixels of the same colour are automatically merged into minimal rectangles via 2D greedy
 * meshing and rendered using pooled {@link TextBackground} panels.
 */
public class Canvas extends Box<Canvas> {

    @FunctionalInterface
    public interface DrawListener {
        void onDraw(Player player, int x, int y, int argb);
    }

    private final Player viewer;
    private final int gridWidth;
    private final int gridHeight;
    private final int[] pixels;
    private final List<TextBackground> quadPool = new ArrayList<>();

    private boolean drawable;
    private int brushColor = 0xFF4F46E5;
    private int brushSize = 1;
    private DrawListener onDraw;

    private boolean drawing;
    private int lastDrawX = -1;
    private int lastDrawY = -1;

    public Canvas(Player viewer, int gridWidth, int gridHeight) {
        if (gridWidth <= 0 || gridHeight <= 0) {
            throw new IllegalArgumentException("gridWidth and gridHeight must be positive");
        }
        this.viewer = viewer;
        this.gridWidth = gridWidth;
        this.gridHeight = gridHeight;
        this.pixels = new int[gridWidth * gridHeight];
    }

    public Canvas(int gridWidth, int gridHeight) {
        this(null, gridWidth, gridHeight);
    }

    @Override
    public String tagName() {
        return "canvas";
    }

    @Override
    public Player viewer() {
        return viewer;
    }

    /** Reserve 2 depth steps: step 0 for the canvas background, step 1 for the pixel quads above it. */
    @Override
    int layerSteps() {
        return 2;
    }

    public int gridWidth() {
        return gridWidth;
    }

    public int gridHeight() {
        return gridHeight;
    }

    /** Returns a copy of the raw ARGB pixel buffer ({@code gridWidth * gridHeight}, row-major). */
    public int[] pixels() {
        return Arrays.copyOf(pixels, pixels.length);
    }

    // --- 2D Drawing API ----------------------------------------------------------------------------------

    /** Sets the ARGB colour of a single grid pixel {@code (x, y)}. Out-of-bounds coordinates are ignored. */
    public Canvas setPixel(int x, int y, int argb) {
        if (x >= 0 && x < gridWidth && y >= 0 && y < gridHeight) {
            pixels[y * gridWidth + x] = argb;
        }
        return this;
    }

    /** Gets the ARGB colour at {@code (x, y)}, or {@code 0} if out of bounds. */
    public int getPixel(int x, int y) {
        if (x >= 0 && x < gridWidth && y >= 0 && y < gridHeight) {
            return pixels[y * gridWidth + x];
        }
        return 0;
    }

    /** Clears all pixels to transparent ({@code 0x00000000}). */
    public Canvas clear() {
        return fill(0);
    }

    /** Fills the entire pixel grid with {@code argb}. */
    public Canvas fill(int argb) {
        Arrays.fill(pixels, argb);
        return this;
    }

    /** Fills a rectangle {@code (x, y, w, h)} in grid coordinates with {@code argb}. */
    public Canvas fillRect(int x, int y, int w, int h, int argb) {
        int x0 = Math.max(0, x);
        int y0 = Math.max(0, y);
        int x1 = Math.min(gridWidth, x + w);
        int y1 = Math.min(gridHeight, y + h);
        for (int py = y0; py < y1; py++) {
            int rowOffset = py * gridWidth;
            for (int px = x0; px < x1; px++) {
                pixels[rowOffset + px] = argb;
            }
        }
        return this;
    }

    /** Draws a 1px outline rectangle {@code (x, y, w, h)} in grid coordinates with {@code argb}. */
    public Canvas strokeRect(int x, int y, int w, int h, int argb) {
        if (w <= 0 || h <= 0) {
            return this;
        }
        fillRect(x, y, w, 1, argb);
        fillRect(x, y + h - 1, w, 1, argb);
        fillRect(x, y, 1, h, argb);
        fillRect(x + w - 1, y, 1, h, argb);
        return this;
    }

    /** Draws a 1px line between {@code (x0, y0)} and {@code (x1, y1)} using Bresenham's algorithm. */
    public Canvas drawLine(int x0, int y0, int x1, int y1, int argb) {
        return drawLine(x0, y0, x1, y1, 1, argb);
    }

    /** Draws a line with brush thickness {@code brushSize} (1 = 1px, 2 = 3x3 circle, etc.). */
    public Canvas drawLine(int x0, int y0, int x1, int y1, int brushSize, int argb) {
        int dx = Math.abs(x1 - x0);
        int dy = -Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx + dy;
        int cx = x0;
        int cy = y0;
        while (true) {
            paintDab(cx, cy, brushSize, argb);
            if (cx == x1 && cy == y1) {
                break;
            }
            int e2 = 2 * err;
            if (e2 >= dy) {
                err += dy;
                cx += sx;
            }
            if (e2 <= dx) {
                err += dx;
                cy += sy;
            }
        }
        return this;
    }

    /** Draws a circle outline centred at {@code (cx, cy)} with {@code radius}. */
    public Canvas drawCircle(int cx, int cy, int radius, int argb) {
        if (radius <= 0) {
            return setPixel(cx, cy, argb);
        }
        int x = radius;
        int y = 0;
        int err = 1 - radius;
        while (x >= y) {
            setPixel(cx + x, cy + y, argb);
            setPixel(cx + y, cy + x, argb);
            setPixel(cx - y, cy + x, argb);
            setPixel(cx - x, cy + y, argb);
            setPixel(cx - x, cy - y, argb);
            setPixel(cx - y, cy - x, argb);
            setPixel(cx + y, cy - x, argb);
            setPixel(cx + x, cy - y, argb);
            y++;
            if (err < 0) {
                err += 2 * y + 1;
            } else {
                x--;
                err += 2 * (y - x) + 1;
            }
        }
        return this;
    }

    /** Fills a circle centred at {@code (cx, cy)} with {@code radius}. */
    public Canvas fillCircle(int cx, int cy, int radius, int argb) {
        if (radius <= 0) {
            return setPixel(cx, cy, argb);
        }
        int r2 = radius * radius;
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                if (dx * dx + dy * dy <= r2) {
                    setPixel(cx + dx, cy + dy, argb);
                }
            }
        }
        return this;
    }

    private void paintDab(int gx, int gy, int size, int argb) {
        if (size <= 1) {
            setPixel(gx, gy, argb);
        } else {
            fillCircle(gx, gy, size - 1, argb);
        }
    }

    // --- Interactive Painting API ------------------------------------------------------------------------

    /** Enables or disables interactive player painting with the cursor. */
    public Canvas drawable(boolean drawable) {
        this.drawable = drawable;
        if (drawable && cursor() == null) {
            cursor("crosshair");
        }
        return this;
    }

    public boolean isDrawable() {
        return drawable;
    }

    /** Sets the active brush colour (ARGB) for interactive player painting. */
    public Canvas brushColor(int argb) {
        this.brushColor = argb;
        return this;
    }

    public int brushColor() {
        return brushColor;
    }

    /** Sets the active brush size (1 = 1px, 2 = 3px circle, 3 = 5px circle, etc.). */
    public Canvas brushSize(int size) {
        this.brushSize = Math.max(1, size);
        return this;
    }

    public int brushSize() {
        return brushSize;
    }

    /** Listener called whenever a player paints on the canvas. */
    public Canvas onDraw(DrawListener listener) {
        this.onDraw = listener;
        return this;
    }

    public boolean isDrawing() {
        return drawing;
    }

    public void stopDrawing() {
        this.drawing = false;
        this.lastDrawX = -1;
        this.lastDrawY = -1;
    }

    /** Converts a canvas coordinate {@code (px, py)} into grid coordinates {@code {gx, gy}}, or {@code null}. */
    private int[] toGrid(double px, double py) {
        Rect content = effectivePadding().shrink(rect());
        if (content.width() <= 0 || content.height() <= 0 || !content.contains(px, py)) {
            return null;
        }
        int gx = Math.min(gridWidth - 1, Math.max(0, (int) (((px - content.x()) / content.width()) * gridWidth)));
        int gy = Math.min(gridHeight - 1, Math.max(0, (int) (((py - content.y()) / content.height()) * gridHeight)));
        return new int[] { gx, gy };
    }

    /** Called during {@link ScreenSession} ticks while the player is actively dragging the brush. */
    public boolean dragTo(double cursorX, double cursorY, Player player) {
        if (!drawing || !drawable) {
            return false;
        }
        int[] cell = toGrid(cursorX, cursorY);
        if (cell == null) {
            stopDrawing();
            return false;
        }
        int gx = cell[0];
        int gy = cell[1];
        if (gx == lastDrawX && gy == lastDrawY) {
            return false;
        }
        if (lastDrawX >= 0 && lastDrawY >= 0) {
            drawLine(lastDrawX, lastDrawY, gx, gy, brushSize, brushColor);
        } else {
            paintDab(gx, gy, brushSize, brushColor);
        }
        lastDrawX = gx;
        lastDrawY = gy;
        if (onDraw != null) {
            onDraw.onDraw(player, gx, gy, brushColor);
        }
        return true;
    }

    @Override
    boolean setHovered(boolean value, Player player) {
        boolean changed = super.setHovered(value, player);
        if (!value && drawing) {
            stopDrawing();
        }
        return changed;
    }

    @Override
    ClickHandler effectiveOnClick() {
        ClickHandler own = super.effectiveOnClick();
        if (!drawable) {
            return own;
        }
        return (player, px, py) -> {
            int[] cell = toGrid(px, py);
            if (cell != null) {
                int gx = cell[0];
                int gy = cell[1];
                boolean inSession = ScreenSession.isInSession(player);
                if (inSession) {
                    if (drawing) {
                        stopDrawing();
                    } else {
                        drawing = true;
                        lastDrawX = gx;
                        lastDrawY = gy;
                        paintDab(gx, gy, brushSize, brushColor);
                        if (onDraw != null) {
                            onDraw.onDraw(player, gx, gy, brushColor);
                        }
                    }
                } else {
                    paintDab(gx, gy, brushSize, brushColor);
                    if (onDraw != null) {
                        onDraw.onDraw(player, gx, gy, brushColor);
                    }
                }
            }
            if (own != null) {
                own.onClick(player, px, py);
            }
        };
    }

    // --- Layout & Greedy Meshing Renderer ----------------------------------------------------------------

    @Override
    protected Size measureContent(double maxWidth) {
        return new Size(gridWidth * 4.0, gridHeight * 4.0);
    }

    @Override
    protected double minContentWidth() {
        return gridWidth;
    }

    /**
     * Greedily merges contiguous same-colour pixels into minimal rectangles and syncs them to
     * {@link TextBackground} panels on the world surface.
     */
    void sync(dev.horuse.pixelay.data.Canvas surface, Player fallbackViewer, int baseBg, float depth) {
        Player effectiveViewer = viewer != null ? viewer : fallbackViewer;
        Rect content = effectivePadding().shrink(rect());
        Rect clipRect = childClip();
        if (content.width() <= 0 || content.height() <= 0 || (clipRect != null && clipRect.isEmpty())) {
            clearQuads();
            return;
        }

        double cellW = content.width() / gridWidth;
        double cellH = content.height() / gridHeight;
        boolean[] visited = new boolean[gridWidth * gridHeight];
        int usedQuads = 0;

        for (int y = 0; y < gridHeight; y++) {
            for (int x = 0; x < gridWidth; x++) {
                int idx = y * gridWidth + x;
                if (visited[idx]) {
                    continue;
                }
                int color = pixels[idx];
                if (color >>> 24 == 0) {
                    visited[idx] = true;
                    continue;
                }

                // Expand width along row
                int w = 1;
                while (x + w < gridWidth) {
                    int nextIdx = y * gridWidth + (x + w);
                    if (!visited[nextIdx] && pixels[nextIdx] == color) {
                        w++;
                    } else {
                        break;
                    }
                }

                // Expand height downwards while the full span [x, x + w) matches
                int h = 1;
                outer: while (y + h < gridHeight) {
                    int rowOffset = (y + h) * gridWidth;
                    for (int kx = x; kx < x + w; kx++) {
                        int checkIdx = rowOffset + kx;
                        if (visited[checkIdx] || pixels[checkIdx] != color) {
                            break outer;
                        }
                    }
                    h++;
                }

                // Mark merged cells visited
                for (int dy = 0; dy < h; dy++) {
                    int rowOffset = (y + dy) * gridWidth;
                    for (int dx = 0; dx < w; dx++) {
                        visited[rowOffset + x + dx] = true;
                    }
                }

                double rx0 = content.x() + x * cellW;
                double ry0 = content.y() + y * cellH;
                double rx1 = content.x() + (x + w) * cellW;
                double ry1 = content.y() + (y + h) * cellH;
                Rect quadBounds = new Rect(rx0, ry0, rx1 - rx0, ry1 - ry0);
                if (clipRect != null) {
                    quadBounds = quadBounds.intersect(clipRect);
                }
                if (quadBounds.isEmpty()) {
                    continue;
                }

                int composited = baseBg != 0 ? MountedLayout.composite(baseBg, color) : color;
                TextBackground panel;
                if (usedQuads < quadPool.size()) {
                    panel = quadPool.get(usedQuads);
                } else {
                    panel = new TextBackground(effectiveViewer);
                    quadPool.add(panel);
                }
                panel.sync(surface, quadBounds, composited, depth);
                usedQuads++;
            }
        }

        // Remove unused pooled panels from previous frames
        for (int i = quadPool.size() - 1; i >= usedQuads; i--) {
            quadPool.remove(i).remove();
        }
    }

    void clearQuads() {
        for (TextBackground panel : quadPool) {
            panel.remove();
        }
        quadPool.clear();
        stopDrawing();
    }
}
