package guideme.scene.element;

import guideme.compiler.PageCompiler;
import guideme.document.LytErrorSink;
import guideme.extensions.Extension;
import guideme.extensions.ExtensionPoint;
import guideme.internal.extensions.DefaultExtensions;
import guideme.libs.mdast.mdx.model.MdxJsxElementFields;
import guideme.scene.GuidebookScene;
import java.util.Set;

/**
 * Contributed by {@link DefaultExtensions}.
 */
public interface SceneElementTagCompiler extends Extension {
    ExtensionPoint<SceneElementTagCompiler> EXTENSION_POINT = new ExtensionPoint<>(SceneElementTagCompiler.class);

    Set<String> getTagNames();

    void compile(GuidebookScene scene, PageCompiler compiler, LytErrorSink errorSink, MdxJsxElementFields el);
}
