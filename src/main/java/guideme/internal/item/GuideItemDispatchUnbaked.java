package guideme.internal.item;

import com.mojang.serialization.MapCodec;
import guideme.internal.GuideME;
import java.util.List;
import net.minecraft.client.renderer.item.BlockModelWrapper;
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
        var baseModel = new BlockModelWrapper.Unbaked(GuideItem.BASE_MODEL_ID, List.of()).bake(bakingContext);

        return new GuideItemDispatchModel(baseModel, bakingContext);
    }

    @Override
    public void resolveDependencies(Resolver resolver) {
        resolver.markDependency(GuideItem.BASE_MODEL_ID);
    }
}
