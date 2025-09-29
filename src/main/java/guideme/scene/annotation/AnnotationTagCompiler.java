package guideme.scene.annotation;

import guideme.compiler.PageCompiler;
import guideme.document.LytErrorSink;
import guideme.document.block.LytBlock;
import guideme.document.block.LytVBox;
import guideme.libs.mdast.mdx.model.MdxJsxElementFields;
import guideme.scene.GuidebookScene;
import guideme.scene.element.SceneElementTagCompiler;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

public abstract class AnnotationTagCompiler implements SceneElementTagCompiler {

    @Override
    public final void compile(GuidebookScene scene, PageCompiler compiler, LytErrorSink errorSink,
            MdxJsxElementFields el) {
        compileTemplate(scene, compiler, errorSink, el, BlockPos.ZERO);
    }

    public final void compileTemplate(GuidebookScene scene, PageCompiler compiler, LytErrorSink errorSink,
            MdxJsxElementFields el, BlockPos instancePosition) {
        var annotation = createAnnotation(scene, compiler, errorSink, el, instancePosition);
        if (annotation == null) {
            return; // Likely parsing error
        }

        var contentBox = new LytVBox();
        compiler.compileBlockContext(el.children(), contentBox);
        if (!contentBox.getChildren().isEmpty()) {
            // Clear any top and bottom margin around the entire content
            var firstChild = contentBox.getChildren().get(0);
            if (firstChild instanceof LytBlock block) {
                block.setMarginTop(0);
            }
            var lastChild = contentBox.getChildren().get(contentBox.getChildren().size() - 1);
            if (lastChild instanceof LytBlock block) {
                block.setMarginBottom(0);
            }

            annotation.setTooltipContent(contentBox);
        }

        scene.addAnnotation(annotation);
    }

    /**
     * @deprecated Use
     *             {@link #createAnnotation(GuidebookScene, PageCompiler, LytErrorSink, MdxJsxElementFields, BlockPos)}
     *             instead.
     */
    @Nullable
    @Deprecated
    protected abstract SceneAnnotation createAnnotation(PageCompiler compiler,
            LytErrorSink errorSink,
            MdxJsxElementFields el);

    /**
     * @param instancePosition Used when the annotation is compiled as part of a template, just add this to the position
     *                         of the annotation. When an annotation is not in a template, {@link BlockPos#ZERO} is
     *                         passed.
     */
    @Nullable
    protected SceneAnnotation createAnnotation(GuidebookScene scene,
            PageCompiler compiler,
            LytErrorSink errorSink,
            MdxJsxElementFields el,
            BlockPos instancePosition) {
        return null;
    }
}
