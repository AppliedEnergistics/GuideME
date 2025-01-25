package guideme.document.block;

import guideme.document.LytRect;
import guideme.render.RenderContext;
import guideme.style.BorderStyle;

final class BorderRenderer {
    public void render(RenderContext context,
            LytRect bounds,
            BorderStyle borderTop,
            BorderStyle borderLeft,
            BorderStyle borderRight,
            BorderStyle borderBottom) {

        if (borderTop.width() > 0) {
            context.fillRect(bounds.x(), bounds.y(), bounds.width(), borderTop.width(), borderTop.color());
        }

        int innerHeight = bounds.height() - borderTop.width() - borderBottom.width();
        if (borderLeft.width() > 0) {
            context.fillRect(bounds.x(), bounds.y() + borderTop.width(), borderLeft.width(), innerHeight,
                    borderLeft.color());
        }
        if (borderRight.width() > 0) {
            context.fillRect(bounds.right() - borderRight.width(), bounds.y() + borderTop.width(), borderRight.width(),
                    innerHeight, borderRight.color());
        }
        if (borderBottom.width() > 0) {
            context.fillRect(bounds.x(), bounds.bottom() - borderBottom.width(), bounds.width(), borderBottom.width(),
                    borderBottom.color());
        }

    }
}
