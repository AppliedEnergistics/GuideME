package guideme.internal.extensions;

import guideme.document.block.LytSlotGrid;
import guideme.document.block.recipes.LytStandardRecipeBox;
import guideme.document.block.recipes.RecipeDisplayHolder;
import guideme.internal.GuidebookText;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.crafting.display.FurnaceRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SmithingRecipeDisplay;
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

    public static LytStandardRecipeBox<FurnaceRecipeDisplay> create(FurnaceRecipeDisplay recipe) {
        return LytStandardRecipeBox.builder(recipe)
                .input(recipe.ingredient())
                .outputFromResultOf(recipe)
                .build(new RecipeDisplayHolder<>(null, recipe));
    }

    public static LytStandardRecipeBox<SmithingRecipeDisplay> create(SmithingRecipeDisplay recipe) {
        return LytStandardRecipeBox.builder(recipe)
                .input(LytSlotGrid.row(getSmithingIngredients(recipe), true))
                .outputFromResultOf(recipe)
                .build(new RecipeDisplayHolder<>(null, recipe));
    }

    private static List<SlotDisplay> getSmithingIngredients(SmithingRecipeDisplay recipe) {
        var result = new ArrayList<SlotDisplay>();

        if (recipe.base().type() != SlotDisplay.Empty.TYPE) {
            result.add(recipe.base());
        }
        if (recipe.template().type() != SlotDisplay.Empty.TYPE) {
            result.add(recipe.template());
        }
        if (recipe.addition().type() != SlotDisplay.Empty.TYPE) {
            result.add(recipe.addition());
        }

        return result;
    }
}
