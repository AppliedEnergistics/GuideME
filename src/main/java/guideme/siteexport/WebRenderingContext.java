package guideme.siteexport;

import guideme.libs.mdast.model.MdAstNode;
import guideme.libs.mdast.model.MdAstParent;
import guideme.siteexport.web.HTMLFragment;
import guideme.siteexport.web.HTMLTag;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Experimental
@ApiStatus.NonExtendable
public interface WebRenderingContext {
    ExportedItemInfo getExportedItem(String itemId);

    ExportedFluidInfo getExportedFluid(String fluidId);

    /**
     * Resolves the path from the current page to the given asset, which is for example the path returned
     * by an exported item icon.
     */
    String getAssetUrl(String assetPath);

    /**
     * Compile an error message to HTML.
     */
    HTMLTag compileError(String message);

    /**
     * {@return the children of this node compiled to HTML}
     */
    HTMLFragment compileChildren(MdAstParent<?> parentNode);

    /**
     * {@return the given node compiled to HTML}
     */
    HTMLFragment compile(MdAstNode node, MdAstParent<?> parentNode);
}
