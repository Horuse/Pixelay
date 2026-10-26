package dev.horuse.pixelay.layout;

import dev.horuse.pixelay.components.LayoutComponent;
import dev.horuse.pixelay.data.Rect;
import dev.horuse.pixelay.data.Size;
import java.util.List;
import java.util.function.Consumer;

/**
 * A scrollable container with an optional scrollbar. Wraps items in a vertical column by default,
 * clipping content at its edges (CSS {@code overflow: scroll} / {@code auto}) and moving content
 * by {@link #scrollOffset}.
 * <p>
 * Supports mouse wheel scrolling, drag-and-drop on the scrollbar thumb, clicking the track to jump,
 * and smooth animated gliding.
 */
public class ScrollView extends Container<ScrollView> {

    public enum ScrollbarPolicy {
        ALWAYS,
        AUTO,
        NEVER,
    }

    private final Box<?> content;
    private final ScrollbarElement track = new ScrollbarElement();
    private final ScrollbarElement thumb = new ScrollbarElement();

    private double scrollOffset;
    private double targetScrollOffset;
    private double maxScrollY;
    private double contentHeight;
    private double viewportHeight;
    private double contentWidth;
    private double viewportWidth;

    private double scrollbarWidth = 4.0;
    private double scrollbarMargin = 2.0;
    private double minThumbSize = 16.0;
    private double scrollStep = 32.0;
    private boolean smooth = true;
    private ScrollbarPolicy scrollbarPolicy = ScrollbarPolicy.AUTO;

    private int trackColor = 0x26000000;
    private int thumbColor = 0x80888888;
    private int thumbHoverColor = 0xD0CCCCCC;
    private int thumbDragColor = 0xFFFFFFFF;

    private boolean dragging;
    private double dragStartCursorY;
    private double dragStartScrollOffset;
    private double lastPointerY;
    private Consumer<ScrollView> onScroll;

    public ScrollView(LayoutItem... items) {
        this(items.length == 1 && items[0] instanceof Box<?> single ? single : new Flex(false, items));
    }

    public ScrollView(Box<?> content) {
        this.content = content;
        overflowY(Overflow.SCROLL);
        overflowX(Overflow.HIDDEN);
        children.add(content);
        children.add(track);
        children.add(thumb);
        setupScrollbar();
    }

    private void setupScrollbar() {
        track.cursor("default");
        track.background(trackColor);
        track.onClick(player -> {
            if (maxScrollY <= 0) {
                return;
            }
            double trackH = track.rect().height();
            double thumbH = thumb.rect().height();
            double travel = trackH - thumbH;
            if (travel <= 0) {
                return;
            }
            double targetThumbY = lastPointerY - track.rect().y() - thumbH / 2.0;
            double pct = Math.max(0, Math.min(1.0, targetThumbY / travel));
            scrollTo(pct * maxScrollY);
            startDragging(lastPointerY);
        });

        thumb.cursor("pointer");
        thumb.background(thumbColor);
        thumb.state(State.HOVERED, s -> s.background(thumbHoverColor));
        thumb.onClick(player -> {
            if (dragging) {
                stopDragging();
            } else {
                startDragging(lastPointerY);
            }
        });
    }

    public Box<?> content() {
        return content;
    }

    @Override
    public ScrollView gap(double gap) {
        if (content instanceof Flex flex) {
            flex.gap(gap);
        }
        return this;
    }

    public ScrollView align(Align align) {
        if (content instanceof Flex flex) {
            flex.align(align);
        }
        return this;
    }

    public ScrollView justify(Justify justify) {
        if (content instanceof Flex flex) {
            flex.justify(justify);
        }
        return this;
    }

    public ScrollView scrollbarWidth(double width) {
        this.scrollbarWidth = Math.max(1, width);
        return this;
    }

    public ScrollView scrollbarMargin(double margin) {
        this.scrollbarMargin = Math.max(0, margin);
        return this;
    }

    public ScrollView minThumbSize(double size) {
        this.minThumbSize = Math.max(4, size);
        return this;
    }

    public ScrollView scrollStep(double px) {
        this.scrollStep = Math.max(1, px);
        return this;
    }

    public double scrollStep() {
        return scrollStep;
    }

    public ScrollView smooth(boolean smooth) {
        this.smooth = smooth;
        return this;
    }

    public ScrollView scrollbar(ScrollbarPolicy policy) {
        this.scrollbarPolicy = policy;
        return this;
    }

    public ScrollView scrollbar(boolean visible) {
        return scrollbar(visible ? ScrollbarPolicy.ALWAYS : ScrollbarPolicy.NEVER);
    }

    public ScrollView trackColor(int argb) {
        this.trackColor = argb;
        track.background(argb);
        return this;
    }

    public ScrollView thumbColor(int argb) {
        this.thumbColor = argb;
        if (!dragging) {
            thumb.background(argb);
        }
        return this;
    }

    public ScrollView thumbHoverColor(int argb) {
        this.thumbHoverColor = argb;
        thumb.state(State.HOVERED, s -> s.background(argb));
        return this;
    }

    public ScrollView thumbDragColor(int argb) {
        this.thumbDragColor = argb;
        if (dragging) {
            thumb.background(argb);
        }
        return this;
    }

    public ScrollView onScroll(Consumer<ScrollView> listener) {
        this.onScroll = listener;
        return this;
    }

    public ScrollView scrollTo(double offset) {
        this.targetScrollOffset = Math.max(0, Math.min(maxScrollY, offset));
        if (!smooth) {
            this.scrollOffset = this.targetScrollOffset;
        }
        notifyScroll();
        return this;
    }

    public ScrollView scrollBy(double delta) {
        return scrollTo(targetScrollOffset + delta);
    }

    public ScrollView scrollToTop() {
        return scrollTo(0);
    }

    public ScrollView scrollToBottom() {
        return scrollTo(maxScrollY);
    }

    public double scrollOffset() {
        return scrollOffset;
    }

    public double maxScroll() {
        return maxScrollY;
    }

    public double scrollPercent() {
        return maxScrollY > 0 ? scrollOffset / maxScrollY : 0.0;
    }

    public ScrollView scrollToPercent(double pct) {
        return scrollTo(Math.max(0.0, Math.min(1.0, pct)) * maxScrollY);
    }

    public boolean canScroll() {
        return maxScrollY > 0;
    }

    public boolean isDragging() {
        return dragging;
    }

    public void startDragging(double cursorY) {
        if (maxScrollY <= 0) {
            return;
        }
        this.dragging = true;
        this.dragStartCursorY = cursorY;
        this.dragStartScrollOffset = scrollOffset;
        this.targetScrollOffset = scrollOffset;
        thumb.background(thumbDragColor);
    }

    public boolean dragTo(double cursorY) {
        if (!dragging || maxScrollY <= 0) {
            return false;
        }
        double trackH = track.rect().height();
        double thumbH = thumb.rect().height();
        double travel = trackH - thumbH;
        if (travel <= 0) {
            return false;
        }

        double deltaCursor = cursorY - dragStartCursorY;
        double scrollDelta = (deltaCursor / travel) * maxScrollY;
        double newOffset = Math.max(0, Math.min(maxScrollY, dragStartScrollOffset + scrollDelta));
        if (Math.abs(newOffset - scrollOffset) > 0.05) {
            scrollOffset = newOffset;
            targetScrollOffset = newOffset;
            notifyScroll();
            return true;
        }
        return false;
    }

    public void stopDragging() {
        if (dragging) {
            dragging = false;
            thumb.background(thumbColor);
        }
    }

    public void updatePointer(double px, double py) {
        this.lastPointerY = py;
    }

    public boolean tick() {
        if (!smooth) {
            return false;
        }
        if (Math.abs(scrollOffset - targetScrollOffset) < 0.1) {
            if (scrollOffset != targetScrollOffset) {
                scrollOffset = targetScrollOffset;
                notifyScroll();
                return true;
            }
            return false;
        }
        scrollOffset += (targetScrollOffset - scrollOffset) * 0.45;
        if (Math.abs(scrollOffset - targetScrollOffset) < 0.25) {
            scrollOffset = targetScrollOffset;
        }
        notifyScroll();
        return true;
    }

    private boolean hasScrollbar() {
        return scrollbarPolicy != ScrollbarPolicy.NEVER;
    }

    private boolean hasVisibleScrollbar() {
        return scrollbarPolicy == ScrollbarPolicy.ALWAYS || (scrollbarPolicy == ScrollbarPolicy.AUTO && maxScrollY > 0);
    }

    private void notifyScroll() {
        if (onScroll != null) {
            onScroll.accept(this);
        }
    }

    @Override
    protected Size measureContent(double maxWidth) {
        double gutter = hasVisibleScrollbar() ? scrollbarWidth + scrollbarMargin : 0;
        double available = maxWidth >= 0 ? Math.max(0, maxWidth - gutter) : UNBOUNDED;
        Size contentSize = content.measure(available);
        double width = contentSize.width() + gutter;
        double height = hasFixedHeight() ? rect().height() : contentSize.height();
        return new Size(width, height);
    }

    @Override
    protected double minContentWidth() {
        double gutter = hasVisibleScrollbar() ? scrollbarWidth + scrollbarMargin : 0;
        return content.minOuterWidth() + gutter;
    }

    @Override
    protected void arrangeContent(Rect viewport) {
        this.viewportHeight = viewport.height();
        this.viewportWidth = viewport.width();

        boolean showBar = hasScrollbar();
        double gutter = showBar ? scrollbarWidth + scrollbarMargin : 0;
        double contentW = Math.max(0, viewport.width() - gutter);
        Size contentSize = content.measure(contentW);
        this.contentHeight = contentSize.height();
        this.contentWidth = contentSize.width();

        this.maxScrollY = Math.max(0, contentHeight - viewportHeight);
        this.scrollOffset = Math.max(0, Math.min(maxScrollY, scrollOffset));
        this.targetScrollOffset = Math.max(0, Math.min(maxScrollY, targetScrollOffset));

        Rect contentRect = new Rect(viewport.x(), viewport.y() - scrollOffset, contentW, contentHeight);
        content.arrange(contentRect, overflowsHere());

        if (showBar && (scrollbarPolicy == ScrollbarPolicy.ALWAYS || maxScrollY > 0)) {
            double trackX = viewport.right() - scrollbarWidth;
            double trackY = viewport.y();
            double trackW = scrollbarWidth;
            double trackH = viewport.height();
            track.arrange(new Rect(trackX, trackY, trackW, trackH), false);

            if (maxScrollY > 0) {
                double thumbH = Math.max(minThumbSize, Math.min(trackH, (viewportHeight / contentHeight) * trackH));
                double travel = trackH - thumbH;
                double thumbY = trackY + (travel > 0 ? (scrollOffset / maxScrollY) * travel : 0);
                thumb.arrange(new Rect(trackX, thumbY, trackW, thumbH), false);
            } else {
                thumb.arrange(new Rect(trackX, trackY, 0, 0), false);
            }
        } else {
            track.arrange(new Rect(viewport.right(), viewport.y(), 0, 0), false);
            thumb.arrange(new Rect(viewport.right(), viewport.y(), 0, 0), false);
        }
    }

    private static final class ScrollbarElement extends Box<ScrollbarElement> {

        @Override
        protected Size measureContent(double maxWidth) {
            return Size.ZERO;
        }

        @Override
        protected double minContentWidth() {
            return 0;
        }

        @Override
        protected void arrangeContent(Rect content) {}

        @Override
        protected void collectComponents(List<LayoutComponent> out) {}
    }
}
