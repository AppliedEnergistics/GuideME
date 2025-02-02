package guideme.document.interaction;

import guideme.document.block.LytBlock;
import guideme.siteexport.ResourceExporter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTextTooltip;
import net.minecraft.network.chat.Component;

public class TextTooltip implements GuideTooltip {
    private final List<LytBlock> content;

    public TextTooltip(String text) {
        this(Component.literal(text));
    }

    public TextTooltip(List<Component> lines) {
        this.content = lines.stream()
                .map(line -> new ClientTextTooltip(line.getVisualOrderText()))
                .<LytBlock>map(LytClientTooltipComponentAdapter::new)
                .toList();
    }

    public TextTooltip(Component firstLine, Component... additionalLines) {
        this(makeLineList(firstLine, additionalLines));
    }

    private static List<Component> makeLineList(Component firstLine, Component[] additionalLines) {
        var lines = new ArrayList<Component>(1 + additionalLines.length);
        lines.add(firstLine);
        Collections.addAll(lines, additionalLines);
        return lines;
    }

    @Override
    public List<LytBlock> geLayoutContent() {
        return content;
    }

    @Override
    public void exportResources(ResourceExporter exporter) {
    }
}
