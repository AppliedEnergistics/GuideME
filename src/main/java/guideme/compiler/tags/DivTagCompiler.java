package guideme.compiler.tags;

import guideme.compiler.PageCompiler;
import guideme.compiler.TagCompiler;
import guideme.document.block.LytBlockContainer;
import guideme.libs.mdast.mdx.model.MdxJsxFlowElement;
import java.util.Set;

public class DivTagCompiler implements TagCompiler {
    @Override
    public Set<String> getTagNames() {
        return Set.of("div");
    }

    @Override
    public void compileBlockContext(PageCompiler compiler, LytBlockContainer parent, MdxJsxFlowElement el) {
        compiler.compileBlockContext(el, parent);
    }
}
