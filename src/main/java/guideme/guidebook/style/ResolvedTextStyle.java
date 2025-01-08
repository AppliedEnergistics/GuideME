package guideme.guidebook.style;

import guideme.guidebook.color.ColorValue;
import net.minecraft.resources.ResourceLocation;

public record ResolvedTextStyle(
        float fontScale,
        boolean bold,
        boolean italic,
        boolean underlined,
        boolean strikethrough,
        boolean obfuscated,
        ResourceLocation font,
        ColorValue color,
        WhiteSpaceMode whiteSpace,
        TextAlignment alignment,
        boolean dropShadow) {
}
