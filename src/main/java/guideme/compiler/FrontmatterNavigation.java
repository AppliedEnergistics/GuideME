package guideme.compiler;

import java.util.Map;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Inserts a page into the navigation tree. Null parent means top-level category.
 */
public record FrontmatterNavigation(
        String title,
        @Nullable Identifier parent,
        int position,
        @Nullable Identifier iconItemId,
        @Nullable Map<?, ?> iconComponents) {
}
