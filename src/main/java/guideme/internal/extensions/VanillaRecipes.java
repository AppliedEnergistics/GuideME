package guideme.internal.extensions;

import guideme.document.block.LytSlotGrid;
import guideme.document.block.recipes.LytStandardRecipeBox;
import guideme.internal.GuidebookText;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import net.minecraft.world.item.crafting.SmithingTrimRecipe;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.level.block.Blocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class VanillaRecipes {
    private static final Logger LOG = LoggerFactory.getLogger(VanillaRecipes.class);

    private VanillaRecipes() {
    }

    public static Stream<LytStandardRecipeBox<CraftingRecipe>> createCrafting(RecipeHolder<CraftingRecipe> holder) {
        var recipe = holder.value();

        var result = Stream.<LytStandardRecipeBox<CraftingRecipe>>builder();

        for (var recipeDisplay : recipe.display()) {
            if (recipeDisplay instanceof ShapedCraftingRecipeDisplay shapedDisplay) {
                var grid = new LytSlotGrid(shapedDisplay.width(), shapedDisplay.height());

                var ingredients = shapedDisplay.ingredients();
                for (var x = 0; x < shapedDisplay.width(); x++) {
                    for (var y = 0; y < shapedDisplay.height(); y++) {
                        var index = y * shapedDisplay.width() + x;
                        if (index < ingredients.size()) {
                            var slotDisplay = ingredients.get(index);
                            if (slotDisplay.type() != SlotDisplay.Empty.TYPE) {
                                grid.setDisplay(x, y, slotDisplay);
                            }
                        }
                    }
                }

                result.add(LytStandardRecipeBox.builder()
                        .title(GuidebookText.Crafting.text().getString())
                        .icon(Blocks.CRAFTING_TABLE)
                        .input(grid)
                        .outputFromResultOf(holder)
                        .build(holder));
            } else if (recipeDisplay instanceof ShapelessCraftingRecipeDisplay shapelessDisplay) {
                var ingredients = shapelessDisplay.ingredients();

                // For shapeless -> layout 3 ingredients per row and break
                var ingredientCount = ingredients.size();
                var grid = new LytSlotGrid(Math.min(3, ingredientCount), (ingredientCount + 2) / 3);
                for (int i = 0; i < ingredients.size(); i++) {
                    var col = i % 3;
                    var row = i / 3;
                    grid.setDisplay(col, row, ingredients.get(i));
                }

                result.add(LytStandardRecipeBox.builder()
                        .title(GuidebookText.ShapelessCrafting.text().getString())
                        .icon(Blocks.CRAFTING_TABLE)
                        .input(grid)
                        .outputFromResultOf(holder)
                        .build(holder));
            }
        }

        return result.build();

    }

    public static Stream<LytStandardRecipeBox<SmeltingRecipe>> createSmelting(RecipeHolder<SmeltingRecipe> recipe) {
        return Stream.of(LytStandardRecipeBox.builder()
                .title(GuidebookText.Smelting.text().getString())
                .icon(Blocks.FURNACE)
                .input(LytSlotGrid.rowFromIngredients(List.of(recipe.value().input()), true))
                .outputFromResultOf(recipe)
                .build(recipe));
    }

    public static Stream<LytStandardRecipeBox<BlastingRecipe>> createBlasting(RecipeHolder<BlastingRecipe> recipe) {
        return Stream.of(LytStandardRecipeBox.builder()
                .title(GuidebookText.Blasting.text().getString())
                .icon(Blocks.BLAST_FURNACE)
                .input(LytSlotGrid.rowFromIngredients(List.of(recipe.value().input()), true))
                .outputFromResultOf(recipe)
                .build(recipe));
    }

    public static Stream<LytStandardRecipeBox<SmithingRecipe>> createSmithing(RecipeHolder<SmithingRecipe> holder) {
        return Stream.of(LytStandardRecipeBox.builder()
                .icon(Blocks.SMITHING_TABLE)
                .title(Items.SMITHING_TABLE.getName().getString())
                .input(LytSlotGrid.rowFromIngredients(getSmithingIngredients(holder.value()), true))
                .outputFromResultOf(holder)
                .build(holder));
    }

    private static List<Ingredient> getSmithingIngredients(SmithingRecipe recipe) {
        if (recipe instanceof SmithingTrimRecipe trimRecipe) {
            return Stream.of(
                    trimRecipe.templateIngredient(),
                    Optional.of(trimRecipe.baseIngredient()),
                    trimRecipe.additionIngredient())
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .toList();
        } else if (recipe instanceof SmithingTransformRecipe transformRecipe) {
            return Stream.of(
                    transformRecipe.templateIngredient(),
                    Optional.of(transformRecipe.baseIngredient()),
                    transformRecipe.additionIngredient())
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .toList();
        } else {
            LOG.warn("Cannot determine ingredients of smithing recipe type {}", recipe.getClass());
            return List.of();
        }
    }
}
