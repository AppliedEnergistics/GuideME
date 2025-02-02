package guideme.document.interaction;

import guideme.document.block.LytBlock;
import guideme.siteexport.ExportableResourceProvider;
import guideme.siteexport.ResourceExporter;
import java.util.List;

/**
 * A {@link GuideTooltip} that renders a {@link LytBlock} as the tooltip content.
 */
public class ContentTooltip implements GuideTooltip {
    private final List<LytBlock> content;

    public ContentTooltip(LytBlock content) {
        this.content = List.of(content);
    }

    public LytBlock getContent() {
        return content.getFirst();
    }

    @Override
    public List<LytBlock> geLayoutContent() {
        return content;
    }

    @Override
    public void exportResources(ResourceExporter exporter) {
        ExportableResourceProvider.visit(content.getFirst(), exporter);
    }
}
