package pub.beermat.engine.eval

/**
 * A spreadsheet range, `B2`, `B2:B21` or `$B$2:$C$9` (docs/tables.md#cell-references): its rows
 * and columns counted from 0, row 1 and column A being 0. Used by tables and by `cells`.
 */
data class CellRange(val rows: IntRange, val cols: IntRange) {

    companion object {
        private val CELL = Regex("""\$?([A-Za-z]{1,3})\$?([0-9]{1,7})""")

        /** One cell's (row, column), or null if [text] isn't a cell name. */
        fun cell(text: String): Pair<Int, Int>? {
            val m = CELL.matchEntire(text.trim()) ?: return null
            val row = m.groupValues[2].toInt() - 1
            if (row < 0) return null
            return row to column(m.groupValues[1])
        }

        /** A range or a single cell; null if [text] is neither. */
        fun parse(text: String): CellRange? {
            val ends = text.trim().split(':')
            if (ends.size !in 1..2) return null
            val (a, b) = (cell(ends.first()) ?: return null) to (cell(ends.last()) ?: return null)
            return CellRange(minOf(a.first, b.first)..maxOf(a.first, b.first), minOf(a.second, b.second)..maxOf(a.second, b.second))
        }

        /** `A` is 0, `Z` 25, `AA` 26. */
        fun column(letters: String): Int = letters.uppercase().fold(0) { n, ch -> n * 26 + (ch - 'A' + 1) } - 1

        fun letters(column: Int): String {
            var n = column + 1
            val sb = StringBuilder()
            while (n > 0) {
                sb.append('A' + (n - 1) % 26)
                n = (n - 1) / 26
            }
            return sb.reverse().toString()
        }

        /** The cell's name: (1, 1) is `B2`. */
        fun name(row: Int, column: Int) = letters(column) + (row + 1)

        /**
         * The cells of [range] in [grid] (a table's whole grid, its header as row 1): one cell's
         * value, or a matrix of them.
         */
        fun take(grid: ArrayValue, range: String): Value {
            val r = parse(range) ?: throw EvalException("cells: '$range' is not a range such as \"B2:B21\"")
            if (r.rows.last >= grid.rows || r.cols.last >= grid.cols) throw EvalException("cells: $range is outside the table (${grid.rows} rows, ${grid.cols} columns)")
            if (r.rows.first == r.rows.last && r.cols.first == r.cols.last) return grid[r.rows.first, r.cols.first]
            return ArrayValue.of(r.rows.count(), r.cols.count()) { i, j -> grid[r.rows.first + i, r.cols.first + j] }
        }
    }
}
