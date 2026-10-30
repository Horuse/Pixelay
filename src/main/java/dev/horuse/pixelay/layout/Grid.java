package dev.horuse.pixelay.layout;

import dev.horuse.pixelay.data.Rect;
import dev.horuse.pixelay.data.Size;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Arranges child elements in a two-dimensional grid following CSS Grid rules.
 * Supports explicit tracks, implicit tracks, and item placement spans.
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
    /** Row and column coordinate pairs per child calculated during layout. */
    private int[][] placements;

    public Grid(LayoutItem... items) {
        super(items);
    }

    @Override
    public String tagName() {
        return "grid";
    }

    /** Defines explicit column tracks. */
    public Grid columns(Track... tracks) {
        this.columns = tracks;
        return this;
    }

    public List<Track> columns() {
        return List.of(columns);
    }

    /** Defines explicit row tracks. */
    public Grid rows(Track... tracks) {
        this.rows = tracks;
        return this;
    }

    public List<Track> rows() {
        return List.of(rows);
    }

    /** Sets size of implicit column tracks. */
    public Grid autoColumns(Track track) {
        this.autoColumns = track;
        return this;
    }

    public Track autoColumns() {
        return autoColumns;
    }

    /** Sets size of implicit row tracks. */
    public Grid autoRows(Track track) {
        this.autoRows = track;
        return this;
    }

    public Track autoRows() {
        return autoRows;
    }

    /** Sets auto-placement direction. */
    public Grid autoFlow(Flow flow) {
        this.flow = flow;
        return this;
    }

    public Flow autoFlow() {
        return flow;
    }

    /** Sets vertical item placement within grid areas. */
    public Grid align(Align align) {
        this.alignItems = align;
        return this;
    }

    public Align alignItems() {
        return alignItems;
    }

    /** Sets horizontal item placement within grid areas. */
    public Grid justifyItems(Align align) {
        this.justifyItems = align;
        return this;
    }

    public Align justifyItems() {
        return justifyItems;
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
        return new Size(totalWithGaps(widths), totalWithGaps(heights));
    }

    @Override
    protected double minContentWidth() {
        List<Box<?>> flow = flowChildren();
        int[][] placements = placements(flow);
        double[] widths = new double[trackCount(flow, true)];
        for (int i = 0; i < flow.size(); i++) {
            Box<?> child = flow.get(i);
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
        return totalWithGaps(widths);
    }

    @Override
    protected void arrangeContent(Rect content) {
        double sx = visualScaleX();
        double sy = visualScaleY();
        Rect unscaled = new Rect(
            content.x(),
            content.y(),
            sx > 1e-4 ? content.width() / sx : content.width(),
            sy > 1e-4 ? content.height() / sy : content.height()
        );
        List<Box<?>> flow = flowChildren();
        int[][] placements = placements(flow);
        double[] widths = trackSizes(true, unscaled.width(), null);
        double[] heights = trackSizes(false, unscaled.height(), widths);
        double[] xs = offsets(widths, unscaled.x());
        double[] ys = offsets(heights, unscaled.y());

        for (int i = 0; i < flow.size(); i++) {
            Box<?> child = flow.get(i);
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
            Rect placed = Align.place(
                area,
                child.outer(area.width()),
                horizontal,
                vertical,
                horizontal == Align.STRETCH && !child.hasFixedWidth(),
                vertical == Align.STRETCH && !child.hasFixedHeight()
            );
            Rect unscaledChild = child.effectiveMargin().shrink(placed);
            Rect scaledChild = new Rect(
                content.x() + (unscaledChild.x() - content.x()) * sx,
                content.y() + (unscaledChild.y() - content.y()) * sy,
                unscaledChild.width() * sx,
                unscaledChild.height() * sy
            );
            arrangeChild(child, scaledChild, overflowsHere());
        }
    }

    // --- track sizing ---

    private int trackCount(List<Box<?>> flow, boolean horizontal) {
        int[][] placements = placements(flow);
        int count = (horizontal ? columns : rows).length;
        for (int i = 0; i < flow.size(); i++) {
            Box<?> child = flow.get(i);
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
     * Calculates track sizes along one axis.
     * Fixed tracks use specified sizes.
     * Automatic tracks measure content.
     * Fractional tracks distribute remaining space.
     */
    private double[] trackSizes(boolean horizontal, double available, double[] columnWidths) {
        List<Box<?>> flow = flowChildren();
        int[][] placements = placements(flow);
        int count = trackCount(flow, horizontal);
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

        // Single-span items size their own content track.
        for (int i = 0; i < flow.size(); i++) {
            Box<?> child = flow.get(i);
            int t = horizontal ? placements[i][1] : placements[i][0];
            if ((horizontal ? child.columnSpan : child.rowSpan) == 1 && contentSized(tracks[t], available)) {
                sizes[t] = Math.max(sizes[t], itemSize(flow, child, i, horizontal, autoLimit, columnWidths));
            }
        }

        // Multi-span items distribute remaining required size across covered tracks.
        for (int i = 0; i < flow.size(); i++) {
            Box<?> child = flow.get(i);
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
            double missing = itemSize(flow, child, i, horizontal, autoLimit, columnWidths) - covered;
            if (missing > 0 && !growable.isEmpty()) {
                for (int t : growable) {
                    sizes[t] += missing / growable.size();
                }
            }
        }

        if (horizontal && autoLimit >= 0) {
            fitAutoColumns(flow, tracks, sizes, placements, autoLimit);
        }

        if (available >= 0 && frTotal > 0) {
            double free = Math.max(0, available - totalWithGaps(sizes));
            for (int t = 0; t < count; t++) {
                if (tracks[t].kind() == Track.Kind.FR) {
                    sizes[t] = (free * tracks[t].value()) / frTotal;
                }
            }
        }
        return sizes;
    }

    /**
     * Reduces automatic column sizes proportionally while respecting min-content limits.
     */
    private void fitAutoColumns(
        List<Box<?>> flow,
        Track[] tracks,
        double[] sizes,
        int[][] placements,
        double autoLimit
    ) {
        double autoTotal = 0;
        double[] floors = new double[sizes.length];
        for (int t = 0; t < sizes.length; t++) {
            if (tracks[t].kind() == Track.Kind.AUTO) {
                autoTotal += sizes[t];
            }
        }
        if (autoTotal <= autoLimit) {
            return;
        }
        for (int i = 0; i < flow.size(); i++) {
            Box<?> child = flow.get(i);
            int t = placements[i][1];
            if (child.columnSpan == 1 && tracks[t].kind() == Track.Kind.AUTO) {
                floors[t] = Math.max(floors[t], child.minOuterWidth());
            }
        }
        double ratio = autoLimit / autoTotal;
        for (int t = 0; t < sizes.length; t++) {
            if (tracks[t].kind() == Track.Kind.AUTO) {
                sizes[t] = Math.max(floors[t], sizes[t] * ratio);
            }
        }
    }

    private static boolean contentSized(Track track, double available) {
        return track.kind() == Track.Kind.AUTO || (track.kind() == Track.Kind.FR && available < 0);
    }

    private double itemSize(
        List<Box<?>> flow,
        Box<?> child,
        int index,
        boolean horizontal,
        double autoLimit,
        double[] columnWidths
    ) {
        if (horizontal) {
            return child.outer(autoLimit).width();
        }
        double width =
            columnWidths == null ? UNBOUNDED : spanWidth(placements(flow)[index][1], child.columnSpan, columnWidths);
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

    private int[][] placements(List<Box<?>> flow) {
        if (placements != null) {
            return placements;
        }

        boolean byRow = this.flow == Flow.ROW;
        // Wrap placement along minor axis and add implicit tracks when required.
        int minorCount = Math.max(1, byRow ? columns.length : rows.length);
        for (Box<?> child : flow) {
            int fixedMinor = (byRow ? child.gridColumn : child.gridRow) - 1;
            int minorSpan = byRow ? child.columnSpan : child.rowSpan;
            if (fixedMinor >= 0) {
                minorCount = Math.max(minorCount, fixedMinor + minorSpan);
            }
        }

        Occupancy occupancy = new Occupancy(minorCount);
        placements = new int[flow.size()][];

        // Place items with explicit coordinates on both axes first.
        for (int i = 0; i < flow.size(); i++) {
            Box<?> child = flow.get(i);
            int major = majorLine(child, byRow);
            int minor = minorLine(child, byRow);
            if (major >= 0 && minor >= 0) {
                place(i, child, byRow, major, minor, occupancy);
            }
        }

        // Place items locked to the major axis in the first free cell.
        for (int i = 0; i < flow.size(); i++) {
            Box<?> child = flow.get(i);
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
            // Place at line start if no vacant space remains.
            place(i, child, byRow, major, minor + minorSpan <= minorCount ? minor : 0, occupancy);
        }

        // Place remaining items using sparse auto-placement cursor.
        int cursorMajor = 0;
        int cursorMinor = 0;
        for (int i = 0; i < flow.size(); i++) {
            Box<?> child = flow.get(i);
            if (majorLine(child, byRow) >= 0) {
                continue;
            }
            int majorSpan = majorSpan(child, byRow);
            int minorSpan = Math.min(minorCount, minorSpan(child, byRow));
            int fixedMinor = minorLine(child, byRow);

            int major = cursorMajor;
            int minor;
            if (fixedMinor >= 0) {
                // Advance cursor to find next vacant space for minor-axis locked item.
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

    /** Tracks occupied grid cells along the placement flow. */
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

    private double totalWithGaps(double[] sizes) {
        double total = gap * Math.max(0, sizes.length - 1);
        for (double size : sizes) {
            total += size;
        }
        return total;
    }
}
