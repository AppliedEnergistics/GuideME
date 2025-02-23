package guideme.color;

/**
 * Symbolic colors can be overridden more easily in styles and define both a light- and dark-themed color variant.
 */
public enum SymbolicColor implements ColorValue {
    LINK(Colors.rgb(0, 213, 255), Colors.rgb(0, 213, 255)),
    BODY_TEXT(Colors.rgb(210, 210, 210), Colors.rgb(210, 210, 210)),
    ERROR_TEXT(Colors.rgb(255, 0, 0), Colors.rgb(255, 0, 0)),
    /**
     * Color used for the type of crafting shown in recipe blocks.
     */
    CRAFTING_RECIPE_TYPE(Colors.rgb(64, 64, 64), Colors.rgb(64, 64, 64)),
    THEMATIC_BREAK(Colors.rgb(55, 55, 55), Colors.rgb(155, 155, 155)),

    HEADER1_SEPARATOR(Colors.argb(127, 255, 255, 255), Colors.argb(127, 255, 255, 255)),
    HEADER2_SEPARATOR(Colors.argb(127, 210, 210, 210), Colors.argb(127, 210, 210, 210)),

    NAVBAR_BG_TOP(Colors.rgb(0, 0, 0), Colors.rgb(0, 0, 0)),
    NAVBAR_BG_BOTTOM(Colors.argb(127, 0, 0, 0), Colors.argb(127, 0, 0, 0)),
    NAVBAR_ROW_HOVER(Colors.rgb(33, 33, 33), Colors.rgb(33, 33, 33)),
    NAVBAR_EXPAND_ARROW(Colors.rgb(238, 238, 238), Colors.rgb(238, 238, 238)),
    TABLE_BORDER(Colors.rgb(124, 124, 124), Colors.rgb(124, 124, 124)),

    ICON_BUTTON_NORMAL(Colors.mono(200), Colors.mono(200)),
    ICON_BUTTON_DISABLED(Colors.mono(64), Colors.mono(64)),
    ICON_BUTTON_HOVER(Colors.rgb(0, 213, 255), Colors.rgb(0, 213, 255)),

    IN_WORLD_BLOCK_HIGHLIGHT(Colors.argb(0xcc, 0x99, 0x99, 0x99), Colors.argb(0xcc, 0x99, 0x99, 0x99)),

    SCENE_BACKGROUND(Colors.argb(20, 0, 0, 0), Colors.argb(20, 0, 0, 0)),

    GUIDE_SCREEN_BACKGROUND(Colors.argb(229, 63, 63, 63), Colors.argb(229, 63, 63, 63)),

    BLOCKQUOTE_BACKGROUND(Colors.argb(64, 255, 255, 255), Colors.argb(64, 255, 255, 255)),

    // these are the Minecraft colors
    BLACK(Colors.hexToRgb("#000"), Colors.hexToRgb("#000")),
    DARK_BLUE(Colors.hexToRgb("#00A"), Colors.hexToRgb("#00A")),
    DARK_GREEN(Colors.hexToRgb("#0A0"), Colors.hexToRgb("#0A0")),
    DARK_AQUA(Colors.hexToRgb("#0AA"), Colors.hexToRgb("#0AA")),
    DARK_RED(Colors.hexToRgb("#A00"), Colors.hexToRgb("#A00")),
    DARK_PURPLE(Colors.hexToRgb("#A0A"), Colors.hexToRgb("#A0A")),
    GOLD(Colors.hexToRgb("#AA0"), Colors.hexToRgb("#AA0")),
    GRAY(Colors.hexToRgb("#AAA"), Colors.hexToRgb("#AAA")),
    DARK_GRAY(Colors.hexToRgb("#555"), Colors.hexToRgb("#555")),
    BLUE(Colors.hexToRgb("#55F"), Colors.hexToRgb("#55F")),
    GREEN(Colors.hexToRgb("#5F5"), Colors.hexToRgb("#5F5")),
    AQUA(Colors.hexToRgb("#5FF"), Colors.hexToRgb("#5FF")),
    RED(Colors.hexToRgb("#F55"), Colors.hexToRgb("#F55")),
    LIGHT_PURPLE(Colors.hexToRgb("#F5F"), Colors.hexToRgb("#F5F")),
    YELLOW(Colors.hexToRgb("#FF5"), Colors.hexToRgb("#FF5")),
    WHITE(Colors.hexToRgb("#FFF"), Colors.hexToRgb("#FFF")),

    ;

    final int lightMode;
    final int darkMode;

    SymbolicColor(int lightMode, int darkMode) {
        this.lightMode = lightMode;
        this.darkMode = darkMode;
    }

    @Override
    public int resolve(LightDarkMode lightDarkMode) {
        return lightDarkMode == LightDarkMode.LIGHT_MODE ? lightMode : darkMode;
    }
}
