package guideme.internal.data;

import guideme.internal.GuideME;
import guideme.internal.item.GuideItem;
import guideme.internal.item.GuideItemDispatchModelLoader;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.client.model.generators.CustomLoaderBuilder;
import net.neoforged.neoforge.client.model.generators.ItemModelBuilder;
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

public class GuideMEModelProvider extends ItemModelProvider {
    public GuideMEModelProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, GuideME.MOD_ID, existingFileHelper);
    }

    @Override
    protected void registerModels() {
        // Generate the base item model
        getBuilder(GuideItem.ID.withSuffix("_base").toString())
                .parent(new ModelFile.UncheckedModelFile("item/generated"))
                .texture("layer0", GuideItem.ID.withPrefix("item/"));

        // Generate the dispatch model
        getBuilder(GuideItem.ID.toString()).customLoader(this::createDispatchModel);
    }

    private CustomLoaderBuilder<ItemModelBuilder> createDispatchModel(ItemModelBuilder itemModelBuilder,
            ExistingFileHelper existingFileHelper) {
        return new CustomLoaderBuilder<>(
                GuideItemDispatchModelLoader.ID, itemModelBuilder, existingFileHelper) {
        };
    }
}
