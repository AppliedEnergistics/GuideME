package guideme.document.block;

import guideme.document.LytRect;
import guideme.layout.LayoutContext;
import guideme.layout.Layouts;

/**
 * Lays out its children vertically.
 */
public class LytVBox extends LytAxisBox {
    @Override
    protected LytRect computeBoxLayout(LayoutContext context, int x, int y, int availableWidth) {
        // Padding is applied through the parent
        return Layouts.verticalLayout(context,
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
