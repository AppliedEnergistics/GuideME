package guideme.color;

import net.minecraft.util.ARGB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class Colors {
    private static final Logger LOG = LoggerFactory.getLogger(Colors.class);

    /**
     * Converts a hexadecimal color string to a packed RGB or ARGB integer. If no alpha is given, assumes alpha 255. The
     * order of colors in the hex string follows CSS notations (#RRGGBBAA or #RGBA).
     *
     * @param hexColor The color string in hex format (with or without #)
     * @return The packed RGB value as an integer
     * @throws IllegalArgumentException if the input format is invalid
     */
    public static int hexToRgb(String hexColor) {
        if (!hexColor.isEmpty()) {
            int start = 0;
            if (hexColor.charAt(0) == '#') {
                start++; // Skip leading #
            }

            int remainingChars = hexColor.length() - start;
            // #rgb
            if (remainingChars == 3 || remainingChars == 4) {
                int r = fromHexChar(hexColor.charAt(start));
                int g = fromHexChar(hexColor.charAt(start + 1));
                int b = fromHexChar(hexColor.charAt(start + 2));
                int a = 15;
                if (remainingChars == 4) {
                    a = fromHexChar(hexColor.charAt(start + 3));
                }
                if (r != -1 && g != -1 && b != -1 && a != -1) {
                    return argb(a << 4 | a, r << 4 | r, g << 4 | g, b << 4 | b);
                }
            } else if (remainingChars == 6 || remainingChars == 8) {
                int rHi = fromHexChar(hexColor.charAt(start));
                int rLo = fromHexChar(hexColor.charAt(start + 1));
                int gHi = fromHexChar(hexColor.charAt(start + 2));
                int gLo = fromHexChar(hexColor.charAt(start + 3));
                int bHi = fromHexChar(hexColor.charAt(start + 4));
                int bLo = fromHexChar(hexColor.charAt(start + 5));
                int aHi = 15, aLo = 15;
                if (remainingChars == 8) {
                    aHi = fromHexChar(hexColor.charAt(start + 6));
                    aLo = fromHexChar(hexColor.charAt(start + 7));
                }
                if (rHi != -1 && rLo != -1 && gHi != -1 && gLo != -1 && bHi != -1 && bLo != -1 && aHi != -1
                        && aLo != -1) {
                    return argb(aHi << 4 | aLo, rHi << 4 | rLo, gHi << 4 | gLo, bHi << 4 | bLo);
                }
            }
        }

        LOG.error("Tried to parse an invalid hexadecimal color string: '{}'", hexColor);
        return 0;
    }

    private static int fromHexChar(int ch) {
        if (ch >= '0' && ch <= '9') {
            return ch - '0';
        } else if (ch >= 'a' && ch <= 'f') {
            return 0xa + (ch - 'a');
        } else if (ch >= 'A' && ch <= 'F') {
            return 0xa + (ch - 'A');
        } else {
            return -1;
        }
    }

    public static int argb(int a, int r, int g, int b) {
        return ARGB.color(a, r, g, b);
    }

    public static int rgb(int r, int g, int b) {
        return argb(255, r, g, b);
    }

    public static int mono(int w) {
        return rgb(w, w, w);
    }
}
