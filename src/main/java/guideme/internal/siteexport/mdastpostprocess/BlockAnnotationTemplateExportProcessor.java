package guideme.internal.siteexport.mdastpostprocess;

import guideme.color.ColorValue;
import guideme.color.LightDarkMode;
import guideme.libs.mdast.mdx.model.MdxJsxElementFields;
import guideme.libs.mdast.mdx.model.MdxJsxFlowElement;
import guideme.libs.mdast.model.MdAstNode;
import guideme.scene.LytGuidebookScene;
import guideme.scene.annotation.DiamondAnnotation;
import guideme.scene.annotation.InWorldBoxAnnotation;
import guideme.scene.annotation.InWorldLineAnnotation;
import guideme.scene.annotation.SceneAnnotation;
import guideme.siteexport.NodeSelector;
import guideme.siteexport.PageExportContext;
import guideme.siteexport.PageExportProcessor;
import java.util.ArrayList;
import java.util.Locale;
import java.util.stream.Stream;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * Templates are applied to the blocks of a scene that match a predicate, which requires the scene. Replace them with
 * the annotations the template created in the scene, so that the website doesn't need to know about templates.
 */
public final class BlockAnnotationTemplateExportProcessor implements PageExportProcessor {
    private static final NodeSelector SELECTOR = NodeSelector.element("BlockAnnotationTemplate");

    @Override
    public NodeSelector getSelector() {
        return SELECTOR;
    }

    @Override
    public void process(PageExportContext context, MdAstNode node) {
        var gameScene = context.getParent(node);
        if (gameScene == null) {
            return;
        }
        var scene = context.getLayoutNodes(gameScene).stream()
                .filter(LytGuidebookScene.class::isInstance)
                .map(lytNode -> ((LytGuidebookScene) lytNode).getScene())
                .findFirst()
                .orElse(null);
        if (scene == null) {
            return; // Leave it to the website to show an error
        }

        var annotations = Stream.concat(scene.getInWorldAnnotations().stream(),
                scene.getOverlayAnnotations().stream()).toList();

        // The children of the template are the annotations to create for each matching block
        var replacement = new ArrayList<MdAstNode>();
        for (var templateChild : ((MdxJsxElementFields) node).children()) {
            if (!(templateChild instanceof MdxJsxElementFields templateElement)) {
                continue;
            }
            for (var annotation : annotations) {
                if (annotation.getSourceNode() == templateChild) {
                    var element = toElement(annotation);
                    if (element != null) {
                        // Every instance needs its own copy, since later export steps modify the content
                        for (var contentNode : templateElement.children()) {
                            element.addChild(((MdAstNode) contentNode).deepCopy());
                        }
                        replacement.add(element);
                    }
                }
            }
        }

        context.replace(node, replacement);
    }

    @Nullable
    private static MdxJsxFlowElement toElement(SceneAnnotation annotation) {
        var element = new MdxJsxFlowElement();
        switch (annotation) {
            case InWorldBoxAnnotation box -> {
                element.setName("BoxAnnotation");
                element.addAttribute("min", toString(box.min()));
                element.addAttribute("max", toString(box.max()));
                element.addAttribute("color", toString(box.color()));
                element.addAttribute("thickness", box.thickness());
                element.addAttribute("alwaysOnTop", box.isAlwaysOnTop());
            }
            case InWorldLineAnnotation line -> {
                element.setName("LineAnnotation");
                element.addAttribute("from", toString(line.min()));
                element.addAttribute("to", toString(line.max()));
                element.addAttribute("color", toString(line.color()));
                element.addAttribute("thickness", line.thickness());
                element.addAttribute("alwaysOnTop", line.isAlwaysOnTop());
            }
            case DiamondAnnotation diamond -> {
                element.setName("DiamondAnnotation");
                element.addAttribute("pos", toString(diamond.getPos()));
                element.addAttribute("color", toString(diamond.getColor()));
            }
            default -> {
                return null;
            }
        }
        return element;
    }

    private static String toString(Vector3f vector) {
        return vector.x + " " + vector.y + " " + vector.z;
    }

    private static String toString(ColorValue color) {
        // The website uses a dark theme
        return String.format(Locale.ROOT, "#%08X", color.resolve(LightDarkMode.DARK_MODE));
    }
}
