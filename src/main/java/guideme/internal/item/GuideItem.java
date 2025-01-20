package guideme.internal.item;

import guideme.internal.GuideME;
import guideme.internal.GuideMECommon;
import guideme.internal.GuideMEProxy;
import guideme.internal.GuideRegistry;
import guideme.internal.GuidebookText;
import guideme.internal.MutableGuide;
import java.util.List;
import net.minecraft.ChatFormatting;
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
        var guide = getGuide(stack);
        if (guide != null && guide.getItemSettings().displayName().isPresent()) {
            return guide.getItemSettings().displayName().get();
        }
        return super.getName(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines,
            TooltipFlag tooltipFlag) {
        var guide = getGuide(stack);
        if (guide == null) {
            lines.add(GuidebookText.ItemNoGuideId.text().withStyle(ChatFormatting.RED));
            return;
        }

        lines.addAll(guide.getItemSettings().tooltipLines());
    }

    private static MutableGuide getGuide(ItemStack stack) {
        var guideId = stack.get(GuideMECommon.GUIDE_ID_COMPONENT);
        if (guideId == null) {
            return null;
        }
        return GuideRegistry.getById(guideId);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);

        var guideId = player.getItemInHand(hand).get(GuideMECommon.GUIDE_ID_COMPONENT);

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
