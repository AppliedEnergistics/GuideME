package guideme.internal;

import guideme.PageAnchor;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;

public interface GuideMEProxy {
    static GuideMEProxy instance() {
        return GuideME.PROXY;
    }

    default void addGuideTooltip(ResourceLocation guideId, Item.TooltipContext context, List<Component> lines,
            TooltipFlag tooltipFlag) {
    }

    @Nullable
    default Component getGuideDisplayName(ResourceLocation guideId) {
        return null;
    }

    default boolean openGuide(Player player, ResourceLocation id) {
        return true;
    }

    default void openGuideAtPreviousPage(ResourceLocation guide) {
    }

    default void openGuideAtAnchor(ResourceLocation guide, PageAnchor anchor) {
    }
}
