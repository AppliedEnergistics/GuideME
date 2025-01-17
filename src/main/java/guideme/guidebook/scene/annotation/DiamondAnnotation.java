package guideme.guidebook.scene.annotation;

import guideme.GuideME;
import guideme.api.color.ColorValue;
import guideme.api.color.ConstantColor;
import guideme.guidebook.document.LytRect;
import guideme.guidebook.render.RenderContext;
import guideme.guidebook.scene.GuidebookScene;
import net.minecraft.client.Minecraft;
import org.joml.Vector3f;

public class DiamondAnnotation extends OverlayAnnotation {
    private final Vector3f pos;
    private final ColorValue outerColor;
    private final ColorValue color;

    public DiamondAnnotation(Vector3f pos, ColorValue color) {
        this.pos = pos;
        this.color = color;
        this.outerColor = new ConstantColor(0xFFCCCCCC);
    }

    public Vector3f getPos() {
        return pos;
    }

    public ColorValue getColor() {
        return color;
    }

    @Override
    public LytRect getBoundingRect(GuidebookScene scene, LytRect viewport) {
        var screenPos = scene.worldToScreen(pos.x, pos.y, pos.z);
        var docPoint = scene.screenToDocument(screenPos, viewport);
        var x = Math.round(docPoint.x());
        var y = Math.round(docPoint.y());
        return new LytRect(x - 8, y - 8, 16, 16);
    }

    @Override
    public void render(GuidebookScene scene, RenderContext context, LytRect viewport) {
        var rect = getBoundingRect(scene, viewport);

        var outer = outerColor;
        var inner = color;
        if (isHovered()) {
            outer = context.mutableColor(outer).lighter(20);
            inner = context.mutableColor(inner).lighter(20);
        }

        var texture = Minecraft.getInstance().getTextureManager()
                .getTexture(GuideME.makeId("textures/guide/diamond.png"));
        context.fillTexturedRect(rect, texture, outer, outer, outer, outer, 0, 0, 0.5f, 1);
        context.fillTexturedRect(rect, texture, inner, inner, inner, inner, 0.5f, 0, 1f, 1);
    }
}
