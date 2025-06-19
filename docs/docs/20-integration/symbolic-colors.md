
# Custom Symbolic Colors

You can add custom symbolic colors in your guide, which you can use to color text using `<Color id="...">...`.

The relevant extension point is
the [symbolic color resolver extension point](https://guideme.appliedenergistics.org/javadoc/guideme/color/SymbolicColorResolver.html#EXTENSION_POINT).

A convenient way to add colors is to use the following approach:

```java
static final Map<ResourceLocation, ColorValue> CUSTOM_COLORS = Map.of(
        // The first argument for ConstantColor is the light-mode color, the second the dark-mode color.
        ResourceLocation.parse("yourmod:color1"), new ConstantColor(0xFFFF0000, 0xFFFF0000),
        ResourceLocation.parse("yourmod:color2"), new ConstantColor(0xFF0FF000, 0xFF0000FF)
);

var guide = Guide.builder(guideId)
        // ...
        .extension(SymbolicColorResolver.EXTENSION_POINT, CUSTOM_COLORS::get)
        // ...
```
