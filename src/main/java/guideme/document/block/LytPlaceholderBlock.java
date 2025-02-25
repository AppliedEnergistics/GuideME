package guideme.document.block;

import guideme.document.DefaultStyles;
import guideme.document.LytRect;
import guideme.layout.LayoutContext;
import guideme.render.RenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

/**
 * This layout block shows a loading indicator and will ultimately replace itself
 * with the final content.
 */
public class LytPlaceholderBlock extends LytBlock {
    private static final Logger LOG = LoggerFactory.getLogger(LytPlaceholderBlock.class);

    private final CompletableFuture<LytBlock> future;

    @Nullable
    private LytBlock element;

    @Nullable
    private LytBlock error;

    private LytBlock loading;

    public LytPlaceholderBlock(CompletableFuture<LytBlock> future) {
        var loading = new LytParagraph();
        loading.appendText("Loading...");
        this.loading = loading;
        this.future = future;
        future.whenCompleteAsync(this::onLoad, Minecraft.getInstance());
    }

    private void onLoad(LytBlock element, Throwable error) {
        this.element = element;
        if (error != null) {
            LOG.error("Failed to load an asynchronous guide element.", error);
            var errorParagraph = new LytParagraph();
            errorParagraph.setStyle(DefaultStyles.ERROR_TEXT);
            errorParagraph.appendText(error.toString());
            this.error = errorParagraph;
        }
        var document = this.getDocument();
        if (document != null) {
            document.invalidateLayout();
        }
    }

    private LytBlock getDelegate() {
        if (element != null) {
            return element;
        } else if (error != null) {
            return error;
        } else {
            return loading;
        }
    }

    @Override
    protected LytRect computeLayout(LayoutContext context, int x, int y, int availableWidth) {
        return getDelegate().computeLayout(context, x, y, availableWidth);
    }

    @Override
    protected void onLayoutMoved(int deltaX, int deltaY) {
        getDelegate().onLayoutMoved(deltaX, deltaY);
    }

    @Override
    public void renderBatch(RenderContext context, MultiBufferSource buffers) {
        getDelegate().renderBatch(context, buffers);
    }

    @Override
    public void render(RenderContext context) {
        getDelegate().render(context);
    }
}
