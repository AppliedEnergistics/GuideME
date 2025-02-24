
package guideme.internal.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
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
            // TODO return minecraft.level.getRecipeManager();
            return null;
        }

        return fallbackClientRecipeManager;
    }

    public static Component getFluidDisplayName(Fluid fluid) {
        var fluidStack = new FluidStack(fluid, 1);
        return fluidStack.getHoverName();
    }

    public static byte[] exportAsPng(NativeImage nativeImage) throws IOException {
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("siteexport", ".png");
            nativeImage.writeToFile(tempFile);
            return Files.readAllBytes(tempFile);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException e) {
                    LOG.error("Failed to delete temporary file {}", tempFile, e);
                }
            }
        }
    }
}
