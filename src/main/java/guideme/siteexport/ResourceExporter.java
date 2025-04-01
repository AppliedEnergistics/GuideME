package guideme.siteexport;

import java.nio.file.Path;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.NonExtendable
public interface ResourceExporter {
    /**
     * Ensures the data needed to show tooltips or icons for this item is exported.
     */
    default void referenceItem(ItemLike item) {
        referenceItem(new ItemStack(item));
    }

    /**
     * Ensures the data needed to show tooltips or icons for this item is exported.
     */
    void referenceItem(ItemStack stack);

    /**
     * Ensures the data needed to show tooltips or icons for this fluid is exported.
     */
    void referenceFluid(Fluid fluid);

    /**
     * Ensures the data needed to show tooltips or icons for the items in this slot display is exported.
     */
    void referenceSlotDisplay(SlotDisplay display);

    /**
     * Ensures the data needed to show tooltips or icons for the items in this ingredient is exported.
     */
    void referenceIngredient(Ingredient ingredient);

    /**
     * Exports a texture by id and returns the relative path to refer to it from other content that is being exported.
     */
    String exportTexture(ResourceLocation texture);

    /**
     * @return The new resource id after applying cache busting.
     */
    Path copyResource(ResourceLocation id);

    Path getPathForWriting(ResourceLocation assetId);

    /**
     * Generates a resource location for a page specific resource.
     */
    Path getPageSpecificPathForWriting(String suffix);

    @Nullable
    ResourceLocation getCurrentPageId();

    Path getOutputFolder();

    default String getPathRelativeFromOutputFolder(Path p) {
        return "/" + getOutputFolder().relativize(p).toString().replace('\\', '/');
    }

    /**
     * Generates a resource location for a page specific resource.
     */
    ResourceLocation getPageSpecificResourceLocation(String suffix);

    /**
     * Ensures that the data needed to show the given recipe, its ingredients and its output is exported.
     *
     * @see RecipeExporter
     */
    void referenceRecipe(RecipeHolder<?> recipe);

    /**
     * Adds arbitrary data to be added to the exported guides index JSON file.
     *
     * @param key   The key under which the data will be retrievable.
     * @param value The value to be serialized to JSON using GSON.
     */
    void addExtraData(ResourceLocation key, Object value);
}
