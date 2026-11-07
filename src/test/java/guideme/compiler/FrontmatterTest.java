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

    @Test
    void requiredModsAreEmptyByDefault() {
        var frontmatter = Frontmatter.parse(PAGE_ID, "navigation:\n  title: Page\n");
        assertThat(frontmatter.requiredMods()).isEmpty();
        assertThat(frontmatter.isAvailable(modId -> false)).isTrue();
    }

    @Test
    void parsesRequiredMods() {
        var frontmatter = Frontmatter.parse(PAGE_ID, "required_mods:\n  - ae2\n  - \"!jei\"\n");
        assertThat(frontmatter.requiredMods()).containsExactly("ae2", "!jei");
    }

    @Test
    void parsesSingleRequiredMod() {
        var frontmatter = Frontmatter.parse(PAGE_ID, "required_mods: ae2\n");
        assertThat(frontmatter.requiredMods()).containsExactly("ae2");
    }

    @Test
    void requiredModsAreNotRetainedAsAdditionalProperty() {
        var frontmatter = Frontmatter.parse(PAGE_ID, "required_mods: ae2\n");
        assertThat(frontmatter.additionalProperties()).doesNotContainKey(Frontmatter.REQUIRED_MODS_KEY);
    }

    @Test
    void rejectsRequiredModsThatAreNotStrings() {
        assertThatThrownBy(() -> Frontmatter.parse(PAGE_ID, "required_mods:\n  - 1\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("required_mods");
    }

    @Test
    void rejectsRequiredModsThatAreNotAList() {
        assertThatThrownBy(() -> Frontmatter.parse(PAGE_ID, "required_mods:\n  mods: ae2\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("required_mods");
    }

    @Test
    void rejectsEmptyRequiredMods() {
        assertThatThrownBy(() -> Frontmatter.parse(PAGE_ID, "required_mods:\n  - \" \"\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("required_mods");
        assertThatThrownBy(() -> Frontmatter.parse(PAGE_ID, "required_mods:\n  - \"!\"\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("required_mods");
    }

    @Test
    void pageIsAvailableIfAllRequiredModsMatch() {
        var frontmatter = Frontmatter.parse(PAGE_ID, "required_mods:\n  - ae2\n  - \"!jei\"\n");

        assertThat(frontmatter.isAvailable(modId -> modId.equals("ae2"))).isTrue();
        // JEI is present
        assertThat(frontmatter.isAvailable(modId -> true)).isFalse();
        // AE2 is missing
        assertThat(frontmatter.isAvailable(modId -> false)).isFalse();
    }
}
