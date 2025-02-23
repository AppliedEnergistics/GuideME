package guideme.color;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ColorsTest {
    @ParameterizedTest(name = "Convert {0} to RGBA({1}, {2}, {3}, {4})")
    @CsvSource({
            // input, R, G, B, A
            "'#FF0000',      255,  0,   0,   255", // Red
            "'FF0000',       255,  0,   0,   255", // Red without #
            "'#00FF00',      0,    255, 0,   255", // Green
            "'#0000FF',      0,    0,   255, 255", // Blue
            "'#000000',      0,    0,   0,   255", // Black
            "'#FFFFFF',      255,  255, 255, 255", // White
            "'#F00',         255,  0,   0,   255", // Red shorthand
            "'#0F0',         0,    255, 0,   255", // Green shorthand
            "'#00F',         0,    0,   255, 255", // Blue shorthand
            "'#FF000080',    255,  0,   0,   128", // Semi-transparent red
            "'#00FF0040',    0,    255, 0,   64", // Semi-transparent green
            "'#0000FF00',    0,    0,   255, 0", // Transparent blue
            "'#F008',        255,  0,   0,   136", // Semi-transparent red shorthand
            "'#0F04',        0,    255, 0,   68", // Semi-transparent green shorthand
            "'#00F0',        0,    0,   255, 0", // Transparent blue shorthand
            "'abc',          170,  187, 204, 255", // Lowercase
            "'#abc',         170,  187, 204, 255", // Lowercase with #
            "'#AbC123',      171,  193, 35,  255", // Mixed case
            "'#AbC12380',    171,  193, 35,  128" // Mixed case with alpha
    })
    void validColorConversions(String input, int red, int green, int blue, int alpha) {
        int expected = Colors.argb(alpha, red, green, blue);
        assertColorsEqual(expected, Colors.hexToRgb(input));
    }

    @ParameterizedTest(name = "Invalid input: {0}")
    @ValueSource(strings = {
            "", // Empty string
            "#FF", // Too short
            "#FFFFFFF", // Wrong length
            "#FFFFFFFFF", // Too long
            "#FFG000", // Invalid hex character
            "FFG000", // Invalid hex character without #
            "#FF-000", // Invalid character
            "##FF0000", // Double hash
            "#FF00000G" // Invalid alpha
    })
    void invalidInputIsIgnored(String input) {
        assertColorsEqual(0, Colors.hexToRgb(input));
    }

    @Test
    void verifyShorthandExpansion() {
        // Verify that shorthand notation produces the same result as full notation
        assertColorsEqual(
                Colors.hexToRgb("#FF0000"),
                Colors.hexToRgb("#F00"));
        // Test RGBA shorthand
        assertColorsEqual(
                Colors.hexToRgb("#FF000088"),
                Colors.hexToRgb("#F008"));
    }

    private static void assertColorsEqual(int expected, int actual) {
        assertEquals(
                "#" + Integer.toString(expected, 16),
                "#" + Integer.toString(actual, 16));
    }
}
