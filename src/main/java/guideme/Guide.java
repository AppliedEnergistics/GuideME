package guideme;

import guideme.extensions.ExtensionCollection;
import net.minecraft.resources.ResourceLocation;

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
