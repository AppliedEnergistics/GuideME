package guideme.siteexport;

import guideme.libs.mdast.model.MdAstNode;
import guideme.libs.mdast.model.MdAstParent;
import guideme.siteexport.web.ExportedGuide;
import guideme.siteexport.web.HtmlFragment;
import guideme.siteexport.web.HtmlTag;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Experimental
@ApiStatus.NonExtendable
public interface WebRenderingContext {
    /**
     * Access to the exported guide data.
     */
    ExportedGuide guide();

    /**
     * Resolves the path from the current page to the given asset, which is for example the path returned by an exported
     * item icon.
     */
    String getAssetUrl(String assetPath);

    /**
     * Compile an error message to HTML.
     */
    HtmlTag compileError(String message);

    /**
     * {@return the children of this node compiled to HTML}
     */
    HtmlFragment compileChildren(MdAstParent<?> parentNode);

    /**
     * {@return the given node compiled to HTML}
     */
    HtmlFragment compile(MdAstNode node, MdAstParent<?> parentNode);
}
