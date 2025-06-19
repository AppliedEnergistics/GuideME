package guideme.compiler.tags;

import guideme.color.ColorValue;
import guideme.color.SymbolicColorResolver;
import guideme.compiler.PageCompiler;
import guideme.document.flow.LytFlowParent;
import guideme.document.flow.LytFlowSpan;
import guideme.libs.mdast.mdx.model.MdxJsxElementFields;
import guideme.style.TextStyle;
import java.util.Set;

public class ColorTagCompiler extends FlowTagCompiler {
    @Override
    public Set<String> getTagNames() {
        return Set.of("Color");
    }

    @Override
    protected void compile(PageCompiler compiler, LytFlowParent parent, MdxJsxElementFields el) {
        var id = MdxAttrs.getString(compiler, parent, el, "id", null);
        ColorValue color;
        if (id != null) {
            color = SymbolicColorResolver.resolve(compiler, id);
            if (color == null) {
                parent.appendError(compiler, "Cannot resolve symbolic color", el);
                return;
            }
        } else if (el.hasAttribute("color")) {
            color = MdxAttrs.getColor(compiler, parent, el, "color", null);
            if (color == null) {
                parent.appendError(compiler, "Malformed color value", el);
                return;
            }
        } else {
            parent.appendError(compiler, "Must either specify 'id' or 'color' attribute", el);
            return;
        }

        var span = new LytFlowSpan();
        span.setStyle(TextStyle.builder().color(color).build());
        parent.append(span);
        compiler.compileFlowContext(el.children(), span);
    }
}
