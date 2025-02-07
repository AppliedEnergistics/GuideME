package guideme.internal.data;

import guideme.internal.GuideME;
import guideme.internal.GuidebookText;
import java.util.Map;
import net.minecraft.data.PackOutput;
import net.minecraftforge.common.data.LanguageProvider;

public final class GuideMELanguageProvider extends LanguageProvider {
    public GuideMELanguageProvider(PackOutput gen) {
        super(gen, GuideME.MOD_ID, "en_us");
    }

    @Override
    protected void addTranslations() {
        addEnum(GuidebookText.class);

        addItem(GuideME.GUIDE_ITEM, "Guide");

        add("key.guideme.category", "GuideME");
        add("key.guideme.guide", "Open Guide for Items");

        addConfigTranslations();
    }

    private void addConfigTranslations() {
        var translations = Map.of(
                "title", "GuideME Configuration",
                "gui", "User Interface",
                "debug", "Debug",
                "debug.tooltip", "Advanced Debugging Settings for Guide development",
                "adaptiveScaling", "Adaptive UI Scaling",
                "fullWidthLayout", "Full Width Layout",
                "showDebugGuiOverlays", "Debug GUI Overlays");
        for (var entry : translations.entrySet()) {
            add("guideme.configuration." + entry.getKey(), entry.getValue());
        }
    }

    public <T extends Enum<T> & LocalizationEnum> void addEnum(Class<T> localizedEnum) {
        for (var enumConstant : localizedEnum.getEnumConstants()) {
            add(enumConstant.getTranslationKey(), enumConstant.getEnglishText());
        }
    }

    @Override
    public String getName() {
        return "GuideME Translations";
    }
}
