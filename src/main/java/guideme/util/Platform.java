
package guideme.util;

import com.google.common.annotations.VisibleForTesting;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.util.thread.SidedThreadGroups;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class Platform {

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

    public static Component getFluidDisplayName(Fluid fluid) {
        var fluidStack = new FluidStack(fluid, 1);
        return fluidStack.getHoverName();
    }

    /**
     * @return True if AE2 is being run within a dev environment.
     */
    public static boolean isDevelopmentEnvironment() {
        return !FMLEnvironment.production;
    }
}
