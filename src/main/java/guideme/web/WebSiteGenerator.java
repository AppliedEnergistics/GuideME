package guideme.web;

import java.nio.file.Path;
import java.util.ArrayList;
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
        Path dataFolder = null;
        Path outputFolder = null;
        Path webAssetsFolder = null;
        String changeVersionUrl = null;
        String title = "Guide";
        Path logo = null;
        Path favicon = null;
        String siteUrl = null;
        String basePath = "/";
        boolean pageSubdirectories = false;
        boolean clean = false;
        var stylesheets = new ArrayList<Path>();
        var scripts = new ArrayList<Path>();
        for (int i = 0; i < args.length; i++) {
            var arg = args[i];
            // Flags without value
            if (arg.equals("--page-subdirectories")) {
                pageSubdirectories = true;
                continue;
            } else if (arg.equals("--clean")) {
                clean = true;
                continue;
            }

            if (i + 1 >= args.length) {
                exitWithUsage(arg + " requires an argument");
            }
            var value = args[++i];
            switch (arg) {
                case "--data" -> dataFolder = Path.of(value);
                case "-o", "--output" -> outputFolder = Path.of(value);
                case "--web-assets" -> webAssetsFolder = Path.of(value);
                case "--change-version-url" -> changeVersionUrl = value;
                case "--title" -> title = value;
                case "--logo" -> logo = Path.of(value);
                case "--favicon" -> favicon = Path.of(value);
                case "--stylesheet" -> stylesheets.add(Path.of(value));
                case "--script" -> scripts.add(Path.of(value));
                case "--site-url" -> siteUrl = value;
                case "--base-path" -> basePath = value;
                default -> exitWithUsage("Unknown argument: " + arg);
            }
        }
        if (dataFolder == null) {
            exitWithUsage("--data is required");
        }
        if (outputFolder == null) {
            exitWithUsage("--output is required");
        }

        new guideme.internal.web.WebSiteGenerator(new guideme.internal.web.WebSiteGenerator.Options(dataFolder,
                outputFolder, webAssetsFolder, changeVersionUrl, title, logo,
                favicon, siteUrl, basePath, pageSubdirectories, clean, stylesheets, scripts)).generate();
    }

    private static void exitWithUsage(String error) {
        System.err.println(error);
        System.err.println(
                """
                        Usage: --data <export-folder> --output <destination-folder> [options]
                        Options:
                          --clean                     Delete the content of the output folder first
                          --title <title>             The title of the guide
                          --logo <file>               Image file to use as the logo
                          --favicon <file>            Image file to use as the favicon (defaults to the logo)
                          --stylesheet <file>         Stylesheet to include on every page (can be repeated)
                          --script <file>             Script to include on every page (can be repeated)
                          --site-url <url>            URL of the website, i.e. https://guide.example.com
                          --base-path <path>          URL path the website is served from, i.e. /1.21.1/
                          --page-subdirectories       Write pages as <page>/index.html to link to them without the .html extension
                          --change-version-url <url>  URL to link to for changing the guide version
                          --web-assets <folder>       Folder with files overriding the default web assets""");
        System.exit(1);
    }
}
