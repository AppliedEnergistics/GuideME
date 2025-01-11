package guideme.guidebook.compiler.tags;

import guideme.guidebook.document.block.LytBlock;
import guideme.guidebook.extensions.Extension;
import guideme.guidebook.extensions.ExtensionPoint;
import java.util.function.Function;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * Allows mods to register mappings between recipe type and their custom recipe blocks for use in {@code <RecipeFor/>}
 * and similar tags.
 */
public interface RecipeTypeMappingSupplier extends Extension {
    ExtensionPoint<RecipeTypeMappingSupplier> EXTENSION_POINT = new ExtensionPoint<>(RecipeTypeMappingSupplier.class);

    void collect(RecipeTypeMappings mappings);

    interface RecipeTypeMappings {
        <T extends Recipe<C>, C extends RecipeInput> void add(
                RecipeType<T> recipeType,
                Function<RecipeHolder<T>, LytBlock> factory);
    }
}
