package guideme;

import guideme.document.block.LytDocument;
import net.minecraft.resources.Identifier;

public record GuidePage(String sourcePack, Identifier id, LytDocument document) {
}
