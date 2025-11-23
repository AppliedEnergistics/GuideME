package guideme.internal;

import guideme.PageAnchor;
import java.util.function.Consumer;
import java.util.stream.Stream;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jetbrains.annotations.Nullable;

public interface GuideMEProxy {
    static GuideMEProxy instance() {
        return GuideME.PROXY;
    }

    default void addGuideTooltip(Identifier guideId, Item.TooltipContext context, TooltipDisplay tooltipDisplay,
            Consumer<Component> lineConsumer,
            TooltipFlag tooltipFlag) {
    }

    @Nullable
    default Component getGuideDisplayName(Identifier guideId) {
        return null;
    }

    boolean openGuide(Player player, Identifier guideId);

    boolean openGuide(Player player, Identifier guideId, PageAnchor anchor);

    Stream<Identifier> getAvailableGuides();

    Stream<Identifier> getAvailablePages(Identifier guideId);
}
