package guideme;

import guideme.compiler.ParsedGuidePage;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

public record GuidePageChange(
        @Nullable String language,
        Identifier pageId,
        @Nullable ParsedGuidePage oldPage,
        @Nullable ParsedGuidePage newPage) {
    @Deprecated(forRemoval = true)
    public GuidePageChange(Identifier pageId, @Nullable ParsedGuidePage oldPage,
            @Nullable ParsedGuidePage newPage) {
        this(null, pageId, oldPage, newPage);
    }
}
