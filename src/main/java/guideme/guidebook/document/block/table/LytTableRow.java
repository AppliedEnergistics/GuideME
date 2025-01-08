package guideme.guidebook.document.block.table;

import guideme.guidebook.document.LytRect;
import guideme.guidebook.document.block.LytNode;
import java.util.ArrayList;
import java.util.List;

/**
 * A row in {@link LytTable}. Contains {@link LytTableCell}.
 */
public class LytTableRow extends LytNode {
    private final LytTable table;
    private final List<LytTableCell> cells = new ArrayList<>();
    LytRect bounds = LytRect.empty();

    public LytTableRow(LytTable table) {
        this.table = table;
        this.parent = table;
    }

    @Override
    public LytRect getBounds() {
        return bounds;
    }

    public LytTableCell appendCell() {
        var cell = new LytTableCell(table, this, table.getOrCreateColumn(cells.size()));
        cells.add(cell);
        return cell;
    }

    @Override
    public List<LytTableCell> getChildren() {
        return cells;
    }
}
