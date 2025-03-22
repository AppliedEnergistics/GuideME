package guideme;

import guideme.compiler.ParsedGuidePage;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public record GuidePageChange(
        @Nullable String language,
        ResourceLocation pageId,
        @Nullable ParsedGuidePage oldPage,
        @Nullable ParsedGuidePage newPage) {
    @Deprecated(forRemoval = true)
    public GuidePageChange(ResourceLocation pageId, @Nullable ParsedGuidePage oldPage,
            @Nullable ParsedGuidePage newPage) {
        this(null, pageId, oldPage, newPage);
    }
}
