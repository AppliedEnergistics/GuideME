package guideme.internal.item;

import guideme.internal.GuideRegistry;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.CuboidItemModelWrapper;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

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
            @Nullable ItemOwner owner,
            int seed) {

        ItemModel itemModel = baseModel;

        var guideId = GuideItem.getGuideId(stack);
        if (guideId != null) {
            var guide = GuideRegistry.getById(guideId);
            if (guide != null && guide.getItemSettings().itemModel().isPresent()) {
                itemModel = new CuboidItemModelWrapper.Unbaked(guide.getItemSettings().itemModel().get(), Optional.empty(), List.of())
                        .bake(bakingContext, new Matrix4f());
            }
        }

        itemModel.update(renderState, stack, itemModelResolver, displayContext, level, owner, seed);
    }
}
