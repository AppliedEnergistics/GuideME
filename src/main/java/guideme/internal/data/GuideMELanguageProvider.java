package guideme.internal.data;

import guideme.internal.GuideME;
import guideme.internal.GuidebookText;
import java.util.Map;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;

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
        var translations = Map.ofEntries(
                Map.entry("guide", "Guides"),
                Map.entry("ignoreTranslatedGuides", "Ignore Guide Translations"),
                Map.entry("ignoreTranslatedGuides.tooltip",
                        "Always load the original version of GuideME guides, regardless of the currently selected UI language"),
                Map.entry("title", "GuideME Configuration"),
                Map.entry("gui", "User Interface"),
                Map.entry("debug", "Debug"),
                Map.entry("debug.tooltip", "Advanced Debugging Settings for Guide development"),
                Map.entry("adaptiveScaling", "Adaptive UI Scaling"),
                Map.entry("fullWidthLayout", "Full Width Layout"),
                Map.entry("showDebugGuiOverlays", "Debug GUI Overlays"),
                Map.entry("cacheSceneMeshes", "Cache Scene Geometry"));
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
