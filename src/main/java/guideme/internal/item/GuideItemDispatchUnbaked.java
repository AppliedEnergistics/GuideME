package guideme.internal.item;

import com.mojang.serialization.MapCodec;
import guideme.internal.GuideME;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.resources.ResourceLocation;

public class GuideItemDispatchUnbaked implements ItemModel.Unbaked {
    public static final ResourceLocation ID = GuideME.makeId("guide");

    public static final MapCodec<GuideItemDispatchUnbaked> CODEC = MapCodec.unit(new GuideItemDispatchUnbaked());

    @Override
    public MapCodec<? extends ItemModel.Unbaked> type() {
        return CODEC;
    }

    @Override
    public ItemModel bake(ItemModel.BakingContext bakingContext) {
        var baseModel = bakingContext.bake(GuideItem.BASE_MODEL_ID);

        return new GuideItemDispatchModel(baseModel, bakingContext);
    }

    @Override
    public void resolveDependencies(Resolver resolver) {
        resolver.resolve(GuideItem.BASE_MODEL_ID);
    }
}
