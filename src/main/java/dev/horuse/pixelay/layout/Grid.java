package dev.horuse.pixelay.layout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * CSS Grid: explicit tracks ({@link #columns}/{@link #rows}, sized in px, {@code fr} or {@code auto}),
 * implicit tracks of {@link #autoColumns}/{@link #autoRows} size, sparse auto-placement along
 * {@link #autoFlow} and per-item {@code gridRow}/{@code gridColumn}/{@code rowSpan}/{@code columnSpan}.
 * Items are placed in their area by {@link #align}/{@link #justifyItems} or their own
 * {@code alignSelf}/{@code justifySelf}.
 * <p>
 * Placement follows CSS: items with both lines set first (never moved, overlaps allowed), then items
 * locked to a line of the flow axis, then the rest with the auto-placement cursor. A line beyond the
 * explicit tracks creates implicit tracks.
 */
public final class Grid extends Container<Grid> {

    public enum Flow {
        ROW,
        COLUMN,
    }

    private Track[] columns = {};
    private Track[] rows = {};
    private Track autoColumns = Track.AUTO;
    private Track autoRows = Track.AUTO;
    private Flow flow = Flow.ROW;
    private Align alignItems = Align.STRETCH;
    private Align justifyItems = Align.STRETCH;
    /** {@code {row, column}} (0-based) per child, computed once per layout pass. */
    private int[][] placements;

    Grid(LayoutItem... items) {
        super(items);
    }

    /** Explicit column tracks (CSS {@code grid-template-columns}). */
    public Grid columns(Track... tracks) {
        this.columns = tracks;
        return this;
    }

    /** Explicit row tracks (CSS {@code grid-template-rows}). */
    public Grid rows(Track... tracks) {
        this.rows = tracks;
        return this;
    }

    /** Size of implicitly created columns (CSS {@code grid-auto-columns}). */
    public Grid autoColumns(Track track) {
        this.autoColumns = track;
        return this;
    }

    /** Size of implicitly created rows (CSS {@code grid-auto-rows}). */
    public Grid autoRows(Track track) {
        this.autoRows = track;
        return this;
    }

    /** Direction of auto-placement (CSS {@code grid-auto-flow}); default {@code ROW}. */
    public Grid autoFlow(Flow flow) {
        this.flow = flow;
        return this;
    }

    /** Vertical placement of every item inside its area (CSS {@code align-items}); default stretch. */
    public Grid align(Align align) {
        this.alignItems = align;
        return this;
    }

    /** Horizontal placement of every item inside its area (CSS {@code justify-items}); default stretch. */
    public Grid justifyItems(Align align) {
        this.justifyItems = align;
        return this;
    }

    @Override
    protected void invalidate() {
        super.invalidate();
        placements = null;
    }

    @Override
    protected Size measureContent(double maxWidth) {
        double[] widths = trackSizes(true, maxWidth, null);
        double[] heights = trackSizes(false, UNBOUNDED, widths);
        return new Size(sum(widths), sum(heights));
    }

    @Override
    protected double minContentWidth() {
        int[][] placements = placements();
        double[] widths = new double[trackCount(true)];
        for (int i = 0; i < children.size(); i++) {
            Box<?> child = children.get(i);
            if (child.columnSpan == 1) {
                int column = placements[i][1];
                widths[column] = Math.max(widths[column], child.minOuterWidth());
            }
        }
        for (int t = 0; t < widths.length; t++) {
            Track track = track(true, t);
            if (track.kind() == Track.Kind.PX) {
                widths[t] = track.value();
            }
        }
        return sum(widths);
    }

    @Override
    protected void arrangeContent(Rect content) {
        int[][] placements = placements();
        double[] widths = trackSizes(true, content.width(), null);
        double[] heights = trackSizes(false, content.height(), widths);
        double[] xs = offsets(widths, content.x());
        double[] ys = offsets(heights, content.y());

        for (int i = 0; i < children.size(); i++) {
            Box<?> child = children.get(i);
            int row = placements[i][0];
            int column = placements[i][1];
            int lastRow = Math.min(heights.length, row + child.rowSpan) - 1;
            int lastColumn = Math.min(widths.length, column + child.columnSpan) - 1;
            Rect area = new Rect(
                xs[column],
                ys[row],
                xs[lastColumn] + widths[lastColumn] - xs[column],
                ys[lastRow] + heights[lastRow] - ys[row]
            );

            Align horizontal = child.justifySelf != null ? child.justifySelf : justifyItems;
            Align vertical = child.alignSelf != null ? child.alignSelf : alignItems;
            Rect placed = area.place(
                child.outer(area.width()),
                horizontal,
                vertical,
                horizontal == Align.STRETCH && !child.hasFixedWidth(),
                vertical == Align.STRETCH && !child.hasFixedHeight()
            );
            child.arrange(placed.inset(child.margin), overflowsHere());
        }
    }

    // --- track sizing ---

    private int trackCount(boolean horizontal) {
        int[][] placements = placements();
        int count = (horizontal ? columns : rows).length;
        for (int i = 0; i < children.size(); i++) {
            Box<?> child = children.get(i);
            int start = horizontal ? placements[i][1] : placements[i][0];
            count = Math.max(count, start + (horizontal ? child.columnSpan : child.rowSpan));
        }
        return count;
    }

    private Track track(boolean horizontal, int index) {
        Track[] template = horizontal ? columns : rows;
        return index < template.length ? template[index] : horizontal ? autoColumns : autoRows;
    }

    /**
     * Track sizes along one axis: px as given; auto from the items in it (span 1 first, then the
     * extra size of spanning items shared between the auto tracks they cover); fr shares what is left
     * of {@code available}, or sizes like auto when there is no limit. Auto columns are measured within
     * the space px tracks leave ({@code fit-content}); rows measure items wrapped to their column width.
     */
    private double[] trackSizes(boolean horizontal, double available, double[] columnWidths) {
        int[][] placements = placements();
        int count = trackCount(horizontal);
        Track[] tracks = new Track[count];
        double[] sizes = new double[count];
        double frTotal = 0;
        double fixed = gap * Math.max(0, count - 1);
        for (int t = 0; t < count; t++) {
            tracks[t] = track(horizontal, t);
            switch (tracks[t].kind()) {
                case PX -> {
                    sizes[t] = tracks[t].value();
                    fixed += sizes[t];
                }
                case FR -> frTotal += tracks[t].value();
                case AUTO -> {
                }
            }
        }
        double autoLimit = available < 0 ? UNBOUNDED : Math.max(0, available - fixed);

        // span 1 items size their own content-sized track
        for (int i = 0; i < children.size(); i++) {
            Box<?> child = children.get(i);
            int t = horizontal ? placements[i][1] : placements[i][0];
            if ((horizontal ? child.columnSpan : child.rowSpan) == 1 && contentSized(tracks[t], available)) {
                sizes[t] = Math.max(sizes[t], itemSize(child, i, horizontal, autoLimit, columnWidths));
            }
        }

        // spanning items: share what their tracks still lack between the content-sized ones
        for (int i = 0; i < children.size(); i++) {
            Box<?> child = children.get(i);
            int span = horizontal ? child.columnSpan : child.rowSpan;
            if (span <= 1) {
                continue;
            }
            int start = horizontal ? placements[i][1] : placements[i][0];
            double covered = gap * (span - 1);
            List<Integer> growable = new ArrayList<>();
            for (int t = start; t < Math.min(count, start + span); t++) {
                covered += sizes[t];
                if (contentSized(tracks[t], available)) {
                    growable.add(t);
                }
            }
            double missing = itemSize(child, i, horizontal, autoLimit, columnWidths) - covered;
            if (missing > 0 && !growable.isEmpty()) {
                for (int t : growable) {
                    sizes[t] += missing / growable.size();
                }
            }
        }

        if (available >= 0 && frTotal > 0) {
            double free = Math.max(0, available - sum(sizes));
            for (int t = 0; t < count; t++) {
                if (tracks[t].kind() == Track.Kind.FR) {
                    sizes[t] = (free * tracks[t].value()) / frTotal;
                }
            }
        }
        return sizes;
    }

    private static boolean contentSized(Track track, double available) {
        return track.kind() == Track.Kind.AUTO || (track.kind() == Track.Kind.FR && available < 0);
    }

    private double itemSize(Box<?> child, int index, boolean horizontal, double autoLimit, double[] columnWidths) {
        if (horizontal) {
            return child.outer(autoLimit).width();
        }
        double width =
            columnWidths == null ? UNBOUNDED : spanWidth(placements()[index][1], child.columnSpan, columnWidths);
        return child.outer(width).height();
    }

    private double spanWidth(int column, int span, double[] widths) {
        double width = gap * (span - 1);
        for (int c = column; c < Math.min(widths.length, column + span); c++) {
            width += widths[c];
        }
        return width;
    }

    // --- placement ---

    private int[][] placements() {
        if (placements != null) {
            return placements;
        }

        boolean byRow = flow == Flow.ROW;
        // The flow wraps after the explicit tracks of the other axis; lines beyond them add implicit tracks.
        int minorCount = Math.max(1, byRow ? columns.length : rows.length);
        for (Box<?> child : children) {
            int fixedMinor = (byRow ? child.gridColumn : child.gridRow) - 1;
            int minorSpan = byRow ? child.columnSpan : child.rowSpan;
            if (fixedMinor >= 0) {
                minorCount = Math.max(minorCount, fixedMinor + minorSpan);
            }
        }

        Occupancy occupancy = new Occupancy(minorCount);
        placements = new int[children.size()][];

        // 1) both lines given: placed as is
        for (int i = 0; i < children.size(); i++) {
            Box<?> child = children.get(i);
            int major = majorLine(child, byRow);
            int minor = minorLine(child, byRow);
            if (major >= 0 && minor >= 0) {
                place(i, child, byRow, major, minor, occupancy);
            }
        }

        // 2) only the flow-axis line given: first free spot along that line
        for (int i = 0; i < children.size(); i++) {
            Box<?> child = children.get(i);
            int major = majorLine(child, byRow);
            if (major < 0 || minorLine(child, byRow) >= 0) {
                continue;
            }
            int minorSpan = Math.min(minorCount, minorSpan(child, byRow));
            int minor = 0;
            while (
                minor + minorSpan <= minorCount && !occupancy.fits(major, minor, majorSpan(child, byRow), minorSpan)
            ) {
                minor++;
            }
            // No room on that line: overlap at its start rather than move the item off its line.
            place(i, child, byRow, major, minor + minorSpan <= minorCount ? minor : 0, occupancy);
        }

        // 3) the rest: sparse auto-placement with a cursor
        int cursorMajor = 0;
        int cursorMinor = 0;
        for (int i = 0; i < children.size(); i++) {
            Box<?> child = children.get(i);
            if (majorLine(child, byRow) >= 0) {
                continue;
            }
            int majorSpan = majorSpan(child, byRow);
            int minorSpan = Math.min(minorCount, minorSpan(child, byRow));
            int fixedMinor = minorLine(child, byRow);

            int major = cursorMajor;
            int minor;
            if (fixedMinor >= 0) {
                // Locked to a minor line: next spot on it at or after the cursor.
                if (fixedMinor < cursorMinor) {
                    major++;
                }
                minor = fixedMinor;
                while (!occupancy.fits(major, minor, majorSpan, minorSpan)) {
                    major++;
                }
            } else {
                minor = cursorMinor;
                while (!occupancy.fits(major, minor, majorSpan, minorSpan)) {
                    if (minor + 1 + minorSpan <= minorCount) {
                        minor++;
                    } else {
                        major++;
                        minor = 0;
                    }
                }
            }

            place(i, child, byRow, major, minor, occupancy);
            cursorMajor = major;
            cursorMinor = minor + minorSpan;
            if (cursorMinor >= minorCount) {
                cursorMajor++;
                cursorMinor = 0;
            }
        }
        return placements;
    }

    private void place(int index, Box<?> child, boolean byRow, int major, int minor, Occupancy occupancy) {
        occupancy.occupy(major, minor, majorSpan(child, byRow), minorSpan(child, byRow));
        placements[index] = byRow ? new int[] { major, minor } : new int[] { minor, major };
    }

    private static int majorLine(Box<?> child, boolean byRow) {
        return (byRow ? child.gridRow : child.gridColumn) - 1;
    }

    private static int minorLine(Box<?> child, boolean byRow) {
        return (byRow ? child.gridColumn : child.gridRow) - 1;
    }

    private static int majorSpan(Box<?> child, boolean byRow) {
        return byRow ? child.rowSpan : child.columnSpan;
    }

    private static int minorSpan(Box<?> child, boolean byRow) {
        return byRow ? child.columnSpan : child.rowSpan;
    }

    /** Taken cells along the flow: one row of {@code minorCount} cells per major line. */
    private static final class Occupancy {

        private final int minorCount;
        private final List<boolean[]> lines = new ArrayList<>();

        Occupancy(int minorCount) {
            this.minorCount = minorCount;
        }

        boolean fits(int major, int minor, int majorSpan, int minorSpan) {
            if (minor < 0 || minor + minorSpan > minorCount) {
                return false;
            }
            for (int a = major; a < Math.min(lines.size(), major + majorSpan); a++) {
                for (int b = minor; b < minor + minorSpan; b++) {
                    if (lines.get(a)[b]) {
                        return false;
                    }
                }
            }
            return true;
        }

        void occupy(int major, int minor, int majorSpan, int minorSpan) {
            while (lines.size() < major + majorSpan) {
                lines.add(new boolean[minorCount]);
            }
            int end = Math.min(minorCount, minor + minorSpan);
            for (int a = major; a < major + majorSpan; a++) {
                Arrays.fill(lines.get(a), minor, end, true);
            }
        }
    }

    // --- helpers ---

    private double[] offsets(double[] sizes, double start) {
        double[] result = new double[sizes.length];
        double cursor = start;
        for (int i = 0; i < sizes.length; i++) {
            result[i] = cursor;
            cursor += sizes[i] + gap;
        }
        return result;
    }

    private double sum(double[] sizes) {
        double total = gap * Math.max(0, sizes.length - 1);
        for (double size : sizes) {
            total += size;
        }
        return total;
    }
}
