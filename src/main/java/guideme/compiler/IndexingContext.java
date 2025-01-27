package guideme.compiler;

import guideme.PageCollection;
import guideme.extensions.Extension;
import guideme.extensions.ExtensionCollection;
import guideme.extensions.ExtensionPoint;
import guideme.indices.PageIndex;
import guideme.libs.mdast.model.MdAstAnyContent;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * The context used during search indexing of custom tags {@link TagCompiler}.
 */
@ApiStatus.NonExtendable
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

    default void indexContent(List<? extends MdAstAnyContent> children, IndexingSink sink) {
        for (var child : children) {
            indexContent(child, sink);
        }
    }

    void indexContent(MdAstAnyContent content, IndexingSink sink);

    default byte @Nullable [] loadAsset(ResourceLocation imageId) {
        return getPageCollection().loadAsset(imageId);
    }

    default <T extends PageIndex> T getIndex(Class<T> clazz) {
        return getPageCollection().getIndex(clazz);
    }
}
