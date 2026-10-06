import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

/**
 * Newer JDK distributions (i.e. Temurin 24+) no longer ship the jmods folder that ProGuard uses as the source for
 * JDK library classes. This task extracts the class files of all JDK modules from the runtime image of the given
 * Java installation into a jar that can be passed to ProGuard as a library jar instead.
 */
public abstract class ExtractJdkClasses extends DefaultTask {
    /**
     * The Java installation to extract the classes from.
     */
    @Input
    public abstract Property<String> getJavaHome();

    @OutputFile
    public abstract RegularFileProperty getOutput();

    @TaskAction
    void extract() throws IOException {
        var output = getOutput().get().getAsFile().toPath();
        Files.createDirectories(output.getParent());

        try (var jrt = openJrtFileSystem(getJavaHome().get());
                var jarOut = new JarOutputStream(Files.newOutputStream(output));
                var modules = Files.list(jrt.getPath("/modules"))) {
            for (var module : (Iterable<Path>) modules::iterator) {
                try (Stream<Path> files = Files.walk(module)) {
                    for (var file : (Iterable<Path>) files::iterator) {
                        var name = module.relativize(file).toString();
                        // module-info classes from different modules would collide
                        if (!name.endsWith(".class") || name.equals("module-info.class")) {
                            continue;
                        }
                        jarOut.putNextEntry(new JarEntry(name));
                        jarOut.write(Files.readAllBytes(file));
                        jarOut.closeEntry();
                    }
                }
            }
        }
    }

    private static FileSystem openJrtFileSystem(String javaHome) throws IOException {
        return FileSystems.newFileSystem(URI.create("jrt:/"), Map.of("java.home", javaHome));
    }
}
