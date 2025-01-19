package guideme.scene.element;

import guideme.compiler.PageCompiler;
import guideme.compiler.tags.MdxAttrs;
import guideme.document.LytErrorSink;
import guideme.libs.mdast.mdx.model.MdxJsxElementFields;
import guideme.scene.GuidebookScene;
import java.util.Set;

public class SceneBlockElementCompiler implements SceneElementTagCompiler {
    @Override
    public Set<String> getTagNames() {
        return Set.of("Block");
    }

    @Override
    public void compile(GuidebookScene scene,
            PageCompiler compiler,
            LytErrorSink errorSink,
            MdxJsxElementFields el) {
        var pair = MdxAttrs.getRequiredBlockAndId(compiler, errorSink, el, "id");
        if (pair == null) {
            return;
        }
        var state = pair.getRight().defaultBlockState();
        state = MdxAttrs.applyBlockStateProperties(compiler, errorSink, el, state);

        var pos = MdxAttrs.getPos(compiler, errorSink, el);
        scene.getLevel().setBlockAndUpdate(pos, state);
    }
}
