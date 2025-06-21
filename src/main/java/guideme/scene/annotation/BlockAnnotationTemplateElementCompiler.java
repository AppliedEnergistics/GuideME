package guideme.scene.annotation;

import guideme.compiler.PageCompiler;
import guideme.compiler.tags.MdxAttrs;
import guideme.document.LytErrorSink;
import guideme.libs.mdast.mdx.model.MdxJsxElementFields;
import guideme.scene.GuidebookScene;
import guideme.scene.element.SceneElementTagCompiler;
import java.util.Objects;
import java.util.Set;

/**
 * This tag allows annotations to be applied to any blockstate currently in the scene.
 * <p>
 * It supports any annotation type that compiles down to a {@link InWorldBoxAnnotation}.
 */
public class BlockAnnotationTemplateElementCompiler implements SceneElementTagCompiler {
    @Override
    public Set<String> getTagNames() {
        return Set.of("BlockAnnotationTemplate");
    }

    @Override
    public void compile(GuidebookScene scene, PageCompiler compiler, LytErrorSink errorSink, MdxJsxElementFields el) {
        var predicate = MdxAttrs.getRequiredBlockStatePredicate(compiler, errorSink, el, "id");
        if (predicate == null) {
            return;
        }

        // Find the template to apply.
        for (var child : el.children()) {
            if (child instanceof MdxJsxElementFields childEl) {
                var childTagName = childEl.name();
                var childCompiler = findCompiler(compiler, childTagName);
                if (childCompiler == null) {
                    errorSink.appendError(compiler, "Element is not supported as an annotation template", child);
                    continue;
                }

                // Instantiate the template for every position matching the predicate
                var it = scene.getFilledBlocks().iterator();
                while (it.hasNext()) {
                    var position = it.next();
                    var state = scene.getLevel().getBlockState(position);
                    if (predicate.test(state)) {
                        childCompiler.compileTemplate(scene, compiler, errorSink, childEl, position);
                    }
                }
            }
        }
    }

    private AnnotationTagCompiler findCompiler(PageCompiler compiler, String childTagName) {
        for (var sceneElementCompiler : compiler.getExtensions(SceneElementTagCompiler.EXTENSION_POINT)) {
            if (!(sceneElementCompiler instanceof AnnotationTagCompiler annotationTagCompiler)) {
                continue;
            }

            for (var tagName : annotationTagCompiler.getTagNames()) {
                if (Objects.equals(childTagName, tagName)) {
                    return annotationTagCompiler;
                }
            }
        }
        return null;
    }
}
