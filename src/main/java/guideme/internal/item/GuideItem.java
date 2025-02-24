package guideme.internal.item;

import guideme.internal.GuideME;
import guideme.internal.GuideMEProxy;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public class GuideItem extends Item {
    public static final ResourceLocation ID = GuideME.makeId("guide");
    public static final ResourceLocation BASE_MODEL_ID = ID.withPrefix("item/").withSuffix("_base");

    public static final Properties PROPERTIES = new Properties();

    public GuideItem(Properties properties) {
        super(properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        var guideId = getGuideId(stack);
        if (guideId != null) {
            var name = GuideMEProxy.instance().getGuideDisplayName(guideId);
            if (name != null) {
                return name;
            }
        }
        return super.getName(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines,
            TooltipFlag tooltipFlag) {
        var guideId = getGuideId(stack);
        if (guideId != null) {
            GuideMEProxy.instance().addGuideTooltip(
                    guideId,
                    context,
                    lines,
                    tooltipFlag);
        }
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);

        var guideId = getGuideId(player.getItemInHand(hand));

        if (level.isClientSide) {
            if (GuideMEProxy.instance().openGuide(player, guideId)) {
                return InteractionResult.CONSUME;
            }
        }

        return InteractionResult.SUCCESS;
    }

    @Nullable
    public static ResourceLocation getGuideId(ItemStack stack) {
        return stack.get(GuideME.GUIDE_ID_COMPONENT);
    }
}
