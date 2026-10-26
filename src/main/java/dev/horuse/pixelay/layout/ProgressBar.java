package dev.horuse.pixelay.layout;

import dev.horuse.pixelay.data.Rect;
import dev.horuse.pixelay.data.Size;
import java.util.function.DoubleSupplier;

/**
 * An HTML {@code <progress>} meter component that renders a background track and a proportional
 * fill bar, supporting both static values ({@code 0.0..1.0}) and live dynamic {@link DoubleSupplier}s.
 */
public final class ProgressBar extends Container<ProgressBar> {

    private final Box<?> trackBox = new Box<>();
    private final Box<?> fillBox = new Box<>();

    private double progress;
    private DoubleSupplier progressSupplier;
    private double trackHeight = 10.0;
    private int trackColor = 0x1F000000;
    private int fillColor = 0xFF4F46E5;

    public ProgressBar(double progress) {
        this.progress = clamp(progress);
        init();
    }

    public ProgressBar(DoubleSupplier progressSupplier) {
        this.progressSupplier = progressSupplier;
        this.progress = progressSupplier != null ? clamp(progressSupplier.getAsDouble()) : 0.0;
        init();
    }

    private void init() {
        children.add(trackBox);
        children.add(fillBox);
        trackBox.background(trackColor);
        fillBox.background(fillColor);
    }

    @Override
    public String tagName() {
        return "progress";
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    public double progress() {
        if (progressSupplier != null) {
            progress = clamp(progressSupplier.getAsDouble());
        }
        return progress;
    }

    public ProgressBar progress(double value) {
        this.progress = clamp(value);
        this.progressSupplier = null;
        return this;
    }

    public ProgressBar progress(DoubleSupplier supplier) {
        this.progressSupplier = supplier;
        if (supplier != null) {
            this.progress = clamp(supplier.getAsDouble());
        }
        return this;
    }

    public ProgressBar trackHeight(double heightPx) {
        this.trackHeight = Math.max(2.0, heightPx);
        invalidate();
        return this;
    }

    public double trackHeight() {
        return trackHeight;
    }

    public ProgressBar trackColor(int argb) {
        this.trackColor = argb;
        trackBox.background(argb);
        return this;
    }

    public int trackColor() {
        return trackColor;
    }

    public ProgressBar fillColor(int argb) {
        this.fillColor = argb;
        fillBox.background(argb);
        return this;
    }

    public int fillColor() {
        return fillColor;
    }

    @Override
    void updateStates() {
        super.updateStates();
        if (progressSupplier != null) {
            progress = clamp(progressSupplier.getAsDouble());
        }
    }

    @Override
    protected Size measureContent(double maxWidth) {
        double w = maxWidth >= 0 ? maxWidth : 200.0;
        return new Size(w, trackHeight);
    }

    @Override
    protected double minContentWidth() {
        return 32.0;
    }

    @Override
    protected void arrangeContent(Rect content) {
        double h = hasFixedHeight() ? content.height() : trackHeight;
        double y = content.y() + Math.max(0, (content.height() - h) / 2.0);
        Rect trackRect = new Rect(content.x(), y, content.width(), h);
        trackBox.arrange(trackRect, overflowsHere());

        double current = progress();
        double fillW = content.width() * current;
        fillBox.arrange(new Rect(content.x(), y, fillW, h), false);
    }
}
