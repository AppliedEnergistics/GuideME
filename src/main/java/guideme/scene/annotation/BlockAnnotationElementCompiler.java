package guideme.scene.annotation;

import guideme.color.ConstantColor;
import guideme.compiler.PageCompiler;
import guideme.compiler.tags.MdxAttrs;
import guideme.document.LytErrorSink;
import guideme.libs.mdast.mdx.model.MdxJsxElementFields;
import guideme.scene.GuidebookScene;
import java.util.Set;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * Annotates a single block in the scene identified by its position.
 */
public class BlockAnnotationElementCompiler extends AnnotationTagCompiler {

    public static final String TAG_NAME = "BlockAnnotation";

    @Override
    public Set<String> getTagNames() {
        return Set.of(TAG_NAME);
    }

    @Override
    protected @Nullable SceneAnnotation createAnnotation(PageCompiler compiler, LytErrorSink errorSink,
            MdxJsxElementFields el) {
        return createAnnotation(null, compiler, errorSink, el, BlockPos.ZERO);
    }

    @Override
    protected @Nullable SceneAnnotation createAnnotation(GuidebookScene scene, PageCompiler compiler,
            LytErrorSink errorSink, MdxJsxElementFields el, BlockPos instancePosition) {
        var pos = MdxAttrs.getPos(compiler, errorSink, el);
        var color = MdxAttrs.getColor(compiler, errorSink, el, "color", ConstantColor.WHITE);

        pos = pos.offset(instancePosition);

        return InWorldBoxAnnotation.forBlock(pos, color);
    }
}
