package guideme.siteexport;

import guideme.extensions.Extension;
import guideme.extensions.ExtensionPoint;
import java.util.Map;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.Recipe;
import org.jetbrains.annotations.Nullable;

/**
 * This interface allows mods to control how their custom recipes are exported to the website.
 */
public interface RecipeExporter extends Extension {
    ExtensionPoint<RecipeExporter> EXTENSION_POINT = new ExtensionPoint<>(RecipeExporter.class);

    /**
     * If this recipe exporters requires additional resources to be added to the export, this is the callback to do it
     * in.
     */
    default void referenceAdditionalResources(ResourceExporter exporter) {
    }

    /**
     * Try to convert a recipe to a JSON map (this map will be converted to JSON using GSON).
     *
     * @return null if this exporter was unable to handle the given type of recipe.
     */
    @Nullable
    Map<String, Object> convertToJson(ResourceKey<Recipe<?>> id, Recipe<?> recipe, ResourceExporter exporter);
}
