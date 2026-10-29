package guideme.internal.siteexport.mdastpostprocess;

import static org.assertj.core.api.Assertions.assertThat;

import guideme.compiler.PageCompiler;
import guideme.libs.mdast.model.MdAstNode;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class OptionalSectionExportProcessorTest {
    @Test
    void testOptionalSectionDependsOnLoadedMods() {
        var root = PageCompiler
                .parse("testmod", "en_us", ResourceLocation.fromNamespaceAndPath("testmod", "page.md"), """
                        Before

                        <OptionalSection modId="minecraft">
                          Shown

                          <OptionalSection modId="!minecraft">
                            Nested
                          </OptionalSection>
                        </OptionalSection>

                        <OptionalSection modId="not_installed">
                          Hidden
                        </OptionalSection>

                        <OptionalSection modId="!not_installed">
                          Inverted
                        </OptionalSection>

                        After""").getAstRoot();

        OptionalSectionExportProcessor.process(root);

        assertThat(root.children()).extracting(child -> ((MdAstNode) child).toText().trim())
                .containsExactly("Before", "Shown", "Inverted", "After");
    }
}
