import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * The shaded libraries (whose stripping down is the primary goal of running Proguard) are
 * under the guideme.* package as well due to relocation.
 */
public abstract class GenerateProGuardConfig extends DefaultTask {
    @InputFile
    public abstract RegularFileProperty getTemplate();

    @InputDirectory
    public abstract DirectoryProperty getSourceDir();

    @OutputFile
    public abstract RegularFileProperty getOutput();

    @TaskAction
    void generate() throws Exception {
        var templateContent = new StringBuilder(Files.readString(getTemplate().get().getAsFile().toPath()));

        var packageNames = new ArrayList<>(collectPackageNames());
        Collections.sort(packageNames);

        templateContent.append("\n");
        for (String packageName : packageNames) {
            templateContent.append("-keep public class ").append(packageName).append(".* {\n");
            templateContent.append("  public <methods>;\n");
            templateContent.append("  protected <methods>;\n");
            templateContent.append("  public <fields>;\n");
            templateContent.append("}\n");
        }

        Files.writeString(getOutput().getAsFile().get().toPath(), templateContent);
    }

    private Set<String> collectPackageNames() throws IOException {
        Set<String> packages = new HashSet<>();
        Path root = getSourceDir().get().getAsFile().toPath();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(@NotNull Path file, BasicFileAttributes attrs) throws IOException {
                if (file.getFileName().toString().endsWith(".java")) {
                    var packageName = root.relativize(file.getParent()).toString()
                            .replace('\\', '/')
                            .replace('/', '.');
                    packages.add(packageName);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return packages;
    }
}
