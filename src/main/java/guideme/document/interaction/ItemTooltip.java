package guideme.document.interaction;

import guideme.document.block.LytBlock;
import guideme.siteexport.ResourceExporter;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public class ItemTooltip implements GuideTooltip {
    private final ItemStack stack;
    private final List<LytBlock> content;

    public ItemTooltip(ItemStack stack) {
        this.stack = stack;
        var lines = Screen.getTooltipFromItem(Minecraft.getInstance(), stack);
        this.content = lines.stream()
                .map(Component::getVisualOrderText)
                .map(ClientTooltipComponent::create)
                .<LytBlock>map(LytClientTooltipComponentAdapter::new)
                .toList();
    }

    @Override
    public ItemStack getIcon() {
        return stack;
    }

    @Override
    public List<LytBlock> geLayoutContent() {
        return content;
    }

    @Override
    public void exportResources(ResourceExporter exporter) {
        exporter.referenceItem(stack);
    }
}
