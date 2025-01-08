package guideme.guidebook.document;

import guideme.guidebook.color.ColorValue;
import guideme.guidebook.color.ConstantColor;
import guideme.guidebook.color.SymbolicColor;
import guideme.guidebook.style.ResolvedTextStyle;
import guideme.guidebook.style.TextAlignment;
import guideme.guidebook.style.TextStyle;
import guideme.guidebook.style.WhiteSpaceMode;
import net.minecraft.client.Minecraft;

public final class DefaultStyles {
    private DefaultStyles() {
    }

    public static final ResolvedTextStyle BASE_STYLE = new ResolvedTextStyle(
            1,
            false,
            false,
            false,
            false,
            false,
            Minecraft.UNIFORM_FONT,
            (ColorValue) SymbolicColor.BODY_TEXT,
            WhiteSpaceMode.NORMAL,
            TextAlignment.LEFT,
            false);

    public static final TextStyle BODY_TEXT = TextStyle.builder()
            .font(Minecraft.UNIFORM_FONT)
            .color((ColorValue) SymbolicColor.BODY_TEXT)
            .build();

    public static final TextStyle CRAFTING_RECIPE_TYPE = TextStyle.builder()
            .font(Minecraft.UNIFORM_FONT)
            .color((ColorValue) SymbolicColor.CRAFTING_RECIPE_TYPE)
            .build();

    public static final TextStyle HEADING1 = TextStyle.builder()
            .fontScale(1.3f)
            .bold(true).font(Minecraft.DEFAULT_FONT)
            .color(ConstantColor.WHITE)
            .build();
    public static final TextStyle HEADING2 = TextStyle.builder()
            .fontScale(1.1f)
            .font(Minecraft.DEFAULT_FONT)
            .build();
    public static final TextStyle HEADING3 = TextStyle.builder()
            .fontScale(1f)
            .font(Minecraft.DEFAULT_FONT)
            .build();
    public static final TextStyle HEADING4 = TextStyle.builder()
            .fontScale(1.1f)
            .bold(true)
            .font(Minecraft.UNIFORM_FONT)
            .build();
    public static final TextStyle HEADING5 = TextStyle.builder()
            .fontScale(1f)
            .bold(true)
            .font(Minecraft.UNIFORM_FONT)
            .build();
    public static final TextStyle HEADING6 = TextStyle.builder()
            .fontScale(1f)
            .font(Minecraft.UNIFORM_FONT)
            .build();
}
