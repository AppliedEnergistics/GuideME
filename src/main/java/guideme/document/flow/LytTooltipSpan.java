package guideme.document.flow;

import guideme.document.interaction.GuideTooltip;
import guideme.document.interaction.InteractiveElement;
import guideme.siteexport.ExportableResourceProvider;
import guideme.siteexport.ResourceExporter;
import java.util.Optional;
import org.jetbrains.annotations.Nullable;

/**
 * An inline span that allows a tooltip to be shown on hover.
 */
public class LytTooltipSpan extends LytFlowSpan implements InteractiveElement, ExportableResourceProvider {
    @Nullable
    private GuideTooltip tooltip;

    @Override
    public Optional<GuideTooltip> getTooltip(float x, float y) {
        return Optional.ofNullable(tooltip);
    }

    public void setTooltip(@Nullable GuideTooltip tooltip) {
        this.tooltip = tooltip;
    }

    @Override
    public void exportResources(ResourceExporter exporter) {
        if (tooltip != null) {
            tooltip.exportResources(exporter);
        }
    }
}
