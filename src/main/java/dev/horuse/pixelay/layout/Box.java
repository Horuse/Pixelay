package dev.horuse.pixelay.layout;

import dev.horuse.pixelay.components.CanvasComponent;
import dev.horuse.pixelay.components.LayoutComponent;
import dev.horuse.pixelay.data.Canvas;
import dev.horuse.pixelay.data.Rect;
import dev.horuse.pixelay.data.Size;
import dev.horuse.pixelay.debug.Fit;
import dev.horuse.pixelay.utils.ColorUtils;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.bukkit.entity.Player;

/**
 * A layout node with a CSS-like box model: optional fixed size, padding (inside the box), margin
 * (outside, may be negative), grow/shrink factors and self alignment. Layout runs in two passes:
 * {@link #measure(double)} computes the size that fits a width limit bottom-up (cached for one pass),
 * {@link #arrange(Rect)} assigns rectangles top-down. Layout runs on the main server thread.
 * <p>
 * May be instantiated directly ({@code new Box<>()} or {@link Layout#box()}) as an empty block element
 * (like an HTML {@code <div>} with no children, or a geometric panel/divider).
 *
 * @param <S> the concrete box type, so chained setters keep it: {@code column(a, b).padding(32).gap(8)}
 */
public class Box<S extends Box<S>> implements LayoutItem, CanvasComponent {

    /** Width limit meaning "no limit" (measure the natural, unwrapped size). */
    static final double UNBOUNDED = -1;

    private static final double OVERFLOW_EPSILON = 0.5;

    protected Insets padding = Insets.NONE;
    protected Insets margin = Insets.NONE;
    protected Insets borderWidths = Insets.NONE;
    protected int borderColor;
    protected double fixedWidth = -1;
    protected double fixedHeight = -1;
    protected double minWidth = -1;
    protected double maxWidth = -1;
    protected double minHeight = -1;
    protected double maxHeight = -1;
    protected double widthPercent = -1;
    protected double heightPercent = -1;
    protected double opacity = 1.0;
    private volatile boolean hidden;
    private BooleanSupplier hiddenWhen;
    protected double grow;
    protected double shrink = 1;
    protected Align alignSelf;
    protected Align justifySelf;
    /** Grid placement (CSS {@code grid-row}/{@code grid-column}): 1-based start line or 0 = auto, and span. */
    int gridRow;
    int gridColumn;
    int rowSpan = 1;
    int columnSpan = 1;
    /** CSS positioning scheme ({@code static}, {@code relative}, {@code absolute}) and edge offsets. */
    protected Position position = Position.STATIC;
    protected double left = Double.NaN;
    protected double top = Double.NaN;
    protected double right = Double.NaN;
    protected double bottom = Double.NaN;
    protected double translateX;
    protected double translateY;
    protected double translatePxX;
    protected double translatePxY;
    protected double scaleX = 1.0;
    protected double scaleY = 1.0;
    protected double rotateDeg;
    protected double rotateXDeg;
    protected double rotateYDeg;
    protected double transformOriginX = 0.5;
    protected double transformOriginY = 0.5;
    private double visualScaleX = 1.0;
    private double visualScaleY = 1.0;
    private double visualRotateDeg;
    private double visualRotateXDeg;
    private double visualRotateYDeg;
    protected int zIndex;
    private MountedLayout directMount;
    /** Result of the last arrange: does this box (or an ancestor) not fit its space. */
    protected Fit fit = Fit.NONE;
    private String id;

    @FunctionalInterface
    public interface ClickHandler {
        void onClick(Player player, double x, double y);
    }

    private ClickHandler onClick;
    private int background;
    /** Depth steps from the last layout pass (see {@link dev.horuse.pixelay.data.Layers}): first and last used. */
    int paintStep = 1;
    int paintEnd;
    private Overflow overflowX = Overflow.VISIBLE;
    private Overflow overflowY = Overflow.VISIBLE;
    /** CSS {@code cursor}: a sprite name of the screen cursor, or {@code null} to inherit it. */
    private String cursor;
    /** Where this box may draw and be pointed at (canvas px), from the last layout pass: its ancestors' clip. */
    private Rect clip;
    /** Styles of states, by {@link State} or custom name, in declaration order. */
    private final LinkedHashMap<Object, Style> stateStyles = new LinkedHashMap<>();
    private final Set<String> customStates = ConcurrentHashMap.newKeySet();
    private volatile boolean selected;
    /** The merged style of the active states; recomputed only after a state or a style changed. */
    private Style activeStyle;
    private volatile boolean styleStale = true;
    private volatile boolean disabled;
    private BooleanSupplier selectedWhen;
    private BooleanSupplier disabledWhen;
    private Consumer<Player> onHoverEnter;
    private Consumer<Player> onHoverLeave;
    private TransitionState backgroundTransition;
    private TransitionState borderTransition;
    private TransformTransitionState transformTransition;
    /** Under the pointer (itself or a descendant), from the last {@link MountedLayout#pointerAt} — CSS {@code :hover}. */
    private volatile boolean hovered;
    private Rect rect = new Rect(0, 0, 0, 0);

    // Two cached sizes per pass: the natural one and the last constrained one (parents ask both).
    private Size unbounded;
    private Size constrained;
    private double constrainedFor = Double.NaN;

    /**
     * Content size without padding that fits {@code maxWidth} when it is not {@link #UNBOUNDED}:
     * texts wrap to it, containers pass it on to their children (like CSS width constraints).
     * Must not change any state: arranging happens in {@link #arrangeContent}.
     */
    protected Size measureContent(double maxWidth) {
        return Size.ZERO;
    }

    /** Places the children inside the content rectangle (the box rectangle minus padding). */
    protected void arrangeContent(Rect content) {}

    /** Adds every canvas component of this subtree to {@code out}. */
    protected void collectComponents(List<LayoutComponent> out) {}

    /**
     * Narrowest content width (without padding) this box can take without breaking words or
     * squeezing fixed sizes — the floor for {@code shrink} (CSS {@code min-content}).
     */
    protected double minContentWidth() {
        return 0;
    }

    /** Semantic HTML-like tag name of this node for debugging, serialization, and the web editor. */
    public String tagName() {
        return "div";
    }

    /** The single player who sees this box's entities, or {@code null} when visible to everyone or inherited. */
    public Player viewer() {
        LayoutComponent comp = component();
        return comp != null ? comp.viewer() : null;
    }

    /** Binds this box's underlying component to packet-only rendering for {@code viewer} if unset. */
    void bindViewer(Player viewer) {}

    /** The component this box draws ({@link Text}, {@link Item}, {@link Block}), or {@code null} for other boxes. */
    LayoutComponent component() {
        return null;
    }

    /** Depth steps the box paints on (see {@link MountedLayout}): a background, border, or a component's layers. */
    int layerSteps() {
        return hasBorder() ? 2 : 1;
    }

    /** Adds every box of this subtree (self included, depth-first, parents before children). */
    protected void collectBoxes(List<Box<?>> out) {
        out.add(this);
    }

    /** Drops the cached sizes of this subtree, so the next pass measures changed texts again. */
    protected void invalidate() {
        unbounded = null;
        constrained = null;
        constrainedFor = Double.NaN;
    }

    /** Natural outer size (fixed size or content + padding), margin not included. */
    public final Size measure() {
        return measure(UNBOUNDED);
    }

    /** Outer size when at most {@code maxWidth} px wide is available; a fixed width overrides it. */
    public final Size measure(double maxWidth) {
        if (hidden) {
            return Size.ZERO;
        }
        double limit = fixedWidth >= 0 ? fixedWidth : maxWidth;
        if (widthPercent >= 0 && maxWidth >= 0 && fixedWidth < 0) {
            limit = maxWidth * widthPercent;
        }
        if (this.maxWidth >= 0) {
            limit = limit < 0 ? this.maxWidth : Math.min(limit, this.maxWidth);
        }
        if (limit < 0) {
            if (unbounded == null) {
                unbounded = compute(UNBOUNDED, UNBOUNDED);
            }
            return unbounded;
        }
        if (constrained == null || constrainedFor != limit) {
            constrained = compute(limit, maxWidth);
            constrainedFor = limit;
        }
        return constrained;
    }

    private Size compute(double limit, double parentAvailableWidth) {
        Insets padding = effectivePadding();
        Insets border = effectiveBorderWidths();
        double hExtra = padding.horizontal() + border.horizontal();
        double vExtra = padding.vertical() + border.vertical();
        double contentLimit = limit < 0 ? UNBOUNDED : Math.max(0, limit - hExtra);
        Size content = measureContent(contentLimit);

        double w;
        if (fixedWidth >= 0) {
            w = fixedWidth;
        } else if (widthPercent >= 0 && parentAvailableWidth >= 0) {
            w = parentAvailableWidth * widthPercent;
        } else {
            w = content.width() + hExtra;
        }
        if (minWidth >= 0) {
            w = Math.max(minWidth, w);
        }
        if (maxWidth >= 0) {
            w = Math.min(maxWidth, w);
        }

        double h;
        if (fixedHeight >= 0) {
            h = fixedHeight;
        } else {
            h = content.height() + vExtra;
        }
        if (minHeight >= 0) {
            h = Math.max(minHeight, h);
        }
        if (maxHeight >= 0) {
            h = Math.min(maxHeight, h);
        }

        return new Size(w, h);
    }

    /** Size plus margin when at most {@code maxWidth} px (margin included) is available. */
    Size outer(double maxWidth) {
        if (hidden) {
            return Size.ZERO;
        }
        Insets margin = effectiveMargin();
        Size size = measure(maxWidth < 0 ? UNBOUNDED : Math.max(0, maxWidth - margin.horizontal()));
        return new Size(size.width() + margin.horizontal(), size.height() + margin.vertical());
    }

    /** {@link #minContentWidth()} as the parent sees it: with padding, border, and margin, or the fixed/min width. */
    double minOuterWidth() {
        if (hidden) {
            return 0;
        }
        double hExtra = effectivePadding().horizontal() + effectiveBorderWidths().horizontal();
        double width = fixedWidth >= 0 ? fixedWidth : minContentWidth() + hExtra;
        if (minWidth >= 0) {
            width = Math.max(minWidth, width);
        }
        if (maxWidth >= 0) {
            width = Math.min(maxWidth, width);
        }
        return width + effectiveMargin().horizontal();
    }

    public final void arrange(Rect rect) {
        arrange(rect, false);
    }

    /** @param insideOverflow an ancestor already does not fit its space */
    final void arrange(Rect rect, boolean insideOverflow) {
        arrange(rect, insideOverflow, 1.0, 1.0, 0.0, 0.0, 0.0);
    }

    /** Arranges {@code child} inside this container, propagating this container's visual scale and rotation. */
    final void arrangeChild(Box<?> child, Rect childRect, boolean insideOverflow) {
        child.arrange(
            childRect,
            insideOverflow,
            this.visualScaleX,
            this.visualScaleY,
            this.visualRotateDeg,
            this.visualRotateXDeg,
            this.visualRotateYDeg
        );
    }

    final void arrange(
        Rect rect,
        boolean insideOverflow,
        double inheritedScaleX,
        double inheritedScaleY,
        double inheritedRotateDeg,
        double inheritedRotateXDeg,
        double inheritedRotateYDeg
    ) {
        if (hidden) {
            this.rect = new Rect(rect.x(), rect.y(), 0, 0);
            this.fit = Fit.NONE;
            this.visualScaleX = 1.0;
            this.visualScaleY = 1.0;
            this.visualRotateDeg = 0.0;
            this.visualRotateXDeg = 0.0;
            this.visualRotateYDeg = 0.0;
            arrangeContent(this.rect);
            arrangeAbsoluteChildren(this.rect);
            return;
        }
        Transform tf = effectiveTransform();
        double sx = tf.scaleX();
        double sy = tf.scaleY();
        this.visualScaleX = inheritedScaleX * sx;
        this.visualScaleY = inheritedScaleY * sy;
        this.visualRotateDeg = inheritedRotateDeg + tf.rotateDeg();
        this.visualRotateXDeg = inheritedRotateXDeg + tf.rotateXDeg();
        this.visualRotateYDeg = inheritedRotateYDeg + tf.rotateYDeg();

        double w = rect.width();
        double h = rect.height();
        double dx = tf.translateX() * w + tf.translatePxX();
        double dy = tf.translateY() * h + tf.translatePxY();
        if (position == Position.RELATIVE) {
            dx += !Double.isNaN(left) ? left : !Double.isNaN(right) ? -right : 0;
            dy += !Double.isNaN(top) ? top : !Double.isNaN(bottom) ? -bottom : 0;
        }
        double x = rect.x() + dx;
        double y = rect.y() + dy;
        if (Math.abs(sx - 1.0) > 1e-4 || Math.abs(sy - 1.0) > 1e-4) {
            double newW = Math.max(0, w * sx);
            double newH = Math.max(0, h * sy);
            x += (w - newW) * tf.originX();
            y += (h - newH) * tf.originY();
            w = newW;
            h = newH;
        }
        rect = new Rect(x, y, w, h);
        this.rect = rect;
        Size natural = measure(rect.width());
        boolean selfOverflow =
            Math.abs(sx - 1.0) <= 1e-4 &&
            Math.abs(sy - 1.0) <= 1e-4 &&
            (natural.width() > rect.width() + OVERFLOW_EPSILON || natural.height() > rect.height() + OVERFLOW_EPSILON);
        fit = selfOverflow ? Fit.SELF : insideOverflow ? Fit.INSIDE : Fit.NONE;
        Insets b = effectiveBorderWidths();
        if (Math.abs(sx - 1.0) > 1e-4 || Math.abs(sy - 1.0) > 1e-4) {
            b = new Insets(b.top() * sy, b.right() * sx, b.bottom() * sy, b.left() * sx);
        }
        Rect inner = b.shrink(rect);
        Insets p = effectivePadding();
        if (Math.abs(sx - 1.0) > 1e-4 || Math.abs(sy - 1.0) > 1e-4) {
            p = new Insets(p.top() * sy, p.right() * sx, p.bottom() * sy, p.left() * sx);
        }
        arrangeContent(p.shrink(inner));
        arrangeAbsoluteChildren(inner);
    }

    double visualScaleX() {
        return visualScaleX;
    }

    double visualScaleY() {
        return visualScaleY;
    }

    double visualRotateDeg() {
        return visualRotateDeg;
    }

    double visualRotateXDeg() {
        return visualRotateXDeg;
    }

    double visualRotateYDeg() {
        return visualRotateYDeg;
    }

    /** Hook overridden by {@link Container} to arrange any {@link Position#ABSOLUTE} children inside {@code bounds}. */
    void arrangeAbsoluteChildren(Rect bounds) {}

    /** Whether the last layout pass found this box bigger than its space, or inside one that is. */
    public Fit fit() {
        return fit;
    }

    /** For children: does anything at or above this box overflow. */
    boolean overflowsHere() {
        return fit != Fit.NONE;
    }

    /** The rectangle assigned by the last layout pass. */
    public Rect rect() {
        return rect;
    }

    public String id() {
        return id;
    }

    public ClickHandler onClick() {
        return onClick;
    }

    boolean hasFixedWidth() {
        return fixedWidth >= 0 || widthPercent >= 0;
    }

    boolean hasFixedHeight() {
        return fixedHeight >= 0 || heightPercent >= 0;
    }

    @SuppressWarnings("unchecked")
    protected final S self() {
        return (S) this;
    }

    /** CSS {@code border}: sets uniform border width and colour (ARGB) on all four sides. */
    public S border(double widthPx, int argb) {
        this.borderWidths = Insets.all(Math.max(0, widthPx));
        this.borderColor = argb;
        return self();
    }

    /** CSS {@code border}: sets uniform border width and CSS colour string (e.g. {@code "#00E5FF"}). */
    public S border(double widthPx, String cssColor) {
        return border(widthPx, ColorUtils.parse(cssColor));
    }

    /** Sets per-side border widths and uniform border colour (ARGB). */
    public S border(Insets widths, int argb) {
        this.borderWidths = widths != null ? widths : Insets.NONE;
        this.borderColor = argb;
        return self();
    }

    /** Sets per-side border widths and CSS colour string. */
    public S border(Insets widths, String cssColor) {
        return border(widths, ColorUtils.parse(cssColor));
    }

    /** CSS {@code border-top}. */
    public S borderTop(double widthPx, int argb) {
        this.borderWidths = new Insets(
            Math.max(0, widthPx),
            borderWidths.right(),
            borderWidths.bottom(),
            borderWidths.left()
        );
        this.borderColor = argb;
        return self();
    }

    public S borderTop(double widthPx, String cssColor) {
        return borderTop(widthPx, ColorUtils.parse(cssColor));
    }

    /** CSS {@code border-right}. */
    public S borderRight(double widthPx, int argb) {
        this.borderWidths = new Insets(
            borderWidths.top(),
            Math.max(0, widthPx),
            borderWidths.bottom(),
            borderWidths.left()
        );
        this.borderColor = argb;
        return self();
    }

    public S borderRight(double widthPx, String cssColor) {
        return borderRight(widthPx, ColorUtils.parse(cssColor));
    }

    /** CSS {@code border-bottom}. */
    public S borderBottom(double widthPx, int argb) {
        this.borderWidths = new Insets(
            borderWidths.top(),
            borderWidths.right(),
            Math.max(0, widthPx),
            borderWidths.left()
        );
        this.borderColor = argb;
        return self();
    }

    public S borderBottom(double widthPx, String cssColor) {
        return borderBottom(widthPx, ColorUtils.parse(cssColor));
    }

    /** CSS {@code border-left}. */
    public S borderLeft(double widthPx, int argb) {
        this.borderWidths = new Insets(
            borderWidths.top(),
            borderWidths.right(),
            borderWidths.bottom(),
            Math.max(0, widthPx)
        );
        this.borderColor = argb;
        return self();
    }

    public S borderLeft(double widthPx, String cssColor) {
        return borderLeft(widthPx, ColorUtils.parse(cssColor));
    }

    /** CSS {@code border-color} (ARGB). */
    public S borderColor(int argb) {
        this.borderColor = argb;
        return self();
    }

    /** CSS {@code border-color} from string (e.g. {@code "#FFD166"}). */
    public S borderColor(String cssColor) {
        return borderColor(ColorUtils.parse(cssColor));
    }

    public Insets borderWidths() {
        return borderWidths;
    }

    public int borderColor() {
        return borderColor;
    }

    boolean hasBorder() {
        Insets b = effectiveBorderWidths();
        return (
            (b.top() > 0 || b.right() > 0 || b.bottom() > 0 || b.left() > 0) &&
            (borderColor != 0 ||
                stateStyles
                    .values()
                    .stream()
                    .anyMatch(s -> s.borderColor() != null))
        );
    }

    public S padding(double all) {
        return padding(Insets.all(all));
    }

    public S padding(double vertical, double horizontal) {
        return padding(Insets.of(vertical, horizontal));
    }

    public S padding(double top, double right, double bottom, double left) {
        return padding(new Insets(top, right, bottom, left));
    }

    public S padding(Insets padding) {
        this.padding = padding != null ? padding : Insets.NONE;
        return self();
    }

    public Insets padding() {
        return padding;
    }

    public S margin(double all) {
        return margin(Insets.all(all));
    }

    public S margin(double vertical, double horizontal) {
        return margin(Insets.of(vertical, horizontal));
    }

    public S margin(double top, double right, double bottom, double left) {
        return margin(new Insets(top, right, bottom, left));
    }

    public S margin(Insets margin) {
        this.margin = margin != null ? margin : Insets.NONE;
        return self();
    }

    public Insets margin() {
        return margin;
    }

    public S width(double width) {
        this.fixedWidth = width;
        return self();
    }

    public double fixedWidth() {
        return fixedWidth;
    }

    /** CSS percentage width ({@code 0.0..1.0}, e.g. {@code 0.5} for {@code width: 50%}) relative to the parent's content width. */
    public S widthPercent(double fraction) {
        this.widthPercent = fraction;
        return self();
    }

    public double widthPercent() {
        return widthPercent;
    }

    /** CSS {@code min-width} in canvas pixels. */
    public S minWidth(double minWidth) {
        this.minWidth = minWidth;
        return self();
    }

    public double minWidth() {
        return minWidth;
    }

    /** CSS {@code max-width} in canvas pixels. */
    public S maxWidth(double maxWidth) {
        this.maxWidth = maxWidth;
        return self();
    }

    public double maxWidth() {
        return maxWidth;
    }

    public S height(double height) {
        this.fixedHeight = height;
        return self();
    }

    public double fixedHeight() {
        return fixedHeight;
    }

    /** CSS percentage height ({@code 0.0..1.0}, e.g. {@code 1.0} for {@code height: 100%}) relative to the parent's content height. */
    public S heightPercent(double fraction) {
        this.heightPercent = fraction;
        return self();
    }

    public double heightPercent() {
        return heightPercent;
    }

    /** CSS {@code min-height} in canvas pixels. */
    public S minHeight(double minHeight) {
        this.minHeight = minHeight;
        return self();
    }

    public double minHeight() {
        return minHeight;
    }

    /** CSS {@code max-height} in canvas pixels. */
    public S maxHeight(double maxHeight) {
        this.maxHeight = maxHeight;
        return self();
    }

    public double maxHeight() {
        return maxHeight;
    }

    public S size(double width, double height) {
        return width(width).height(height);
    }

    /** CSS {@code min-width} and {@code min-height} shorthand. */
    public S minSize(double minWidth, double minHeight) {
        return minWidth(minWidth).minHeight(minHeight);
    }

    /** CSS {@code max-width} and {@code max-height} shorthand. */
    public S maxSize(double maxWidth, double maxHeight) {
        return maxWidth(maxWidth).maxHeight(maxHeight);
    }

    /**
     * Share of the free space along the parent's main axis, added to the natural size (CSS
     * {@code flex-grow} with {@code flex-basis: auto}).
     */
    public S grow(double grow) {
        this.grow = grow;
        return self();
    }

    public double grow() {
        return grow;
    }

    /**
     * Share of the missing space a row takes from this box when its children do not fit (CSS
     * {@code flex-shrink}, default 1). Never below {@link #minContentWidth()}: texts wrap, words stay whole.
     */
    public S shrink(double shrink) {
        this.shrink = Math.max(0, shrink);
        return self();
    }

    public double shrink() {
        return shrink;
    }

    /**
     * Vertical placement in the area given by the parent (CSS {@code align-self}); in a row or a
     * column it applies to the cross axis. A leaf also pins its text by it inside its rectangle.
     */
    public S alignSelf(Align align) {
        this.alignSelf = align;
        return self();
    }

    public Align alignSelf() {
        return alignSelf;
    }

    /**
     * Horizontal placement in a grid or stack area (CSS {@code justify-self}). In a row or a column it
     * does not move the box (as in CSS flexbox); a leaf still uses it to pin its text inside its rectangle.
     */
    public S justifySelf(Align align) {
        this.justifySelf = align;
        return self();
    }

    public Align justifySelf() {
        return justifySelf;
    }

    /** Rows this item covers in a grid (CSS {@code grid-row: span n}). */
    public S rowSpan(int span) {
        this.rowSpan = Math.max(1, span);
        return self();
    }

    public int rowSpan() {
        return rowSpan;
    }

    /** Columns this item covers in a grid (CSS {@code grid-column: span n}). */
    public S columnSpan(int span) {
        this.columnSpan = Math.max(1, span);
        return self();
    }

    public int columnSpan() {
        return columnSpan;
    }

    /** Explicit start row line in a grid, 1-based like CSS {@code grid-row-start}; auto-placed otherwise. */
    public S gridRow(int line) {
        this.gridRow = Math.max(0, line);
        return self();
    }

    public int gridRow() {
        return gridRow;
    }

    /** Explicit start column line in a grid, 1-based like CSS {@code grid-column-start}; auto-placed otherwise. */
    public S gridColumn(int line) {
        this.gridColumn = Math.max(0, line);
        return self();
    }

    public int gridColumn() {
        return gridColumn;
    }

    /** CSS {@code position}: {@link Position#STATIC} (default), {@link Position#RELATIVE}, or {@link Position#ABSOLUTE}. */
    public S position(Position position) {
        this.position = position != null ? position : Position.STATIC;
        return self();
    }

    public Position position() {
        return position;
    }

    public boolean isAbsolute() {
        return position == Position.ABSOLUTE;
    }

    /** Shorthand for {@code position(Position.ABSOLUTE).left(left).top(top)}. */
    public S absolute(double left, double top) {
        this.position = Position.ABSOLUTE;
        this.left = left;
        this.top = top;
        return self();
    }

    /** CSS {@code left} offset in canvas px (used when {@code position} is {@code ABSOLUTE} or {@code RELATIVE}). */
    public S left(double px) {
        this.left = px;
        return self();
    }

    public double left() {
        return left;
    }

    /** CSS {@code top} offset in canvas px (used when {@code position} is {@code ABSOLUTE} or {@code RELATIVE}). */
    public S top(double px) {
        this.top = px;
        return self();
    }

    public double top() {
        return top;
    }

    /** CSS {@code right} offset in canvas px (used when {@code position} is {@code ABSOLUTE} or {@code RELATIVE}). */
    public S right(double px) {
        this.right = px;
        return self();
    }

    public double right() {
        return right;
    }

    /** CSS {@code bottom} offset in canvas px (used when {@code position} is {@code ABSOLUTE} or {@code RELATIVE}). */
    public S bottom(double px) {
        this.bottom = px;
        return self();
    }

    public double bottom() {
        return bottom;
    }

    /** CSS {@code inset} shorthand setting {@code top}, {@code right}, {@code bottom}, and {@code left}. */
    public S inset(double all) {
        return inset(all, all, all, all);
    }

    /** CSS {@code inset} shorthand setting vertical ({@code top}/{@code bottom}) and horizontal ({@code left}/{@code right}) offsets. */
    public S inset(double vertical, double horizontal) {
        return inset(vertical, horizontal, vertical, horizontal);
    }

    /** CSS {@code inset} shorthand setting {@code top}, {@code right}, {@code bottom}, and {@code left}. */
    public S inset(double top, double right, double bottom, double left) {
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        this.left = left;
        return self();
    }

    /**
     * CSS {@code transform: translate(percentX, percentY)} relative to this box's own width and height
     * (e.g. {@code translate(-0.5, -0.5)} shifts by {@code -50%} on both axes to center on {@code left}/{@code top}).
     */
    public S translate(double percentX, double percentY) {
        this.translateX = percentX;
        this.translateY = percentY;
        return self();
    }

    public double translateX() {
        return translateX;
    }

    public double translateY() {
        return translateY;
    }

    /** CSS {@code transform: translate(dxPx, dyPx)} offset in canvas pixels. */
    public S translatePx(double dxPx, double dyPx) {
        this.translatePxX = dxPx;
        this.translatePxY = dyPx;
        return self();
    }

    public S translateX(double dxPx) {
        this.translatePxX = dxPx;
        return self();
    }

    public S translateY(double dyPx) {
        this.translatePxY = dyPx;
        return self();
    }

    public double translatePxX() {
        return translatePxX;
    }

    public double translatePxY() {
        return translatePxY;
    }

    /** CSS {@code transform: scale(factor)} (uniform X and Y scale around {@link #transformOrigin}). */
    public S scale(double factor) {
        return scale(factor, factor);
    }

    /** CSS {@code transform: scale(sx, sy)} around {@link #transformOrigin}. */
    public S scale(double sx, double sy) {
        this.scaleX = sx;
        this.scaleY = sy;
        return self();
    }

    public S scaleX(double sx) {
        this.scaleX = sx;
        return self();
    }

    public S scaleY(double sy) {
        this.scaleY = sy;
        return self();
    }

    public double scaleX() {
        return scaleX;
    }

    public double scaleY() {
        return scaleY;
    }

    /** CSS {@code transform: rotate(deg)} (clockwise rotation in degrees on the 2D canvas plane). */
    public S rotate(double degrees) {
        this.rotateDeg = degrees;
        return self();
    }

    public double rotateDeg() {
        return rotateDeg;
    }

    /** CSS {@code transform: rotateX(deg)} (3D pitch tilt in degrees). */
    public S rotateX(double degrees) {
        this.rotateXDeg = degrees;
        return self();
    }

    public double rotateXDeg() {
        return rotateXDeg;
    }

    /** CSS {@code transform: rotateY(deg)} (3D yaw turn in degrees). */
    public S rotateY(double degrees) {
        this.rotateYDeg = degrees;
        return self();
    }

    public double rotateYDeg() {
        return rotateYDeg;
    }

    /** CSS {@code transform-origin: ox oy} ({@code 0.0..1.0}, default {@code 0.5, 0.5} center). */
    public S transformOrigin(double ox, double oy) {
        this.transformOriginX = ox;
        this.transformOriginY = oy;
        return self();
    }

    public double transformOriginX() {
        return transformOriginX;
    }

    public double transformOriginY() {
        return transformOriginY;
    }

    /** Sets the full CSS {@link Transform} on this box. */
    public S transform(Transform t) {
        if (t == null) {
            t = Transform.NONE;
        }
        this.translateX = t.translateX();
        this.translateY = t.translateY();
        this.translatePxX = t.translatePxX();
        this.translatePxY = t.translatePxY();
        this.scaleX = t.scaleX();
        this.scaleY = t.scaleY();
        this.rotateDeg = t.rotateDeg();
        this.rotateXDeg = t.rotateXDeg();
        this.rotateYDeg = t.rotateYDeg();
        this.transformOriginX = t.originX();
        this.transformOriginY = t.originY();
        return self();
    }

    public Transform transform() {
        return new Transform(
            translateX,
            translateY,
            translatePxX,
            translatePxY,
            scaleX,
            scaleY,
            rotateDeg,
            rotateXDeg,
            rotateYDeg,
            transformOriginX,
            transformOriginY
        );
    }

    /** CSS {@code z-index}: controls stacking order for both depth layers and pointer hit-testing (default 0). */
    public S zIndex(int zIndex) {
        this.zIndex = zIndex;
        return self();
    }

    public int zIndex() {
        return zIndex;
    }

    /** Tag for hit testing ({@link MountedLayout#idAt}), e.g. to highlight a hovered card. */
    public S id(String id) {
        this.id = id;
        return self();
    }

    /**
     * Fills the box (padding included, margin not) with a colour, {@code #AARRGGBB} as an int, like CSS
     * {@code background-color}. Nested boxes are drawn above their parents, all of them under the text.
     */
    public S background(int argb) {
        this.background = argb;
        return self();
    }

    /**
     * Fills the box with a colour parsed from a CSS string (e.g. {@code "#0B1426"}, {@code "#FF335580"},
     * {@code "rgba(11, 20, 38, 0.85)"}, or {@code "transparent"}).
     */
    public S background(String cssColor) {
        return background(ColorUtils.parse(cssColor));
    }

    public int background() {
        return background;
    }

    /** CSS {@code opacity} ({@code 0.0..1.0}) applied to this box's background and border. */
    public S opacity(double opacity) {
        this.opacity = Math.clamp(opacity, 0.0, 1.0);
        return self();
    }

    public double opacity() {
        return opacity;
    }

    double effectiveOpacity() {
        Style style = activeStyle();
        return style != null && style.opacity() != null ? style.opacity() : opacity;
    }

    /**
     * CSS {@code display: none} — when {@code true}, removes this box and its subtree from layout flow
     * and rendering completely.
     */
    public S hidden(boolean hidden) {
        this.hidden = hidden;
        this.hiddenWhen = null;
        return self();
    }

    /** Dynamically hides this box (CSS {@code display: none}) whenever {@code condition} returns {@code true}. */
    public S hidden(BooleanSupplier condition) {
        this.hiddenWhen = condition;
        if (condition != null) {
            this.hidden = condition.getAsBoolean();
        }
        return self();
    }

    public boolean isHidden() {
        return hidden;
    }

    /**
     * CSS {@code overflow} on both axes: with {@link Overflow#HIDDEN} the children are cut at this box's edges
     * (padding included) and can be hovered and clicked only inside it.
     */
    public S overflow(Overflow overflow) {
        return overflowX(overflow).overflowY(overflow);
    }

    /** CSS {@code overflow-x}: cut the children at the left and right edges. */
    public S overflowX(Overflow overflow) {
        this.overflowX = overflow;
        return self();
    }

    public Overflow overflowX() {
        return overflowX;
    }

    /** CSS {@code overflow-y}: cut the children at the top and bottom edges. */
    public S overflowY(Overflow overflow) {
        this.overflowY = overflow;
        return self();
    }

    public Overflow overflowY() {
        return overflowY;
    }

    /**
     * CSS {@code cursor}: the sprite the screen cursor shows over this box and the boxes inside it that set none,
     * e.g. {@code "pointer"} or {@code "text"} (names from {@code generated-pack.cursor.sprites} in config.yml).
     * Without one, the cursor is {@code "pointer"} over clickable boxes and {@code "default"} elsewhere.
     */
    public S cursor(String name) {
        this.cursor = name;
        return self();
    }

    public String cursor() {
        return cursor;
    }

    /** The cursor set on this box now (by the active states or the box), or {@code null}. */
    String effectiveCursor() {
        Style style = activeStyle();
        return style != null && style.cursor() != null ? style.cursor() : cursor;
    }

    /** Where this box may draw, from the last layout pass (the canvas when nothing around it clips). */
    Rect clip() {
        return clip;
    }

    void clip(Rect clip) {
        this.clip = clip;
    }

    /** Where the children may draw: this box's clip, cut at its own edges on the axes it hides. */
    Rect childClip() {
        Rect own = rect;
        double left = overflowX.clips() ? Math.max(clip.x(), own.x()) : clip.x();
        double right = overflowX.clips() ? Math.min(clip.right(), own.right()) : clip.right();
        double top = overflowY.clips() ? Math.max(clip.y(), own.y()) : clip.y();
        double bottom = overflowY.clips() ? Math.min(clip.bottom(), own.bottom()) : clip.bottom();
        return new Rect(left, top, Math.max(0, right - left), Math.max(0, bottom - top));
    }

    /**
     * The look of the box in a state, like a CSS rule for a pseudo-class:
     * {@code state(State.HOVERED, style -> style.background(0x40FFFFFF).text("<yellow>Play"))}.
     */
    public S state(State state, Consumer<Style> rule) {
        rule.accept(stateStyles.computeIfAbsent(state, key -> new Style()));
        styleStale = true;
        return self();
    }

    /** The look of the box in a custom state (switched with {@link #toggle(String, boolean)}). */
    public S state(String name, Consumer<Style> rule) {
        rule.accept(stateStyles.computeIfAbsent(name, key -> new Style()));
        styleStale = true;
        return self();
    }

    /** Read-only view of all state style rules registered on this box. */
    public Map<Object, Style> stateStyles() {
        return Collections.unmodifiableMap(stateStyles);
    }

    /** Read-only view of active custom state names on this box. */
    public Set<String> customStates() {
        return Collections.unmodifiableSet(customStates);
    }

    /** Switches a custom state on or off, like {@code classList.toggle(name, on)}. */
    public S toggle(String name, boolean on) {
        if (on ? customStates.add(name) : customStates.remove(name)) {
            styleStale = true;
        }
        return self();
    }

    public S selected(boolean value) {
        this.selected = value;
        this.selectedWhen = null;
        styleStale = true;
        return self();
    }

    /** Selected while the condition holds; checked on every refresh of the layout. */
    public S selected(BooleanSupplier condition) {
        this.selectedWhen = condition;
        return self();
    }

    /** A disabled box swallows clicks (nothing runs, not even a parent's handler) and shows its disabled style. */
    public S disabled(boolean value) {
        this.disabled = value;
        this.disabledWhen = null;
        styleStale = true;
        return self();
    }

    /** Disabled while the condition holds; checked on every refresh of the layout. */
    public S disabled(BooleanSupplier condition) {
        this.disabledWhen = condition;
        return self();
    }

    public boolean is(State state) {
        return switch (state) {
            case DISABLED -> disabled;
            case HOVERED -> hovered;
            case SELECTED -> selected;
        };
    }

    public boolean is(String customState) {
        return customStates.contains(customState);
    }

    /** Re-reads the conditional states (see {@link #selected(BooleanSupplier)} and {@link #hidden(BooleanSupplier)}). */
    void updateStates() {
        if (hiddenWhen != null && hidden != hiddenWhen.getAsBoolean()) {
            hidden = !hidden;
            invalidate();
        }
        if (selectedWhen != null && selected != selectedWhen.getAsBoolean()) {
            selected = !selected;
            styleStale = true;
        }
        if (disabledWhen != null && disabled != disabledWhen.getAsBoolean()) {
            disabled = !disabled;
            styleStale = true;
        }
    }

    /**
     * The style of the active states, or {@code null}. Like CSS rules of the same specificity, a style declared
     * later wins over an earlier one, property by property.
     */
    Style activeStyle() {
        if (styleStale) {
            activeStyle = mergeActiveStyles();
            styleStale = false;
        }
        return activeStyle;
    }

    private Style mergeActiveStyles() {
        Style merged = new Style();
        boolean any = false;
        for (Map.Entry<Object, Style> entry : stateStyles.sequencedEntrySet().reversed()) {
            boolean on =
                entry.getKey() instanceof State state ? is(state) : customStates.contains((String) entry.getKey());
            if (on) {
                merged.fillFrom(entry.getValue());
                any = true;
            }
        }
        return any ? merged : null;
    }

    /** Padding now: from the active states, if they set one. */
    Insets effectivePadding() {
        Style style = activeStyle();
        return style != null && style.padding() != null ? style.padding() : padding;
    }

    /** Margin now: from the active states, if they set one. */
    Insets effectiveMargin() {
        Style style = activeStyle();
        return style != null && style.margin() != null ? style.margin() : margin;
    }

    /** Border widths now: from the active states, if they set one. */
    Insets effectiveBorderWidths() {
        Style style = activeStyle();
        return style != null && style.borderWidths() != null ? style.borderWidths() : borderWidths;
    }

    /** Runs when the pointer enters the box (or a box inside it), with the pointing player. */
    public S onHoverEnter(Consumer<Player> handler) {
        this.onHoverEnter = handler;
        return self();
    }

    /** Runs when the pointer leaves the box, with the pointing player. */
    public S onHoverLeave(Consumer<Player> handler) {
        this.onHoverLeave = handler;
        return self();
    }

    /**
     * Configures CSS-style property transitions (e.g. background, border color, or transform smoothing on hover).
     */
    public S transition(Transition... transitions) {
        for (Transition t : transitions) {
            if (t.property() == Transition.Property.ALL || t.property() == Transition.Property.BACKGROUND) {
                this.backgroundTransition = new TransitionState(t);
                this.borderTransition = new TransitionState(t);
            }
            if (t.property() == Transition.Property.ALL || t.property() == Transition.Property.TRANSFORM) {
                this.transformTransition = new TransformTransitionState(t);
            }
        }
        return self();
    }

    /**
     * Configures a smooth transition for all properties with the given duration in milliseconds and easing timing function.
     */
    public S transition(long durationMs, Easing easing) {
        return transition(Transition.all(durationMs, easing));
    }

    /**
     * Configures a smooth transition for all properties with the given duration in milliseconds and standard {@link Easing#EASE}.
     */
    public S transition(long durationMs) {
        return transition(Transition.all(durationMs, Easing.EASE));
    }

    /**
     * Configures a smooth transition for a specific property.
     */
    public S transition(Transition.Property property, long durationMs, Easing easing) {
        return transition(new Transition(property, durationMs, easing));
    }

    boolean hasTransitions() {
        return backgroundTransition != null || borderTransition != null || transformTransition != null;
    }

    boolean isTransitioning() {
        return (
            (backgroundTransition != null && backgroundTransition.isAnimating()) ||
            (borderTransition != null && borderTransition.isAnimating()) ||
            (transformTransition != null && transformTransition.isAnimating())
        );
    }

    boolean tickTransitions(long now) {
        boolean animating = false;
        if (backgroundTransition != null) {
            effectiveBackground(now);
            animating |= backgroundTransition.isAnimating();
        }
        if (borderTransition != null) {
            effectiveBorderColor(now);
            animating |= borderTransition.isAnimating();
        }
        if (transformTransition != null) {
            effectiveTransform(now);
            animating |= transformTransition.isAnimating();
        }
        return animating;
    }

    Transform effectiveTransform() {
        return effectiveTransform(System.currentTimeMillis());
    }

    Transform effectiveTransform(long now) {
        Style style = activeStyle();
        Transform target = new Transform(
            style != null && style.translateX() != null ? style.translateX() : translateX,
            style != null && style.translateY() != null ? style.translateY() : translateY,
            style != null && style.translatePxX() != null ? style.translatePxX() : translatePxX,
            style != null && style.translatePxY() != null ? style.translatePxY() : translatePxY,
            style != null && style.scaleX() != null ? style.scaleX() : scaleX,
            style != null && style.scaleY() != null ? style.scaleY() : scaleY,
            style != null && style.rotateDeg() != null ? style.rotateDeg() : rotateDeg,
            style != null && style.rotateXDeg() != null ? style.rotateXDeg() : rotateXDeg,
            style != null && style.rotateYDeg() != null ? style.rotateYDeg() : rotateYDeg,
            style != null && style.originX() != null ? style.originX() : transformOriginX,
            style != null && style.originY() != null ? style.originY() : transformOriginY
        );
        if (transformTransition != null) {
            return transformTransition.update(target, now);
        }
        return target;
    }

    /** The background to draw now: from the active states, if they set one. */
    int effectiveBackground() {
        return effectiveBackground(System.currentTimeMillis());
    }

    int effectiveBackground(long now) {
        if (hidden) {
            return 0;
        }
        Style style = activeStyle();
        int raw = style != null && style.background() != null ? style.background() : background;
        int target = ColorUtils.withOpacity(raw, effectiveOpacity());
        if (backgroundTransition != null) {
            return backgroundTransition.update(target, now);
        }
        return target;
    }

    /** The border colour to draw now: from the active states, if they set one. */
    int effectiveBorderColor() {
        return effectiveBorderColor(System.currentTimeMillis());
    }

    int effectiveBorderColor(long now) {
        if (hidden) {
            return 0;
        }
        Style style = activeStyle();
        int raw = style != null && style.borderColor() != null ? style.borderColor() : borderColor;
        int target = ColorUtils.withOpacity(raw, effectiveOpacity());
        if (borderTransition != null) {
            return borderTransition.update(target, now);
        }
        return target;
    }

    /** @return whether the state changed (the enter/leave handler has run then) */
    boolean setHovered(boolean value, Player player) {
        if (hovered == value) {
            return false;
        }
        hovered = value;
        styleStale = true;
        Consumer<Player> handler = value ? onHoverEnter : onHoverLeave;
        if (handler != null && player != null) {
            handler.accept(player);
        }
        return true;
    }

    /** What a click on this box runs: its own {@link #onClick}, or {@code null}. */
    ClickHandler effectiveOnClick() {
        return onClick;
    }

    /** Makes the whole box clickable via {@link MountedLayout#click} with pixel coordinates. */
    public S onClick(ClickHandler onClick) {
        this.onClick = onClick;
        return self();
    }

    /** Makes the whole box clickable via {@link MountedLayout#click}. */
    public S onClick(Consumer<Player> onClick) {
        this.onClick = onClick != null ? (player, x, y) -> onClick.accept(player) : null;
        return self();
    }

    // --- Direct CanvasComponent Lifecycle ----------------------------------------------------------------

    @Override
    public void spawn(Canvas canvas) {
        if (directMount != null) {
            directMount.despawn();
        }
        directMount = new MountedLayout(this);
        directMount.spawn(canvas);
    }

    @Override
    public void despawn() {
        if (directMount != null) {
            directMount.despawn();
            directMount = null;
        }
    }

    @Override
    public boolean isValid() {
        return directMount != null && directMount.isValid();
    }

    @Override
    public void refresh() {
        if (directMount != null) {
            directMount.refresh();
        }
    }
}
