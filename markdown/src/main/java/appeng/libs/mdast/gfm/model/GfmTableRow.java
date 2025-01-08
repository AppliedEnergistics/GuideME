package guideme.libs.mdast.gfm.model;

import guideme.libs.mdast.model.MdAstAnyContent;
import guideme.libs.mdast.model.MdAstParent;

public class GfmTableRow extends MdAstParent<GfmTableCell> implements MdAstAnyContent {
    public GfmTableRow() {
        super("tableRow");
    }

    @Override
    protected Class<GfmTableCell> childClass() {
        return GfmTableCell.class;
    }
}
