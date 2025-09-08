import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

public abstract class PostProcessProGuardFile extends DefaultTask {

    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public abstract ConfigurableFileCollection getInput();

    @OutputFile
    public abstract RegularFileProperty getOutput();

    @TaskAction
    public void process() throws IOException {
        var input = getInput().getSingleFile();
        var output = getOutput().getAsFile().get();

        try (var jar = new JarFile(input);
             var zout = new JarOutputStream(new BufferedOutputStream(new FileOutputStream(output)), jar.getManifest())) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (entry.getName().equals("META-INF/MANIFEST.MF")) {
                    continue; // Will already be written by JarOutputStream
                }

                zout.putNextEntry(entry);
                if (!entry.isDirectory() && entry.getName().startsWith("META-INF/services/")) {
                    // Transform the entry
                    var result = new StringBuilder();
                    try (var reader = new BufferedReader(new InputStreamReader(jar.getInputStream(entry), StandardCharsets.ISO_8859_1))) {
                        for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                            var commentIdx = line.indexOf('#');
                            if (commentIdx != -1) {
                                line = line.substring(0, commentIdx);
                            }
                            line = line.trim();
                            if (line.isEmpty()) {
                                continue;
                            }
                            if (jar.getEntry(line.replace('.', '/') + ".class") == null) {
                                getLogger().info("Removing services entry {} from {}", line, input);
                                continue;
                            }
                            result.append(line).append('\n');
                        }
                    }

                    zout.write(result.toString().getBytes(StandardCharsets.ISO_8859_1));
                } else {
                    try (var in = jar.getInputStream(entry)) {
                        in.transferTo(zout);
                    }
                }
                zout.closeEntry();
            }
        }
    }

}
