package guideme.internal.siteexport.mdastpostprocess;

import guideme.compiler.tags.OptionalSectionCompiler;
import guideme.libs.mdast.mdx.model.MdxJsxElementFields;
import guideme.libs.mdast.model.MdAstNode;
import guideme.libs.mdast.model.MdAstParent;
import java.util.List;

/**
 * Replaces optional sections with their content if their condition is met, or removes them otherwise.
 */
final class OptionalSectionExportProcessor {
    private OptionalSectionExportProcessor() {
    }

    @SuppressWarnings("unchecked")
    static void process(MdAstNode node) {
        if (!(node instanceof MdAstParent<?> parent)) {
            return;
        }

        var children = (List<Object>) parent.children();
        for (int i = 0; i < children.size(); i++) {
            var child = (MdAstNode) children.get(i);
            // Process nested sections first, so that unwrapped content does not need to be revisited
            process(child);

            if (child instanceof MdxJsxElementFields el && OptionalSectionCompiler.TAG_NAME.equals(el.name())) {
                children.remove(i);
                if (OptionalSectionCompiler.test(el.getAttributeString(OptionalSectionCompiler.MOD_ID_ATTR, ""))) {
                    var content = el.children();
                    children.addAll(i, content);
                    i += content.size();
                }
                i--;
            }
        }
    }
}
