package guideme.compiler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

class FrontmatterTest {
    private static final Identifier PAGE_ID = Identifier.fromNamespaceAndPath("testmod", "page.md");

    @Test
    void maxDepthIsUnsetByDefault() {
        var frontmatter = Frontmatter.parse(PAGE_ID, "navigation:\n  title: Page\n");
        assertThat(frontmatter.navigationEntry().maxDepth()).isNull();
    }

    @Test
    void parsesMaxDepth() {
        var frontmatter = Frontmatter.parse(PAGE_ID, "navigation:\n  title: Page\n  max_depth: 0\n");
        assertThat(frontmatter.navigationEntry().maxDepth()).isZero();
    }

    @Test
    void rejectsNegativeMaxDepth() {
        assertThatThrownBy(() -> Frontmatter.parse(PAGE_ID, "navigation:\n  title: Page\n  max_depth: -1\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max_depth");
    }
}
