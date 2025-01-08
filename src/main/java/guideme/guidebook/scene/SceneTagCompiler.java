
package guideme.guidebook.scene;

import guideme.guidebook.color.SymbolicColor;
import guideme.guidebook.compiler.PageCompiler;
import guideme.guidebook.compiler.tags.BlockTagCompiler;
import guideme.guidebook.compiler.tags.MdxAttrs;
import guideme.guidebook.document.block.LytBlockContainer;
import guideme.guidebook.extensions.Extension;
import guideme.guidebook.extensions.ExtensionCollection;
import guideme.guidebook.scene.element.SceneElementTagCompiler;
import guideme.guidebook.scene.level.GuidebookLevel;
import guideme.libs.mdast.mdx.model.MdxJsxElementFields;
import guideme.libs.mdast.model.MdAstNode;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class SceneTagCompiler extends BlockTagCompiler implements Extension {
    public static final String TAG_NAME = "GameScene";

    private final Map<String, SceneElementTagCompiler> elementTagCompilers = new HashMap<>();

    @Override
    public Set<String> getTagNames() {
        return Set.of(TAG_NAME);
    }

    @Override
    protected void compile(PageCompiler compiler, LytBlockContainer parent, MdxJsxElementFields el) {
        var padding = MdxAttrs.getInt(compiler, parent, el, "padding", 5);
        var zoom = MdxAttrs.getFloat(compiler, parent, el, "zoom", 1.0f);
        var background = MdxAttrs.getColor(compiler, parent, el, "background", SymbolicColor.SCENE_BACKGROUND);

        var level = new GuidebookLevel();
        var cameraSettings = new CameraSettings();
        cameraSettings.setZoom(zoom);

        var scene = new GuidebookScene(level, cameraSettings);

        for (var child : el.children()) {
            if (child instanceof MdxJsxElementFields childEl) {
                var childTagName = childEl.name();
                var childCompiler = elementTagCompilers.get(childTagName);
                if (childCompiler == null) {
                    parent.appendError(compiler, "Unknown scene element", child);
                } else {
                    childCompiler.compile(scene, compiler, parent, childEl);
                }
            }
        }

        scene.getCameraSettings().setRotationCenter(scene.getWorldCenter());
        scene.centerScene();

        var lytScene = new LytGuidebookScene(compiler.getExtensions());
        lytScene.setScene(scene);
        lytScene.setBackground(background);
        lytScene.setPadding(padding);
        if (MdxAttrs.getBoolean(compiler, parent, el, "interactive", false)) {
            lytScene.setInteractive(true);
        }
        if (MdxAttrs.getBoolean(compiler, parent, el, "fullWidth", false)) {
            lytScene.setFullWidth(true);
        }
        lytScene.setSourceNode((MdAstNode) el);

        parent.append(lytScene);
    }

    @Override
    public void onExtensionsBuilt(ExtensionCollection extensions) {
        for (var sceneElementTag : extensions.get(SceneElementTagCompiler.EXTENSION_POINT)) {
            for (var tagName : sceneElementTag.getTagNames()) {
                // This explicitly allows for overrides
                elementTagCompilers.put(tagName, sceneElementTag);
            }
        }
    }
}
