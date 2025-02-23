package guideme.internal.datadriven;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import guideme.GuideItemSettings;

/**
 * Format for data driven guide definition files.
 */
public record DataDrivenGuide(GuideItemSettings itemSettings, String defaultLanguage) {
    @Deprecated(forRemoval = true)
    public DataDrivenGuide(GuideItemSettings itemSettings) {
        this(itemSettings, "en_us");
    }

    public static Codec<DataDrivenGuide> CODEC = RecordCodecBuilder.create(
            builder -> builder.group(
                    GuideItemSettings.CODEC.optionalFieldOf("item_settings", GuideItemSettings.DEFAULT)
                            .forGetter(DataDrivenGuide::itemSettings),
                    Codec.STRING.optionalFieldOf("default_language", "en_us")
                            .forGetter(DataDrivenGuide::defaultLanguage))
                    .apply(builder, DataDrivenGuide::new));
}
