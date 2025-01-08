package guideme.guidebook.scene.element;

import guideme.guidebook.compiler.PageCompiler;
import guideme.guidebook.compiler.tags.MdxAttrs;
import guideme.guidebook.document.LytErrorSink;
import guideme.guidebook.scene.GuidebookScene;
import guideme.libs.mdast.mdx.model.MdxJsxElementFields;
import java.util.Set;

public class IsometricCameraElementCompiler implements SceneElementTagCompiler {
    @Override
    public Set<String> getTagNames() {
        return Set.of("IsometricCamera");
    }

    @Override
    public void compile(GuidebookScene scene, PageCompiler compiler, LytErrorSink errorSink, MdxJsxElementFields el) {
        float yaw = MdxAttrs.getFloat(compiler, errorSink, el, "yaw", 0.0f);
        float pitch = MdxAttrs.getFloat(compiler, errorSink, el, "pitch", 0.0f);
        float roll = MdxAttrs.getFloat(compiler, errorSink, el, "roll", 0.0f);

        var cameraSettings = scene.getCameraSettings();
        cameraSettings.setIsometricYawPitchRoll(yaw, pitch, roll);
    }
}
