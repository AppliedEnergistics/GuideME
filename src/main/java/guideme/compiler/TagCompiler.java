package guideme.compiler;

import guideme.document.block.LytBlockContainer;
import guideme.document.flow.LytFlowParent;
import guideme.extensions.Extension;
import guideme.extensions.ExtensionPoint;
import guideme.libs.mdast.mdx.model.MdxJsxElementFields;
import guideme.libs.mdast.mdx.model.MdxJsxFlowElement;
import guideme.libs.mdast.mdx.model.MdxJsxTextElement;
import java.util.Set;

/**
 * Tag compilers handle HTML-like tags found in Markdown content, such as <code>&lt;Image /&gt;</code> and similar.
 */
public interface TagCompiler extends Extension {
    ExtensionPoint<TagCompiler> EXTENSION_POINT = new ExtensionPoint<>(TagCompiler.class);

    /**
     * The tag names this compiler is responsible for.
     */
    Set<String> getTagNames();

    default void compileBlockContext(PageCompiler compiler, LytBlockContainer parent, MdxJsxFlowElement el) {
        parent.append(compiler.createErrorBlock("Cannot use MDX tag " + el.name + " in block context", el));
    }

    default void compileFlowContext(PageCompiler compiler, LytFlowParent parent, MdxJsxTextElement el) {
        parent.append(compiler.createErrorFlowContent("Cannot use MDX tag " + el.name() + " in flow context", el));
    }

    default void index(IndexingContext indexer, MdxJsxElementFields el, IndexingSink sink) {
        indexer.indexContent(el.children(), sink);
    }
}
