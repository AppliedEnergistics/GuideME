package guideme.compiler;

import guideme.Guide;
import guideme.GuidePage;
import guideme.extensions.ExtensionCollection;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(EphemeralTestServerProvider.class)
class PageCompilerTest {
    private Path guidebookFolder;

    public PageCompilerTest(MinecraftServer server) {
    }

    @BeforeEach
    void setUp() throws Exception {
        guidebookFolder = findGuidebookFolder();
    }

    @Test
    void testCompileIndexPage() throws Exception {
        compilePage("index");
    }

    private GuidePage compilePage(String id) throws Exception {
        var path = guidebookFolder.resolve(id + ".md");
        try (var in = Files.newInputStream(path)) {
            // Use the namespace of the test mod, so that assets are loaded from the development sources
            var parsed = PageCompiler.parse("testmod", "en_us", Identifier.fromNamespaceAndPath("testmod", id + ".md"),
                    in);
            var testPages = Guide.builder(Identifier.fromNamespaceAndPath("testmod", "guide"))
                    .developmentSources(guidebookFolder)
                    .watchDevelopmentSources(false)
                    .register(false)
                    .disableOpenHotkey()
                    .build();
            return PageCompiler.compile(testPages, ExtensionCollection.empty(), parsed);
        }
    }

    private static Path findGuidebookFolder() throws Exception {
        var sources = System.getProperty("guideme.testmod.guide.sources");
        if (sources != null) {
            return Paths.get(sources);
        }

        // Search up for the guidebook folder
        var url = ClassLoader.getSystemClassLoader()
                .getResource(PageCompilerTest.class.getName().replace('.', '/') + ".class");
        var jarPath = Paths.get(url.toURI());
        var current = jarPath.getParent();
        while (current != null) {
            var guidebookFolder = current.resolve("src/testmod/resources/assets/testmod/guides/testmod/guide");
            if (Files.isDirectory(guidebookFolder) && Files.exists(guidebookFolder.resolve("index.md"))) {
                return guidebookFolder;
            }

            current = current.getParent();
        }

        throw new FileNotFoundException("Couldn't find guidebook folder. Started looking at " + jarPath);
    }
}
