import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.zip.ZipFile;

public abstract class DownloadFlatBufferCompilerTask extends DefaultTask {

    private static final String DOWNLOAD_BASE_URL = "https://github.com/google/flatbuffers/releases/download";

    @Input
    public abstract Property<String> getVersion();

    @Input
    public abstract Property<URI> getDownloadUrl();

    @Input
    public abstract Property<String> getExecutableName();

    @OutputFile
    public abstract RegularFileProperty getExecutableFile();

    public DownloadFlatBufferCompilerTask() {
        getExecutableFile().convention(getProject().getLayout().getBuildDirectory().file(getExecutableName().map(exeName -> "flatbuffers-bin/" + exeName)));
        setDescription("This task download the FlatBuffers compiler suitable for the current platform.");
        setGroup("flatbuffers");

        var os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        var arch = System.getProperty("os.arch");

        getDownloadUrl().convention(getVersion().map(version -> {
            if (os.startsWith("windows")) {
                return URI.create(DOWNLOAD_BASE_URL + "/v" + version + "/Windows.flatc.binary.zip");
            } else if (os.startsWith("mac")) {
                if (arch.equals("aarch64")) {
                    return URI.create(DOWNLOAD_BASE_URL + "/v" + version + "/Mac.flatc.binary.zip");
                } else if (arch.equals("amd64")) {
                    return URI.create(DOWNLOAD_BASE_URL + "/v" + version + "/MacIntel.flatc.binary.zip");
                } else {
                    throw new GradleException("Unknown Mac architecture: " + arch);
                }
            } else if (os.startsWith("linux")) {
                return URI.create(DOWNLOAD_BASE_URL + "/v" + version + "/Linux.flatc.binary.clang++-12.zip");
            } else {
                throw new GradleException("Unknown OS: " + os);
            }
        }));
        getExecutableName().convention(os.startsWith("windows") ? "flatc.exe" : "flatc");
    }

    @TaskAction
    public void download() throws Exception {
        var executableFile = getExecutableFile().getAsFile().get().toPath();

        var tempZipPath = new File(getTemporaryDir(), "flatc.zip");
        try (var in = getDownloadUrl().get().toURL().openStream()) {
            Files.copy(in, tempZipPath.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }

        String executableName = getExecutableName().get();
        try (var zf = new ZipFile(tempZipPath)) {
            var entry = zf.getEntry(executableName);
            if (entry == null) {
                throw new IllegalStateException("Downloaded flatbuffers ZIP " + tempZipPath + " does not contain " + executableName);
            }
            try (var in = zf.getInputStream(entry)) {
                Files.copy(in, executableFile, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

}
