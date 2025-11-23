package guideme;

import guideme.compiler.ParsedGuidePage;
import guideme.indices.PageIndex;
import guideme.navigation.NavigationTree;
import java.util.Collection;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

public interface PageCollection {
    <T extends PageIndex> T getIndex(Class<T> indexClass);

    Collection<ParsedGuidePage> getPages();

    @Nullable
    ParsedGuidePage getParsedPage(Identifier id);

    @Nullable
    GuidePage getPage(Identifier id);

    byte @Nullable [] loadAsset(Identifier id);

    NavigationTree getNavigationTree();

    boolean pageExists(Identifier pageId);
}
