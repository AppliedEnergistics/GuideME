package guideme.internal;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Metadata about a guide, which is available on both client and server.
 */
public record GuideMetadata(ResourceLocation id, ItemStack representativeItem) {
}
