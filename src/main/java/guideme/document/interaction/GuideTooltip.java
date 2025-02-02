package guideme.document.interaction;

import guideme.document.block.LytBlock;
import guideme.siteexport.ExportableResourceProvider;
import java.util.List;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.item.ItemStack;

public interface GuideTooltip extends ExportableResourceProvider {

    default ItemStack getIcon() {
        return ItemStack.EMPTY;
    }

    @Deprecated(forRemoval = true)
    default List<ClientTooltipComponent> getLines() {
        return List.of();
    }

    default List<LytBlock> geLayoutContent() {
        return List.of();
    }

}
