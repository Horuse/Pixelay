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

    public static Options defaults() {
        return new Options(
            "<white>Change the size until all 4 marks are at the edges of your screen",
            "<white>-",
            "<white>+",
            "<green><b>Done",
            "<yellow><b>Done",
            percent -> "<yellow>" + percent + "%",
            null,
            80,
            12,
            0xFFFFFFFF,
            48,
            72,
            110,
            72,
            0.05
        );
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

        public Options withTexts(String hint, String minus, String plus, String done, String doneHover) {
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

        /** How the current size is shown, from the size in percent. */
        public Options withValue(IntFunction<String> value) {
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

        /** Draws the marks with font characters (top-left, top-right, bottom-left, bottom-right) of this height. */
        public Options withCornerGlyphs(String[] glyphs, double heightPx) {
            return new Options(
                hint,
                minus,
                plus,
                done,
                doneHover,
                value,
                glyphs,
                heightPx,
                cornerThicknessPx,
                cornerColor,
                hintSizePx,
                valueSizePx,
                buttonSizePx,
                doneSizePx,
                step
            );
        }

        public Options withSizes(double hint, double value, double button, double done) {
            return new Options(
                this.hint,
                minus,
                plus,
                this.done,
                doneHover,
                this.value,
                cornerGlyphs,
                cornerSizePx,
                cornerThicknessPx,
                cornerColor,
                hint,
                value,
                button,
                done,
                step
            );
        }

        public Options withStep(double step) {
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
