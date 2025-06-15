# Custom Tags

To add custom tags to your guide, you pass an extension to the Guide builder when building your guide.

The relevant extension point is
the [tag compiler extension point](https://guideme.appliedenergistics.org/javadoc/guideme/compiler/TagCompiler.html#EXTENSION_POINT).

To avoid namespace clashes with other mods, you should prefix your custom tags with your mod-id such as `modid:Tag`.

## Example

Here's a trivial example for a tag that appends the current value of a mod configuration to the page when it is
compiled.

Since it appends content in a "flow" content as opposed to "block" context, it extends from `FlowTagCompiler`.
If your custom tag adds block-level content like paragraphs, tables, or similar UI elements, it can extend from
`BlockTagCompiler`.

```java
public class ConfigValueTagExtension extends FlowTagCompiler {
    @Override
    public Set<String> getTagNames() {
        return Set.of("ae2:ConfigValue");
    }

    @Override
    protected void compile(PageCompiler compiler, LytFlowParent parent, MdxJsxElementFields el) {
        var configValueName = el.getAttributeString("name", "");
        if (configValueName.isEmpty()) {
            // This will append an error to the page content which points back to the location in the source Markdown file
            parent.appendError(compiler, "name is required", el);
            return;
        }

        // ... get the config value somehow
        var configValue = "123";

        parent.appendText(configFile);
    }
}
```

To register this extension in your guide, you pass it to the `extension` method of the Guide builder:

```java
var guide = Guide.builder(ResourceLocation.parse("ae2:guide"))
        .extension(TagCompiler.EXTENSION_POINT, new ConfigValueTagExtension())
        .build();
```
