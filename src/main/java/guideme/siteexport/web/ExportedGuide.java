package guideme.siteexport.web;

import guideme.internal.siteexport.model.FluidInfoJson;
import guideme.internal.siteexport.model.ItemInfoJson;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

public interface ExportedGuide {
    String getDefaultNamespace();

    @Nullable
    ItemInfoJson tryGetItemInfo(String itemId);

    ExportedItemInfo getItemInfo(String itemId);

    @Nullable
    FluidInfoJson tryGetFluidInfo(String fluidId);

    ExportedFluidInfo getFluidInfo(String fluidId);

    /**
     * {@return mod specific data that was exported alongside the guide}
     *
     * @see guideme.siteexport.ResourceExporter#addExtraData(Identifier, Object)
     */
    @Nullable
    Object getExtraData(String identifier);
}
