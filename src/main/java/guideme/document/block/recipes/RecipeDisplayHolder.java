package guideme.document.block.recipes;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.display.RecipeDisplay;

public record RecipeDisplayHolder<T extends RecipeDisplay>(ResourceLocation id, T value) {
}
