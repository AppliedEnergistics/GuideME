package guideme.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;

import guideme.PageAnchor;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class IdUtilsTest {

    @CsvSource({
            "some_page,ae2,ae2:some_page",
            "ae2:some_page,ae2,ae2:some_page",
            "minecraft:some_page,ae2,minecraft:some_page",
    })
    @ParameterizedTest
    void testResolveId(String input, String defaultNamespace, String expected) {
        assertEquals(expected, IdUtils.resolveId(input, defaultNamespace).toString());
    }

    @CsvSource({
            "./some_page.md,ae2:folder/page.md,ae2:folder/some_page.md"
    })
    @ParameterizedTest
    void testResolveLink(String input, String anchor, String expected) {
        assertEquals(expected, IdUtils.resolveLink(input, ResourceLocation.parse(anchor)).toString());
    }

}
