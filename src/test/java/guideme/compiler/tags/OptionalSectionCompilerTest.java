package guideme.compiler.tags;

import static org.assertj.core.api.Assertions.assertThat;

import guideme.GuidePage;
import net.minecraft.server.MinecraftServer;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@ExtendWith(EphemeralTestServerProvider.class)
class OptionalSectionCompilerTest {

    public OptionalSectionCompilerTest(MinecraftServer server) {
    }

    @ParameterizedTest
    @CsvSource({
            "minecraft, true",
            "guideme, true",
            "not_installed, false",
            "!minecraft, false",
            "!not_installed, true"
    })
    void testContentVisibility(String modId, boolean visible) {
        var page = compilePage("<OptionalSection modId=\"" + modId + "\">\n  Content\n</OptionalSection>\n");

        assertThat(page.document().getTextContent().trim()).isEqualTo(visible ? "Content" : "");
    }

    @Test
    void testMissingModIdIsAnError() {
        var page = compilePage("<OptionalSection>\n  Content\n</OptionalSection>\n");

        assertThat(page.document().getTextContent())
                .contains("Missing modId attribute.")
                .doesNotContain("Content");
    }

    private static GuidePage compilePage(String content) {
        return TestPages.compile(content, new OptionalSectionCompiler());
    }
}
