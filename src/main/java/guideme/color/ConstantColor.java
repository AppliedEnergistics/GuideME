package guideme.color;

public record ConstantColor(int lightModeColor, int darkModeColor) implements ColorValue {
    public static ConstantColor WHITE = new ConstantColor(-1, -1);

    public ConstantColor(int color) {
        this(color, color);
    }

    @Override
    public int resolve(LightDarkMode lightDarkMode) {
        return lightDarkMode == LightDarkMode.LIGHT_MODE ? lightModeColor : darkModeColor;
    }
}
