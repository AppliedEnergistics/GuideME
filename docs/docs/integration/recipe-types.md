# Custom Recipe Types

## Registering Custom Recipe Types

To display your custom recipe types in mods, you can implement
the [RecipeTypeMappingSupplier](https://guideme.appliedenergistics.org/javadoc/guideme/compiler/tags/RecipeTypeMappingSupplier.html).

While it can be added to guides using the standard GuideME extension mechanism, this interface can also be
exposed as a Java ServiceLoader service, which enables its use in all guides.

This is important since your recipe types can show up in other guides through the use of data packs.

The following example adds custom recipe layout implementations for the AE2 custom recipe types.
You do not need to use a custom block subclass necessarily, since `RecipeTypeMappings` just expects a
factory of the form `Function<RecipeHolder<T>, LytBlock>` for each recipe type.

```java
package appeng.client.guidebook;

// ...

public class RecipeTypeContributions implements RecipeTypeMappingSupplier {
    @Override
    public void collect(RecipeTypeMappings mappings) {
        mappings.add(AERecipeTypes.INSCRIBER, LytInscriberRecipe::new);
        mappings.add(AERecipeTypes.CHARGER, LytChargerRecipe::new);
        mappings.add(AERecipeTypes.TRANSFORM, LytTransformRecipe::new);
    }
}
```

To make GuideME load this extension, add its fully qualified class name to a file with the following path in your
project:
`src/main/resources/META-INF/services/guideme.compiler.tags.RecipeTypeMappingSupplier`.

## Custom Layout Blocks

If your recipe requires a custom layout block, you can use the
existing [recipe layouts in GuideME](https://github.com/AppliedEnergistics/GuideME/tree/main/src/main/java/guideme/document/block/recipes)
as a starting point.
