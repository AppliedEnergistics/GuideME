package guideme.siteexport.web;

import guideme.internal.web.StaticSiteGenerator;
import org.jetbrains.annotations.ApiStatus;

/**
 * Generates a static website from a guide that was previously exported from the game (i.e. using the
 * {@code guideme.exportOnStartupAndExit} system property or the {@code /guidemec <guide> export} command).
 * <p>
 * This is meant to be run as a Java application, i.e. by a Gradle {@code JavaExec} task, whose classpath contains
 * GuideME, Minecraft and your mod. Implementations of {@link RecipeWebRenderer}, {@link CustomElementWebRenderer} and
 * {@link WebSiteContribution} are discovered from that classpath using the Java {@link java.util.ServiceLoader}.
 * <p>
 * Arguments:
 * <ul>
 * <li>{@code --data <folder>}: The folder containing the exported guide (required).</li>
 * <li>{@code --output <folder>}: The folder to write the website to (required).</li>
 * <li>{@code --web-assets <folder>}: A folder whose content overrides the default web assets (optional).</li>
 * <li>{@code --change-version-url <url>}: The URL to link to for changing the guide version (optional).</li>
 * <li>Run without arguments to see all options, such as {@code --title}, {@code --site-url}, {@code --base-path} and
 * {@code --clean-urls}.</li>
 * </ul>
 */
@ApiStatus.Experimental
public final class WebSiteGenerator {
    private WebSiteGenerator() {
    }

    public static void main(String[] args) {
        StaticSiteGenerator.main(args);
    }
}
