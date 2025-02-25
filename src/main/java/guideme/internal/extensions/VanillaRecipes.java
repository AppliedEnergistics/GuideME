package guideme.internal.extensions;

import guideme.document.block.LytSlotGrid;
import guideme.document.block.recipes.LytStandardRecipeBox;
import guideme.document.block.recipes.RecipeDisplayHolder;
import guideme.internal.GuidebookText;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.level.block.Blocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class VanillaRecipes {
    private static final Logger LOG = LoggerFactory.getLogger(VanillaRecipes.class);

    private VanillaRecipes() {
    }

    public static LytStandardRecipeBox<ShapelessCraftingRecipeDisplay> create(ShapelessCraftingRecipeDisplay recipe) {
        var ingredients = recipe.ingredients();

        // For shapeless -> layout 3 ingredients per row and break
        var ingredientCount = ingredients.size();
        var grid = new LytSlotGrid(Math.min(3, ingredientCount), (ingredientCount + 2) / 3);
        for (int i = 0; i < ingredients.size(); i++) {
            var col = i % 3;
            var row = i / 3;
            grid.setDisplay(col, row, ingredients.get(i));
        }

        String title = GuidebookText.ShapelessCrafting.text().getString();

        return LytStandardRecipeBox.builder()
                .title(title)
                .icon(Blocks.CRAFTING_TABLE) // TODO -> use crafting station
                .input(grid)
                .outputFromResultOf(recipe)
                .build(new RecipeDisplayHolder<>(null, recipe));
    }

    public static LytStandardRecipeBox<ShapedCraftingRecipeDisplay> create(ShapedCraftingRecipeDisplay recipe) {
        var ingredients = recipe.ingredients();

        // For shapeless -> layout 3 ingredients per row and break
        var ingredientCount = ingredients.size();
        var grid = new LytSlotGrid(recipe.width(), recipe.height());

            for (var x = 0; x < recipe.width(); x++) {
                for (var y = 0; y < recipe.height(); y++) {
                    var index = y * recipe.width() + x;
                    if (index < ingredients.size()) {
                        grid.setDisplay(x, y, ingredients.get(index));
                    }
                }
            }

        String title = GuidebookText.Crafting.text().getString();

        return LytStandardRecipeBox.builder()
                .title(title)
                .icon(Blocks.CRAFTING_TABLE) // TODO -> use crafting station
                .input(grid)
                .outputFromResultOf(recipe)
                .build(new RecipeDisplayHolder<>(null, recipe));
    }

//    public static LytStandardRecipeBox<SmeltingRecipe> createSmelting(RecipeHolder<SmeltingRecipe> recipe) {
//        return LytStandardRecipeBox.builder()
//                .title(GuidebookText.Smelting.text().getString())
//                .icon(Blocks.FURNACE)
//                .input(LytSlotGrid.row(recipe.value().getIngredients(), true))
//                .outputFromResultOf(recipe)
//                .build(recipe);
//    }
//
//    public static LytStandardRecipeBox<BlastingRecipe> createBlasting(RecipeHolder<BlastingRecipe> recipe) {
//        return LytStandardRecipeBox.builder()
//                .title(GuidebookText.Blasting.text().getString())
//                .icon(Blocks.BLAST_FURNACE)
//                .input(LytSlotGrid.row(recipe.value().getIngredients(), true))
//                .outputFromResultOf(recipe)
//                .build(recipe);
//    }
//
//    public static LytStandardRecipeBox<SmithingRecipe> createSmithing(RecipeHolder<SmithingRecipe> holder) {
//        return LytStandardRecipeBox.builder()
//                .icon(Blocks.SMITHING_TABLE)
//                .title(Items.SMITHING_TABLE.getDescription().getString())
//                .input(LytSlotGrid.row(getSmithingIngredients(holder.value()), true))
//                .outputFromResultOf(holder)
//                .build(holder);
//    }
//
//    private static List<Ingredient> getSmithingIngredients(SmithingRecipe recipe) {
//        if (recipe instanceof SmithingTrimRecipe trimRecipe) {
//            return List.of(
//                    trimRecipe.template,
//                    trimRecipe.base,
//                    trimRecipe.addition);
//        } else if (recipe instanceof SmithingTransformRecipe transformRecipe) {
//            return List.of(
//                    transformRecipe.template,
//                    transformRecipe.base,
//                    transformRecipe.addition);
//        } else {
//            LOG.warn("Cannot determine ingredients of smithing recipe type {}", recipe.getClass());
//            return List.of();
//        }
//    }
}
