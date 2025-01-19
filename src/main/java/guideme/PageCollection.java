package guideme;

import guideme.compiler.ParsedGuidePage;
import guideme.indices.PageIndex;
import guideme.navigation.NavigationTree;
import java.util.Collection;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public interface PageCollection {
    <T extends PageIndex> T getIndex(Class<T> indexClass);

    Collection<ParsedGuidePage> getPages();

    @Nullable
    ParsedGuidePage getParsedPage(ResourceLocation id);

    @Nullable
    GuidePage getPage(ResourceLocation id);

    byte @Nullable [] loadAsset(ResourceLocation id);

    NavigationTree getNavigationTree();

    boolean pageExists(ResourceLocation pageId);
}
