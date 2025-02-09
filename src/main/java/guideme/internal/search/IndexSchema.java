package guideme.internal.search;

import java.util.Map;

final class IndexSchema {
    static final String FIELD_GUIDE_ID = "guide_id";
    static final String FIELD_PAGE_ID = "page_id";
    static final String FIELD_TEXT = "page_content";
    static final String FIELD_TITLE = "page_title";
    // Fields for analyzed text
    static final String FIELD_TITLE_EN = "page_title_en";
    static final String FIELD_TEXT_EN = "page_content_en";

    private static final Map<String, String> titleFields = Map.of(
            "en", FIELD_TITLE_EN);
    private static final Map<String, String> textFields = Map.of(
            "en", FIELD_TEXT_EN);

    private IndexSchema() {
    }

    public static String getTitleField(String language) {
        return titleFields.getOrDefault(language, FIELD_TITLE_EN);
    }

    public static String getTextField(String language) {
        return textFields.getOrDefault(language, FIELD_TEXT_EN);
    }
}
