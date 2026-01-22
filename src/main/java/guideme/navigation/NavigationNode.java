package guideme.navigation;

import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStackTemplate;
import org.jetbrains.annotations.Nullable;

public record NavigationNode(
        @Nullable Identifier pageId,
        String title,
        @org.jspecify.annotations.Nullable ItemStackTemplate icon,
        List<NavigationNode> children,
        int position,
        boolean hasPage) {
}
