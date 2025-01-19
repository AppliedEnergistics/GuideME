package guideme;

import guideme.document.block.LytDocument;
import net.minecraft.resources.ResourceLocation;

public record GuidePage(String sourcePack, ResourceLocation id, LytDocument document) {
}
