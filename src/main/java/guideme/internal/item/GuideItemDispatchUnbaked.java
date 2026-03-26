package guideme.internal.item;

import com.mojang.serialization.MapCodec;
import guideme.internal.GuideME;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.renderer.item.CuboidItemModelWrapper;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4fc;

public class GuideItemDispatchUnbaked implements ItemModel.Unbaked {
    public static final Identifier ID = GuideME.makeId("guide");

    public static final MapCodec<GuideItemDispatchUnbaked> CODEC = MapCodec.unit(new GuideItemDispatchUnbaked());

    @Override
    public MapCodec<? extends ItemModel.Unbaked> type() {
        return CODEC;
    }

    @Override
    public ItemModel bake(ItemModel.BakingContext bakingContext, Matrix4fc transformation) {
        var baseModel = new CuboidItemModelWrapper.Unbaked(GuideItem.BASE_MODEL_ID, Optional.empty(), List.of()).bake(bakingContext, transformation);

        return new GuideItemDispatchModel(baseModel, bakingContext);
    }

    @Override
    public void resolveDependencies(Resolver resolver) {
        resolver.markDependency(GuideItem.BASE_MODEL_ID);
    }
}
