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
 * A layout node with a box model like CSS.
 * It supports fixed size, padding, margin, grow and shrink factors, and self alignment.
 * Layout runs in two steps.
 * {@link #measure(double)} calculates the size bottom-up and caches it for one pass.
 * {@link #arrange(Rect)} sets rectangles top-down.
 * Layout runs on the main server thread.
 * <p>
 * You can instantiate this class directly ({@code new Box<>()} or {@link Layout#box()}) as an empty block element.
 *
 * @param <S> the concrete box type for chained setter methods
 */
public class Box<S extends Box<S>> implements LayoutItem, CanvasComponent {

    /** Width limit that means no limit. Measures the natural, unwrapped size. */
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
    /** Grid placement (CSS {@code grid-row} and {@code grid-column}). 1-based start line or 0 for auto, and span. */
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
    private double visualPivotX;
    private double visualPivotY;
    private int visualTransformationDurationTicks;
    private Transform lastTargetTransform;
    private long clientTransformUntilTime;
    protected int zIndex;
    private MountedLayout directMount;
    /** Result of the last arrange step. Shows if this box or an ancestor does not fit its space. */
    protected Fit fit = Fit.NONE;
    private String id;

    @FunctionalInterface
    public interface ClickHandler {
        void onClick(Player player, double x, double y);
    }

    private ClickHandler onClick;
    private int background;
    /** Depth steps from the last layout pass. See {@link dev.horuse.pixelay.data.Layers}. First and last used. */
    int paintStep = 1;
    int paintEnd;
    private Overflow overflowX = Overflow.VISIBLE;
    private Overflow overflowY = Overflow.VISIBLE;
    /** CSS {@code cursor}: sprite name of the screen cursor, or {@code null} to inherit it. */
    private String cursor;
    /** The clip rectangle from ancestors where this box can draw and receive pointer events. */
    private Rect clip;
    /** Styles of states by {@link State} or custom name, in declaration order. */
    private final LinkedHashMap<Object, Style> stateStyles = new LinkedHashMap<>();
    private final Set<String> customStates = ConcurrentHashMap.newKeySet();
    private volatile boolean selected;
    /** The merged style of active states. Recomputed only after a state or style changes. */
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
    /** Shows if the pointer is over this box or a descendant. Represents CSS {@code :hover}. */
    private volatile boolean hovered;
    private Rect rect = new Rect(0, 0, 0, 0);

    // Two cached sizes per pass: the natural size and the last constrained size.
    private Size unbounded;
    private Size constrained;
    private double constrainedFor = Double.NaN;

    /**
     * Calculates the content size without padding that fits {@code maxWidth}.
     * Text wraps to this width, and containers pass it to children.
     * This method must not change state.
     */
    protected Size measureContent(double maxWidth) {
        return Size.ZERO;
    }

    /** Places children inside the content rectangle. */
    protected void arrangeContent(Rect content) {}

    /** Adds every canvas component of this subtree to {@code out}. */
    protected void collectComponents(List<LayoutComponent> out) {}

    /**
     * Narrowest content width without padding this box can take without word breaks or squeezed fixed sizes.
     * This is the minimum limit for {@code shrink} (CSS {@code min-content}).
     */
    protected double minContentWidth() {
        return 0;
    }

    /** Semantic HTML tag name of this node for debugging, serialization, and the web editor. */
    public String tagName() {
        return "div";
    }

    /** The single player that sees this box's entities, or {@code null} when visible to all players or inherited. */
    public Player viewer() {
        LayoutComponent comp = component();
        return comp != null ? comp.viewer() : null;
    }

    /** Binds the component of this box to packet-only rendering for {@code viewer} if unset. */
    void bindViewer(Player viewer) {}

    /** The component that this box draws ({@link Text}, {@link Item}, {@link Block}), or {@code null} for other boxes. */
    LayoutComponent component() {
        return null;
    }

    /** Depth steps on which this box paints (see {@link MountedLayout}). This includes a background, border, or component layers. */
    int layerSteps() {
        return hasBorder() ? 2 : 1;
    }

    /** Adds every box of this subtree, including self, depth-first, with parents before children. */
    protected void collectBoxes(List<Box<?>> out) {
        out.add(this);
    }

    /** Clears the cached sizes of this subtree so the next pass measures changed text again. */
    protected void invalidate() {
        unbounded = null;
        constrained = null;
        constrainedFor = Double.NaN;
    }

    /** Natural outer size (fixed size or content with padding). Does not include margin. */
    public final Size measure() {
        return measure(UNBOUNDED);
    }

    /** Outer size when at most {@code maxWidth} pixels are available. A fixed width overrides this limit. */
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

    /** Size plus margin when at most {@code maxWidth} pixels (margin included) are available. */
    Size outer(double maxWidth) {
        if (hidden) {
            return Size.ZERO;
        }
        Insets margin = effectiveMargin();
        Size size = measure(maxWidth < 0 ? UNBOUNDED : Math.max(0, maxWidth - margin.horizontal()));
        return new Size(size.width() + margin.horizontal(), size.height() + margin.vertical());
    }

    /** {@link #minContentWidth()} as the parent sees it, with padding, border, and margin, or the fixed or minimum width. */
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

    /** @param insideOverflow True if an ancestor does not fit its space. */
    final void arrange(Rect rect, boolean insideOverflow) {
        arrange(rect, insideOverflow, 1.0, 1.0, 0.0, 0.0, 0.0, Double.NaN, Double.NaN, 0);
    }

    final void arrange(
        Rect rect,
        boolean insideOverflow,
        double inheritedScaleX,
        double inheritedScaleY,
        double inheritedRotateDeg,
        double inheritedRotateXDeg,
        double inheritedRotateYDeg,
        double inheritedPivotX,
        double inheritedPivotY
    ) {
        arrange(
            rect,
            insideOverflow,
            inheritedScaleX,
            inheritedScaleY,
            inheritedRotateDeg,
            inheritedRotateXDeg,
            inheritedRotateYDeg,
            inheritedPivotX,
            inheritedPivotY,
            0
        );
    }

    /** Arranges {@code child} inside this container and passes visual scale, rotation, and animation ticks. */
    final void arrangeChild(Box<?> child, Rect childRect, boolean insideOverflow) {
        child.arrange(
            childRect,
            insideOverflow,
            this.visualScaleX,
            this.visualScaleY,
            this.visualRotateDeg,
            this.visualRotateXDeg,
            this.visualRotateYDeg,
            this.visualPivotX,
            this.visualPivotY,
            this.visualTransformationDurationTicks
        );
    }

    final void arrange(
        Rect rect,
        boolean insideOverflow,
        double inheritedScaleX,
        double inheritedScaleY,
        double inheritedRotateDeg,
        double inheritedRotateXDeg,
        double inheritedRotateYDeg,
        double inheritedPivotX,
        double inheritedPivotY,
        int inheritedDurationTicks
    ) {
        if (hidden) {
            this.rect = new Rect(rect.x(), rect.y(), 0, 0);
            this.fit = Fit.NONE;
            this.visualScaleX = 1.0;
            this.visualScaleY = 1.0;
            this.visualRotateDeg = 0.0;
            this.visualRotateXDeg = 0.0;
            this.visualRotateYDeg = 0.0;
            this.visualPivotX = 0.0;
            this.visualPivotY = 0.0;
            this.visualTransformationDurationTicks = 0;
            arrangeContent(this.rect);
            arrangeAbsoluteChildren(this.rect);
            return;
        }
        Transform tf = effectiveTransform();
        if (lastTargetTransform != null && !tf.approximatelyEquals(lastTargetTransform)) {
            if (transformTransition != null) {
                int ticks = (int) Math.max(1, Math.round(transformTransition.transition().durationMs() / 50.0));
                this.visualTransformationDurationTicks = Math.max(ticks, inheritedDurationTicks);
                this.clientTransformUntilTime =
                    System.currentTimeMillis() + transformTransition.transition().durationMs();
            } else {
                this.visualTransformationDurationTicks = inheritedDurationTicks;
            }
        } else {
            this.visualTransformationDurationTicks = inheritedDurationTicks;
        }
        this.lastTargetTransform = tf;

        LayoutComponent comp = component();
        if (comp != null) {
            comp.setTransformationDuration(this.visualTransformationDurationTicks);
        }

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
        if (tf.hasRotation() || Double.isNaN(inheritedPivotX)) {
            this.visualPivotX = rect.x() + rect.width() * tf.originX();
            this.visualPivotY = rect.y() + rect.height() * tf.originY();
        } else {
            this.visualPivotX = inheritedPivotX;
            this.visualPivotY = inheritedPivotY;
        }
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

    double visualPivotX() {
        return visualPivotX;
    }

    double visualPivotY() {
        return visualPivotY;
    }

    public int visualTransformationDurationTicks() {
        return visualTransformationDurationTicks;
    }

    /** Hook that {@link Container} overrides to arrange {@link Position#ABSOLUTE} children inside {@code bounds}. */
    void arrangeAbsoluteChildren(Rect bounds) {}

    /** Shows if the last layout pass found this box larger than its space, or inside an overflowing container. */
    public Fit fit() {
        return fit;
    }

    /** Shows if this box or an ancestor overflows. */
    boolean overflowsHere() {
        return fit != Fit.NONE;
    }

    /** The rectangle assigned by the last layout pass. */
    public Rect rect() {
        return rect;
    }

    /** Shows if the canvas point {@code (px, py)} is inside this box, with rotation considered. */
    public boolean contains(double px, double py) {
        if (rect == null || hidden) {
            return false;
        }
        if (
            Math.abs(visualRotateDeg) < 1e-4 && Math.abs(visualRotateXDeg) < 1e-4 && Math.abs(visualRotateYDeg) < 1e-4
        ) {
            return rect.contains(px, py);
        }
        double pivX = visualPivotX;
        double pivY = visualPivotY;
        double dx = px - pivX;
        double dy = py - pivY;
        if (Math.abs(visualRotateDeg) >= 1e-4) {
            double rad = Math.toRadians(-visualRotateDeg);
            double cos = Math.cos(rad);
            double sin = Math.sin(rad);
            double rx = dx * cos - dy * sin;
            double ry = dx * sin + dy * cos;
            dx = rx;
            dy = ry;
        }
        if (Math.abs(visualRotateXDeg) >= 1e-4) {
            double cosX = Math.cos(Math.toRadians(visualRotateXDeg));
            if (Math.abs(cosX) > 0.05) {
                dy = dy / cosX;
            }
        }
        if (Math.abs(visualRotateYDeg) >= 1e-4) {
            double cosY = Math.cos(Math.toRadians(visualRotateYDeg));
            if (Math.abs(cosY) > 0.05) {
                dx = dx / cosY;
            }
        }
        return rect.contains(pivX + dx, pivY + dy);
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

    /** Sets uniform border width and color (ARGB) on all four sides (CSS {@code border}). */
    public S border(double widthPx, int argb) {
        this.borderWidths = Insets.all(Math.max(0, widthPx));
        this.borderColor = argb;
        return self();
    }

    /** Sets uniform border width and CSS color string on all four sides. */
    public S border(double widthPx, String cssColor) {
        return border(widthPx, ColorUtils.parse(cssColor));
    }

    /** Sets per-side border widths and uniform border color (ARGB). */
    public S border(Insets widths, int argb) {
        this.borderWidths = widths != null ? widths : Insets.NONE;
        this.borderColor = argb;
        return self();
    }

    /** Sets per-side border widths and CSS color string. */
    public S border(Insets widths, String cssColor) {
        return border(widths, ColorUtils.parse(cssColor));
    }

    /** Sets top border width and color (ARGB). */
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

    /** Sets right border width and color (ARGB). */
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

    /** Sets bottom border width and color (ARGB). */
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

    /** Sets left border width and color (ARGB). */
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

    /** Sets border color (ARGB). */
    public S borderColor(int argb) {
        this.borderColor = argb;
        return self();
    }

    /** Sets border color from CSS color string. */
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

    /** CSS percentage width ({@code 0.0..1.0}) relative to the parent content width. */
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

    /** CSS percentage height ({@code 0.0..1.0}) relative to the parent content height. */
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

    /** Sets minimum width and minimum height. */
    public S minSize(double minWidth, double minHeight) {
        return minWidth(minWidth).minHeight(minHeight);
    }

    /** Sets maximum width and maximum height. */
    public S maxSize(double maxWidth, double maxHeight) {
        return maxWidth(maxWidth).maxHeight(maxHeight);
    }

    /**
     * Share of free space along the parent main axis added to the natural size (CSS {@code flex-grow}).
     */
    public S grow(double grow) {
        this.grow = grow;
        return self();
    }

    public double grow() {
        return grow;
    }

    /**
     * Share of space reduction when children do not fit (CSS {@code flex-shrink}).
     * Never goes below {@link #minContentWidth()}.
     */
    public S shrink(double shrink) {
        this.shrink = Math.max(0, shrink);
        return self();
    }

    public double shrink() {
        return shrink;
    }

    /**
     * Vertical placement in the parent area (CSS {@code align-self}).
     * Applies to the cross axis in rows or columns.
     */
    public S alignSelf(Align align) {
        this.alignSelf = align;
        return self();
    }

    public Align alignSelf() {
        return alignSelf;
    }

    /**
     * Horizontal placement in a grid or stack area (CSS {@code justify-self}).
     * In a row or column it does not move the box.
     */
    public S justifySelf(Align align) {
        this.justifySelf = align;
        return self();
    }

    public Align justifySelf() {
        return justifySelf;
    }

    /** Rows that this item spans in a grid (CSS {@code grid-row: span n}). */
    public S rowSpan(int span) {
        this.rowSpan = Math.max(1, span);
        return self();
    }

    public int rowSpan() {
        return rowSpan;
    }

    /** Columns that this item spans in a grid (CSS {@code grid-column: span n}). */
    public S columnSpan(int span) {
        this.columnSpan = Math.max(1, span);
        return self();
    }

    public int columnSpan() {
        return columnSpan;
    }

    /** Start row line in a grid, 1-based (CSS {@code grid-row-start}). Auto-placed if 0. */
    public S gridRow(int line) {
        this.gridRow = Math.max(0, line);
        return self();
    }

    public int gridRow() {
        return gridRow;
    }

    /** Start column line in a grid, 1-based (CSS {@code grid-column-start}). Auto-placed if 0. */
    public S gridColumn(int line) {
        this.gridColumn = Math.max(0, line);
        return self();
    }

    public int gridColumn() {
        return gridColumn;
    }

    /** Sets CSS {@code position} scheme. */
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

    /** Sets absolute position with {@code left} and {@code top} offsets. */
    public S absolute(double left, double top) {
        this.position = Position.ABSOLUTE;
        this.left = left;
        this.top = top;
        return self();
    }

    /** CSS {@code left} offset in canvas pixels. */
    public S left(double px) {
        this.left = px;
        return self();
    }

    public double left() {
        return left;
    }

    /** CSS {@code top} offset in canvas pixels. */
    public S top(double px) {
        this.top = px;
        return self();
    }

    public double top() {
        return top;
    }

    /** CSS {@code right} offset in canvas pixels. */
    public S right(double px) {
        this.right = px;
        return self();
    }

    public double right() {
        return right;
    }

    /** CSS {@code bottom} offset in canvas pixels. */
    public S bottom(double px) {
        this.bottom = px;
        return self();
    }

    public double bottom() {
        return bottom;
    }

    /** Sets uniform inset offset on all four sides. */
    public S inset(double all) {
        return inset(all, all, all, all);
    }

    /** Sets vertical and horizontal inset offsets. */
    public S inset(double vertical, double horizontal) {
        return inset(vertical, horizontal, vertical, horizontal);
    }

    /** Sets top, right, bottom, and left inset offsets. */
    public S inset(double top, double right, double bottom, double left) {
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        this.left = left;
        return self();
    }

    /**
     * Translates this box by percentages of its width and height.
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

    /** Translates this box by canvas pixels. */
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

    /** Sets uniform scale around the transform origin. */
    public S scale(double factor) {
        return scale(factor, factor);
    }

    /** Sets horizontal and vertical scale around the transform origin. */
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

    /** Sets 2D clockwise rotation in degrees. */
    public S rotate(double degrees) {
        this.rotateDeg = degrees;
        return self();
    }

    public double rotateDeg() {
        return rotateDeg;
    }

    /** Sets 3D pitch tilt in degrees. */
    public S rotateX(double degrees) {
        this.rotateXDeg = degrees;
        return self();
    }

    public double rotateXDeg() {
        return rotateXDeg;
    }

    /** Sets 3D yaw turn in degrees. */
    public S rotateY(double degrees) {
        this.rotateYDeg = degrees;
        return self();
    }

    public double rotateYDeg() {
        return rotateYDeg;
    }

    /** Sets the transform origin fractions (0.0 to 1.0). */
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

    /** Sets z-index for depth layering and pointer hit testing. */
    public S zIndex(int zIndex) {
        this.zIndex = zIndex;
        return self();
    }

    public int zIndex() {
        return zIndex;
    }

    /** Tag for pointer hit testing ({@link MountedLayout#idAt}). */
    public S id(String id) {
        this.id = id;
        return self();
    }

    /**
     * Fills the box with color (ARGB) including padding but not margin (CSS {@code background-color}).
     * Nested boxes render above parents and below text.
     */
    public S background(int argb) {
        this.background = argb;
        return self();
    }

    /**
     * Fills the box with color parsed from a CSS color string.
     */
    public S background(String cssColor) {
        return background(ColorUtils.parse(cssColor));
    }

    public int background() {
        return background;
    }

    /** CSS {@code opacity} (0.0 to 1.0) applied to the background and border. */
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
     * Removes this box and its subtree from layout flow and rendering when true (CSS {@code display: none}).
     */
    public S hidden(boolean hidden) {
        this.hidden = hidden;
        this.hiddenWhen = null;
        return self();
    }

    /** Dynamically hides this box when {@code condition} returns true (CSS {@code display: none}). */
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
     * Sets CSS {@code overflow} on both axes.
     * {@link Overflow#HIDDEN} clips children at the box edges and restricts hover and click actions to the box area.
     */
    public S overflow(Overflow overflow) {
        return overflowX(overflow).overflowY(overflow);
    }

    /** Sets CSS {@code overflow-x} to clip children at horizontal edges. */
    public S overflowX(Overflow overflow) {
        this.overflowX = overflow;
        return self();
    }

    public Overflow overflowX() {
        return overflowX;
    }

    /** Sets CSS {@code overflow-y} to clip children at vertical edges. */
    public S overflowY(Overflow overflow) {
        this.overflowY = overflow;
        return self();
    }

    public Overflow overflowY() {
        return overflowY;
    }

    /**
     * Sets screen cursor sprite over this box and descendants (CSS {@code cursor}).
     * Uses pointer over clickable boxes and default elsewhere when unset.
     */
    public S cursor(String name) {
        this.cursor = name;
        return self();
    }

    public String cursor() {
        return cursor;
    }

    /** Current cursor on this box or {@code null}. */
    String effectiveCursor() {
        Style style = activeStyle();
        return style != null && style.cursor() != null ? style.cursor() : cursor;
    }

    /** Where this box can draw from the last layout pass. Canvas when not clipped. */
    Rect clip() {
        return clip;
    }

    void clip(Rect clip) {
        this.clip = clip;
    }

    /** Clip rectangle for children, cut at edges on hidden axes. */
    Rect childClip() {
        Rect own = rect;
        double left = overflowX.clips() ? Math.max(clip.x(), own.x()) : clip.x();
        double right = overflowX.clips() ? Math.min(clip.right(), own.right()) : clip.right();
        double top = overflowY.clips() ? Math.max(clip.y(), own.y()) : clip.y();
        double bottom = overflowY.clips() ? Math.min(clip.bottom(), own.bottom()) : clip.bottom();
        return new Rect(left, top, Math.max(0, right - left), Math.max(0, bottom - top));
    }

    /**
     * The style of the box in a state, similar to a CSS pseudo-class.
     */
    public S state(State state, Consumer<Style> rule) {
        rule.accept(stateStyles.computeIfAbsent(state, key -> new Style()));
        styleStale = true;
        return self();
    }

    /** The style of the box in a custom state (switched with {@link #toggle(String, boolean)}). */
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

    /** Switches a custom state on or off. */
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

    /** Selected while the condition is true. Checked on every layout refresh. */
    public S selected(BooleanSupplier condition) {
        this.selectedWhen = condition;
        return self();
    }

    /** A disabled box ignores clicks and displays its disabled style. */
    public S disabled(boolean value) {
        this.disabled = value;
        this.disabledWhen = null;
        styleStale = true;
        return self();
    }

    /** Disabled while the condition is true. Checked on every layout refresh. */
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

    /** Re-evaluates conditional states. */
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
     * The style of active states, or {@code null}.
     * A later style overrides an earlier style per property.
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

    /** Current padding from active states, or default padding. */
    Insets effectivePadding() {
        Style style = activeStyle();
        return style != null && style.padding() != null ? style.padding() : padding;
    }

    /** Current margin from active states, or default margin. */
    Insets effectiveMargin() {
        Style style = activeStyle();
        return style != null && style.margin() != null ? style.margin() : margin;
    }

    /** Current border widths from active states, or default border widths. */
    Insets effectiveBorderWidths() {
        Style style = activeStyle();
        return style != null && style.borderWidths() != null ? style.borderWidths() : borderWidths;
    }

    /** Runs when pointer enters the box or child box, with the player. */
    public S onHoverEnter(Consumer<Player> handler) {
        this.onHoverEnter = handler;
        return self();
    }

    /** Runs when pointer leaves the box, with the player. */
    public S onHoverLeave(Consumer<Player> handler) {
        this.onHoverLeave = handler;
        return self();
    }

    /**
     * Configures CSS property transitions.
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
     * Configures a transition for all properties with duration and easing.
     */
    public S transition(long durationMs, Easing easing) {
        return transition(Transition.all(durationMs, easing));
    }

    /**
     * Configures a transition for all properties with duration and standard ease.
     */
    public S transition(long durationMs) {
        return transition(Transition.all(durationMs, Easing.EASE));
    }

    /**
     * Configures a transition for a specific property.
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
            System.currentTimeMillis() < clientTransformUntilTime
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
        return animating;
    }

    Transform effectiveTransform() {
        Style style = activeStyle();
        return new Transform(
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
    }

    Transform effectiveTransform(long now) {
        return effectiveTransform();
    }

    /** The background to draw now, from active states if set. */
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

    /** The border color to draw now, from active states if set. */
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

    /** @return True if state changed. */
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

    /** Handler that a click on this box runs (its own {@link #onClick} or {@code null}). */
    ClickHandler effectiveOnClick() {
        return onClick;
    }

    /** Makes the whole box clickable through {@link MountedLayout#click} with pixel coordinates. */
    public S onClick(ClickHandler onClick) {
        this.onClick = onClick;
        return self();
    }

    /** Makes the whole box clickable through {@link MountedLayout#click}. */
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
