package guideme;

import guideme.extensions.ExtensionCollection;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.NonExtendable
public interface Guide extends PageCollection {
    static GuideBuilder builder(ResourceLocation id) {
        return new GuideBuilder(id);
    }

    ResourceLocation getId();

    ResourceLocation getStartPage();

    String getDefaultNamespace();

    String getContentRootFolder();

    ExtensionCollection getExtensions();
}
