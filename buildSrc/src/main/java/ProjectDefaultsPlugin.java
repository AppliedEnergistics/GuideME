import dev.lukebemish.immaculate.ImmaculateExtension;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.jvm.toolchain.JavaLanguageVersion;

import java.io.File;
import java.util.Collections;

public class ProjectDefaultsPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.setGroup("guideme");

        var java = project.getExtensions().getByType(JavaPluginExtension.class);
        java.toolchain(spec -> {
            spec.getLanguageVersion().set(project.getProviders().gradleProperty("java_version").map(JavaLanguageVersion::of));
        });

        // ensure everything uses UTF-8 and not some random codepage chosen by Gradle
        project.getTasks().withType(JavaCompile.class, ProjectDefaultsPlugin::setupCompiler);

        configureImmaculate(project);
    }

    private static void setupCompiler(JavaCompile task) {
        var options = task.getOptions();
        options.setEncoding("UTF-8");
        options.setDeprecation(false);
        Collections.addAll(
                options.getCompilerArgs(),
                "-Xmaxerrs", "9999"
        );
    }

    static void configureImmaculate(Project project) {
        // Setup the formatter
        project.getPlugins().apply("dev.lukebemish.immaculate");

        var immaculate = project.getExtensions().getByType(ImmaculateExtension.class);
        var workflow = immaculate.getWorkflows().create("java");

        workflow.java();
        // Ending files by a new line is good practice, and avoids "No newline at end of file" comments by Git.
        workflow.trailingNewline();
        // Tabs can render differently, and can accidentally be inserted. Enforce spaces only.
        workflow.noTabs();
        // Reorder imports according to simple lexicographic ordering, and remove unused imports.
        workflow.googleFixImports();
        // Allow disabling the formatter for specific sections. Should be used sparingly.
        workflow.getToggleOff().set("spotless:off");
        workflow.getToggleOn().set("spotless:on");
        // Most formatting rules are handled by the eclipse formatter config.
        // They are generally chosen to match standard Java style, and eliminate discussions about style.
        workflow.eclipse(step -> {
            step.version("3.37.0");
            step.getConfig().set(new File(project.getRootDir(), "docs/codeformat.xml"));
        });

        // Wildcard imports:
        // - cannot be automatically removed if unused
        // - make it harder to see which classes are being used
        // - are often inserted automatically by IDEs, which leads to unnecessary diffs in imports
        // courtesy of diffplug/spotless#240
        // https://github.com/diffplug/spotless/issues/240#issuecomment-385206606
        workflow.custom("noWildcardImports", fileContents -> {
            if (fileContents.contains("*;\n")) {
                throw new InvalidUserDataException("No wildcard imports are allowed!");
            }
            return fileContents;
        });

        // Mixing non-nullable annotations with non-annotated types leads to confusion
        // wrt. nullability of non-annotated types.
        // Annotating all types would be too verbose, so we assume non-nullability by default,
        // and disallow non-null annotations which are then unnecessary.
        workflow.custom("noNotNull", fileContents -> {
            if (fileContents.contains("@NotNull") || fileContents.contains("@Nonnull")) {
                throw new InvalidUserDataException("@NotNull and @Nonnull are disallowed.");
            }
            return fileContents;
        });

        // JetBrains nullability annotations can be used in more contexts,
        // and we also use other JB annotations such as @ApiStatus.
        workflow.custom("jetbrainsNullable", fileContents -> {
            return fileContents.replace("javax.annotation.Nullable", "org.jetbrains.annotations.Nullable");
        });
    }
}
