
package guideme.internal.util;

import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Platform {
    private static final Logger LOG = LoggerFactory.getLogger(Platform.class);

    // This hack is used to allow tests and the guidebook to provide a recipe manager before the client loads a world
    public static RecipeManager fallbackClientRecipeManager;
    public static RegistryAccess fallbackClientRegistryAccess;

    public static RegistryAccess getClientRegistryAccess() {
        if (Minecraft.getInstance() != null && Minecraft.getInstance().level != null) {
            return Minecraft.getInstance().level.registryAccess();
        }
        return Objects.requireNonNull(Platform.fallbackClientRegistryAccess);
    }

    public static RecipeManager getClientRecipeManager() {
        var minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            return minecraft.level.getRecipeManager();
        }

        return fallbackClientRecipeManager;
    }

    public static boolean recipeHasResult(RecipeHolder<?> recipe, Item item) {
        ItemStack resultItem = recipe.value().getResultItem(getClientRegistryAccess());
        if (resultItem == null) {
            LOG.error("Recipe {} has a null result item. It should be an empty item stack!", recipe.id());
            return false;
        }

        return resultItem.is(item);
    }
}
