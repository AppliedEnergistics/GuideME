package guideme.layout.flow;

import guideme.render.RenderContext;
import guideme.style.ResolvedTextStyle;

public class LineTextRun extends LineElement {
    final String text;
    final ResolvedTextStyle style;
    final ResolvedTextStyle hoverStyle;

    public LineTextRun(String text, ResolvedTextStyle style, ResolvedTextStyle hoverStyle) {
        this.text = text;
        this.style = style;
        this.hoverStyle = hoverStyle;
    }

    @Override
    public void render(RenderContext context) {
        var style = containsMouse ? this.hoverStyle : this.style;

        context.renderText(text, style, (float) bounds.x(), (float) bounds.y());
    }

    @Override
    public String toString() {
        return "TextRun[" + text + "]";
    }
}
