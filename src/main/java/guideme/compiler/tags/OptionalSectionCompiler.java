package guideme.compiler.tags;

import guideme.compiler.PageCompiler;
import guideme.document.block.LytBlockContainer;
import guideme.libs.mdast.mdx.model.MdxJsxElementFields;
import java.util.Set;
import net.neoforged.fml.ModList;

public class OptionalSectionCompiler extends BlockTagCompiler {
    @Override
    public Set<String> getTagNames() {
        return Set.of("OptionalSection");
    }

    @Override
    protected void compile(PageCompiler compiler, LytBlockContainer parent, MdxJsxElementFields el) {
        var modId = MdxAttrs.getString(compiler, parent, el, "modId", null);
        if (modId == null) {
            parent.appendError(compiler, "Missing modId attribute.", el);
            return;
        }

        boolean invert = false;
        if (modId.startsWith("!")) {
            modId = modId.substring(1);
            invert = true;
        }
        if (ModList.get().isLoaded(modId) ^ invert) {
            compiler.compileBlockContext(el.children(), parent);
        }
    }
}
