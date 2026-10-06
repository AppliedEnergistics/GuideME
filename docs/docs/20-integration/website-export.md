# Website Export

GuideME can turn your guide into a static website. This happens in two steps:

1. The guide is **exported** from the game. This writes the pages, item icons, recipes and 3D scenes to a folder.
2. A **website is generated** from that export. This runs outside the game as a Gradle task, with GuideME,
   Minecraft and your mod on the classpath.

Since the website is generated from the export, you can also regenerate the website for guides you exported in the
past, for example with a newer version of GuideME.

:::warning
The website export API is experimental and may change between versions.
:::

## Exporting the Guide

Add a run configuration that exports your guide and then exits the game. With ModDevGradle:

```gradle
neoForge {
    runs {
        exportGuide {
            client()
            systemProperty('guideme.exportOnStartupAndExit', 'yourmod:guide')
            systemProperty('guideme.exportDestination.yourmod.guide', file('build/exportedGuide').absolutePath)
            // Optional, defaults to the version of your mod
            systemProperty('guideme.exportModVersion.yourmod.guide', project.version.toString())
        }
    }
}

// Declare the output, so that tasks using the export know where it comes from
tasks.named('runExportGuide') {
    outputs.dir('build/exportedGuide')
}
```

In a running game, you can also use `/guidemec <guide> export`, which writes the export to
`guideme_exports` in the game directory.

## Generating the Website

Add a `JavaExec` task that runs the website generator with the runtime classpath of your mod:

```gradle
tasks.register('createGuideWebsite', JavaExec) {
    def exportFolder = file('build/exportedGuide')
    def websiteFolder = file('build/guideWebsite')

    dependsOn 'runExportGuide'
    // The classpath and arguments are tracked automatically, but the folders have to be declared, so Gradle can
    // skip the task if nothing changed
    inputs.dir(exportFolder)
    outputs.dir(websiteFolder)

    classpath = sourceSets.main.runtimeClasspath
    mainClass = 'guideme.web.WebSiteGenerator'
    args '--data', exportFolder.absolutePath,
         '--output', websiteFolder.absolutePath,
         '--clean'
}
```

| Argument | Description |
|---|---|
| `--data <folder>` | The folder containing the guide export (required). |
| `--output <folder>` | The folder the website is written to (required). |
| `--clean` | Deletes the content of the output folder first, so pages that no longer exist are removed. |
| `--title <title>` | The title of the guide, shown in the header and the browser title. |
| `--logo <file>` | An image file to use as the logo. Defaults to the GuideME logo. |
| `--favicon <file>` | An image file to use as the favicon. Defaults to the logo. |
| `--stylesheet <file>` | A stylesheet to include on every page, to change the look of the website. Can be repeated. |
| `--script <file>` | A script to include on every page. Can be repeated. |
| `--site-url <url>` | The URL the website is published at, i.e. `https://guide.example.com`. Enables canonical links, `sitemap.xml` and `robots.txt`. |
| `--base-path <path>` | The URL path the website is served from, i.e. `/1.21.1/` when publishing several versions side by side. Defaults to `/`. |
| `--clean-urls` | Writes pages as `page/index.html` so they can be linked without the `.html` extension on any web host. |
| `--web-assets <folder>` | A folder whose files override the default web assets, such as the page layout. |
| `--change-version-url <url>` | A URL to link to for picking a different version of the guide. Defaults to `/` if a base path is set. |

The website includes a search function, a `404.html` page, and an `index.html` that redirects to the start page
of the guide if the guide has no `index.md`.

## Previewing the Website

Browsers block the scripts of pages opened directly from disk, so serve the website over HTTP to preview it.
GuideME includes a small server that serves a folder on a random local port:

```gradle
tasks.register('serveGuideWebsite', JavaExec) {
    mustRunAfter 'createGuideWebsite'
    classpath = sourceSets.main.runtimeClasspath
    mainClass = 'guideme.web.WebSiteServer'
    args file('build/guideWebsite').absolutePath
}
```

Running `./gradlew createGuideWebsite serveGuideWebsite` prints the URL to open. Stop the server with Ctrl+C.

## Rendering Custom Content

The tags built into GuideME are rendered automatically. If your guide uses custom tags or custom recipe types, you
need to tell the website generator how to render them. The following interfaces are discovered using the
Java `ServiceLoader`, so register your implementations in `META-INF/services/<interface name>` in your mod.

| Interface | Purpose |
|---|---|
| `guideme.web.CustomElementWebRenderer` | Renders custom tags to HTML. |
| `guideme.web.RecipeWebRenderer` | Renders custom recipe types. Use `RecipeExporter` to export the recipe data. |

:::note
These run in the website generator, **not in the game**. They may use Minecraft and mod classes, but must not access
registries or other game state. Everything they need must come from the export.

Data that is only available in-game can be exported using `ResourceExporter#addExtraData` from an
`AdditionalResourceExporter` extension, and read using `ExportedGuide#getExtraData`.
:::

Renderers produce HTML using `HtmlNode.tag(...)` and the helpers on their rendering context, for example
`itemIcon`, `itemLink`, `getPageUrl`, or `getAssetUrl(Identifier)`, which copies a texture from your mod resources
into the website.

```java
public class ConfigValueWebRenderer implements CustomElementWebRenderer {
    @Override
    public Set<String> getTagNames() {
        return Set.of("yourmod:ConfigValue");
    }

    @Override
    public void render(CustomElementWebRenderingContext context, Consumer<HtmlNode> output) {
        record Attributes(String name) {
        }
        var attributes = context.map(Attributes.class);

        @SuppressWarnings("unchecked")
        var values = (Map<String, String>) context.guide().getExtraData("yourmod:config_values");
        var value = values != null ? values.get(attributes.name()) : null;
        if (value == null) {
            output.accept(context.compileError("Unknown config value " + attributes.name()));
            return;
        }
        output.accept(HtmlNode.tag("code").append(value));
    }
}
```

If your renderers need styles, include a stylesheet from your mod with `requireStylesheet` (or a script with
`requireScript`). It's only included on pages where your renderer was used, so the styles of your mod don't affect
the websites of other mods that have your mod on their classpath. Relative `url(...)` references in stylesheets are
copied into the website as well.

```java
context.requireStylesheet("assets/yourmod/web/recipes.css");
```

### Resolving In-Game Information During Export

Some tags depend on information that is only available in-game, such as whether a mod is loaded. A
`guideme.siteexport.PageExportProcessor` extension can modify the page before it is exported, so the website
generator only sees the result. Register it on your guide like any other extension.

A processor selects the nodes it applies to and can change their attributes, or replace them, for example with their
own content. Processors work on a copy of the page, so their changes don't affect the in-game guide. GuideME uses this itself to resolve symbolic colors of `<Color>`
and the keys of `<KeyBind>`.

Processors can also access the result of compiling the page for the in-game guide: `PageExportContext#getLayoutNodes`
returns the layout nodes compiled from an element, and `getParent` its parent. GuideME uses this to replace
`<BlockAnnotationTemplate>` with the annotations it created in the scene of the enclosing `<GameScene>`.

```java
public class OptionalSectionExportProcessor implements PageExportProcessor {
    @Override
    public NodeSelector getSelector() {
        return NodeSelector.element("OptionalSection");
    }

    @Override
    public void process(PageExportContext context, MdAstNode node) {
        var element = (MdxJsxElementFields) node;
        if (ModList.get().isLoaded(element.getAttributeString("modId", ""))) {
            context.unwrap(node); // Replace the element with its content
        } else {
            context.remove(node);
        }
    }
}
```

Keep in mind that renderers may be given data exported by older versions of your mod, if you regenerate the website
for older guide exports.

## Styling the Website

To change the look of the whole website, pass your own stylesheets to the generator with `--stylesheet`. They are
included after all other stylesheets, so they can override them.

```gradle
    args '--stylesheet', file('src/main/web/guide.css').absolutePath
    // Declare it, so the website is regenerated when it changes
    inputs.file('src/main/web/guide.css')
```
