package guideme.internal.extensions;

import guideme.document.block.LytSlotGrid;
import guideme.document.block.recipes.LytStandardRecipeBox;
import guideme.internal.GuidebookText;
import java.util.List;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import net.minecraft.world.item.crafting.SmithingTrimRecipe;
import net.minecraft.world.level.block.Blocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class VanillaRecipes {
    private static final Logger LOG = LoggerFactory.getLogger(VanillaRecipes.class);

    private VanillaRecipes() {
    }

    public static LytStandardRecipeBox<CraftingRecipe> createCrafting(CraftingRecipe recipe) {
        LytSlotGrid grid;
        var ingredients = recipe.getIngredients();
        if (recipe instanceof ShapedRecipe shapedRecipe) {
            grid = new LytSlotGrid(shapedRecipe.getWidth(), shapedRecipe.getHeight());

            for (var x = 0; x < shapedRecipe.getWidth(); x++) {
                for (var y = 0; y < shapedRecipe.getHeight(); y++) {
                    var index = y * shapedRecipe.getWidth() + x;
                    if (index < ingredients.size()) {
                        var ingredient = ingredients.get(index);
                        if (!ingredient.isEmpty()) {
                            grid.setIngredient(x, y, ingredient);
                        }
                    }
                }
            }
        } else {
            // For shapeless -> layout 3 ingredients per row and break
            var ingredientCount = ingredients.size();
            grid = new LytSlotGrid(Math.min(3, ingredientCount), (ingredientCount + 2) / 3);
            for (int i = 0; i < ingredients.size(); i++) {
                var col = i % 3;
                var row = i / 3;
                grid.setIngredient(col, row, ingredients.get(i));
            }
        }

        String title;
        if (recipe instanceof ShapedRecipe) {
            title = GuidebookText.Crafting.text().getString();
        } else {
            title = GuidebookText.ShapelessCrafting.text().getString();
        }

        return LytStandardRecipeBox.builder()
                .title(title)
                .icon(Blocks.CRAFTING_TABLE)
                .input(grid)
                .outputFromResultOf(recipe)
                .build(recipe);

    }

    public static LytStandardRecipeBox<SmeltingRecipe> createSmelting(SmeltingRecipe recipe) {
        return LytStandardRecipeBox.builder()
                .title(GuidebookText.Smelting.text().getString())
                .icon(Blocks.FURNACE)
                .input(LytSlotGrid.row(recipe.getIngredients(), true))
                .outputFromResultOf(recipe)
                .build(recipe);
    }

    public static LytStandardRecipeBox<BlastingRecipe> createBlasting(BlastingRecipe recipe) {
        return LytStandardRecipeBox.builder()
                .title(GuidebookText.Blasting.text().getString())
                .icon(Blocks.BLAST_FURNACE)
                .input(LytSlotGrid.row(recipe.getIngredients(), true))
                .outputFromResultOf(recipe)
                .build(recipe);
    }

    public static LytStandardRecipeBox<SmithingRecipe> createSmithing(SmithingRecipe recipe) {
        return LytStandardRecipeBox.builder()
                .icon(Blocks.SMITHING_TABLE)
                .title(Items.SMITHING_TABLE.getDescription().getString())
                .input(LytSlotGrid.row(getSmithingIngredients(recipe), true))
                .outputFromResultOf(recipe)
                .build(recipe);
    }

    private static List<Ingredient> getSmithingIngredients(SmithingRecipe recipe) {
        if (recipe instanceof SmithingTrimRecipe trimRecipe) {
            return List.of(
                    trimRecipe.template,
                    trimRecipe.base,
                    trimRecipe.addition);
        } else if (recipe instanceof SmithingTransformRecipe transformRecipe) {
            return List.of(
                    transformRecipe.template,
                    transformRecipe.base,
                    transformRecipe.addition);
        } else {
            LOG.warn("Cannot determine ingredients of smithing recipe type {}", recipe.getClass());
            return List.of();
        }
    }
}
