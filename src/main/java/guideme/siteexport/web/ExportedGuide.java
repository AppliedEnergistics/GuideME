package guideme.siteexport.web;

import java.util.List;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

/**
 * Access to the guide data that was previously exported from the game.
 */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
public interface ExportedGuide {
    String getDefaultNamespace();

    /**
     * Resolves a potentially namespace-less id to an id that is guaranteed to have a namespace by using the default
     * namespace of the guide.
     */
    String resolveId(String id);

    @Nullable
    ExportedItemInfo tryGetItemInfo(String itemId);

    /**
     * @throws IllegalArgumentException If the item was not exported.
     */
    ExportedItemInfo getItemInfo(String itemId);

    @Nullable
    ExportedFluidInfo tryGetFluidInfo(String fluidId);

    /**
     * @throws IllegalArgumentException If the fluid was not exported.
     */
    ExportedFluidInfo getFluidInfo(String fluidId);

    @Nullable
    ExportedRecipe getRecipeById(String recipeId);

    /**
     * {@return all recipes that produce the given item}
     */
    List<? extends ExportedRecipe> getRecipesForItem(String itemId);

    boolean pageExists(String pageId);

    /**
     * {@return the id of the page that is the primary page for the given item, or null if no such page exists}
     */
    @Nullable
    String getPageIdForItem(String itemId);

    /**
     * {@return mod specific data that was exported alongside the guide}
     *
     * @see guideme.siteexport.ResourceExporter#addExtraData(Identifier, Object)
     */
    @Nullable
    Object getExtraData(String identifier);
}
