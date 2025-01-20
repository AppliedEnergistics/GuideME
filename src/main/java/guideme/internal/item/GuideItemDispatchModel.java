package guideme.internal.item;

import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.resources.model.BakedModel;
import net.neoforged.neoforge.client.model.BakedModelWrapper;

public class GuideItemDispatchModel extends BakedModelWrapper<BakedModel> {
    private final ItemOverrides itemOverrides;

    public GuideItemDispatchModel(BakedModel originalModel, ItemOverrides itemOverrides) {
        super(originalModel);
        this.itemOverrides = itemOverrides;
    }

    @Override
    public ItemOverrides getOverrides() {
        return this.itemOverrides;
    }
}
