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
import org.joml.Vector3f;

/**
 * Annotates a region of blocks given by its min and max block position.
 */
public class BoxAnnotationElementCompiler extends AnnotationTagCompiler {

    public static final String TAG_NAME = "BoxAnnotation";

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
        var min = MdxAttrs.getVector3(compiler, errorSink, el, "min", new Vector3f());
        var max = MdxAttrs.getVector3(compiler, errorSink, el, "max", new Vector3f());
        ensureMinMax(min, max);

        var color = MdxAttrs.getColor(compiler, errorSink, el, "color", ConstantColor.WHITE);
        var thickness = MdxAttrs.getFloat(compiler, errorSink, el, "thickness", InWorldBoxAnnotation.DEFAULT_THICKNESS);
        var alwaysOnTop = MdxAttrs.getBoolean(compiler, errorSink, el, "alwaysOnTop", false);

        min.add(instancePosition.getX(), instancePosition.getY(), instancePosition.getZ());
        max.add(instancePosition.getX(), instancePosition.getY(), instancePosition.getZ());

        var annotation = new InWorldBoxAnnotation(min, max, color, thickness);
        annotation.setAlwaysOnTop(alwaysOnTop);
        return annotation;
    }

    // Ensures component-wise that min has the minimum and max has the maximum values
    private void ensureMinMax(Vector3f min, Vector3f max) {
        for (var i = 0; i < 3; i++) {
            var minVal = min.get(i);
            var maxVal = max.get(i);
            if (minVal > maxVal) {
                min.setComponent(i, maxVal);
                max.setComponent(i, minVal);
            }
        }
    }
}
