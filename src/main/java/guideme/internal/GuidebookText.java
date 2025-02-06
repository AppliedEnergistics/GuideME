package guideme.internal;

import guideme.internal.data.LocalizationEnum;

public enum GuidebookText implements LocalizationEnum {
    HistoryGoBack("Go back one page"),
    HistoryGoForward("Go forward one page"),
    Close("Close"),
    HoldToShow("Hold [%s] to open guide"),
    HideAnnotations("Hide Annotations"),
    ShowAnnotations("Show Annotations"),
    ZoomIn("Zoom In"),
    ZoomOut("Zoom Out"),
    ResetView("Reset View"),
    Search("Search"),
    SearchNoQuery("Enter a Search Query"),
    SearchNoResults("No Results"),
    ContentFrom("Content from"),
    ItemNoGuideId("No guide id set"),
    ItemInvalidGuideId("Invalid guide id set: %s"),
    CommandOnlyWorksInSinglePlayer("This command only works in single-player."),
    Smelting("Smelting"),
    Blasting("Blasting"),
    ShapelessCrafting("Crafting (Shapeless)"),
    Crafting("Crafting"),
    FullWidthView("Full Width View"),
    CloseFullWidthView("Close Full Width View");

    private final String englishText;

    GuidebookText(String englishText) {
        this.englishText = englishText;
    }

    @Override
    public String getTranslationKey() {
        return "guideme.guidebook." + name();
    }

    @Override
    public String getEnglishText() {
        return englishText;
    }
}
