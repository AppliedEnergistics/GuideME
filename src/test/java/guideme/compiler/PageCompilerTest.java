package guideme.compiler;

import guideme.Guide;
import guideme.GuidePage;
import guideme.extensions.ExtensionCollection;
import guideme.internal.GuideME;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PageCompilerTest {
    private Path guidebookFolder;

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
            var parsed = PageCompiler.parse("ae2", GuideME.makeId(id), in);
            var testPages = Guide.builder(ResourceLocation.fromNamespaceAndPath("ae2", "ae2guide"))
                    .developmentSources(guidebookFolder)
                    .watchDevelopmentSources(false)
                    .register(false)
                    .disableOpenHotkey()
                    .build();
            return PageCompiler.compile(testPages, ExtensionCollection.empty(), parsed);
        }
    }

    private static Path findGuidebookFolder() throws Exception {
        var sources = System.getProperty("guideDev.ae2guide.sources");
        if (sources != null) {
            return Paths.get(sources);
        }

        // Search up for the guidebook folder
        var url = PageCompilerTest.class.getProtectionDomain().getCodeSource().getLocation();
        var jarPath = Paths.get(url.toURI());
        var current = jarPath.getParent();
        while (current != null) {
            var guidebookFolder = current.resolve("guideme/guidebook");
            if (Files.isDirectory(guidebookFolder) && Files.exists(guidebookFolder.resolve("index.md"))) {
                return guidebookFolder;
            }

            current = current.getParent();
        }

        throw new FileNotFoundException("Couldn't find guidebook folder. Started looking at " + jarPath);
    }
}
