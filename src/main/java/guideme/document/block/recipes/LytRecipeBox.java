package guideme.document.block.recipes;

import guideme.document.block.LytBox;
import guideme.internal.siteexport.ExportableResourceProvider;
import guideme.internal.siteexport.ResourceExporter;
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
