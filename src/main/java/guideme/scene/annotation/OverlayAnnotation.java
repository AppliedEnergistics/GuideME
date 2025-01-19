package guideme.scene.annotation;

import guideme.document.LytRect;
import guideme.render.RenderContext;
import guideme.scene.GuidebookScene;

/**
 * A {@link SceneAnnotation} that renders as part of the user-interface, overlaid on top of the in-world scene.
 */
public abstract class OverlayAnnotation extends SceneAnnotation {
    /**
     * Returns the bounding rectangle in document layout coordinates, given the scene and viewport.
     */
    public abstract LytRect getBoundingRect(GuidebookScene scene, LytRect viewport);

    public abstract void render(GuidebookScene scene, RenderContext context, LytRect viewport);
}
