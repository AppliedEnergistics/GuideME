package guideme.compiler.tags;

import guideme.compiler.PageCompiler;
import guideme.document.block.LytBlockContainer;
import guideme.libs.mdast.mdx.model.MdxJsxElementFields;
import java.util.Set;
import net.neoforged.fml.ModList;

public class OptionalSectionCompiler extends BlockTagCompiler {
    public static final String TAG_NAME = "OptionalSection";
    public static final String MOD_ID_ATTR = "modId";

    @Override
    public Set<String> getTagNames() {
        return Set.of(TAG_NAME);
    }

    @Override
    protected void compile(PageCompiler compiler, LytBlockContainer parent, MdxJsxElementFields el) {
        var modId = MdxAttrs.getString(compiler, parent, el, MOD_ID_ATTR, null);
        if (modId == null) {
            parent.appendError(compiler, "Missing modId attribute.", el);
            return;
        }

        if (test(modId)) {
            compiler.compileBlockContext(el.children(), parent);
        }
    }

    public static boolean test(String modId) {
        boolean invert = false;
        if (modId.startsWith("!")) {
            modId = modId.substring(1);
            invert = true;
        }
        return ModList.get().isLoaded(modId) ^ invert;
    }
}
