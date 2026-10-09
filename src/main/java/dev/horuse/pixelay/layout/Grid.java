package dev.horuse.pixelay.layout;

/**
 * Fixed-size cells filled column by column ({@code rows} per column). An item taller than one cell
 * spans as many rows as it needs, moving to the next column when the current one has too few rows left.
 */
public final class Grid extends Container {

    private int rows = 1;
    private double cellWidth = 100;
    private double cellHeight = 100;
    private boolean fromBottom;
    private Align alignItems = Align.STRETCH;
    private Align justifyItems = Align.STRETCH;

    Grid(LayoutItem... items) {
        super(items);
    }

    public Grid rows(int rows) {
        this.rows = Math.max(1, rows);
        return this;
    }

    public Grid cell(double width, double height) {
        this.cellWidth = width;
        this.cellHeight = height;
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

    /** Fills every column from the bottom row up instead of top-down. */
    public Grid fromBottom() {
        this.fromBottom = true;
        return this;
    }

    @Override
    protected double[] measureContent() {
        int[][] placements = placements();
        int columns = placements.length == 0 ? 0 : placements[placements.length - 1][0] + 1;
        double width = columns * cellWidth + gap * Math.max(0, columns - 1);
        double height = rows * cellHeight + gap * (rows - 1);
        return new double[] { width, height };
    }

    @Override
    protected void arrangeContent(Rect content) {
        int[][] placements = placements();
        for (int i = 0; i < children.size(); i++) {
            int column = placements[i][0];
            int row = placements[i][1];
            int span = placements[i][2];
            double x = content.x() + column * (cellWidth + gap);
            double height = span * cellHeight + (span - 1) * gap;
            double y = fromBottom
                ? content.bottom() - row * (cellHeight + gap) - height
                : content.y() + row * (cellHeight + gap);
            Box child = children.get(i);
            child.arrange(itemRect(child, new Rect(x, y, cellWidth, height)).inset(child.margin));
        }
    }

    /** The item's rectangle inside its grid area, by {@code justifyItems} and {@code align}/{@code alignSelf}. */
    private Rect itemRect(Box child, Rect area) {
        double[] natural = outer(child);
        Align vertical = child.alignSelf != null ? child.alignSelf : alignItems;
        double width = justifyItems == Align.STRETCH ? area.width() : Math.min(area.width(), natural[0]);
        double height = vertical == Align.STRETCH ? area.height() : Math.min(area.height(), natural[1]);
        return new Rect(
            area.x() + justifyItems.offset(area.width(), width),
            area.y() + vertical.offset(area.height(), height),
            width,
            height
        );
    }

    /** {@code {column, row, span}} for every child. */
    private int[][] placements() {
        int[][] result = new int[children.size()][];
        int column = 0;
        int row = 0;
        for (int i = 0; i < children.size(); i++) {
            int span = Math.min(rows, rowSpan(children.get(i)));
            if (row + span > rows) {
                column++;
                row = 0;
            }
            result[i] = new int[] { column, row, span };
            row += span;
        }
        return result;
    }

    private int rowSpan(Box child) {
        double height = outer(child)[1];
        return Math.max(1, (int) Math.ceil((height + gap) / (cellHeight + gap)));
    }
}
