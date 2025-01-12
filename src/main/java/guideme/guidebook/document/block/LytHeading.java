package guideme.guidebook.document.block;

import guideme.api.color.SymbolicColor;
import guideme.guidebook.document.DefaultStyles;
import guideme.guidebook.render.RenderContext;

public class LytHeading extends LytParagraph {
    private int depth = 1;

    public LytHeading() {
        setMarginTop(5);
        setMarginBottom(5);
    }

    public int getDepth() {
        return depth;
    }

    public void setDepth(int depth) {
        this.depth = depth;
        var style = switch (depth) {
            case 1 -> DefaultStyles.HEADING1;
            case 2 -> DefaultStyles.HEADING2;
            case 3 -> DefaultStyles.HEADING3;
            case 4 -> DefaultStyles.HEADING4;
            case 5 -> DefaultStyles.HEADING5;
            case 6 -> DefaultStyles.HEADING6;
            default -> DefaultStyles.BODY_TEXT;
        };
        setStyle(style);
    }

    @Override
    public void render(RenderContext context) {
        super.render(context);

        if (depth == 1) {
            var bounds = getBounds();
            context.fillRect(
                    bounds.x(), bounds.bottom() - 1, bounds.width(), 1, SymbolicColor.HEADER1_SEPARATOR);
        } else if (depth == 2) {
            var bounds = getBounds();
            context.fillRect(
                    bounds.x(), bounds.bottom() - 1, bounds.width(), 1, SymbolicColor.HEADER2_SEPARATOR);
        }
    }
}
