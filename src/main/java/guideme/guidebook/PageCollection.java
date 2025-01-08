package guideme.guidebook;

import guideme.guidebook.compiler.ParsedGuidePage;
import guideme.guidebook.indices.PageIndex;
import guideme.guidebook.navigation.NavigationTree;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public interface PageCollection {
    <T extends PageIndex> T getIndex(Class<T> indexClass);

    @Nullable
    ParsedGuidePage getParsedPage(ResourceLocation id);

    @Nullable
    GuidePage getPage(ResourceLocation id);

    byte @Nullable [] loadAsset(ResourceLocation id);

    NavigationTree getNavigationTree();

    boolean pageExists(ResourceLocation pageId);
}
