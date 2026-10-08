package guideme.web;

import guideme.libs.mdast.model.MdAstNode;
import guideme.libs.mdast.model.MdAstParent;
import guideme.web.html.HtmlFragment;
import guideme.web.html.HtmlNode;
import guideme.web.html.HtmlTag;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

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
     * Copies a resource from {@code assets/<namespace>/<path>} on the classpath of the website generator into the
     * website and resolves the path from the current page to it. Use this for static images your renderers need, such
     * as textures from your mods resources.
     *
     * @throws IllegalArgumentException If the resource does not exist.
     */
    String getAssetUrl(Identifier resource);

    /**
     * Includes a stylesheet from the classpath of the website generator on the current page, i.e. for the styles your
     * renderer uses. Relative {@code url(...)} references in it are copied as well.
     *
     * @param resourcePath The path of the stylesheet on the classpath, i.e. {@code modid/web/recipes.css}.
     * @throws IllegalArgumentException If the resource does not exist.
     */
    void requireStylesheet(String resourcePath);

    /**
     * Includes a script from the classpath of the website generator on the current page. It is loaded with the
     * {@code defer} attribute.
     *
     * @param resourcePath The path of the script on the classpath, i.e. {@code modid/web/recipes.js}.
     * @throws IllegalArgumentException If the resource does not exist.
     */
    void requireScript(String resourcePath);

    /**
     * Resolves the path from the current page to another page.
     *
     * @throws IllegalArgumentException If the page does not exist.
     */
    String getPageUrl(String pageId);

    /**
     * Creates the icon of an item.
     *
     * @param link If true, the icon links to the primary page of the item, if it has one, and shows the name of the
     *             item on hover.
     */
    HtmlNode itemIcon(String itemId, boolean link);

    /**
     * Creates the icon of a fluid, or an error if the fluid was not exported.
     */
    HtmlNode fluidIcon(String fluidId);

    /**
     * Creates a link to the primary page of the item, which shows the icon of the item on hover. If the item has no
     * primary page, or it is the current page, no link is created.
     *
     * @param content The content of the link. If null, the name of the item is used.
     */
    HtmlNode itemLink(String itemId, @Nullable HtmlFragment content);

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
