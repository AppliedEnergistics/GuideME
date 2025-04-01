
package guideme.internal.util;

import com.mojang.blaze3d.platform.NativeImage;
import guideme.internal.GuideMEClient;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.level.block.entity.FuelValues;
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

    public static ContextMap getSlotDisplayContext() {
        var level = Minecraft.getInstance().level;
        if (level != null) {
            return SlotDisplayContext.fromLevel(level);
        } else {
            return new ContextMap.Builder()
                    .withParameter(SlotDisplayContext.FUEL_VALUES, fuelValues())
                    .withParameter(SlotDisplayContext.REGISTRIES, getClientRegistryAccess())
                    .create(SlotDisplayContext.CONTEXT);
        }
    }

    public static FuelValues fuelValues() {
        var level = Minecraft.getInstance().level;
        if (level != null) {
            return fuelValues();
        }

        return FuelValues.vanillaBurnTimes(getClientRegistryAccess(), FeatureFlags.VANILLA_SET);
    }

    public static RecipeMap getRecipeMap() {
        if (!isRecipeTypeAvailable(RecipeType.CRAFTING) && fallbackClientRecipeManager != null) {
            return fallbackClientRecipeManager.recipeMap();
        }
        return GuideMEClient.instance().getRecipeMap();
    }

    public static boolean isRecipeTypeAvailable(RecipeType<?> recipeType) {
        return GuideMEClient.instance().isRecipeTypeAvailable(recipeType);
    }

    public static boolean recipeHasResult(Recipe<?> recipe, Item item) {
        for (var recipeDisplay : recipe.display()) {
            boolean hasResult = recipeDisplay.result()
                    .resolve(Platform.getSlotDisplayContext(), SlotDisplay.ItemStackContentsFactory.INSTANCE)
                    .anyMatch(is -> is.is(item));
            if (hasResult) {
                return true;
            }
        }
        return false;
    }
}
