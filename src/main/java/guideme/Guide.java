package guideme;

import guideme.extensions.ExtensionCollection;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.NonExtendable
public interface Guide extends PageCollection {
    static GuideBuilder builder(Identifier id) {
        return new GuideBuilder(id);
    }

    Identifier getId();

    Identifier getStartPage();

    String getDefaultNamespace();

    String getContentRootFolder();

    ExtensionCollection getExtensions();
}
