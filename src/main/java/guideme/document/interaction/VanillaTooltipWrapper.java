package guideme.document.interaction;

import guideme.document.block.LytBlock;
import guideme.siteexport.ResourceExporter;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTextTooltip;

public class VanillaTooltipWrapper implements GuideTooltip {
    private final List<LytBlock> content;

    public VanillaTooltipWrapper(Tooltip tooltip) {
        this.content = tooltip.toCharSequence(Minecraft.getInstance())
                .stream()
                .map(ClientTextTooltip::new)
                .<LytBlock>map(LytClientTooltipComponentAdapter::new)
                .toList();
    }

    @Override
    public List<LytBlock> geLayoutContent() {
        return content;
    }

    @Override
    public void exportResources(ResourceExporter exporter) {
    }
}
