package guideme.internal;

import guideme.Guide;
import guideme.Guides;
import guideme.PageAnchor;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class GuideMEClientProxy extends GuideMEServerProxy {
    private static final Logger LOG = LoggerFactory.getLogger(GuideMEClientProxy.class);

    @Override
    public void addGuideTooltip(ResourceLocation guideId, Item.TooltipContext context, List<Component> lines,
            TooltipFlag tooltipFlag) {
        var guide = GuideRegistry.getById(guideId);
        if (guide == null) {
            lines.add(GuidebookText.ItemInvalidGuideId.text().withStyle(ChatFormatting.RED));
            return;
        }

        lines.addAll(guide.getItemSettings().tooltipLines());
    }

    @Override
    public @Nullable Component getGuideDisplayName(ResourceLocation guideId) {
        var guide = GuideRegistry.getById(guideId);
        if (guide != null) {
            return guide.getItemSettings().displayName().orElse(null);
        }

        return null;
    }

    @Override
    public boolean openGuide(Player player, ResourceLocation id) {
        if (player == Minecraft.getInstance().player) {
            var guide = Guides.getById(id);
            if (guide == null) {
                player.sendSystemMessage(GuidebookText.ItemInvalidGuideId.text(id.toString()));
                return false;
            } else {
                return GuideMEClient.openGuideAtPreviousPage(guide, guide.getStartPage());
            }
        }

        return super.openGuide(player, id);
    }

    @Override
    public boolean openGuide(Player player, ResourceLocation id, PageAnchor anchor) {
        if (player == Minecraft.getInstance().player) {
            var guide = Guides.getById(id);
            if (guide == null) {
                player.sendSystemMessage(GuidebookText.ItemInvalidGuideId.text(id.toString()));
                return false;
            } else {
                return GuideMEClient.openGuideAtAnchor(guide, anchor);
            }
        }

        return super.openGuide(player, id, anchor);
    }

    @Override
    public List<GuideMetadata> getAvailableGuides() {
        // On the client, this only works for yourself
        return Guides.getAll().stream().map(
                guide -> new GuideMetadata(guide.getId(), getRepresentativeItem(guide))).toList();
    }

    private ItemStack getRepresentativeItem(Guide guide) {
        return Guides.createGuideItem(guide.getId());
    }
}
