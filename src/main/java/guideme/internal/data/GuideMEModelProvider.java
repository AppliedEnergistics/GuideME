package guideme.internal.data;

import guideme.internal.GuideME;
import guideme.internal.item.GuideItem;
import guideme.internal.item.GuideItemDispatchUnbaked;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.data.PackOutput;

public class GuideMEModelProvider extends ModelProvider {
    public GuideMEModelProvider(PackOutput output) {
        super(output, GuideME.MOD_ID);
    }

    @Override
    protected void registerModels(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        // Generate the base item model
        ModelTemplates.FLAT_ITEM.create(
                GuideItem.BASE_MODEL_ID,
                TextureMapping.layer0(GuideItem.ID.withPrefix("item/")),
                itemModels.modelOutput);

        // Generate the dispatch model
        itemModels.itemModelOutput.accept(GuideME.GUIDE_ITEM.get(), new GuideItemDispatchUnbaked());
    }
}
