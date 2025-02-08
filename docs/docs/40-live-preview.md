
import Video from '@site/src/components/Video';

# Live Preview

GuideME supports a live-preview mode where a local directory is designated as the source folder for guidebook pages.
In this mode, any change to files in this directory is immediately reflected in-game, without having to copy
resources.

<Video src="live-preview.mp4" />

This mode can be enabled by passing system properties (`-D<prop>=<value>` if you're using a launcher).

| System Property                                                 | Description                                                                                                                                                             |
|-----------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `guideme.<guide_id_namespace>.<guide_id_path>.sources`          | Path to the directory on disk containing the pages. If you are developing a mod, i.e. `file("guidebook").absolutePath` to refer to pages in a root guidebook directory. |
| `guideme.<guide_id_namespace>.<guide_id_path>.sourcesNamespace` | Specifies the resourcepack namespace for pages found in the directory specified as `.sources`. Defaults to `<guide_id_namespace>`.                                      |

## Gradle Example

```groovy
systemProperty "guideme.<guide_id_namespace>.<guide_id_path>.sources", file("guidebook").absolutePath
systemProperty "guideme.<guide_id_namespace>.<guide_id_path>.sourcesNamespace", "your-mod-id"
```

This will load the `guidebook` folder as if it was included in the resource-pack of your mod under the `ae2guide`
folder.
It will also automatically reload any pages that are changed in this folder, while the game is running.

To automatically show the guidebook directly after launching the game, you can also set the
`guideme.showOnStartup` system property to the id of the guide that should be shown on startup. You can also jump
to a specific page using `mod:guide!page.md#anchor`.

You can combine these properties for a separate `runGuide` run configuration, that will directly launch into your guide
live preview.

For example, if you are developing for the AE2 guide (with id `ae2:guide`), that configuration would look like this:

```groovy
neoForge {
    runs {
        guide {
            client()
            property 'guideme.ae2.guide.sources', file('guidebook').absolutePath
            // This is only needed if you are developing an addon and it should be your mod id
            // property "guideme.ae2.guide.sourcesNamespace", "ae2addon"
            systemProperty('guideme.showOnStartup', 'ae2:index.md') // Start at index.md
        }
    }
}
```
