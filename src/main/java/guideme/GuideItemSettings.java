package guideme;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;

/**
 * Configuration settings for the automatically generated guide item.
 */
public record GuideItemSettings(Optional<Component> displayName,
        List<Component> tooltipLines,
        Optional<ResourceLocation> itemModel) {
    public static GuideItemSettings DEFAULT = new GuideItemSettings(Optional.empty(), List.of(), Optional.empty());

    public static Codec<GuideItemSettings> CODEC = RecordCodecBuilder.create(
            builder -> builder.group(
                    ComponentSerialization.CODEC.optionalFieldOf("display_name")
                            .forGetter(GuideItemSettings::displayName),
                    ComponentSerialization.CODEC.listOf().optionalFieldOf("tooltip_lines", List.of())
                            .forGetter(GuideItemSettings::tooltipLines),
                    ResourceLocation.CODEC.optionalFieldOf("model").forGetter(GuideItemSettings::itemModel))
                    .apply(builder, GuideItemSettings::new));
}
