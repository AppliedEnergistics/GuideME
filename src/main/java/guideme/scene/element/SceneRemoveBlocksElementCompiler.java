package guideme.scene.element;

import guideme.compiler.PageCompiler;
import guideme.compiler.tags.MdxAttrs;
import guideme.document.LytErrorSink;
import guideme.libs.mdast.mdx.model.MdxJsxElementFields;
import guideme.scene.GuidebookScene;
import java.util.Set;
import net.minecraft.world.level.block.Blocks;

/**
 * Removes block states from the scene.
 */
public class SceneRemoveBlocksElementCompiler implements SceneElementTagCompiler {
    @Override
    public Set<String> getTagNames() {
        return Set.of("RemoveBlocks");
    }

    @Override
    public void compile(GuidebookScene scene,
            PageCompiler compiler,
            LytErrorSink errorSink,
            MdxJsxElementFields el) {
        var predicate = MdxAttrs.getRequiredBlockStatePredicate(compiler, errorSink, el, "id");
        if (predicate == null) {
            return;
        }

        var level = scene.getLevel();
        var it = level.getFilledBlocks().iterator();
        while (it.hasNext()) {
            var pos = it.next();
            if (predicate.test(level.getBlockState(pos))) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 0);
            }
        }
    }
}
