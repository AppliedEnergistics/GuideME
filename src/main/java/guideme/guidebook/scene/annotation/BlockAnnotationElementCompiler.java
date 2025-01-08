package guideme.guidebook.scene.annotation;

import guideme.guidebook.color.ConstantColor;
import guideme.guidebook.compiler.PageCompiler;
import guideme.guidebook.compiler.tags.MdxAttrs;
import guideme.guidebook.document.LytErrorSink;
import guideme.libs.mdast.mdx.model.MdxJsxElementFields;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

public class BlockAnnotationElementCompiler extends AnnotationTagCompiler {

    public static final String TAG_NAME = "BlockAnnotation";

    @Override
    public Set<String> getTagNames() {
        return Set.of(TAG_NAME);
    }

    @Override
    protected @Nullable SceneAnnotation createAnnotation(PageCompiler compiler, LytErrorSink errorSink,
            MdxJsxElementFields el) {
        var pos = MdxAttrs.getPos(compiler, errorSink, el);
        var color = MdxAttrs.getColor(compiler, errorSink, el, "color", ConstantColor.WHITE);

        return InWorldBoxAnnotation.forBlock(pos, color);
    }
}
