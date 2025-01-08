package guideme.guidebook.document.flow;

import guideme.guidebook.document.LytSize;
import guideme.guidebook.document.block.LytBlock;
import guideme.guidebook.document.block.LytVisitor;
import guideme.guidebook.document.interaction.GuideTooltip;
import guideme.guidebook.document.interaction.InteractiveElement;
import guideme.guidebook.layout.LayoutContext;
import guideme.guidebook.layout.MinecraftFontMetrics;
import guideme.guidebook.screen.GuideScreen;
import java.util.Optional;

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
    public boolean mouseClicked(GuideScreen screen, int x, int y, int button) {
        if (block instanceof InteractiveElement interactiveElement) {
            return interactiveElement.mouseClicked(screen, x, y, button);
        }
        return false;
    }

    @Override
    public boolean mouseReleased(GuideScreen screen, int x, int y, int button) {
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
