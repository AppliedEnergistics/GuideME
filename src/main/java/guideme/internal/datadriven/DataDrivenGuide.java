package guideme.internal.datadriven;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import guideme.GuideItemSettings;

/**
 * Format for data driven guide definition files.
 */
public record DataDrivenGuide(GuideItemSettings itemSettings) {
    public static Codec<DataDrivenGuide> CODEC = RecordCodecBuilder.create(
            builder -> builder.group(
                    GuideItemSettings.CODEC.optionalFieldOf("item_settings", GuideItemSettings.DEFAULT)
                            .forGetter(DataDrivenGuide::itemSettings))
                    .apply(builder, DataDrivenGuide::new));
}
