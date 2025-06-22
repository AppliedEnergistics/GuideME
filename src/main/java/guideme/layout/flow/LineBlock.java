package guideme.layout.flow;

import guideme.document.block.LytBlock;
import guideme.render.RenderContext;

/**
 * Standalone block in-line with other content.
 */
public class LineBlock extends LineElement {

    private final LytBlock block;

    public LineBlock(LytBlock block) {
        this.block = block;
    }

    public LytBlock getBlock() {
        return block;
    }

    @Override
    public void render(RenderContext context) {
        block.render(context);
    }
}
