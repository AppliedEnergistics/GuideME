package guideme.siteexport;

import guideme.extensions.Extension;
import guideme.extensions.ExtensionPoint;
import guideme.libs.mdast.model.MdAstNode;
import org.jetbrains.annotations.ApiStatus;

/**
 * Modifies the Markdown AST of pages before they are exported for the website. This allows elements to resolve
 * information that is only available in-game, such as symbolic colors or whether a mod is loaded.
 * <p>
 * Processors can change the attributes of elements or replace elements entirely (i.e. with their own children), using
 * the methods of {@link PageExportContext}. These modifications are undone after the export, since the AST is shared
 * with the in-game guide.
 * <p>
 * Processors run before images and scenes are exported, so content that is unwrapped by a processor is exported
 * normally, and content that is removed is not exported at all.
 */
@ApiStatus.Experimental
public interface PageExportProcessor extends Extension {
    ExtensionPoint<PageExportProcessor> EXTENSION_POINT = new ExtensionPoint<>(PageExportProcessor.class);

    /**
     * {@return which nodes {@link #process} is called for}
     */
    NodeSelector getSelector();

    /**
     * Called for every node of the page matched by {@link #getSelector()}, in document order. If the node is replaced,
     * the nodes that replace it are processed as well.
     */
    void process(PageExportContext context, MdAstNode node);
}
