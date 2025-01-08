import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

public abstract class GenerateFlatBufferCode extends DefaultTask {
    private final ExecOperations execOperations;

    @InputFile
    public abstract RegularFileProperty getCompiler();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getSchemaFiles();

    @Input
    public abstract ListProperty<String> getOptions();

    @Input
    public abstract MapProperty<String, String> getReplacements();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @Inject
    public GenerateFlatBufferCode(ExecOperations execOperations) {
        this.execOperations = execOperations;
    }

    @TaskAction
    public void generate() throws Exception {

        var outputDir = getOutputDirectory().getAsFile().get();
        if (outputDir.exists()) {
            Files.walkFileTree(outputDir.toPath(), new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return super.visitFile(file, attrs);
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    if (!dir.equals(outputDir.toPath())) {
                        Files.delete(dir);
                    }
                    return super.postVisitDirectory(dir, exc);
                }
            });
        }

        var logFile = new File(getTemporaryDir(), "flatc.log");
        try (var log = new FileOutputStream(logFile)) {
            execOperations.exec(spec -> {
                spec.executable(getCompiler().get().getAsFile().getAbsolutePath());
                spec.args(getOptions().get());
                spec.args("-o", getOutputDirectory().getAsFile().get().getAbsolutePath());
                spec.args(getSchemaFiles().getFiles().stream().map(File::getAbsolutePath).toList());
            });
        }

        applyReplacements();
    }

    private void applyReplacements() throws IOException {
        var replacements = getReplacements().get();
        if (!replacements.isEmpty()) {
            try (var fileStream = Files.walk(getOutputDirectory().getAsFile().get().toPath())) {
                fileStream.filter(Files::isRegularFile)
                        .forEach(f -> {
                            try {
                                var content = Files.readString(f, StandardCharsets.UTF_8);
                                if (replacements.keySet().stream().anyMatch(content::contains)) {
                                    for (var entry : replacements.entrySet()) {
                                        content = content.replace(entry.getKey(), entry.getValue());
                                    }
                                    Files.writeString(f, content, StandardCharsets.UTF_8);
                                    getLogger().info("Replaced in '{}", f);
                                }
                            } catch (IOException e) {
                                throw new UncheckedIOException("Failed replacement in " + f, e);
                            }
                        });
            }
        }
    }
}
