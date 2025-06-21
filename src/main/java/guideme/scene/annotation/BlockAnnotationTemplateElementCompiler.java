package guideme.scene.annotation;

import guideme.compiler.PageCompiler;
import guideme.compiler.tags.MdxAttrs;
import guideme.document.LytErrorSink;
import guideme.libs.mdast.mdx.model.MdxJsxAttribute;
import guideme.libs.mdast.mdx.model.MdxJsxElementFields;
import guideme.scene.GuidebookScene;
import guideme.scene.element.SceneElementTagCompiler;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.predicate.BlockStatePredicate;

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
        var pair = MdxAttrs.getRequiredBlockAndId(compiler, errorSink, el, "id");
        if (pair == null) {
            return;
        }

        var predicate = getBlockStatePredicate(compiler, errorSink, el, pair.getRight());

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

    /**
     * Reads all attributes of the element starting with {@code p:} and builds a predicate testing a block states
     * properties against these values.
     */
    private static Predicate<BlockState> getBlockStatePredicate(PageCompiler compiler, LytErrorSink errorSink,
            MdxJsxElementFields el, Block block) {
        var predicate = BlockStatePredicate.forBlock(block);

        for (var attrNode : el.attributes()) {
            if (!(attrNode instanceof MdxJsxAttribute attr)) {
                continue;
            }
            var attrName = attr.name;
            if (!attrName.startsWith("p:")) {
                continue;
            }
            var statePropertyName = attrName.substring("p:".length());
            var stateDefinition = block.getStateDefinition();
            var property = stateDefinition.getProperty(statePropertyName);
            if (property == null) {
                errorSink.appendError(compiler, "block doesn't have property " + statePropertyName, el);
                continue;
            }

            String stringValue = attr.getStringValue();
            var maybePropertyValue = property.getValue(stringValue);
            if (maybePropertyValue.isEmpty()) {
                errorSink.appendError(compiler, "Invalid value  for property " + property + ": " + stringValue, el);
                continue;
            }

            var propertyValue = maybePropertyValue.get();
            predicate.where(property, o -> Objects.equals(o, propertyValue));
        }

        return predicate;
    }
}
