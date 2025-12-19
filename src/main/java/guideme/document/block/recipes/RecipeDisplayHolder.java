package guideme.document.block.recipes;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.display.RecipeDisplay;

public record RecipeDisplayHolder<T extends RecipeDisplay>(Identifier id, T value) {
}
