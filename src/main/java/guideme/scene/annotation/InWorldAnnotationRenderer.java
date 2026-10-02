package guideme.scene.annotation;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.AddressMode;
import com.mojang.renderpearl.api.textures.FilterMode;
import guideme.color.ColorValue;
import guideme.color.LightDarkMode;
import guideme.color.MutableColor;
import guideme.internal.GuideME;
import guideme.internal.scene.SceneRenderTarget;
import java.util.Collection;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.data.AtlasIds;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import org.jetbrains.annotations.ApiStatus;
import org.joml.Vector3f;

@ApiStatus.Internal
public final class InWorldAnnotationRenderer {

    /**
     * Renders the parts of annotations that are hidden behind other geometry. Note that Minecraft uses a reversed depth
     * buffer, so hidden fragments are those with a smaller depth value than what has been rendered before.
     */
    public static final RenderPipeline OCCLUDED_PIPELINE = RenderPipelines.TRANSLUCENT_BLOCK.toBuilder()
            .withLocation(GuideME.makeId("pipeline/annotation_occluded"))
            .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN, false))
            .build();

    // Mirrors RenderTypes.translucentMovingBlock(), which we use for the non-occluded parts
    private static final RenderType OCCLUDED = RenderType.create(
            "guideme_annotation_occluded",
            RenderSetup.builder(OCCLUDED_PIPELINE)
                    .useLightmap()
                    .withTexture("Sampler0", TextureAtlas.LOCATION_BLOCKS,
                            () -> RenderSystem.getSamplerCache().getSampler(AddressMode.CLAMP_TO_EDGE,
                                    AddressMode.CLAMP_TO_EDGE, FilterMode.LINEAR, FilterMode.NEAREST, true))
                    .sortOnUpload()
                    .createRenderSetup());

    private InWorldAnnotationRenderer() {
    }

    /**
     * Renders the annotations on top of the already rendered scene. Every pass is rendered immediately, since they
     * depend on the depth buffer contents left behind by the previous passes.
     */
    public static void render(FeatureRenderDispatcher dispatcher, Collection<InWorldAnnotation> annotations,
            LightDarkMode lightDarkMode) {
        if (annotations.isEmpty()) {
            return;
        }

        var sprite = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS)
                .getSprite(GuideME.makeId("block/noise"));

        // Render the parts of annotations hidden by other geometry in a darker color
        renderPass(dispatcher, OCCLUDED, consumer -> {
            for (var annotation : annotations) {
                if (annotation.isAlwaysOnTop()) {
                    continue; // Don't render occlusion for always-on-top annotations
                }
                renderAnnotation(consumer, annotation, lightDarkMode, true, sprite);
            }
        });

        // Render two passes to support annotations that are always on top of other annotations
        for (var pass = 1; pass <= 2; pass++) {
            var alwaysOnTop = pass == 2;
            if (annotations.stream().noneMatch(annotation -> annotation.isAlwaysOnTop() == alwaysOnTop)) {
                continue;
            }

            if (alwaysOnTop) {
                var depthTexture = SceneRenderTarget.depthView().texture();
                // The depth buffer is reversed, so 0 is the farthest value
                RenderSystem.getDevice().createCommandEncoder().clearDepthTexture(depthTexture, 0.0);
            }

            renderPass(dispatcher, RenderTypes.translucentMovingBlock(), consumer -> {
                for (var annotation : annotations) {
                    if (annotation.isAlwaysOnTop() == alwaysOnTop) {
                        renderAnnotation(consumer, annotation, lightDarkMode, false, sprite);
                    }
                }
            });
        }
    }

    private static void renderPass(FeatureRenderDispatcher dispatcher, RenderType renderType,
            Consumer<VertexConsumer> geometry) {
        var nodes = new SubmitNodeStorage();
        // Annotations are positioned in absolute level coordinates, so no pose is applied
        nodes.submitCustomGeometry(new PoseStack(), renderType, (pose, consumer) -> geometry.accept(consumer));
        SceneRenderTarget.renderAllFeatures(dispatcher, nodes, () -> "GuideME annotations");
    }

    private static void renderAnnotation(VertexConsumer consumer, InWorldAnnotation annotation,
            LightDarkMode lightDarkMode, boolean occluded, TextureAtlasSprite sprite) {
        if (annotation instanceof InWorldBoxAnnotation boxAnnotation) {
            var color = getColor(boxAnnotation.color(), boxAnnotation.isHovered(), lightDarkMode, occluded);
            render(consumer, boxAnnotation.min(), boxAnnotation.max(), color, boxAnnotation.thickness(), sprite);
        } else if (annotation instanceof InWorldLineAnnotation lineAnnotation) {
            var color = getColor(lineAnnotation.color(), lineAnnotation.isHovered(), lightDarkMode, occluded);
            strut(consumer, lineAnnotation.min(), lineAnnotation.max(), color, lineAnnotation.thickness(), true,
                    true, sprite);
        }
    }

    private static int getColor(ColorValue colorValue, boolean hovered, LightDarkMode lightDarkMode,
            boolean occluded) {
        var color = MutableColor.of(colorValue, lightDarkMode);
        if (occluded) {
            color.darker(50).setAlpha(color.alpha() * 0.5f);
        }
        if (hovered) {
            color.lighter(50);
        }
        return color.toArgb32();
    }

    public static void render(VertexConsumer consumer,
            Vector3f min,
            Vector3f max,
            int color,
            float thickness,
            TextureAtlasSprite sprite) {
        var thickHalf = thickness * 0.5f;

        var u = new Vector3f(max.x - min.x, 0, 0);
        var v = new Vector3f(0, max.y - min.y, 0);
        var t = new Vector3f(0, 0, max.z - min.z);
        var uNorm = new Vector3f(u).normalize();
        var vNorm = new Vector3f(v).normalize();
        var tNorm = new Vector3f(t).normalize();

        Vector3f[] corners = new Vector3f[8];
        corners[0] = new Vector3f(min);
        corners[1] = new Vector3f(min).add(u);
        corners[2] = new Vector3f(min).add(v);
        corners[3] = new Vector3f(min).add(t);
        corners[4] = new Vector3f(max);
        corners[5] = new Vector3f(max).sub(u);
        corners[6] = new Vector3f(max).sub(v);
        corners[7] = new Vector3f(max).sub(t);

        // Along X-Axis
        // Extend these out to cover past the corner (half the extrude thickness)
        strut(consumer, new Vector3f(uNorm).mulAdd(-thickHalf, corners[0]),
                new Vector3f(uNorm).mulAdd(thickHalf, corners[1]), color, thickness, true, true, sprite);
        strut(consumer, new Vector3f(uNorm).mulAdd(-thickHalf, corners[2]),
                new Vector3f(uNorm).mulAdd(thickHalf, corners[7]), color, thickness, true, true, sprite);
        strut(consumer, new Vector3f(uNorm).mulAdd(-thickHalf, corners[3]),
                new Vector3f(uNorm).mulAdd(thickHalf, corners[6]), color, thickness, true, true, sprite);
        strut(consumer, new Vector3f(uNorm).mulAdd(-thickHalf, corners[5]),
                new Vector3f(uNorm).mulAdd(thickHalf, corners[4]), color, thickness, true, true, sprite);

        // Along Y-Axis
        strut(consumer, new Vector3f(vNorm).mulAdd(thickHalf, corners[0]),
                new Vector3f(vNorm).mulAdd(-thickHalf, corners[2]), color, thickness, false, false, sprite);
        strut(consumer, new Vector3f(vNorm).mulAdd(thickHalf, corners[1]),
                new Vector3f(vNorm).mulAdd(-thickHalf, corners[7]), color, thickness, false, false, sprite);
        strut(consumer, new Vector3f(vNorm).mulAdd(thickHalf, corners[3]),
                new Vector3f(vNorm).mulAdd(-thickHalf, corners[5]), color, thickness, false, false, sprite);
        strut(consumer, new Vector3f(vNorm).mulAdd(thickHalf, corners[6]),
                new Vector3f(vNorm).mulAdd(-thickHalf, corners[4]), color, thickness, false, false, sprite);

        // Along Z-Axis
        strut(consumer, new Vector3f(tNorm).mulAdd(thickHalf, corners[0]),
                new Vector3f(tNorm).mulAdd(-thickHalf, corners[3]), color, thickness, false, false, sprite);
        strut(consumer, new Vector3f(tNorm).mulAdd(thickHalf, corners[1]),
                new Vector3f(tNorm).mulAdd(-thickHalf, corners[6]), color, thickness, false, false, sprite);
        strut(consumer, new Vector3f(tNorm).mulAdd(thickHalf, corners[2]),
                new Vector3f(tNorm).mulAdd(-thickHalf, corners[5]), color, thickness, false, false, sprite);
        strut(consumer, new Vector3f(tNorm).mulAdd(thickHalf, corners[7]),
                new Vector3f(tNorm).mulAdd(-thickHalf, corners[4]), color, thickness, false, false, sprite);
    }

    private static void strut(VertexConsumer consumer, Vector3f from, Vector3f to, int color, float thickness,
            boolean startCap, boolean endCap, TextureAtlasSprite sprite) {
        var norm = new Vector3f(to).sub(from).normalize();
        Vector3f prefUp;
        if (Math.abs(from.x - to.x) < 0.01f && Math.abs(from.z - to.z) < 0.01f) {
            prefUp = new Vector3f(1, 0, 0);
        } else {
            prefUp = new Vector3f(0, 1, 0);
        }

        var rightNorm = new Vector3f(norm).cross(prefUp).normalize();
        var leftNorm = new Vector3f(rightNorm).negate();
        var upNorm = new Vector3f(rightNorm).cross(norm).normalize();
        var downNorm = new Vector3f(upNorm).negate();

        var up = new Vector3f(upNorm).mul(thickness * 0.5f);
        var right = new Vector3f(rightNorm).mul(thickness * 0.5f);

        if (startCap) {
            quad(
                    consumer, downNorm, color,
                    new Vector3f(from).add(up).sub(right),
                    new Vector3f(from).sub(up).sub(right),
                    new Vector3f(from).sub(up).add(right),
                    new Vector3f(from).add(up).add(right),
                    sprite);
        }

        if (endCap) {
            quad(
                    consumer, norm, color,
                    new Vector3f(to).add(up).add(right),
                    new Vector3f(to).sub(up).add(right),
                    new Vector3f(to).sub(up).sub(right),
                    new Vector3f(to).add(up).sub(right),
                    sprite);
        }

        quad(
                consumer, leftNorm, color,
                new Vector3f(from).sub(right).add(up),
                new Vector3f(to).sub(right).add(up),
                new Vector3f(to).sub(right).sub(up),
                new Vector3f(from).sub(right).sub(up),
                sprite);
        quad(
                consumer, rightNorm, color,
                new Vector3f(to).add(right).sub(up),
                new Vector3f(to).add(right).add(up),
                new Vector3f(from).add(right).add(up),
                new Vector3f(from).add(right).sub(up),
                sprite);
        quad(
                consumer, upNorm, color,
                new Vector3f(from).add(up).sub(right),
                new Vector3f(from).add(up).add(right),
                new Vector3f(to).add(up).add(right),
                new Vector3f(to).add(up).sub(right),
                sprite);
        quad(
                consumer, downNorm, color,
                new Vector3f(to).sub(up).sub(right),
                new Vector3f(to).sub(up).add(right),
                new Vector3f(from).sub(up).add(right),
                new Vector3f(from).sub(up).sub(right),
                sprite);
    }

    private static void quad(VertexConsumer consumer, Vector3f faceNormal, int color,
            Vector3f v1, Vector3f v2, Vector3f v3, Vector3f v4,
            TextureAtlasSprite sprite) {
        var d = Direction.getApproximateNearest(faceNormal.x, faceNormal.y, faceNormal.z);
        var shade = switch (d) {
            case DOWN -> 0.5F;
            case NORTH, SOUTH -> 0.8F;
            case WEST, EAST -> 0.6F;
            default -> 1.0F;
        };
        color = ARGB.multiply(
                ARGB.color(255, (int) (shade * 255), (int) (shade * 255), (int) (shade * 255)),
                color);

        vertex(consumer, faceNormal, color, v1, sprite.getU0(), sprite.getV1());
        vertex(consumer, faceNormal, color, v2, sprite.getU0(), sprite.getV0());
        vertex(consumer, faceNormal, color, v3, sprite.getU1(), sprite.getV0());
        vertex(consumer, faceNormal, color, v4, sprite.getU1(), sprite.getV1());
    }

    private static void vertex(VertexConsumer consumer,
            Vector3f faceNormal,
            int color,
            Vector3f bottomLeft,
            float u, float v) {
        consumer.addVertex(bottomLeft.x, bottomLeft.y, bottomLeft.z)
                .setColor(color)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightCoordsUtil.FULL_BRIGHT)
                .setNormal(faceNormal.x(), faceNormal.y(), faceNormal.z());
    }
}
