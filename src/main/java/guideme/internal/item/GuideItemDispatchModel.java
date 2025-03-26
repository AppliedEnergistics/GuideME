package guideme.internal.item;

import guideme.internal.GuideRegistry;
import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.BlockModelWrapper;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public class GuideItemDispatchModel implements ItemModel {
    private final ItemModel baseModel;
    private final BakingContext bakingContext;

    public GuideItemDispatchModel(ItemModel baseModel,
            BakingContext bakingContext) {
        this.baseModel = baseModel;
        this.bakingContext = bakingContext;
    }

    @Override
    public void update(ItemStackRenderState renderState,
            ItemStack stack,
            ItemModelResolver itemModelResolver,
            ItemDisplayContext displayContext,
            @Nullable ClientLevel level,
            @Nullable LivingEntity entity,
            int seed) {

        ItemModel itemModel = baseModel;

        var guideId = GuideItem.getGuideId(stack);
        if (guideId != null) {
            var guide = GuideRegistry.getById(guideId);
            if (guide != null && guide.getItemSettings().itemModel().isPresent()) {
                itemModel = new BlockModelWrapper.Unbaked(guide.getItemSettings().itemModel().get(), List.of())
                        .bake(bakingContext);
            }
        }

        itemModel.update(renderState, stack, itemModelResolver, displayContext, level, entity, seed);
    }
}
