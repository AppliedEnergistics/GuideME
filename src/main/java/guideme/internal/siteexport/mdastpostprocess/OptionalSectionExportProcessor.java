package guideme.internal.siteexport.mdastpostprocess;

import guideme.compiler.tags.OptionalSectionCompiler;
import guideme.libs.mdast.mdx.model.MdxJsxElementFields;
import guideme.libs.mdast.model.MdAstNode;
import guideme.siteexport.NodeSelector;
import guideme.siteexport.PageExportContext;
import guideme.siteexport.PageExportProcessor;

public final class OptionalSectionExportProcessor implements PageExportProcessor {
    private static final NodeSelector SELECTOR = NodeSelector.element(OptionalSectionCompiler.TAG_NAME);

    @Override
    public NodeSelector getSelector() {
        return SELECTOR;
    }

    @Override
    public void process(PageExportContext context, MdAstNode node) {
        String modId = ((MdxJsxElementFields) node).getAttributeString(OptionalSectionCompiler.MOD_ID_ATTR, "");
        if (OptionalSectionCompiler.test(modId)) {
            context.unwrap(node);
        } else {
            context.remove(node);
        }
    }
}
