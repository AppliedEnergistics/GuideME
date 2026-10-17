package guideme.siteexport;

import guideme.libs.mdast.mdx.model.MdxJsxElementFields;
import guideme.libs.mdast.model.MdAstNode;
import java.util.Set;
import org.jetbrains.annotations.ApiStatus;

/**
 * Selects the Markdown AST nodes a {@link PageExportProcessor} applies to.
 */
@ApiStatus.Experimental
@FunctionalInterface
public interface NodeSelector {
    boolean matches(MdAstNode node);

    /**
     * Selects custom elements (i.e. {@code <Color>}) by their tag name, both in block and in inline content.
     */
    static NodeSelector element(String... tagNames) {
        var names = Set.of(tagNames);
        return node -> node instanceof MdxJsxElementFields element && names.contains(element.name());
    }

    /**
     * Selects nodes by their mdast type (i.e. {@code "image"} or {@code "link"}).
     */
    static NodeSelector type(String... types) {
        var typeSet = Set.of(types);
        return node -> typeSet.contains(node.type());
    }
}
