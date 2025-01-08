package guideme.guidebook.document.block.recipes;

import guideme.guidebook.document.block.LytBox;
import guideme.siteexport.ExportableResourceProvider;
import guideme.siteexport.ResourceExporter;
import net.minecraft.world.item.crafting.RecipeHolder;

public abstract class LytRecipeBox extends LytBox implements ExportableResourceProvider {
    private final RecipeHolder<?> recipe;

    public LytRecipeBox(RecipeHolder<?> recipe) {
        this.recipe = recipe;
    }

    @Override
    public void exportResources(ResourceExporter exporter) {
        exporter.referenceRecipe(this.recipe);
    }
}
