package guideme.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import guideme.color.ColorValue;
import guideme.color.ConstantColor;
import guideme.color.LightDarkMode;
import guideme.color.MutableColor;
import guideme.document.LytRect;
import guideme.internal.util.FluidBlitter;
import guideme.layout.MinecraftFontMetrics;
import guideme.style.ResolvedTextStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec2;
import net.minecraftforge.fluids.FluidStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public interface RenderContext {

    LightDarkMode lightDarkMode();

    default boolean isDarkMode() {
        return lightDarkMode() == LightDarkMode.DARK_MODE;
    }

    GuiGraphics guiGraphics();

    default PoseStack poseStack() {
        return guiGraphics().pose();
    }

    LytRect viewport();

    /**
     * Checks if the given rectangle intersects with the current viewport, after applying the active pose.
     */
    default boolean intersectsViewport(LytRect bounds) {
        return bounds.intersects(viewport());
    }

    int resolveColor(ColorValue ref);

    void fillRect(LytRect rect, ColorValue topLeft, ColorValue topRight, ColorValue bottomRight, ColorValue bottomLeft);

    default void drawIcon(int x, int y, GuiSprite guiSprite) {
        drawIcon(x, y, guiSprite, ConstantColor.WHITE);
    }

    default void drawIcon(int x, int y, GuiSprite guiSprite, ColorValue color) {
        drawIcon(x, y, guiSprite.atlasSprite(lightDarkMode()), color);
    }

    default void drawIcon(int x, int y, TextureAtlasSprite sprite, ColorValue color) {
        var contents = sprite.contents();
        fillIcon(x, y, contents.width(), contents.height(), sprite, color);
    }

    default void fillIcon(LytRect bounds, GuiSprite guiSprite) {
        fillIcon(bounds.x(), bounds.y(), bounds.width(), bounds.height(), guiSprite);
    }

    default void fillIcon(LytRect bounds, GuiSprite guiSprite, ColorValue color) {
        fillIcon(bounds.x(), bounds.y(), bounds.width(), bounds.height(), guiSprite, color);
    }

    default void fillIcon(int x, int y, int width, int height, GuiSprite guiSprite) {
        fillIcon(x, y, width, height, guiSprite, ConstantColor.WHITE);
    }

    default void fillIcon(int x, int y, int width, int height, GuiSprite guiSprite, ColorValue color) {
        fillIcon(x, y, width, height, guiSprite.atlasSprite(lightDarkMode()), color);
    }

    default void fillIcon(int x, int y, int width, int height, TextureAtlasSprite sprite, ColorValue color) {
        var spriteLayer = new SpriteLayer();
        spriteLayer.fillSprite(sprite.contents().name(), 0, 0, 0, width, height, resolveColor(color));
        spriteLayer.render(poseStack(), x, y, 0);
    }

    default void fillTexturedRect(LytRect rect, AbstractTexture texture, ColorValue topLeft, ColorValue topRight,
            ColorValue bottomRight, ColorValue bottomLeft) {
        // Just use the entire texture by default
        fillTexturedRect(rect, texture, topLeft, topRight, bottomRight, bottomLeft, 0, 0, 1, 1);
    }

    void fillTexturedRect(LytRect rect, AbstractTexture texture, ColorValue topLeft, ColorValue topRight,
            ColorValue bottomRight, ColorValue bottomLeft, float u0, float v0, float u1, float v1);

    default void fillTexturedRect(LytRect rect, GuidePageTexture texture) {
        fillTexturedRect(rect, texture.use(), ConstantColor.WHITE);
    }

    default void fillTexturedRect(LytRect rect, AbstractTexture texture) {
        fillTexturedRect(rect, texture, ConstantColor.WHITE);
    }

    default void fillTexturedRect(LytRect rect, AbstractTexture texture, ColorValue color) {
        fillTexturedRect(rect, texture, color, color, color, color);
    }

    default void fillTexturedRect(LytRect rect, GuidePageTexture texture, ColorValue color) {
        fillTexturedRect(rect, texture.use(), color, color, color, color);
    }

    default void fillTexturedRect(LytRect rect, TextureAtlasSprite sprite, ColorValue color) {
        var texture = Minecraft.getInstance().getTextureManager().getTexture(sprite.atlasLocation());
        fillTexturedRect(rect, texture, color, color, color, color,
                sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1());
    }

    default void fillTexturedRect(LytRect rect, ResourceLocation textureId) {
        fillTexturedRect(rect, textureId, ConstantColor.WHITE);
    }

    default void fillTexturedRect(LytRect rect, ResourceLocation textureId, ColorValue color) {
        var texture = Minecraft.getInstance().getTextureManager().getTexture(textureId);
        fillTexturedRect(rect, texture, color);
    }

    void fillTriangle(Vec2 p1, Vec2 p2, Vec2 p3, ColorValue color);

    default Font font() {
        return Minecraft.getInstance().font;
    }

    default float getAdvance(int codePoint, ResolvedTextStyle style) {
        return font().getFontSet(style.font()).getGlyphInfo(codePoint, false)
                .getAdvance(style.bold());
    }

    default float getWidth(String text, ResolvedTextStyle style) {
        return (float) text.codePoints()
                .mapToDouble(cp -> getAdvance(cp, style))
                .sum();
    }

    default void renderTextCenteredIn(String text, ResolvedTextStyle style, LytRect rect) {
        var splitter = new StringSplitter((i, ignored) -> getAdvance(i, style));
        var fontMetrics = new MinecraftFontMetrics(font());

        var splitLines = splitter.splitLines(text, (int) ((rect.width() - 10) / style.fontScale()), Style.EMPTY);
        var lineHeight = fontMetrics.getLineHeight(style);
        var overallHeight = splitLines.size() * lineHeight;
        var overallWidth = (int) (splitLines.stream().mapToDouble(splitter::stringWidth).max().orElse(0f)
                * style.fontScale());
        var textRect = new LytRect(0, 0, overallWidth, overallHeight);
        textRect = textRect.centerIn(rect);

        var y = textRect.y();
        for (var line : splitLines) {
            var x = textRect.x() + (textRect.width() - splitter.stringWidth(line) * style.fontScale()) / 2;
            renderText(line.getString(), style, x, y);
            y += lineHeight;
        }
    }

    default void renderText(String text, ResolvedTextStyle style, float x, float y) {
        var bufferSource = MultiBufferSource.immediate(Tesselator.getInstance().getBuilder());
        renderTextInBatch(text, style, x, y, bufferSource);
        bufferSource.endBatch();
    }

    default void renderTextInBatch(String text, ResolvedTextStyle style, float x, float y, MultiBufferSource buffers) {
        var effectiveStyle = Style.EMPTY
                .withBold(style.bold())
                .withItalic(style.italic())
                .withUnderlined(style.underlined())
                .withStrikethrough(style.strikethrough())
                .withFont(style.font());

        var matrix = poseStack().last().pose();
        if (style.fontScale() != 1) {
            matrix = new Matrix4f(matrix);

            matrix.scale(style.fontScale(), style.fontScale(), 1);
            matrix.translate(new Vector3f(x / style.fontScale(), y / style.fontScale(), 0));
            x = 0;
            y = 0;
        }

        font().drawInBatch(Component.literal(text).withStyle(effectiveStyle), x, y, resolveColor(style.color()),
                style.dropShadow(),
                matrix, buffers, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
    }

    default void fillRect(int x, int y, int width, int height, ColorValue color) {
        fillRect(new LytRect(x, y, width, height), color);
    }

    default void fillRect(LytRect rect, ColorValue color) {
        fillRect(rect, color, color, color, color);
    }

    default void fillGradientVertical(LytRect rect, ColorValue top, ColorValue bottom) {
        fillRect(rect, top, top, bottom, bottom);
    }

    default void fillGradientVertical(int x, int y, int width, int height, ColorValue top, ColorValue bottom) {
        fillGradientVertical(new LytRect(x, y, width, height), top, bottom);
    }

    default void fillGradientHorizontal(LytRect rect, ColorValue left, ColorValue right) {
        fillRect(rect, left, right, right, left);
    }

    default void fillGradientHorizontal(int x, int y, int width, int height, ColorValue left, ColorValue right) {
        fillGradientHorizontal(new LytRect(x, y, width, height), left, right);
    }

    default MultiBufferSource.BufferSource beginBatch() {
        return MultiBufferSource.immediate(Tesselator.getInstance().getBuilder());
    }

    default void endBatch(MultiBufferSource.BufferSource batch) {
        batch.endBatch();
    }

    default void renderItem(ItemStack stack, int x, int y, float width, float height) {
        renderItem(stack, x, y, 0, width, height);
    }

    default void renderFluid(Fluid fluid, int x, int y, int z, int width, int height) {
        FluidBlitter.create(new FluidStack(fluid, 1))
                .dest(x, y, width, height)
                .zOffset(z)
                .blit(guiGraphics());
    }

    default void renderFluid(FluidStack stack, int x, int y, int z, int width, int height) {
        FluidBlitter.create(stack)
                .dest(x, y, width, height)
                .blit(guiGraphics());
    }

    void renderItem(ItemStack stack, int x, int y, int z, float width, float height);

    default void renderPanel(LytRect bounds) {
        var panelBlitter = new PanelBlitter(lightDarkMode());
        panelBlitter.addBounds(0, 0, bounds.width(), bounds.height());
        panelBlitter.blit(guiGraphics(), bounds.x(), bounds.y());
    }

    default void pushScissor(LytRect bounds) {
        var dest = new Vector3f();
        var pose = poseStack().last().pose();
        pose.transformPosition(bounds.x(), bounds.y(), 0, dest);
        var left = dest.x;
        var top = dest.y;
        pose.transformPosition(bounds.right(), bounds.bottom(), 0, dest);
        guiGraphics().flush(); // Previously recorded draws should not use the scissor rect
        guiGraphics().enableScissor(
                (int) left,
                (int) top,
                (int) dest.x,
                (int) dest.y);
    }

    default void popScissor() {
        guiGraphics().flush(); // The recorded draws must be flushed before the scissor rect is reset
        guiGraphics().disableScissor();
    }

    default MutableColor mutableColor(ColorValue symbolicColor) {
        return MutableColor.of(symbolicColor, lightDarkMode());
    }
}
