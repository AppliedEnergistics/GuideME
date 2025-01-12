package guideme.guidebook.scene.annotation;

import guideme.api.color.ConstantColor;
import guideme.guidebook.compiler.PageCompiler;
import guideme.guidebook.compiler.tags.MdxAttrs;
import guideme.guidebook.document.LytErrorSink;
import guideme.libs.mdast.mdx.model.MdxJsxElementFields;
import java.util.Set;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

public class DiamondAnnotationElementCompiler extends AnnotationTagCompiler {
    @Override
    public Set<String> getTagNames() {
        return Set.of("DiamondAnnotation");
    }

    @Override
    protected @Nullable SceneAnnotation createAnnotation(PageCompiler compiler, LytErrorSink errorSink,
            MdxJsxElementFields el) {
        var pos = MdxAttrs.getVector3(compiler, errorSink, el, "pos", new Vector3f());
        var color = MdxAttrs.getColor(compiler, errorSink, el, "color", ConstantColor.WHITE);

        return new DiamondAnnotation(pos, color);
    }
}
