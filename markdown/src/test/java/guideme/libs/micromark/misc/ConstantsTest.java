package guideme.libs.micromark.misc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import guideme.libs.micromark.HtmlTagName;
import guideme.libs.micromark.NamedCharacterEntities;
import guideme.libs.micromark.symbol.Constants;
import java.util.Collection;
import org.junit.jupiter.api.Test;

public class ConstantsTest {

    @Test
    public void testCharacterReferenceDecimalSizeMax() {
        assertEquals(
                Integer.toString(0x10_ff_ff, 10).length(),
                Constants.characterReferenceDecimalSizeMax);
    }

    @Test
    public void testCharacterReferenceHexadecimalSizeMax() {
        assertEquals(
                Integer.toString(0x10_ff_ff, 16).length(),
                Constants.characterReferenceHexadecimalSizeMax);
    }

    @Test
    public void testCharacterReferenceNamedSizeMax() {
        assertEquals(
                longest(NamedCharacterEntities.getNames()).length(),
                Constants.characterReferenceNamedSizeMax);
    }

    @Test
    public void testHtmlRawSizeMax() {
        assertEquals(
                longest(HtmlTagName.htmlRawNames).length(),
                Constants.htmlRawSizeMax);
    }

    private static String longest(Collection<String> list) {
        String result = "";
        for (String s : list) {
            if (s.length() > result.length()) {
                result = s;
            }
        }
        return result;
    }
}
