package dev.horuse.pixelay.layout;

import dev.horuse.pixelay.components.LayoutComponent;
import dev.horuse.pixelay.data.Rect;
import dev.horuse.pixelay.data.Size;
import dev.horuse.pixelay.debug.Fit;
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
 *
 * @param <S> the concrete box type, so chained setters keep it: {@code column(a, b).padding(32).gap(8)}
 */
public abstract class Box<S extends Box<S>> implements LayoutItem {

    /** Width limit meaning "no limit" (measure the natural, unwrapped size). */
    static final double UNBOUNDED = -1;

    private static final double OVERFLOW_EPSILON = 0.5;

    protected Insets padding = Insets.NONE;
    protected Insets margin = Insets.NONE;
    protected double fixedWidth = -1;
    protected double fixedHeight = -1;
    protected double grow;
    protected double shrink = 1;
    protected Align alignSelf;
    protected Align justifySelf;
    /** Grid placement (CSS {@code grid-row}/{@code grid-column}): 1-based start line or 0 = auto, and span. */
    int gridRow;
    int gridColumn;
    int rowSpan = 1;
    int columnSpan = 1;
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
    private boolean anchor;
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
    protected abstract Size measureContent(double maxWidth);

    /** Places the children inside the content rectangle (the box rectangle minus padding). */
    protected abstract void arrangeContent(Rect content);

    /** Adds every canvas component of this subtree to {@code out}. */
    protected abstract void collectComponents(List<LayoutComponent> out);

    /**
     * Narrowest content width (without padding) this box can take without breaking words or
     * squeezing fixed sizes — the floor for {@code shrink} (CSS {@code min-content}).
     */
    protected abstract double minContentWidth();

    /** The component this box draws ({@link Leaf}, {@link ModelLeaf}), or {@code null} for other boxes. */
    LayoutComponent component() {
        return null;
    }

    /** Depth steps the box paints on (see {@link MountedLayout}): a background, or a component's layers. */
    int layerSteps() {
        return 1;
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
        double limit = fixedWidth >= 0 ? fixedWidth : maxWidth;
        if (limit < 0) {
            if (unbounded == null) {
                unbounded = compute(UNBOUNDED);
            }
            return unbounded;
        }
        if (constrained == null || constrainedFor != limit) {
            constrained = compute(limit);
            constrainedFor = limit;
        }
        return constrained;
    }

    private Size compute(double limit) {
        Insets padding = effectivePadding();
        double contentLimit = limit < 0 ? UNBOUNDED : Math.max(0, limit - padding.horizontal());
        Size content = measureContent(contentLimit);
        return new Size(
            fixedWidth >= 0 ? fixedWidth : content.width() + padding.horizontal(),
            fixedHeight >= 0 ? fixedHeight : content.height() + padding.vertical()
        );
    }

    /** Size plus margin when at most {@code maxWidth} px (margin included) is available. */
    Size outer(double maxWidth) {
        Insets margin = effectiveMargin();
        Size size = measure(maxWidth < 0 ? UNBOUNDED : Math.max(0, maxWidth - margin.horizontal()));
        return new Size(size.width() + margin.horizontal(), size.height() + margin.vertical());
    }

    /** {@link #minContentWidth()} as the parent sees it: with padding and margin, or the fixed width. */
    double minOuterWidth() {
        double width = fixedWidth >= 0 ? fixedWidth : minContentWidth() + effectivePadding().horizontal();
        return width + effectiveMargin().horizontal();
    }

    public final void arrange(Rect rect) {
        arrange(rect, false);
    }

    /** @param insideOverflow an ancestor already does not fit its space */
    final void arrange(Rect rect, boolean insideOverflow) {
        this.rect = rect;
        Size natural = measure(rect.width());
        boolean selfOverflow =
            natural.width() > rect.width() + OVERFLOW_EPSILON || natural.height() > rect.height() + OVERFLOW_EPSILON;
        fit = selfOverflow ? Fit.SELF : insideOverflow ? Fit.INSIDE : Fit.NONE;
        arrangeContent(effectivePadding().shrink(rect));
    }

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
        return fixedWidth >= 0;
    }

    boolean hasFixedHeight() {
        return fixedHeight >= 0;
    }

    @SuppressWarnings("unchecked")
    protected final S self() {
        return (S) this;
    }

    public S padding(double all) {
        return padding(Insets.all(all));
    }

    public S padding(Insets padding) {
        this.padding = padding;
        return self();
    }

    public S margin(Insets margin) {
        this.margin = margin;
        return self();
    }

    public S width(double width) {
        this.fixedWidth = width;
        return self();
    }

    public S height(double height) {
        this.fixedHeight = height;
        return self();
    }

    public S size(double width, double height) {
        return width(width).height(height);
    }

    /**
     * Share of the free space along the parent's main axis, added to the natural size (CSS
     * {@code flex-grow} with {@code flex-basis: auto}).
     */
    public S grow(double grow) {
        this.grow = grow;
        return self();
    }

    /**
     * Share of the missing space a row takes from this box when its children do not fit (CSS
     * {@code flex-shrink}, default 1). Never below {@link #minContentWidth()}: texts wrap, words stay whole.
     */
    public S shrink(double shrink) {
        this.shrink = Math.max(0, shrink);
        return self();
    }

    /**
     * Vertical placement in the area given by the parent (CSS {@code align-self}); in a row or a
     * column it applies to the cross axis. A leaf also pins its text by it inside its rectangle.
     */
    public S alignSelf(Align align) {
        this.alignSelf = align;
        return self();
    }

    /**
     * Horizontal placement in a grid or stack area (CSS {@code justify-self}). In a row or a column it
     * does not move the box (as in CSS flexbox); a leaf still uses it to pin its text inside its rectangle.
     */
    public S justifySelf(Align align) {
        this.justifySelf = align;
        return self();
    }

    /** Rows this item covers in a grid (CSS {@code grid-row: span n}). */
    public S rowSpan(int span) {
        this.rowSpan = Math.max(1, span);
        return self();
    }

    /** Columns this item covers in a grid (CSS {@code grid-column: span n}). */
    public S columnSpan(int span) {
        this.columnSpan = Math.max(1, span);
        return self();
    }

    /** Explicit start row line in a grid, 1-based like CSS {@code grid-row-start}; auto-placed otherwise. */
    public S gridRow(int line) {
        this.gridRow = Math.max(0, line);
        return self();
    }

    /** Explicit start column line in a grid, 1-based like CSS {@code grid-column-start}; auto-placed otherwise. */
    public S gridColumn(int line) {
        this.gridColumn = Math.max(0, line);
        return self();
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

    public int background() {
        return background;
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

    /** CSS {@code overflow-y}: cut the children at the top and bottom edges. */
    public S overflowY(Overflow overflow) {
        this.overflowY = overflow;
        return self();
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

    /** Re-reads the conditional states (see {@link #selected(BooleanSupplier)}). */
    void updateStates() {
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

    /** The background to draw now: from the active states, if they set one. */
    int effectiveBackground() {
        Style style = activeStyle();
        return style != null && style.background() != null ? style.background() : background;
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

    /**
     * Pins this box to the point of a layout mounted with {@link Layout#mountAt}: the layout is moved so
     * that this box (not the whole tree) sits on the point, aligned as the mount says. E.g. an icon centred
     * on a map position with its label hanging below it. Only the first anchored box counts.
     */
    public S anchor() {
        this.anchor = true;
        return self();
    }

    boolean isAnchor() {
        return anchor;
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
}
