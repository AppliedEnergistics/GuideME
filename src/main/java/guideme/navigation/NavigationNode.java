package guideme.navigation;

import java.util.List;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public record NavigationNode(
        @Nullable ResourceLocation pageId,
        String title,
        @Deprecated(forRemoval = true) ItemStack icon,
        Supplier<ItemStack> iconFactory,
        List<NavigationNode> children,
        int position,
        boolean hasPage) {
    public NavigationNode(@Nullable ResourceLocation pageId,
            String title,
            ItemStack icon,
            List<NavigationNode> children,
            int position,
            boolean hasPage) {
        this(pageId, title, icon, () -> icon, children, position, hasPage);
    }
}
