package guideme.internal.item;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import guideme.internal.GuideME;
import guideme.internal.GuideRegistry;
import java.util.function.Function;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IUnbakedGeometry;
import org.jetbrains.annotations.Nullable;

public class GuideItemDispatchUnbakedModel implements IUnbakedGeometry<GuideItemDispatchUnbakedModel> {
    @Override
    public BakedModel bake(IGeometryBakingContext geometryBakingContext,
            ModelBaker modelBaker,
            Function<Material, TextureAtlasSprite> sprites,
            ModelState modelState,
            ItemOverrides itemOverrides) {
        var baseModel = modelBaker.bake(GuideItem.BASE_MODEL_ID, modelState, sprites);

        class Loader extends CacheLoader<ResourceLocation, BakedModel> {
            @Override
            public BakedModel load(ResourceLocation modelId) {
                var model = modelBaker.getModel(modelId);
                model.resolveParents(modelBaker::getModel);
                return model.bake(modelBaker, sprites, modelState);
            }
        }

        var modelCache = CacheBuilder.newBuilder().build(new Loader());

        var overrides = new ItemOverrides() {
            @Override
            public @Nullable BakedModel resolve(BakedModel model, ItemStack stack, @Nullable ClientLevel level,
                    @Nullable LivingEntity entity, int seed) {
                var guideId = stack.get(GuideME.GUIDE_ID_COMPONENT);
                if (guideId != null) {
                    var guide = GuideRegistry.getById(guideId);
                    if (guide != null && guide.getItemSettings().itemModel().isPresent()) {
                        return modelCache.getUnchecked(guide.getItemSettings().itemModel().get());
                    }
                }

                return baseModel;
            }
        };

        return new GuideItemDispatchModel(baseModel, overrides);
    }
}
