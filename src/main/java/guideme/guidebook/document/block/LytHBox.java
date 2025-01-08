package guideme.guidebook.document.block;

import guideme.guidebook.document.LytRect;
import guideme.guidebook.layout.LayoutContext;
import guideme.guidebook.layout.Layouts;

/**
 * Lays out its children vertically.
 */
public class LytHBox extends LytAxisBox {
    @Override
    protected LytRect computeBoxLayout(LayoutContext context, int x, int y, int availableWidth) {
        // Padding is applied through the parent
        return Layouts.horizontalLayout(context,
                children,
                x,
                y,
                availableWidth,
                0,
                0,
                0,
                0,
                getGap(),
                getAlignItems());
    }
}
