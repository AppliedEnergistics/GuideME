package guideme.document.flow;

import guideme.document.LytSize;
import guideme.document.block.LytBlock;
import guideme.document.block.LytVisitor;
import guideme.document.interaction.GuideTooltip;
import guideme.document.interaction.InteractiveElement;
import guideme.layout.LayoutContext;
import guideme.layout.MinecraftFontMetrics;
import guideme.ui.GuideUiHost;
import java.util.Optional;
import net.minecraft.client.input.MouseButtonInfo;

public class LytFlowInlineBlock extends LytFlowContent implements InteractiveElement {

    private LytBlock block;

    private InlineBlockAlignment alignment = InlineBlockAlignment.INLINE;

    public LytBlock getBlock() {
        return block;
    }

    public void setBlock(LytBlock block) {
        this.block = block;
    }

    public InlineBlockAlignment getAlignment() {
        return alignment;
    }

    public void setAlignment(InlineBlockAlignment alignment) {
        this.alignment = alignment;
    }

    public LytSize getPreferredSize(int lineWidth) {
        if (block == null) {
            return LytSize.empty();
        }

        // We need to compute the layout
        var layoutContext = new LayoutContext(new MinecraftFontMetrics());
        var bounds = block.layout(layoutContext, 0, 0, lineWidth);
        return new LytSize(bounds.right(), bounds.bottom());
    }

    @Override
    public boolean mouseClicked(GuideUiHost screen, int x, int y, MouseButtonInfo button, boolean doubleClick) {
        if (block instanceof InteractiveElement interactiveElement) {
            return interactiveElement.mouseClicked(screen, x, y, button, doubleClick);
        }
        return false;
    }

    @Override
    public boolean mouseReleased(GuideUiHost screen, int x, int y, MouseButtonInfo button) {
        if (block instanceof InteractiveElement interactiveElement) {
            return interactiveElement.mouseReleased(screen, x, y, button);
        }
        return false;
    }

    @Override
    public Optional<GuideTooltip> getTooltip(float x, float y) {
        if (block instanceof InteractiveElement interactiveElement) {
            return interactiveElement.getTooltip(x, y);
        }
        return Optional.empty();
    }

    @Override
    protected void visitChildren(LytVisitor visitor) {
        if (block != null) {
            block.visit(visitor);
        }
    }

    public static LytFlowInlineBlock of(LytBlock block) {
        var inlineBlock = new LytFlowInlineBlock();
        inlineBlock.setBlock(block);
        return inlineBlock;
    }
}
