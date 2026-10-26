package dev.horuse.pixelay.layout;

import dev.horuse.pixelay.content.TextContent;
import dev.horuse.pixelay.data.Rect;
import dev.horuse.pixelay.data.Size;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import org.bukkit.entity.Player;

/**
 * An interactive HTML {@code <input type="checkbox">} component with a font-independent geometric
 * check indicator and an optional text label.
 */
public final class Checkbox extends Container<Checkbox> {

    private final Player viewer;
    private final Box<?> outerBox = new Box<>();
    private final Box<?> innerCheck = new Box<>();
    private Leaf labelLeaf;

    private boolean checked;
    private BooleanSupplier checkedWhen;
    private BiConsumer<Player, Boolean> onChange;

    private double boxSize = 22.0;
    private int activeColor = 0xFF4F46E5;
    private int inactiveColor = 0xFFCBD5E1;
    private int hoverColor = 0xFF94A3B8;
    private int checkColor = 0xFFFFFFFF;
    private double fontSize = 20.0;
    private String labelColor = "<#334155>";

    public Checkbox(Player viewer, String label, boolean checked, BiConsumer<Player, Boolean> onChange) {
        this.viewer = viewer;
        this.checked = checked;
        this.onChange = onChange;
        this.gap = 12.0;
        cursor("pointer");
        children.add(outerBox);
        children.add(innerCheck);
        if (label != null && !label.isEmpty()) {
            this.labelLeaf = new Leaf(viewer, fontSize, formatLabel(label));
            children.add(labelLeaf);
        }
        outerBox.transition(140, Easing.EASE_OUT);
        innerCheck.transition(140, Easing.EASE_OUT);
        syncVisuals();
        onClick((player, x, y) -> toggle(player));
    }

    public Checkbox(boolean checked, BiConsumer<Player, Boolean> onChange) {
        this(null, null, checked, onChange);
    }

    @Override
    public String tagName() {
        return "checkbox";
    }

    @Override
    public Player viewer() {
        return viewer != null ? viewer : super.viewer();
    }

    private String formatLabel(String raw) {
        return raw.startsWith("<") ? raw : labelColor + raw;
    }

    public boolean isChecked() {
        return checked;
    }

    public Checkbox checked(boolean value) {
        this.checked = value;
        this.checkedWhen = null;
        syncVisuals();
        return this;
    }

    public Checkbox checked(BooleanSupplier condition) {
        this.checkedWhen = condition;
        return this;
    }

    public void toggle(Player player) {
        this.checked = !this.checked;
        syncVisuals();
        if (onChange != null) {
            onChange.accept(player, this.checked);
        }
    }

    public Checkbox onChange(BiConsumer<Player, Boolean> onChange) {
        this.onChange = onChange;
        return this;
    }

    public Checkbox boxSize(double px) {
        this.boxSize = Math.max(8, px);
        invalidate();
        return this;
    }

    public double boxSize() {
        return boxSize;
    }

    public Checkbox activeColor(int argb) {
        this.activeColor = argb;
        syncVisuals();
        return this;
    }

    public int activeColor() {
        return activeColor;
    }

    public Checkbox inactiveColor(int argb) {
        this.inactiveColor = argb;
        syncVisuals();
        return this;
    }

    public int inactiveColor() {
        return inactiveColor;
    }

    public Checkbox hoverColor(int argb) {
        this.hoverColor = argb;
        syncVisuals();
        return this;
    }

    public int hoverColor() {
        return hoverColor;
    }

    public Checkbox checkColor(int argb) {
        this.checkColor = argb;
        syncVisuals();
        return this;
    }

    public int checkColor() {
        return checkColor;
    }

    public Checkbox fontSize(double heightPx) {
        this.fontSize = heightPx;
        if (labelLeaf != null) {
            labelLeaf.fontSize(heightPx);
            invalidate();
        }
        return this;
    }

    public double fontSize() {
        return fontSize;
    }

    public Checkbox label(String text) {
        if (labelLeaf != null) {
            labelLeaf.text(TextContent.of(formatLabel(text)));
        } else if (text != null && !text.isEmpty()) {
            labelLeaf = new Leaf(viewer, fontSize, formatLabel(text));
            children.add(labelLeaf);
        }
        invalidate();
        return this;
    }

    public Leaf labelLeaf() {
        return labelLeaf;
    }

    private void syncVisuals() {
        int baseBg = checked ? activeColor : is(State.HOVERED) ? hoverColor : inactiveColor;
        outerBox.background(baseBg);
        innerCheck.background(checked ? checkColor : 0x00000000);
    }

    @Override
    boolean setHovered(boolean value, Player player) {
        boolean changed = super.setHovered(value, player);
        if (changed) {
            syncVisuals();
        }
        return changed;
    }

    @Override
    boolean hasTransitions() {
        return super.hasTransitions() || outerBox.hasTransitions() || innerCheck.hasTransitions();
    }

    @Override
    void updateStates() {
        super.updateStates();
        if (checkedWhen != null) {
            boolean current = checkedWhen.getAsBoolean();
            if (current != checked) {
                checked = current;
            }
        }
        syncVisuals();
    }

    @Override
    protected Size measureContent(double maxWidth) {
        if (labelLeaf == null) {
            return new Size(boxSize, boxSize);
        }
        double labelLimit = maxWidth < 0 ? UNBOUNDED : Math.max(0, maxWidth - boxSize - gap);
        Size labelSize = labelLeaf.outer(labelLimit);
        return new Size(boxSize + gap + labelSize.width(), Math.max(boxSize, labelSize.height()));
    }

    @Override
    protected double minContentWidth() {
        if (labelLeaf == null) {
            return boxSize;
        }
        return boxSize + gap + labelLeaf.minOuterWidth();
    }

    @Override
    protected void arrangeContent(Rect content) {
        double boxY = content.y() + Math.max(0, (content.height() - boxSize) / 2.0);
        Rect outerRect = new Rect(content.x(), boxY, boxSize, boxSize);
        outerBox.arrange(outerRect, overflowsHere());

        double checkInset = Math.max(3.0, Math.round(boxSize * 0.25));
        double checkSize = Math.max(2.0, boxSize - checkInset * 2.0);
        Rect innerRect = checked
            ? new Rect(content.x() + checkInset, boxY + checkInset, checkSize, checkSize)
            : new Rect(content.x() + boxSize / 2.0, boxY + boxSize / 2.0, 0, 0);
        innerCheck.arrange(innerRect, false);

        if (labelLeaf != null) {
            double labelX = content.x() + boxSize + gap;
            double labelW = Math.max(0, content.width() - boxSize - gap);
            Size labelSize = labelLeaf.outer(labelW);
            double labelY = content.y() + Math.max(0, (content.height() - labelSize.height()) / 2.0);
            labelLeaf.arrange(new Rect(labelX, labelY, labelW, labelSize.height()), overflowsHere());
        }
    }
}
