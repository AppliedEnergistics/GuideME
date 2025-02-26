package guideme.internal.item;

import guideme.internal.GuideRegistry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public class GuideItemDispatchModel implements ItemModel {
    private final BakedModel baseModel;
    private final BakingContext bakingContext;

    public GuideItemDispatchModel(BakedModel baseModel,
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
        BakedModel model = baseModel;

        var guideId = GuideItem.getGuideId(stack);
        if (guideId != null) {
            var guide = GuideRegistry.getById(guideId);
            if (guide != null && guide.getItemSettings().itemModel().isPresent()) {
                model = bakingContext.bake(guide.getItemSettings().itemModel().get());
            }
        }

        renderState.newLayer().setupBlockModel(model, model.getRenderType(stack));
    }
}
