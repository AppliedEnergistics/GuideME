package guideme.style;

import guideme.color.ColorValue;
import guideme.color.ConstantColor;

public record BorderStyle(ColorValue color, int width) {
    public static BorderStyle NONE = new BorderStyle(ConstantColor.TRANSPARENT, 0);
}
