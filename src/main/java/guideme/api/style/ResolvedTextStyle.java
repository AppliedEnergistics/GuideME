package guideme.api.style;

import guideme.api.color.ColorValue;
import net.minecraft.resources.ResourceLocation;

/**
 * Represents the styling of text for rendering.
 */
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
