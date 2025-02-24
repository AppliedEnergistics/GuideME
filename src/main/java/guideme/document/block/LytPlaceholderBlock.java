package guideme.document.block;

import guideme.document.DefaultStyles;
import guideme.document.LytRect;
import guideme.layout.LayoutContext;
import guideme.render.RenderContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This layout block shows a loading indicator and will ultimately replace itself with the final content.
 */
public class LytPlaceholderBlock extends LytBlock {
    private static final Logger LOG = LoggerFactory.getLogger(LytPlaceholderBlock.class);

    private final CompletableFuture<LytBlock> future;

    private LytBlock currentBlock;

    private final List<LytBlock> currentChildren = new ArrayList<>(1);

    private final List<LytBlock> unmodifiableChildren = Collections.unmodifiableList(currentChildren);

    public LytPlaceholderBlock(CompletableFuture<LytBlock> future) {
        var loading = new LytParagraph();
        loading.appendText("Loading...");
        setCurrent(loading);

        this.future = future;
        future.whenCompleteAsync(this::onLoad, Minecraft.getInstance());
    }

    private void setCurrent(LytBlock block) {
        if (currentBlock != block) {
            currentChildren.clear();
            currentBlock = block;
            currentChildren.add(block);
            var document = getDocument();
            if (document != null) {
                document.invalidateLayout();
            }
        }
    }

    private void onLoad(LytBlock element, Throwable error) {
        if (error != null || element == null) {
            LOG.error("Failed to load an asynchronous guide element.", error);
            var errorParagraph = new LytParagraph();
            errorParagraph.setStyle(DefaultStyles.ERROR_TEXT);
            if (error == null) {
                errorParagraph.appendText("An unknown error occurred");
            } else {
                errorParagraph.appendText(error.toString());
            }
            setCurrent(errorParagraph);
        } else {
            setCurrent(element);
        }
    }

    @Override
    protected LytRect computeLayout(LayoutContext context, int x, int y, int availableWidth) {
        return currentBlock.layout(context, x, y, availableWidth);
    }

    @Override
    protected void onLayoutMoved(int deltaX, int deltaY) {
        currentBlock.onLayoutMoved(deltaX, deltaY);
    }

    @Override
    public void renderBatch(RenderContext context, MultiBufferSource buffers) {
        currentBlock.renderBatch(context, buffers);
    }

    @Override
    public void render(RenderContext context) {
        currentBlock.render(context);
    }

    @Override
    public List<? extends LytNode> getChildren() {
        return unmodifiableChildren;
    }
}
