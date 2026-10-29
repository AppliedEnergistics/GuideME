package guideme.compiler.tags;

import guideme.Guide;
import guideme.GuidePage;
import guideme.compiler.PageCompiler;
import guideme.compiler.TagCompiler;
import guideme.extensions.ExtensionCollection;
import guideme.internal.GuideME;
import net.minecraft.resources.ResourceLocation;

/**
 * Compiles single pages for tag compiler tests. Requires a running test server (see
 * {@code EphemeralTestServerProvider}).
 */
final class TestPages {
    private TestPages() {
    }

    static final String DEFAULT_PAGE_ID = "guideme:test_page";

    /**
     * Same as {@link #compile(String, String, TagCompiler...)} using {@link #DEFAULT_PAGE_ID}.
     */
    static GuidePage compile(String markdown, TagCompiler... tagCompilers) {
        return compile(DEFAULT_PAGE_ID, markdown, tagCompilers);
    }

    /**
     * Compiles a page from the GuideME source pack in an empty guide, using only the given tag compilers.
     */
    static GuidePage compile(String pageId, String markdown, TagCompiler... tagCompilers) {
        var extensions = ExtensionCollection.builder();
        for (var tagCompiler : tagCompilers) {
            extensions.add(TagCompiler.EXTENSION_POINT, tagCompiler);
        }
        var guide = Guide.builder(GuideME.makeId("test"))
                .watchDevelopmentSources(false)
                .register(false)
                .disableOpenHotkey()
                .build();
        var parsed = PageCompiler.parse(GuideME.MOD_ID, "en_us", ResourceLocation.parse(pageId), markdown);
        return PageCompiler.compile(guide, extensions.build(), parsed);
    }
}
