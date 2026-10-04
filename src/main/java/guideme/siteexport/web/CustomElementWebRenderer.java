package guideme.siteexport.web;

import guideme.compiler.TagCompiler;
import java.util.Set;
import java.util.function.Consumer;
import org.jetbrains.annotations.ApiStatus;

/**
 * Compiles custom elements (i.e. those added through {@link TagCompiler}) to HTML for the website.
 * <p>
 * <b>NOTE:</b> This is loaded through the Java {@link java.util.ServiceLoader} mechanism by the
 * {@link WebSiteGenerator}. Implementations run outside the game. They may use Minecraft classes, but must not access
 * registries or other game state. Everything they need must come from the element or
 * {@link CustomElementWebRenderingContext#guide()}. Data that is only available in-game can be exported using
 * {@link guideme.siteexport.ResourceExporter#addExtraData}.
 */
@ApiStatus.Experimental
public interface CustomElementWebRenderer {
    /**
     * The tag names this compiler is responsible for.
     *
     * @see TagCompiler#getTagNames()
     */
    Set<String> getTagNames();

    /**
     * Compiles the given custom element to HTML for insertion into the resulting web page.
     */
    void render(CustomElementWebRenderingContext context, Consumer<HtmlNode> output);
}
