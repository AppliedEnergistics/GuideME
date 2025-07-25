package guideme.document.interaction;

import guideme.siteexport.ResourceExporter;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public class ItemTooltip implements GuideTooltip {
    private final ItemStack stack;
    private final ItemStack icon;

    public ItemTooltip(ItemStack stack) {
        this(stack, stack);
    }

    public ItemTooltip(ItemStack stack, ItemStack icon) {
        this.stack = stack;
        this.icon = icon;
    }

    public ItemStack getItem() {
        return stack;
    }

    @Override
    public ItemStack getIcon() {
        return icon;
    }

    @Override
    public List<ClientTooltipComponent> getLines() {
        var lines = Screen.getTooltipFromItem(Minecraft.getInstance(), stack);
        return lines.stream()
                .map(Component::getVisualOrderText)
                .map(ClientTooltipComponent::create)
                .toList();
    }

    @Override
    public void exportResources(ResourceExporter exporter) {
        exporter.referenceItem(stack);
    }
}
