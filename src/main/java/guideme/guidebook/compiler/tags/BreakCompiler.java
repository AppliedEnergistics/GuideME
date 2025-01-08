package guideme.guidebook.compiler.tags;

import guideme.guidebook.compiler.PageCompiler;
import guideme.guidebook.document.flow.LytFlowBreak;
import guideme.guidebook.document.flow.LytFlowParent;
import guideme.libs.mdast.mdx.model.MdxJsxElementFields;
import guideme.libs.mdast.model.MdAstNode;
import java.util.Set;

public class BreakCompiler extends FlowTagCompiler {
    @Override
    public Set<String> getTagNames() {
        return Set.of("br");
    }

    @Override
    protected void compile(PageCompiler compiler, LytFlowParent parent, MdxJsxElementFields el) {
        var br = new LytFlowBreak();
        var clear = el.getAttributeString("clear", "none");
        switch (clear) {
            case "left" -> br.setClearLeft(true);
            case "right" -> br.setClearRight(true);
            case "all" -> {
                br.setClearLeft(true);
                br.setClearRight(true);
            }
            case "none" -> {
            }
            default -> parent.append(compiler.createErrorFlowContent("Invalid 'clear' attribute", (MdAstNode) el));
        }

        parent.append(br);
    }
}
