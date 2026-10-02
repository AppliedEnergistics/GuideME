package guideme.siteexport.web;

import guideme.compiler.TagCompiler;
import java.util.Set;
import java.util.function.Consumer;
import org.jetbrains.annotations.ApiStatus;

/**
 * This interface is loaded via service-loader and used to compile custom elements in the website renderer.
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
    void render(CustomElementWebRenderingContext context, Consumer<HTMLNode> output);
}
