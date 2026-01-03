package guideme.siteexport;

import java.util.Set;
import org.jetbrains.annotations.ApiStatus;

/**
 * Renders recipes that were previously exported through {@link RecipeExporter} for use in the web-version of the guide.
 * <p/>
 * **NOTE:** This is loaded through the Java {@link java.util.ServiceLoader} mechanism.
 */
@ApiStatus.Experimental
public interface RecipeWebRenderer {
    /**
     * {@return the recipe types supported by this renderer. This is the string-form of the RecipeType registration ids,
     * i.e. minecraft:crafting}.
     */
    Set<String> getSupportedTypes();

    /**
     * Given a recipe that was previously exported to JSON using {@link RecipeExporter#convertToJson}, produce HTML
     * suitable for displaying it in the website.
     * <p>
     * Note that this method may be given recipes that were exported in previous versions of your mod if you try to
     * update the website for older guide versions. Your code should be lenient and compatible with all previous recipe
     * formats you have exported.
     */
    void render(RecipeWebRenderingContext builder, ExportedRecipe recipe);
}
