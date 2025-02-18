package guideme.internal.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LangUtilTest {
    private static final Set<String> supportedLanguages = Set.of(
            "de_de",
            "fr_fr",
            "en_us",
            "es_es");

    @ParameterizedTest
    @CsvSource({
            "ns:_de_de/a/b/d/e.md, de_de", // valid language code
            "ns:de_de/a/b/d/e.md, ", // missing underscore
            "ns:_fr_fr/a/b/d/e.md, fr_fr", // valid language code
            "ns:_en_us/a/b/d/e.md, en_us", // valid language code
            "ns:_es_es/a/b/d/e.md, es_es", // valid language code
            "ns:_invalid_language/a/b/d/e.md, ", // invalid language code
            "ns:no_lang_code/a/b/d/e.md, ", // no language code
            "ns:file.md, ", // no folder, just a file
            "ns:folder/file.md, ", // single folder, no language code
            "ns:folder/_de_de.md, ", // single folder with language code as file
            "ns:_de_de.md, ", // language code is filename with extension
            "ns:_de_de/, ", // no filename
            "ns:_de_de, ", // filename only
    })
    void testGetLangFromPageId(String path, String expectedLang) {
        assertEquals(expectedLang, LangUtil.getLangFromPageId(ResourceLocation.parse(path), supportedLanguages));
    }

    @ParameterizedTest
    @CsvSource({
            "ns:_de_de/a/b/d/e.md, ns:a/b/d/e.md", // valid language code
            "ns:de_de/a/b/d/e.md, ns:de_de/a/b/d/e.md", // missing underscore
            "ns:_fr_fr/a/b/d/e.md, ns:a/b/d/e.md", // valid language code
            "ns:_en_us/a/b/d/e.md, ns:a/b/d/e.md", // valid language code
            "ns:_es_es/a/b/d/e.md, ns:a/b/d/e.md", // valid language code
            "ns:_invalid_language/a/b/d/e.md, ns:_invalid_language/a/b/d/e.md", // invalid language code
            "ns:no_lang_code/a/b/d/e.md, ns:no_lang_code/a/b/d/e.md", // no language code
            "ns:file.md, ns:file.md", // no folder, just a file
            "ns:folder/file.md, ns:folder/file.md", // single folder, no language code
            "ns:_de_de.md, ns:_de_de.md", // language code as file
            "ns:folder/_de_de/e.md, ns:folder/_de_de/e.md", // language code is not the first folder
            "ns:_de_de, ns:_de_de", // language code only
            "ns:_de_de/, ns:_de_de/", // language code folder only
            "ns:_de_de/e.md, ns:e.md" // language code is the first folder
    })
    void testStripLangFolderFromPath(String path, String expectedPath) {
        assertEquals(ResourceLocation.parse(expectedPath),
                LangUtil.stripLangFromPageId(ResourceLocation.parse(path), supportedLanguages));
    }
}
