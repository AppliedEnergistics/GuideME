package guideme.scene.annotation;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import guideme.color.LightDarkMode;
import guideme.color.MutableColor;
import guideme.internal.GuideME;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
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
import org.joml.Vector3fc;

@ApiStatus.Internal
public final class InWorldAnnotationRenderer {

    // Submit orders are executed in ascending numerical order by FeatureRenderDispatcher. Within a single
    // order, render types are batched in HashMap iteration order, so this is the only ordering guarantee
    // that 26.3 still gives us. Order 0 is used by the scene geometry itself.
    private static final int ORDER_OCCLUDED = 1;
    private static final int ORDER_NORMAL = 2;
    private static final int ORDER_ALWAYS_ON_TOP = 3;

    // RenderPipeline.Builder#withVertexFormat(format, mode) was split into
    // withVertexBinding(binding, format) + withPrimitiveTopology(topology). We no longer override the
    // vertex format at all: DefaultVertexFormat.BLOCK lost its Normal element in 26.3 (terrain does not
    // need it any more), while the geometry emitted below writes position/color/uv/overlay/light/normal.
    // That is exactly DefaultVertexFormat.ENTITY, which is what RenderPipelines.ITEM_TRANSLUCENT already
    // uses, so inheriting it is both correct and safer than forcing a mismatching format.
    // 26.3 uses a REVERSED-Z depth buffer (DepthStencilState.DEFAULT is GREATER_THAN_OR_EQUAL and
    // depth is cleared to 0.0), so the "only draw the parts hidden behind geometry" test flips from
    // GREATER_THAN to LESS_THAN.
    public static final RenderPipeline OCCLUDED_PIPELINE = RenderPipelines.ITEM_TRANSLUCENT.toBuilder()
            .withLocation(GuideME.makeId("pipeline/annotation_occluded"))
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN, false))
            .build();

    private static final RenderType OCCLUDED = RenderType.create(
            "guideme_annotation_occluded",
            RenderSetup.builder(OCCLUDED_PIPELINE)
                    .useLightmap()
                    .withTexture("Sampler0", TextureAtlas.LOCATION_BLOCKS)
                    .useLightmap()
                    .useOverlay()
                    .createRenderSetup());

    private InWorldAnnotationRenderer() {
    }

    /**
     * Submits the annotation geometry for the scene.
     * <p>
     * This used to be immediate-mode rendering into a {@code MultiBufferSource.BufferSource} with
     * an explicit depth-buffer clear in between the normal and the always-on-top pass. In 26.3 geometry is
     * submitted as nodes first and actually drawn later, inside a single render pass that we do not own, so
     * clearing the depth buffer in between is no longer possible. Always-on-top annotations are instead
     * biased towards the camera by {@code alwaysOnTopOffset}. Under the scene's orthographic projection a
     * translation along the view direction is a pure depth shift that does not change the image, so this
     * keeps them in front of the rest of the scene while retaining correct depth sorting amongst
     * themselves (which a depth-test-disabled pipeline would not).
     *
     * @param alwaysOnTopOffset World space vector pointing towards the camera.
     */
    public static void render(SubmitNodeCollector collector,
            PoseStack poseStack,
            Iterable<InWorldAnnotation> annotations,
            LightDarkMode lightDarkMode,
            Vector3fc alwaysOnTopOffset) {
        var sprite = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS)
                .getSprite(GuideME.makeId("block/noise"));

        // Pass 1: the parts of the (non always-on-top) annotations that are hidden behind scene geometry.
        collector.order(ORDER_OCCLUDED).submitCustomGeometry(poseStack, OCCLUDED, (pose, buffer) -> {
            for (var annotation : annotations) {
                if (annotation.isAlwaysOnTop()) {
                    continue; // Don't render occlusion for always-on-top annotations
                }
                emit(buffer, pose, annotation, lightDarkMode, sprite, true);
            }
        });

        // Pass 2: the regular, depth-tested annotations.
        collector.order(ORDER_NORMAL).submitCustomGeometry(poseStack, RenderTypes.translucentMovingBlock(),
                (pose, buffer) -> {
                    for (var annotation : annotations) {
                        if (annotation.isAlwaysOnTop()) {
                            continue;
                        }
                        emit(buffer, pose, annotation, lightDarkMode, sprite, false);
                    }
                });

        // Pass 3: annotations that should always be on top of everything else.
        poseStack.pushPose();
        poseStack.translate(alwaysOnTopOffset.x(), alwaysOnTopOffset.y(), alwaysOnTopOffset.z());
        collector.order(ORDER_ALWAYS_ON_TOP).submitCustomGeometry(poseStack, RenderTypes.translucentMovingBlock(),
                (pose, buffer) -> {
                    for (var annotation : annotations) {
                        if (!annotation.isAlwaysOnTop()) {
                            continue;
                        }
                        emit(buffer, pose, annotation, lightDarkMode, sprite, false);
                    }
                });
        poseStack.popPose();
    }

    private static void emit(VertexConsumer buffer,
            PoseStack.Pose pose,
            InWorldAnnotation annotation,
            LightDarkMode lightDarkMode,
            TextureAtlasSprite sprite,
            boolean occludedPass) {
        if (annotation instanceof InWorldBoxAnnotation boxAnnotation) {
            var color = MutableColor.of(boxAnnotation.color(), lightDarkMode);
            if (occludedPass) {
                color.darker(50).setAlpha(color.alpha() * 0.5f);
            }
            if (boxAnnotation.isHovered()) {
                color.lighter(50);
            }
            render(buffer, pose,
                    boxAnnotation.min(),
                    boxAnnotation.max(),
                    color.toArgb32(),
                    boxAnnotation.thickness(),
                    sprite);
        } else if (annotation instanceof InWorldLineAnnotation lineAnnotation) {
            var color = MutableColor.of(lineAnnotation.color(), lightDarkMode);
            if (occludedPass) {
                color.darker(50).setAlpha(color.alpha() * 0.5f);
            }
            if (lineAnnotation.isHovered()) {
                color.lighter(50);
            }
            strut(buffer, pose,
                    lineAnnotation.min(),
                    lineAnnotation.max(),
                    color.toArgb32(),
                    lineAnnotation.thickness(),
                    true,
                    true,
                    sprite);
        }
    }

    public static void render(VertexConsumer consumer,
            PoseStack.Pose pose,
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
        strut(consumer, pose, new Vector3f(uNorm).mulAdd(-thickHalf, corners[0]),
                new Vector3f(uNorm).mulAdd(thickHalf, corners[1]), color, thickness, true, true, sprite);
        strut(consumer, pose, new Vector3f(uNorm).mulAdd(-thickHalf, corners[2]),
                new Vector3f(uNorm).mulAdd(thickHalf, corners[7]), color, thickness, true, true, sprite);
        strut(consumer, pose, new Vector3f(uNorm).mulAdd(-thickHalf, corners[3]),
                new Vector3f(uNorm).mulAdd(thickHalf, corners[6]), color, thickness, true, true, sprite);
        strut(consumer, pose, new Vector3f(uNorm).mulAdd(-thickHalf, corners[5]),
                new Vector3f(uNorm).mulAdd(thickHalf, corners[4]), color, thickness, true, true, sprite);

        // Along Y-Axis
        strut(consumer, pose, new Vector3f(vNorm).mulAdd(thickHalf, corners[0]),
                new Vector3f(vNorm).mulAdd(-thickHalf, corners[2]), color, thickness, false, false, sprite);
        strut(consumer, pose, new Vector3f(vNorm).mulAdd(thickHalf, corners[1]),
                new Vector3f(vNorm).mulAdd(-thickHalf, corners[7]), color, thickness, false, false, sprite);
        strut(consumer, pose, new Vector3f(vNorm).mulAdd(thickHalf, corners[3]),
                new Vector3f(vNorm).mulAdd(-thickHalf, corners[5]), color, thickness, false, false, sprite);
        strut(consumer, pose, new Vector3f(vNorm).mulAdd(thickHalf, corners[6]),
                new Vector3f(vNorm).mulAdd(-thickHalf, corners[4]), color, thickness, false, false, sprite);

        // Along Z-Axis
        strut(consumer, pose, new Vector3f(tNorm).mulAdd(thickHalf, corners[0]),
                new Vector3f(tNorm).mulAdd(-thickHalf, corners[3]), color, thickness, false, false, sprite);
        strut(consumer, pose, new Vector3f(tNorm).mulAdd(thickHalf, corners[1]),
                new Vector3f(tNorm).mulAdd(-thickHalf, corners[6]), color, thickness, false, false, sprite);
        strut(consumer, pose, new Vector3f(tNorm).mulAdd(thickHalf, corners[2]),
                new Vector3f(tNorm).mulAdd(-thickHalf, corners[5]), color, thickness, false, false, sprite);
        strut(consumer, pose, new Vector3f(tNorm).mulAdd(thickHalf, corners[7]),
                new Vector3f(tNorm).mulAdd(-thickHalf, corners[4]), color, thickness, false, false, sprite);
    }

    private static void strut(VertexConsumer consumer, PoseStack.Pose pose, Vector3f from, Vector3f to, int color,
            float thickness, boolean startCap, boolean endCap, TextureAtlasSprite sprite) {
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
                    consumer, pose, downNorm, color,
                    new Vector3f(from).add(up).sub(right),
                    new Vector3f(from).sub(up).sub(right),
                    new Vector3f(from).sub(up).add(right),
                    new Vector3f(from).add(up).add(right),
                    sprite);
        }

        if (endCap) {
            quad(
                    consumer, pose, norm, color,
                    new Vector3f(to).add(up).add(right),
                    new Vector3f(to).sub(up).add(right),
                    new Vector3f(to).sub(up).sub(right),
                    new Vector3f(to).add(up).sub(right),
                    sprite);
        }

        quad(
                consumer, pose, leftNorm, color,
                new Vector3f(from).sub(right).add(up),
                new Vector3f(to).sub(right).add(up),
                new Vector3f(to).sub(right).sub(up),
                new Vector3f(from).sub(right).sub(up),
                sprite);
        quad(
                consumer, pose, rightNorm, color,
                new Vector3f(to).add(right).sub(up),
                new Vector3f(to).add(right).add(up),
                new Vector3f(from).add(right).add(up),
                new Vector3f(from).add(right).sub(up),
                sprite);
        quad(
                consumer, pose, upNorm, color,
                new Vector3f(from).add(up).sub(right),
                new Vector3f(from).add(up).add(right),
                new Vector3f(to).add(up).add(right),
                new Vector3f(to).add(up).sub(right),
                sprite);
        quad(
                consumer, pose, downNorm, color,
                new Vector3f(to).sub(up).sub(right),
                new Vector3f(to).sub(up).add(right),
                new Vector3f(from).sub(up).add(right),
                new Vector3f(from).sub(up).sub(right),
                sprite);
    }

    private static void quad(VertexConsumer consumer, PoseStack.Pose pose, Vector3f faceNormal, int color,
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

        vertex(consumer, pose, faceNormal, color, v1, sprite.getU0(), sprite.getV1());
        vertex(consumer, pose, faceNormal, color, v2, sprite.getU0(), sprite.getV0());
        vertex(consumer, pose, faceNormal, color, v3, sprite.getU1(), sprite.getV0());
        vertex(consumer, pose, faceNormal, color, v4, sprite.getU1(), sprite.getV1());
    }

    private static void vertex(VertexConsumer consumer,
            PoseStack.Pose pose,
            Vector3f faceNormal,
            int color,
            Vector3f bottomLeft,
            float u, float v) {
        consumer.addVertex(pose, bottomLeft.x, bottomLeft.y, bottomLeft.z)
                .setColor(color)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightCoordsUtil.FULL_BRIGHT)
                .setNormal(pose, faceNormal.x(), faceNormal.y(), faceNormal.z());
    }
}
