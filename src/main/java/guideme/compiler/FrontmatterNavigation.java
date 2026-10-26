package guideme.compiler;

import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Inserts a page into the navigation tree. Null parent means top-level category.
 *
 * @param maxDepth If set, the number of levels below this page that the navigation sidebar shows, overriding the depth
 *                 inherited from its ancestors.
 */
public record FrontmatterNavigation(
        String title,
        @Nullable ResourceLocation parent,
        int position,
        @Nullable ResourceLocation iconItemId,
        @Nullable Map<?, ?> iconComponents,
        @Nullable Integer maxDepth) {
    public FrontmatterNavigation(String title,
            @Nullable ResourceLocation parent,
            int position,
            @Nullable ResourceLocation iconItemId,
            @Nullable Map<?, ?> iconComponents) {
        this(title, parent, position, iconItemId, iconComponents, null);
    }
}
