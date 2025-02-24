package guideme.internal.item;

import com.google.common.cache.LoadingCache;
import guideme.internal.GuideRegistry;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.DelegateBakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class GuideItemDispatchModel extends DelegateBakedModel {
    private final LoadingCache<ResourceLocation, BakedModel> modelCache;

    public GuideItemDispatchModel(BakedModel originalModel, LoadingCache<ResourceLocation, BakedModel> modelCache) {
        super(originalModel);
        this.modelCache = modelCache;
    }

    @Override
    public List<BakedModel> getRenderPasses(ItemStack stack) {
        var guideId = GuideItem.getGuideId(stack);
        if (guideId != null) {
            var guide = GuideRegistry.getById(guideId);
            if (guide != null && guide.getItemSettings().itemModel().isPresent()) {
                return List.of(modelCache.getUnchecked(guide.getItemSettings().itemModel().get()));
            }
        }

        return super.getRenderPasses(stack);
    }
}
