package guideme.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.metadata.gui.GuiSpriteScaling;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;

/**
 * Helper to build and draw a layer of sprites in a single draw-call.
 */
final class SpriteLayer {
    private final GuiGraphics graphics;
    private final List<Vertex> vertices = new ArrayList<>();
    private final ResourceLocation atlasLocation = GuiAssets.GUI_SPRITE_ATLAS;

    public SpriteLayer(GuiGraphics graphics) {
        this.graphics = graphics;
    }

    public void drawSprite(ResourceLocation id, float x, float y, float z, int color) {
        var guiSprites = Minecraft.getInstance().getGuiSprites();
        var sprite = guiSprites.getSprite(id);
        var scaling = guiSprites.getSpriteScaling(sprite);
        switch (scaling) {
            case GuiSpriteScaling.Tile tiled -> {
                fillSprite(id, x, y, z, tiled.width(), tiled.height(), color);
            }
            case GuiSpriteScaling.Stretch stretch -> {
                fillSprite(id, x, y, z, sprite.contents().width(), sprite.contents().height(), color);
            }
            case GuiSpriteScaling.NineSlice nineSlice -> {
                fillSprite(id, x, y, z, nineSlice.width(), nineSlice.height(), color);
            }
            default -> {
            }
        }
    }

    public void fillSprite(ResourceLocation id, float x, float y, float z, float width, float height, int color) {
        fillSprite(id, x, y, z, width, height, color, SpriteFillDirection.TOP_TO_BOTTOM);
    }

    public void fillSprite(ResourceLocation id, float x, float y, float z, float width, float height, int color,
            SpriteFillDirection fillDirection) {
        // Too large values for width / height cause immediate crashes of the VM due to graphics driver bugs<
        // These maximum values are picked without too much thought.
        width = Math.min(65535, width);
        height = Math.min(65535, height);

        var guiSprites = Minecraft.getInstance().getGuiSprites();
        var sprite = guiSprites.getSprite(id);
        var scaling = guiSprites.getSpriteScaling(sprite);
        var u0 = sprite.getU0();
        var u1 = sprite.getU1();
        var v0 = sprite.getV0();
        var v1 = sprite.getV1();
        switch (scaling) {
            case GuiSpriteScaling.Tile tiled -> {
                fillTiled(x, y, z, width, height, color, tiled.width(), tiled.height(), u0, u1, v0, v1, fillDirection);
            }
            case GuiSpriteScaling.Stretch stretch -> {
                addQuad(x, y, z, width, height, color, u0, u1, v0, v1);
            }
            case GuiSpriteScaling.NineSlice nineSlice -> {
                addTiledNineSlice(id, x, y, z, width, height, color, nineSlice.width(), nineSlice.height(),
                        nineSlice.border(), u0, u1, v0, v1);
            }
            default -> {
            }
        }
    }

    private void addTiledNineSlice(ResourceLocation id,
            float x,
            float y,
            float z,
            float width,
            float height,
            int color,
            float nineSliceWidth,
            float nineSliceHeight,
            GuiSpriteScaling.NineSlice.Border border,
            float u0,
            float u1,
            float v0,
            float v1) {

        var leftWidth = Math.min(border.left(), width / 2);
        var rightWidth = Math.min(border.right(), width / 2);
        var topHeight = Math.min(border.top(), height / 2);
        var bottomHeight = Math.min(border.bottom(), height / 2);
        var innerWidth = nineSliceWidth - border.left() - border.right();
        var innerHeight = nineSliceHeight - border.top() - border.bottom();

        // The U/V values for the cuts through the nine-slice we'll use
        var leftU = u0 + leftWidth / nineSliceWidth * (u1 - u0);
        var rightU = u1 - rightWidth / nineSliceWidth * (u1 - u0);
        var topV = v0 + topHeight / nineSliceHeight * (v1 - v0);
        var bottomV = v1 - bottomHeight / nineSliceHeight * (v1 - v0);

        // Destination pixel values of the inner rectangle
        var dstInnerLeft = x + leftWidth;
        var dstInnerTop = y + topHeight;
        var dstInnerRight = x + width - rightWidth;
        var dstInnerBottom = y + height - bottomHeight;
        var dstInnerWidth = dstInnerRight - dstInnerLeft;
        var dstInnerHeight = dstInnerBottom - dstInnerTop;

        // Corners are always untiled, but may be cropped
        addQuad(x, y, z, leftWidth, topHeight, color, u0, leftU, v0, topV); // Top left
        addQuad(dstInnerRight, y, z, rightWidth, topHeight, color, rightU, u1, v0, topV); // Top right
        addQuad(dstInnerRight, dstInnerBottom, z, rightWidth, bottomHeight, color, rightU, u1, bottomV, v1); // Bottom
        // right
        addQuad(x, dstInnerBottom, z, leftWidth, bottomHeight, color, u0, leftU, bottomV, v1); // Bottom left

        // The edges are tiled
        fillTiled(dstInnerLeft, y, z, dstInnerWidth, topHeight, color, innerWidth, border.top(), leftU, rightU, v0,
                topV); // Top Edge
        fillTiled(dstInnerLeft, dstInnerBottom, z, dstInnerWidth, bottomHeight, color, innerWidth, border.bottom(),
                leftU, rightU, bottomV, v1); // Bottom Edge
        fillTiled(x, dstInnerTop, z, leftWidth, dstInnerHeight, color, border.left(), innerHeight, u0, leftU, topV,
                bottomV); // Left Edge
        fillTiled(dstInnerRight, dstInnerTop, z, rightWidth, dstInnerHeight, color, border.right(), innerHeight, rightU,
                u1, topV, bottomV); // Right Edge

        // The center is tiled too
        fillTiled(dstInnerLeft, dstInnerTop, z, dstInnerWidth, dstInnerHeight, color, innerWidth, innerHeight, leftU,
                rightU, topV, bottomV);
    }

    private void fillTiled(float x, float y, float z, float width, float height, int color, float destTileWidth,
            float destTileHeight, float u0, float u1, float v0, float v1) {
        fillTiled(x, y, z, width, height, color, destTileWidth, destTileHeight, u0, u1, v0, v1,
                SpriteFillDirection.TOP_TO_BOTTOM);
    }

    private void fillTiled(float x, float y, float z, float width, float height, int color, float destTileWidth,
            float destTileHeight, float u0, float u1, float v0, float v1, SpriteFillDirection fillDirection) {
        if (destTileWidth <= 0 || destTileHeight <= 0) {
            return;
        }

        var right = x + width;
        var bottom = y + height;

        if (fillDirection == SpriteFillDirection.BOTTOM_TO_TOP) {
            for (var cy = bottom; cy >= y; cy -= destTileHeight) {
                // This handles not stretching the potentially partial last column
                var tileHeight = Math.min(cy - y, destTileHeight);
                var tileV0 = v1 - (v1 - v0) * tileHeight / destTileHeight;

                for (var cx = x; cx < right; cx += destTileWidth) {
                    // This handles not stretching the potentially partial last row
                    var tileWidth = Math.min(right - cx, destTileWidth);
                    var tileU1 = u0 + (u1 - u0) * tileWidth / destTileWidth;

                    addQuad(cx, cy - tileHeight, z, tileWidth, tileHeight, color, u0, tileU1, tileV0, v1);
                }
            }
        } else {
            for (var cy = y; cy < bottom; cy += destTileHeight) {
                // This handles not stretching the potentially partial last column
                var tileHeight = Math.min(bottom - cy, destTileHeight);
                var tileV1 = v0 + (v1 - v0) * tileHeight / destTileHeight;

                for (var cx = x; cx < right; cx += destTileWidth) {
                    // This handles not stretching the potentially partial last row
                    var tileWidth = Math.min(right - cx, destTileWidth);
                    var tileU1 = u0 + (u1 - u0) * tileWidth / destTileWidth;

                    addQuad(cx, cy, z, tileWidth, tileHeight, color, u0, tileU1, v0, tileV1);
                }
            }
        }
    }

    public void addQuad(float x, float y, float z, float width, float height, int color, float minU, float maxU,
            float minV, float maxV) {
        if (width < 0 || height < 0) {
            return;
        }

        vertices.add(new Vertex(x, y, z, minU, minV, color));
        vertices.add(new Vertex(x, y + height, z, minU, maxV, color));
        vertices.add(new Vertex(x + width, y + height, z, maxU, maxV, color));
        vertices.add(new Vertex(x + width, y, z, maxU, minV, color));
    }

    public void render(Matrix3x2fStack poseStack, int x, int y, int z) {
        poseStack.pushMatrix();
        poseStack.translate(x, y);
        var scissor = graphics.peekScissorStack();
        var bounds = RenderState.getBounds(vertices, poseStack, scissor);
        var texture = Minecraft.getInstance().getTextureManager().getTexture(atlasLocation).getTextureView();
        graphics.submitGuiElementRenderState(new RenderState(
                RenderPipelines.GUI_TEXTURED, TextureSetup.singleTexture(texture), new Matrix3x2f(poseStack), vertices,
                scissor, bounds));
        poseStack.popMatrix();
    }

    record Vertex(float x, float y, float z, float u, float v, int color) {
    }

    record RenderState(
            RenderPipeline pipeline,
            TextureSetup textureSetup,
            Matrix3x2f pose,
            List<Vertex> vertices,
            @Nullable ScreenRectangle scissorArea,
            @Nullable ScreenRectangle bounds) implements GuiElementRenderState {
        @Override
        public void buildVertices(VertexConsumer vertices, float z) {
            for (Vertex vertex : this.vertices) {
                vertices.addVertexWith2DPose(pose, vertex.x, vertex.y, vertex.z() + z).setUv(vertex.u, vertex.v)
                        .setColor(vertex.color);
            }
        }

        @Nullable
        private static ScreenRectangle getBounds(
                List<Vertex> vertices, Matrix3x2f pose, @Nullable ScreenRectangle scissorArea) {
            var minX = (int) vertices.getFirst().x;
            var minY = (int) vertices.getFirst().y;
            var maxX = (int) Math.ceil(vertices.getFirst().x);
            var maxY = (int) Math.ceil(vertices.getFirst().y);
            for (Vertex vertex : vertices) {
                minX = Math.min(minX, (int) vertex.x);
                minY = Math.min(minY, (int) vertex.y);
                maxX = Math.max(maxX, (int) Math.ceil(vertex.x));
                maxY = Math.max(maxY, (int) Math.ceil(vertex.y));
            }

            ScreenRectangle screenrectangle = new ScreenRectangle(minX, minY, maxX - minX, maxY - minY)
                    .transformMaxBounds(pose);
            return scissorArea != null ? scissorArea.intersection(screenrectangle) : screenrectangle;
        }
    }
}
