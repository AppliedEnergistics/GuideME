package guideme.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import guideme.GuidePageChange;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GuideSourceWatcherTest {
    private static final String NAMESPACE = "testmod";
    private static final Identifier PAGE_ID = Identifier.fromNamespaceAndPath(NAMESPACE,
            "advanced/camo_crafting.md");
    private static final Duration CHANGE_TIMEOUT = Duration.ofSeconds(10);

    @TempDir
    Path sourceFolder;

    private GuideSourceWatcher watcher;

    @AfterEach
    void closeWatcher() {
        if (watcher != null) {
            watcher.close();
        }
    }

    @Test
    void deletingDefaultLanguagePageMarksPageAsDeleted() throws Exception {
        var pagePath = writePage("advanced/camo_crafting.md");
        startWatcher();

        Files.delete(pagePath);

        assertThat(awaitChanges()).singleElement().satisfies(change -> {
            assertThat(change.pageId()).isEqualTo(PAGE_ID);
            assertThat(change.newPage()).isNull();
        });
    }

    @Test
    void deletingLanguageSpecificPageFallsBackToDefaultLanguagePage() throws Exception {
        writePage("advanced/camo_crafting.md");
        var languagePagePath = writePage("_en_us/advanced/camo_crafting.md");
        startWatcher();

        Files.delete(languagePagePath);

        assertThat(awaitChanges()).singleElement().satisfies(change -> {
            assertThat(change.pageId()).isEqualTo(PAGE_ID);
            assertThat(change.language()).isEqualTo("en_us");
            assertThat(change.newPage()).isNotNull();
        });
    }

    private void startWatcher() {
        watcher = new GuideSourceWatcher(NAMESPACE, "en_us", sourceFolder);
        // The directories are registered by the time the constructor returns, but discard anything
        // that may have been queued while starting up anyway
        watcher.clearChanges();
    }

    private List<GuidePageChange> awaitChanges() {
        var changes = new AtomicReference<List<GuidePageChange>>();
        await().atMost(CHANGE_TIMEOUT)
                .alias("changes reported by the file watcher")
                .until(() -> {
                    changes.set(watcher.takeChanges());
                    return !changes.get().isEmpty();
                });
        return changes.get();
    }

    private Path writePage(String relativePath) throws Exception {
        var path = sourceFolder.resolve(relativePath);
        Files.createDirectories(path.getParent());
        Files.writeString(path, "# Camo Crafting\n");
        return path;
    }
}
