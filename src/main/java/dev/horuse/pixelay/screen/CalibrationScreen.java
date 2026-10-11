package dev.horuse.pixelay.screen;

import static dev.horuse.pixelay.layout.Layout.column;
import static dev.horuse.pixelay.layout.Layout.mount;
import static dev.horuse.pixelay.layout.Layout.node;
import static dev.horuse.pixelay.layout.Layout.row;
import static dev.horuse.pixelay.layout.Layout.stack;

import dev.horuse.pixelay.components.CanvasText;
import dev.horuse.pixelay.content.TextContent;
import dev.horuse.pixelay.layout.Align;
import dev.horuse.pixelay.layout.Box;
import dev.horuse.pixelay.layout.LayoutItem;
import dev.horuse.pixelay.layout.MountedLayout;
import dev.horuse.pixelay.layout.State;
import java.util.Objects;
import java.util.function.IntFunction;

/**
 * The screen-size calibration of a {@link ScreenSession}: four corner marks on the canvas edges, a hint,
 * {@code -}/{@code +} buttons with the current size and a done button. The player changes the size until
 * all four marks sit at the edges of their screen; the result is saved per player.
 * <p>
 * The marks are coloured panels by default, so it works without a resource pack; give
 * {@link Options#cornerGlyphs} to draw them with your own font characters instead (crop their textures to
 * the drawing, see the wiki).
 */
public final class CalibrationScreen {

    private final ScreenSession session;
    private final Options options;

    CalibrationScreen(ScreenSession session, Options options) {
        this.session = session;
        this.options = options;
    }

    /** Options with the defaults; change what you need and {@code build()}. */
    public static Options.Builder options() {
        return new Options.Builder();
    }

    MountedLayout build() {
        CanvasText percent = text(
            options.valueSizePx(),
            TextContent.supplier(() -> options.value().apply((int) Math.round(session.scale() * 100)))
        );
        return mount(
            stack(
                corner(0, Align.START, Align.START),
                corner(1, Align.END, Align.START),
                corner(2, Align.START, Align.END),
                corner(3, Align.END, Align.END),
                column(
                    node(text(options.hintSizePx(), TextContent.of(options.hint()))).justifySelf(Align.CENTER),
                    row(
                        node(text(options.buttonSizePx(), TextContent.of(options.minus())))
                            .padding(16)
                            .alignSelf(Align.CENTER)
                            .onClick(player -> session.stepScale(-1)),
                        node(percent).padding(16).alignSelf(Align.CENTER),
                        node(text(options.buttonSizePx(), TextContent.of(options.plus())))
                            .padding(16)
                            .alignSelf(Align.CENTER)
                            .onClick(player -> session.stepScale(1))
                    ).gap(24),
                    node(text(options.doneSizePx(), TextContent.of(options.done())))
                        .padding(16)
                        .state(State.HOVERED, style -> {
                            if (options.doneHover() != null) {
                                style.text(options.doneHover());
                            }
                        })
                        .onClick(player -> session.finishCalibration())
                )
                    .align(Align.CENTER)
                    .gap(48)
                    .justifySelf(Align.CENTER)
                    .alignSelf(Align.CENTER)
            )
        );
    }

    /** Corner {@code index} (top-left, top-right, bottom-left, bottom-right), pinned to its canvas corner. */
    private LayoutItem corner(int index, Align horizontal, Align vertical) {
        Box<?> mark;
        if (options.cornerGlyphs() != null) {
            mark = node(text(options.cornerSizePx(), TextContent.of(options.cornerGlyphs()[index])));
        } else {
            // An L of two bars, opened towards the canvas centre.
            double length = options.cornerSizePx();
            double thickness = options.cornerThicknessPx();
            mark = stack(
                stack().size(length, thickness).background(options.cornerColor()).alignSelf(vertical),
                stack().size(thickness, length).background(options.cornerColor()).justifySelf(horizontal)
            ).size(length, length);
        }
        return mark.justifySelf(horizontal).alignSelf(vertical);
    }

    private CanvasText text(double heightPx, TextContent content) {
        return CanvasText.fake(session.player(), 0, 0, heightPx, content);
    }

    /**
     * Texts are MiniMessage; {@code doneHover} (optional) replaces {@code done} under the cursor, {@code value}
     * renders the size in percent. {@code cornerGlyphs} (4 characters: top-left, top-right, bottom-left,
     * bottom-right) replaces the panel marks, {@code cornerSizePx} is then their text height. {@code step} is
     * the scale change of one {@code -}/{@code +} click (0.05 = 5%).
     */
    public record Options(
        String hint,
        String minus,
        String plus,
        String done,
        String doneHover,
        IntFunction<String> value,
        String[] cornerGlyphs,
        double cornerSizePx,
        double cornerThicknessPx,
        int cornerColor,
        double hintSizePx,
        double valueSizePx,
        double buttonSizePx,
        double doneSizePx,
        double step
    ) {
        public Options {
            Objects.requireNonNull(hint);
            Objects.requireNonNull(value);
            if (cornerGlyphs != null && cornerGlyphs.length != 4) {
                throw new IllegalArgumentException("cornerGlyphs needs 4 characters");
            }
        }

        /** Builds {@link Options}; every setter is optional. */
        public static final class Builder {

            private String hint = "<white>Change the size until all 4 marks are at the edges of your screen";
            private String minus = "<white>-";
            private String plus = "<white>+";
            private String done = "<green><b>Done";
            private String doneHover = "<yellow><b>Done";
            private IntFunction<String> value = percent -> "<yellow>" + percent + "%";
            private String[] cornerGlyphs;
            private double cornerSizePx = 80;
            private double cornerThicknessPx = 12;
            private int cornerColor = 0xFFFFFFFF;
            private double hintSizePx = 48;
            private double valueSizePx = 72;
            private double buttonSizePx = 110;
            private double doneSizePx = 72;
            private double step = 0.05;

            private Builder() {}

            /** The hint above the controls (MiniMessage). */
            public Builder hint(String hint) {
                this.hint = hint;
                return this;
            }

            /** The {@code -} and {@code +} button texts (MiniMessage). */
            public Builder buttons(String minus, String plus) {
                this.minus = minus;
                this.plus = plus;
                return this;
            }

            /** The done button text, and its text under the cursor ({@code null} = the same). */
            public Builder done(String done, String hovered) {
                this.done = done;
                this.doneHover = hovered;
                return this;
            }

            /** How the current size is shown, from the size in percent. */
            public Builder value(IntFunction<String> value) {
                this.value = value;
                return this;
            }

            /** Panel corner marks: an L of two bars, {@code size} long and {@code thickness} wide (canvas px). */
            public Builder cornerPanels(double size, double thickness, int argb) {
                this.cornerGlyphs = null;
                this.cornerSizePx = size;
                this.cornerThicknessPx = thickness;
                this.cornerColor = argb;
                return this;
            }

            /** Corner marks drawn with 4 font characters (top-left, top-right, bottom-left, bottom-right). */
            public Builder cornerGlyphs(String[] glyphs, double heightPx) {
                this.cornerGlyphs = glyphs;
                this.cornerSizePx = heightPx;
                return this;
            }

            /** Text heights (canvas px) of the hint, the size value, the {@code -}/{@code +} buttons and done. */
            public Builder sizes(double hint, double value, double buttons, double done) {
                this.hintSizePx = hint;
                this.valueSizePx = value;
                this.buttonSizePx = buttons;
                this.doneSizePx = done;
                return this;
            }

            /** Scale change of one {@code -}/{@code +} click (0.05 = 5%). */
            public Builder step(double step) {
                this.step = step;
                return this;
            }

            public Options build() {
                return new Options(
                    hint,
                    minus,
                    plus,
                    done,
                    doneHover,
                    value,
                    cornerGlyphs,
                    cornerSizePx,
                    cornerThicknessPx,
                    cornerColor,
                    hintSizePx,
                    valueSizePx,
                    buttonSizePx,
                    doneSizePx,
                    step
                );
            }
        }
    }
}
