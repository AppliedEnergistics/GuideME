package guideme.navigation;

import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.neoforged.neoforge.common.util.Lazy;
import org.jetbrains.annotations.Nullable;

public record NavigationNode(
        @Nullable Identifier pageId,
        String title,
        @Nullable @Deprecated(forRemoval = true) ItemStackTemplate icon,
        @Nullable Lazy<ItemStack> iconFactory,
        List<NavigationNode> children,
        int position,
        boolean hasPage) {
    public NavigationNode(@Nullable Identifier pageId,
            String title,
            @Nullable ItemStackTemplate icon,
            List<NavigationNode> children,
            int position,
            boolean hasPage) {
        this(pageId, title, icon, icon != null ? Lazy.of(icon::create) : null, children, position, hasPage);
    }
}
