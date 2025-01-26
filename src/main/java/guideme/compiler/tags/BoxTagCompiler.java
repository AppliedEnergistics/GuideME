package guideme.compiler.tags;

import guideme.compiler.PageCompiler;
import guideme.document.block.AlignItems;
import guideme.document.block.LytAxisBox;
import guideme.document.block.LytBlockContainer;
import guideme.document.block.LytHBox;
import guideme.document.block.LytVBox;
import guideme.libs.mdast.mdx.model.MdxJsxElementFields;
import java.util.Set;

public class BoxTagCompiler extends BlockTagCompiler {
    private final BoxFlowDirection direction;

    public BoxTagCompiler(BoxFlowDirection direction) {
        this.direction = direction;
    }

    @Override
    public Set<String> getTagNames() {
        return direction == BoxFlowDirection.ROW ? Set.of("Row") : Set.of("Column");
    }

    @Override
    protected void compile(PageCompiler compiler, LytBlockContainer parent, MdxJsxElementFields el) {
        var gap = MdxAttrs.getInt(compiler, parent, el, "gap", 5);
        var alignItems = MdxAttrs.getEnum(compiler, parent, el, "alignItems", AlignItems.START);
        var fullWidth = MdxAttrs.getBoolean(compiler, parent, el, "fullWidth", false);

        LytAxisBox box = switch (this.direction) {
            case ROW -> {
                var hbox = new LytHBox();
                hbox.setGap(gap);
                yield hbox;
            }
            case COLUMN -> {
                var vbox = new LytVBox();
                vbox.setGap(gap);
                yield vbox;
            }
        };

        box.setAlignItems(alignItems);
        box.setFullWidth(fullWidth);

        compiler.compileBlockContext(el.children(), box);

        parent.append(box);
    }
}
