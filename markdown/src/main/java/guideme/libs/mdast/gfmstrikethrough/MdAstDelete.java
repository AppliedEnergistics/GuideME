package guideme.libs.mdast.gfmstrikethrough;

import guideme.libs.mdast.model.MdAstParent;
import guideme.libs.mdast.model.MdAstPhrasingContent;
import guideme.libs.mdast.model.MdAstStaticPhrasingContent;

/**
 * Delete (Parent) represents contents that are no longer accurate or no longer relevant. Delete can be used where
 * phrasing content is expected. Its content model is phrasing content.
 * 
 * <pre>
 * {type:'delete',children:[{type:'text',value:'alpha'}]}
 * </pre>
 */
public class MdAstDelete extends MdAstParent<MdAstPhrasingContent> implements MdAstStaticPhrasingContent {
    public MdAstDelete() {
        super("delete");
    }

    @Override
    protected Class<MdAstPhrasingContent> childClass() {
        return MdAstPhrasingContent.class;
    }
}
