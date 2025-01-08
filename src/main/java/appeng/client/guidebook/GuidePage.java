package appeng.client.guidebook;

import appeng.client.guidebook.document.block.LytDocument;
import net.minecraft.resources.ResourceLocation;

public record GuidePage(String sourcePack, ResourceLocation id, LytDocument document) {
}
