package guideme.scene.element;

import guideme.compiler.PageCompiler;
import guideme.compiler.tags.MdxAttrs;
import guideme.document.LytErrorSink;
import guideme.libs.mdast.mdx.model.MdxJsxElementFields;
import guideme.scene.GuidebookScene;
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
