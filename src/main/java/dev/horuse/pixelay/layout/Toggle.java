package dev.horuse.pixelay.layout;

import dev.horuse.pixelay.content.TextContent;
import dev.horuse.pixelay.data.Rect;
import dev.horuse.pixelay.data.Size;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import org.bukkit.entity.Player;

/**
 * Interactive toggle switch component with sliding knob and optional label.
 */
public final class Toggle extends Container<Toggle> {

    private final Player viewer;
    private final Box<?> trackBox = new Box<>();
    private final Box<?> knobBox = new Box<>();
    private Text labelText;

    private boolean checked;
    private BooleanSupplier checkedWhen;
    private BiConsumer<Player, Boolean> onChange;

    private double trackWidth = 42.0;
    private double trackHeight = 22.0;
    private double knobInset = 3.0;
    private int activeColor = 0xFF4F46E5;
    private int inactiveColor = 0xFFCBD5E1;
    private int hoverColor = 0xFF94A3B8;
    private int knobColor = 0xFFFFFFFF;
    private double fontSize = 20.0;
    private String labelColor = "<#334155>";

    public Toggle(Player viewer, String label, boolean checked, BiConsumer<Player, Boolean> onChange) {
        this.viewer = viewer;
        this.checked = checked;
        this.onChange = onChange;
        this.gap = 12.0;
        cursor("pointer");
        children.add(trackBox);
        children.add(knobBox);
        if (label != null && !label.isEmpty()) {
            this.labelText = new Text(viewer, fontSize, formatLabel(label));
            children.add(labelText);
        }
        trackBox.transition(150, Easing.EASE_OUT);
        syncVisuals();
        onClick((player, x, y) -> toggle(player));
    }

    public Toggle(String label, boolean checked, BiConsumer<Player, Boolean> onChange) {
        this(null, label, checked, onChange);
    }

    public Toggle(boolean checked, BiConsumer<Player, Boolean> onChange) {
        this(null, null, checked, onChange);
    }

    @Override
    public String tagName() {
        return "toggle";
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

    public Toggle checked(boolean value) {
        this.checked = value;
        this.checkedWhen = null;
        syncVisuals();
        return this;
    }

    public Toggle checked(BooleanSupplier condition) {
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

    public Toggle onChange(BiConsumer<Player, Boolean> onChange) {
        this.onChange = onChange;
        return this;
    }

    public Toggle trackSize(double width, double height) {
        this.trackWidth = Math.max(16, width);
        this.trackHeight = Math.max(10, height);
        invalidate();
        return this;
    }

    public double trackWidth() {
        return trackWidth;
    }

    public double trackHeight() {
        return trackHeight;
    }

    public Toggle activeColor(int argb) {
        this.activeColor = argb;
        syncVisuals();
        return this;
    }

    public int activeColor() {
        return activeColor;
    }

    public Toggle inactiveColor(int argb) {
        this.inactiveColor = argb;
        syncVisuals();
        return this;
    }

    public int inactiveColor() {
        return inactiveColor;
    }

    public Toggle hoverColor(int argb) {
        this.hoverColor = argb;
        syncVisuals();
        return this;
    }

    public int hoverColor() {
        return hoverColor;
    }

    public Toggle knobColor(int argb) {
        this.knobColor = argb;
        syncVisuals();
        return this;
    }

    public int knobColor() {
        return knobColor;
    }

    public Toggle fontSize(double heightPx) {
        this.fontSize = heightPx;
        if (labelText != null) {
            labelText.fontSize(heightPx);
            invalidate();
        }
        return this;
    }

    public double fontSize() {
        return fontSize;
    }

    public Toggle label(String text) {
        if (labelText != null) {
            labelText.text(TextContent.of(formatLabel(text)));
        } else if (text != null && !text.isEmpty()) {
            labelText = new Text(viewer, fontSize, formatLabel(text));
            children.add(labelText);
        }
        invalidate();
        return this;
    }

    public Text labelText() {
        return labelText;
    }

    private void syncVisuals() {
        int bg = checked ? activeColor : is(State.HOVERED) ? hoverColor : inactiveColor;
        trackBox.background(bg);
        knobBox.background(knobColor);
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
        return super.hasTransitions() || trackBox.hasTransitions();
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
        if (labelText == null) {
            return new Size(trackWidth, trackHeight);
        }
        double labelLimit = maxWidth < 0 ? UNBOUNDED : Math.max(0, maxWidth - trackWidth - gap);
        Size labelSize = labelText.outer(labelLimit);
        return new Size(trackWidth + gap + labelSize.width(), Math.max(trackHeight, labelSize.height()));
    }

    @Override
    protected double minContentWidth() {
        if (labelText == null) {
            return trackWidth;
        }
        return trackWidth + gap + labelText.minOuterWidth();
    }

    @Override
    protected void arrangeContent(Rect content) {
        double trackY = content.y() + Math.max(0, (content.height() - trackHeight) / 2.0);
        Rect trackRect = new Rect(content.x(), trackY, trackWidth, trackHeight);
        trackBox.arrange(trackRect, overflowsHere());

        double knobSize = Math.max(4.0, trackHeight - knobInset * 2.0);
        double knobX = checked ? content.x() + trackWidth - knobInset - knobSize : content.x() + knobInset;
        double knobY = trackY + knobInset;
        knobBox.arrange(new Rect(knobX, knobY, knobSize, knobSize), false);

        if (labelText != null) {
            double labelX = content.x() + trackWidth + gap;
            double labelW = Math.max(0, content.width() - trackWidth - gap);
            Size labelSize = labelText.outer(labelW);
            double labelY = content.y() + Math.max(0, (content.height() - labelSize.height()) / 2.0);
            labelText.arrange(new Rect(labelX, labelY, labelW, labelSize.height()), overflowsHere());
        }
    }
}
