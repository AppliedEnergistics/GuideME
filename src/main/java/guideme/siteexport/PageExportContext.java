package guideme.siteexport;

import guideme.document.block.LytNode;
import guideme.extensions.Extension;
import guideme.extensions.ExtensionPoint;
import guideme.libs.mdast.model.MdAstNode;
import java.util.List;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

/**
 * Gives {@link PageExportProcessor} access to the page being exported. Processors work on a copy of the page, so they
 * can modify it freely without affecting the in-game guide.
 */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
public interface PageExportContext {
    ResourceExporter exporter();

    Identifier pageId();

    /**
     * Resolves an id relative to the page, using the namespace of the page if the id has none.
     */
    Identifier resolveId(String id);

    /**
     * {@return the extensions of the guide being exported}
     */
    <T extends Extension> List<T> getExtensions(ExtensionPoint<T> extensionPoint);

    /**
     * {@return the parent of the node, or null for the root of the page}
     */
    @Nullable
    MdAstNode getParent(MdAstNode node);

    /**
     * {@return the layout nodes compiled from the given node for the in-game guide} This allows access to information
     * that is only known after compiling the page, such as the scene of a {@code <GameScene>}.
     */
    List<LytNode> getLayoutNodes(MdAstNode node);

    /**
     * Replaces the node with the given nodes.
     *
     * @throws IllegalArgumentException If the parent of the node cannot contain the replacement nodes.
     */
    void replace(MdAstNode node, List<? extends MdAstNode> replacement);

    /**
     * Replaces the node with its own children, i.e. for elements that only affect whether their content is shown.
     */
    void unwrap(MdAstNode node);

    /**
     * Removes the node and its content from the page.
     */
    void remove(MdAstNode node);
}
