package guideme.internal.datadriven;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import guideme.GuideItemSettings;
import guideme.color.Colors;
import guideme.color.ConstantColor;
import java.util.Locale;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;

/**
 * Format for data driven guide definition files.
 */
public record DataDrivenGuide(GuideItemSettings itemSettings, String defaultLanguage,
        Map<ResourceLocation, ConstantColor> customColors) {

    private static final Codec<Integer> COLOR_VALUE_CODEC = Codec.STRING
            .comapFlatMap(value -> DataResult.success(Colors.hexToRgb(value)),
                    rgba -> String.format(Locale.ROOT, "#%08X", rgba));

    private static final Codec<ConstantColor> CONSTANT_COLOR_CODEC = RecordCodecBuilder.create(builder -> builder.group(
            COLOR_VALUE_CODEC.fieldOf("dark_mode").forGetter(ConstantColor::darkModeColor),
            COLOR_VALUE_CODEC.fieldOf("light_mode").forGetter(ConstantColor::lightModeColor))
            .apply(builder, ConstantColor::new));

    @Deprecated(forRemoval = true)
    public DataDrivenGuide(GuideItemSettings itemSettings) {
        this(itemSettings, "en_us", Map.of());
    }

    public static Codec<DataDrivenGuide> CODEC = RecordCodecBuilder.create(
            builder -> builder.group(
                    GuideItemSettings.CODEC.optionalFieldOf("item_settings", GuideItemSettings.DEFAULT)
                            .forGetter(DataDrivenGuide::itemSettings),
                    Codec.STRING.optionalFieldOf("default_language", "en_us")
                            .forGetter(DataDrivenGuide::defaultLanguage),
                    ExtraCodecs.strictUnboundedMap(ResourceLocation.CODEC, CONSTANT_COLOR_CODEC)
                            .optionalFieldOf("custom_colors", Map.of()).forGetter(DataDrivenGuide::customColors))
                    .apply(builder, DataDrivenGuide::new));
}
