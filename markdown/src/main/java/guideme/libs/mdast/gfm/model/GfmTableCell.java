package guideme.libs.mdast.gfm.model;

import guideme.libs.mdast.model.MdAstAnyContent;
import guideme.libs.mdast.model.MdAstParent;
import guideme.libs.mdast.model.MdAstPhrasingContent;

public class GfmTableCell extends MdAstParent<MdAstPhrasingContent> implements MdAstAnyContent {
    public static final String TYPE = "tableCell";

    public GfmTableCell() {
        super(TYPE);
    }

    @Override
    protected Class<MdAstPhrasingContent> childClass() {
        return MdAstPhrasingContent.class;
    }
}
