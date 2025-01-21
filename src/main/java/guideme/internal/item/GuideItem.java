package guideme.internal.item;

import guideme.internal.GuideME;
import guideme.internal.GuideMEProxy;
import guideme.internal.GuidebookText;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

public class GuideItem extends Item {
    public static final ResourceLocation ID = GuideME.makeId("guide");
    public static final ResourceLocation BASE_MODEL_ID = ID.withPrefix("item/").withSuffix("_base");

    public static final Properties PROPERTIES = new Properties();

    public GuideItem(Properties properties) {
        super(properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        var guideId = stack.get(GuideME.GUIDE_ID_COMPONENT);
        var name = GuideMEProxy.instance().getGuideDisplayName(guideId);
        if (name != null) {
            return name;
        }
        return super.getName(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines,
            TooltipFlag tooltipFlag) {
        var guideId = stack.get(GuideME.GUIDE_ID_COMPONENT);
        GuideMEProxy.instance().addGuideTooltip(
                guideId,
                context,
                lines,
                tooltipFlag);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);

        var guideId = player.getItemInHand(hand).get(GuideME.GUIDE_ID_COMPONENT);

        if (level.isClientSide) {
            if (guideId == null) {
                player.sendSystemMessage(GuidebookText.ItemNoGuideId.text());
            } else if (GuideMEProxy.instance().openGuide(player, guideId)) {
                return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
            }
        }

        return InteractionResultHolder.success(stack);
    }
}
