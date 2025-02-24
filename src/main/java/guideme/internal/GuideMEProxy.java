package guideme.internal;

import guideme.PageAnchor;
import java.util.List;
import java.util.stream.Stream;
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

    boolean openGuide(Player player, ResourceLocation guideId);

    boolean openGuide(Player player, ResourceLocation guideId, PageAnchor anchor);

    Stream<ResourceLocation> getAvailableGuides();

    Stream<ResourceLocation> getAvailablePages(ResourceLocation guideId);
}
