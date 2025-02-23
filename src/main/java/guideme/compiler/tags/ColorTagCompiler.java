package guideme.compiler.tags;

import guideme.color.ColorValue;
import guideme.color.SymbolicColor;
import guideme.compiler.PageCompiler;
import guideme.document.flow.LytFlowParent;
import guideme.document.flow.LytFlowSpan;
import guideme.libs.mdast.mdx.model.MdxJsxElementFields;
import guideme.style.TextStyle;
import java.util.Locale;
import java.util.Set;

public class ColorTagCompiler extends FlowTagCompiler {
    @Override
    public Set<String> getTagNames() {
        return Set.of("Color");
    }

    @Override
    protected void compile(PageCompiler compiler, LytFlowParent parent, MdxJsxElementFields el) {
        var id = MdxAttrs.getString(compiler, parent, el, "id", null);
        if (id == null) {
            parent.appendError(compiler, "Missing 'id' attribute", el);
            return;
        }

        ColorValue color;
        try {
            color = SymbolicColor.valueOf(id.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            parent.appendError(compiler, "Unknown color: '" + id + "'", el);
            return;
        }

        var span = new LytFlowSpan();
        span.setStyle(TextStyle.builder().color(color).build());
        parent.append(span);
        compiler.compileFlowContext(el.children(), span);
    }
}
