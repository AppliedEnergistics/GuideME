package guideme.internal.item;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.block.model.TextureSlots;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.context.ContextMap;
import net.neoforged.neoforge.client.model.ExtendedUnbakedModel;

public class GuideItemDispatchUnbakedModel implements ExtendedUnbakedModel {

    @Override
    public BakedModel bake(TextureSlots textures, ModelBaker baker, ModelState modelState, boolean useAmbientOcclusion, boolean usesBlockLight, ItemTransforms itemTransforms, ContextMap additionalProperties) {
        var baseModel = baker.bake(GuideItem.BASE_MODEL_ID, modelState);

        class Loader extends CacheLoader<ResourceLocation, BakedModel> {
            @Override
            public BakedModel load(ResourceLocation modelId) {
                var model = baker.getModel(modelId);
                model.resolveDependencies(baker::getModel);
                return model.bake(textures, baker, modelState, useAmbientOcclusion, usesBlockLight, itemTransforms, additionalProperties);
            }
        }

        var modelCache = CacheBuilder.newBuilder().build(new Loader());

        return new GuideItemDispatchModel(baseModel, modelCache);
    }

    @Override
    public void resolveDependencies(Resolver resolver) {
    }
}
