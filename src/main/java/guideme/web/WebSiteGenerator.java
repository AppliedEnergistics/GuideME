package guideme.web;

import java.nio.file.Path;
import java.util.ArrayList;
import org.jetbrains.annotations.ApiStatus;

/**
 * Generates a static website from a guide that was previously exported from the game (i.e. using the
 * {@code guideme.exportOnStartupAndExit} system property or the {@code /guidemec <guide> export} command).
 * <p>
 * Using {@code --versions} instead of {@code --data} generates a website with multiple versions of the guide, i.e. one
 * for each supported Minecraft version. Each version is written to a folder named after its Minecraft version, and the
 * root of the website lists all versions.
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
        Path versionsFolder = null;
        String developmentUrl = null;
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
                case "--versions" -> versionsFolder = Path.of(value);
                case "--development-url" -> developmentUrl = value;
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
        if ((dataFolder == null) == (versionsFolder == null)) {
            exitWithUsage("Either --data or --versions is required");
        }
        if (outputFolder == null) {
            exitWithUsage("--output is required");
        }
        if (developmentUrl != null && versionsFolder == null) {
            exitWithUsage("--development-url requires --versions");
        }

        var options = new guideme.internal.web.WebSiteGenerator.Options(
                versionsFolder != null ? versionsFolder : dataFolder, outputFolder, webAssetsFolder, changeVersionUrl,
                title, logo, favicon, siteUrl, basePath, pageSubdirectories, clean, stylesheets, scripts);
        if (versionsFolder != null) {
            new guideme.internal.web.VersionedWebSiteGenerator(
                    new guideme.internal.web.VersionedWebSiteGenerator.Options(options, developmentUrl)).generate();
        } else {
            new guideme.internal.web.WebSiteGenerator(options).generate();
        }
    }

    private static void exitWithUsage(String error) {
        System.err.println(error);
        System.err.println(
                """
                        Usage: --data <export-folder> --output <destination-folder> [options]
                           or: --versions <folder-of-export-folders> --output <destination-folder> [options]
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
                          --change-version-url <url>  URL to link to for changing the guide version (with --versions, defaults to the root)
                          --development-url <url>     With --versions, URL of a separately published development version to link to
                          --web-assets <folder>       Folder with files overriding the default web assets""");
        System.exit(1);
    }
}
