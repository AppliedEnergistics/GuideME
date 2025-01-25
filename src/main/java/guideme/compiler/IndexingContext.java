package guideme.compiler;

import guideme.PageCollection;
import guideme.extensions.Extension;
import guideme.extensions.ExtensionCollection;
import guideme.extensions.ExtensionPoint;
import guideme.indices.PageIndex;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public interface IndexingContext {
    ExtensionCollection getExtensions();

    default <T extends Extension> List<T> getExtensions(ExtensionPoint<T> extensionPoint) {
        return getExtensions().get(extensionPoint);
    }

    /**
     * Get the current page id.
     */
    ResourceLocation getPageId();

    PageCollection getPageCollection();

    default byte @Nullable [] loadAsset(ResourceLocation imageId) {
        return getPageCollection().loadAsset(imageId);
    }

    default <T extends PageIndex> T getIndex(Class<T> clazz) {
        return getPageCollection().getIndex(clazz);
    }
}
