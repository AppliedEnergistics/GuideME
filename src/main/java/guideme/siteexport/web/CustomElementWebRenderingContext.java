package guideme.siteexport.web;

import guideme.libs.mdast.mdx.model.MdxJsxElementFields;
import guideme.siteexport.DefaultValue;
import guideme.siteexport.WebRenderingContext;
import org.jetbrains.annotations.ApiStatus;

/**
 * Context for rendering a custom element with {@link CustomElementWebRenderer}.
 */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
public interface CustomElementWebRenderingContext extends WebRenderingContext {
    /**
     * {@return the name of the tag being compiled}
     */
    String tagName();

    /**
     * {@return the element being compiled}
     */
    MdxJsxElementFields element();

    /**
     * Maps between a JSX tags attributes and a given record that models these attributes.
     * <p>
     * Record components that are annotated with {@link org.jspecify.annotations.Nullable} are considered optional.
     * Record components can be annotated with {@link DefaultValue} to give them a default value.
     *
     * @see DefaultValue
     * @see org.jspecify.annotations.Nullable
     */
    <T extends Record> T map(Class<T> modelClass);
}
