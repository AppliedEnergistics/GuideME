package guideme.document.block.recipes;

import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;

public record RecipeDisplayHolder<T extends RecipeDisplay>(RecipeDisplayId id, T value) {
}
