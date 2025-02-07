package guideme.document.block.recipes;

import guideme.document.block.LytBox;
import guideme.siteexport.ExportableResourceProvider;
import guideme.siteexport.ResourceExporter;
import net.minecraft.world.item.crafting.Recipe;

public abstract class LytRecipeBox extends LytBox implements ExportableResourceProvider {
    private final Recipe<?> recipe;

    public LytRecipeBox(Recipe<?> recipe) {
        this.recipe = recipe;
    }

    @Override
    public void exportResources(ResourceExporter exporter) {
        exporter.referenceRecipe(this.recipe);
    }
}
