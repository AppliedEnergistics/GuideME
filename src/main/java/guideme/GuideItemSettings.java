package guideme;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;

/**
 * Configuration settings for the automatically generated guide item.
 */
public record GuideItemSettings(Optional<Component> displayName,
        List<Component> tooltipLines,
        Optional<ResourceLocation> itemModel) {
    public static GuideItemSettings DEFAULT = new GuideItemSettings(Optional.empty(), List.of(), Optional.empty());

    public static Codec<GuideItemSettings> CODEC = RecordCodecBuilder.create(
            builder -> builder.group(
                    ExtraCodecs.COMPONENT.optionalFieldOf("display_name")
                            .forGetter(GuideItemSettings::displayName),
                    ExtraCodecs.COMPONENT.listOf().optionalFieldOf("tooltip_lines", List.of())
                            .forGetter(GuideItemSettings::tooltipLines),
                    ResourceLocation.CODEC.optionalFieldOf("model").forGetter(GuideItemSettings::itemModel))
                    .apply(builder, GuideItemSettings::new));
}
