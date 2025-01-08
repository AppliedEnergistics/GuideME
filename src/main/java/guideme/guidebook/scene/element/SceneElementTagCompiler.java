package guideme.guidebook.scene.element;

import guideme.guidebook.compiler.PageCompiler;
import guideme.guidebook.document.LytErrorSink;
import guideme.guidebook.extensions.Extension;
import guideme.guidebook.extensions.ExtensionPoint;
import guideme.guidebook.scene.GuidebookScene;
import guideme.libs.mdast.mdx.model.MdxJsxElementFields;
import java.util.Set;

/**
 * Contributed by {@link SceneElementCompilerPlugin}.
 */
public interface SceneElementTagCompiler extends Extension {
    ExtensionPoint<SceneElementTagCompiler> EXTENSION_POINT = new ExtensionPoint<>(SceneElementTagCompiler.class);

    Set<String> getTagNames();

    void compile(GuidebookScene scene, PageCompiler compiler, LytErrorSink errorSink, MdxJsxElementFields el);
}
