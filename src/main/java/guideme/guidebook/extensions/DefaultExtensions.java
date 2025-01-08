package guideme.guidebook.extensions;

import guideme.guidebook.compiler.TagCompiler;
import guideme.guidebook.compiler.tags.ATagCompiler;
import guideme.guidebook.compiler.tags.BoxFlowDirection;
import guideme.guidebook.compiler.tags.BoxTagCompiler;
import guideme.guidebook.compiler.tags.BreakCompiler;
import guideme.guidebook.compiler.tags.CategoryIndexCompiler;
import guideme.guidebook.compiler.tags.DivTagCompiler;
import guideme.guidebook.compiler.tags.FloatingImageCompiler;
import guideme.guidebook.compiler.tags.ItemGridCompiler;
import guideme.guidebook.compiler.tags.ItemLinkCompiler;
import guideme.guidebook.compiler.tags.RecipeCompiler;
import guideme.guidebook.compiler.tags.SubPagesCompiler;
import guideme.guidebook.scene.BlockImageTagCompiler;
import guideme.guidebook.scene.ItemImageTagCompiler;
import guideme.guidebook.scene.SceneTagCompiler;
import guideme.guidebook.scene.annotation.BlockAnnotationElementCompiler;
import guideme.guidebook.scene.annotation.BoxAnnotationElementCompiler;
import guideme.guidebook.scene.annotation.DiamondAnnotationElementCompiler;
import guideme.guidebook.scene.annotation.LineAnnotationElementCompiler;
import guideme.guidebook.scene.element.ImportStructureElementCompiler;
import guideme.guidebook.scene.element.IsometricCameraElementCompiler;
import guideme.guidebook.scene.element.SceneBlockElementCompiler;
import guideme.guidebook.scene.element.SceneElementTagCompiler;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

public final class DefaultExtensions {
    private static final List<Registration<?>> EXTENSIONS = List.of(
            new Registration<>(TagCompiler.EXTENSION_POINT, DefaultExtensions::tagCompilers),
            new Registration<>(SceneElementTagCompiler.EXTENSION_POINT, DefaultExtensions::sceneElementTagCompilers));

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

    private record Registration<T extends Extension>(ExtensionPoint<T> extensionPoint,
            Supplier<Collection<T>> factory) {
    }
}
