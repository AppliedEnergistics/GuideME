package guideme.internal.extensions;

import guideme.compiler.TagCompiler;
import guideme.compiler.tags.ATagCompiler;
import guideme.compiler.tags.BoxFlowDirection;
import guideme.compiler.tags.BoxTagCompiler;
import guideme.compiler.tags.BreakCompiler;
import guideme.compiler.tags.CategoryIndexCompiler;
import guideme.compiler.tags.DivTagCompiler;
import guideme.compiler.tags.FloatingImageCompiler;
import guideme.compiler.tags.ItemGridCompiler;
import guideme.compiler.tags.ItemLinkCompiler;
import guideme.compiler.tags.RecipeCompiler;
import guideme.compiler.tags.RecipeTypeMappingSupplier;
import guideme.compiler.tags.SubPagesCompiler;
import guideme.document.block.recipes.LytCraftingRecipe;
import guideme.document.block.recipes.LytSmeltingRecipe;
import guideme.document.block.recipes.LytSmithingRecipe;
import guideme.extensions.Extension;
import guideme.extensions.ExtensionCollection;
import guideme.extensions.ExtensionPoint;
import guideme.scene.BlockImageTagCompiler;
import guideme.scene.ItemImageTagCompiler;
import guideme.scene.SceneTagCompiler;
import guideme.scene.annotation.BlockAnnotationElementCompiler;
import guideme.scene.annotation.BoxAnnotationElementCompiler;
import guideme.scene.annotation.DiamondAnnotationElementCompiler;
import guideme.scene.annotation.LineAnnotationElementCompiler;
import guideme.scene.element.ImportStructureElementCompiler;
import guideme.scene.element.IsometricCameraElementCompiler;
import guideme.scene.element.SceneBlockElementCompiler;
import guideme.scene.element.SceneElementTagCompiler;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.world.item.crafting.RecipeType;

public final class DefaultExtensions {
    private static final List<Registration<?>> EXTENSIONS = List.of(
            new Registration<>(TagCompiler.EXTENSION_POINT, DefaultExtensions::tagCompilers),
            new Registration<>(SceneElementTagCompiler.EXTENSION_POINT, DefaultExtensions::sceneElementTagCompilers),
            new Registration<>(RecipeTypeMappingSupplier.EXTENSION_POINT, DefaultExtensions::vanillaRecipeTypes));

    private DefaultExtensions() {
    }

    public static void addAll(ExtensionCollection.Builder builder, Set<ExtensionPoint<?>> disabledExtensionPoints) {
        for (var registration : EXTENSIONS) {
            add(builder, disabledExtensionPoints, registration);
        }
    }

    private static <T extends Extension> void add(ExtensionCollection.Builder builder,
            Set<ExtensionPoint<?>> disabledExtensionPoints, Registration<T> registration) {
        if (disabledExtensionPoints.contains(registration.extensionPoint)) {
            return;
        }

        for (var extension : registration.factory.get()) {
            builder.add(registration.extensionPoint, extension);
        }
    }

    private static List<TagCompiler> tagCompilers() {
        return List.of(
                new DivTagCompiler(),
                new ATagCompiler(),
                new ItemLinkCompiler(),
                new FloatingImageCompiler(),
                new BreakCompiler(),
                new RecipeCompiler(),
                new ItemGridCompiler(),
                new CategoryIndexCompiler(),
                new BlockImageTagCompiler(),
                new ItemImageTagCompiler(),
                new BoxTagCompiler(BoxFlowDirection.ROW),
                new BoxTagCompiler(BoxFlowDirection.COLUMN),
                new SceneTagCompiler(),
                new SubPagesCompiler());
    }

    private static List<SceneElementTagCompiler> sceneElementTagCompilers() {
        return List.of(
                new SceneBlockElementCompiler(),
                new ImportStructureElementCompiler(),
                new IsometricCameraElementCompiler(),
                new BlockAnnotationElementCompiler(),
                new BoxAnnotationElementCompiler(),
                new LineAnnotationElementCompiler(),
                new DiamondAnnotationElementCompiler());
    }

    private static List<RecipeTypeMappingSupplier> vanillaRecipeTypes() {
        return List.of(
                mappings -> {
                    mappings.add(RecipeType.CRAFTING, LytCraftingRecipe::new);
                    mappings.add(RecipeType.SMELTING, LytSmeltingRecipe::new);
                    mappings.add(RecipeType.SMITHING, LytSmithingRecipe::new);
                });
    }

    private record Registration<T extends Extension>(ExtensionPoint<T> extensionPoint,
            Supplier<Collection<T>> factory) {
    }
}
