package appeng.client.guidebook;

import appeng.client.guidebook.compiler.ParsedGuidePage;
import appeng.client.guidebook.indices.PageIndex;
import appeng.client.guidebook.navigation.NavigationTree;
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
