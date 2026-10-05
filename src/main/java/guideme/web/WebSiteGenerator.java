package guideme.web;

import org.jetbrains.annotations.ApiStatus;

/**
 * Generates a static website from a guide that was previously exported from the game (i.e. using the
 * {@code guideme.exportOnStartupAndExit} system property or the {@code /guidemec <guide> export} command).
 * <p>
 * This is meant to be run as a Java application, i.e. by a Gradle {@code JavaExec} task, whose classpath contains
 * GuideME, Minecraft and your mod. Implementations of {@link RecipeWebRenderer} and {@link CustomElementWebRenderer}
 * are discovered from that classpath using the Java {@link java.util.ServiceLoader}.
 * <p>
 * Run it without arguments to see all options.
 */
@ApiStatus.Experimental
public final class WebSiteGenerator {
    private WebSiteGenerator() {
    }

    static void main(String[] args) {
        guideme.internal.web.WebSiteGenerator.main(args);
    }
}
