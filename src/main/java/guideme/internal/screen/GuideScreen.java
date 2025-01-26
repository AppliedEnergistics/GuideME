package guideme.internal.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import guideme.Guide;
import guideme.GuidePage;
import guideme.PageAnchor;
import guideme.PageCollection;
import guideme.color.ConstantColor;
import guideme.color.SymbolicColor;
import guideme.compiler.AnchorIndexer;
import guideme.compiler.PageCompiler;
import guideme.compiler.ParsedGuidePage;
import guideme.document.DefaultStyles;
import guideme.document.LytRect;
import guideme.document.block.LytDocument;
import guideme.document.block.LytHeading;
import guideme.document.block.LytParagraph;
import guideme.document.flow.LytFlowAnchor;
import guideme.document.flow.LytFlowContent;
import guideme.document.flow.LytFlowSpan;
import guideme.internal.GuideME;
import guideme.internal.GuidebookText;
import guideme.layout.LayoutContext;
import guideme.layout.MinecraftFontMetrics;
import guideme.render.GuidePageTexture;
import guideme.render.SimpleRenderContext;
import guideme.style.TextAlignment;
import guideme.style.TextStyle;
import guideme.ui.GuideUiHost;
import guideme.ui.UiPoint;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModInfo;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class GuideScreen extends DocumentScreen implements GuideUiHost {
    private static final Logger LOG = LoggerFactory.getLogger(GuideScreen.class);

    // 20 virtual px margin around the document
    public static final int DOCUMENT_RECT_MARGIN = 20;

    private static final ResourceLocation BACKGROUND_TEXTURE = GuideME.makeId("textures/guide/background.png");

    private final Guide guide;

    private GuidePage currentPage;
    private final LytParagraph pageTitle;

    private final NavigationToolbar toolbar;

    @Nullable
    private Screen returnToOnClose;

    /**
     * When the guidebook is initially opened, it does not do a proper layout due to missing width/height info. When we
     * should scroll to a point in the page, we have to "stash" that and do it after the initial proper layout has been
     * done.
     */
    @Nullable
    private String pendingScrollToAnchor;

    private GuideScreen(Guide guide, PageAnchor anchor) {
        super(Component.literal("AE2 Guidebook"));
        this.guide = guide;

        this.pageTitle = new LytParagraph();
        this.pageTitle.setStyle(DefaultStyles.HEADING1);
        loadPageAndScrollTo(anchor);

        toolbar = new NavigationToolbar(guide);
        toolbar.setCloseCallback(this::onClose);
    }

    /**
     * Opens and resets history. Uses per-guide history by default.
     */
    public static GuideScreen openNew(Guide guide, PageAnchor anchor) {
        return openNew(guide, anchor, GlobalInMemoryHistory.get(guide));
    }

    /**
     * Opens and resets history.
     */
    public static GuideScreen openNew(Guide guide, PageAnchor anchor, GuideScreenHistory history) {
        history.push(anchor);

        return new GuideScreen(guide, anchor);
    }

    @Override
    protected void init() {
        super.init();

        updateDocumentLayout();

        GuideNavBar navbar = new GuideNavBar(this);
        addRenderableWidget(navbar);

        toolbar.addToScreen(this::addRenderableWidget, 2, width - DOCUMENT_RECT_MARGIN);
    }

    @Override
    public void tick() {
        super.tick();

        toolbar.update();

        processPendingScrollTo();
    }

    @Override
    protected boolean documentClicked(UiPoint documentPoint, int button) {
        if (button == 3) {
            GuideNavigation.navigateBack(guide);
            return true;
        } else if (button == 4) {
            GuideNavigation.navigateForward(guide);
            return true;
        }

        return false;
    }

    /**
     * If a scroll-to command is queued, this processes that.
     */
    private void processPendingScrollTo() {
        if (pendingScrollToAnchor == null) {
            return;
        }

        var anchor = pendingScrollToAnchor;
        pendingScrollToAnchor = null;

        var indexer = new AnchorIndexer(currentPage.document());

        var targetAnchor = indexer.get(anchor);
        if (targetAnchor == null) {
            LOG.warn("Failed to find anchor {} in page {}", anchor, currentPage);
            return;
        }

        if (targetAnchor.flowContent() instanceof LytFlowAnchor flowAnchor && flowAnchor.getLayoutY().isPresent()) {
            setDocumentScrollY(flowAnchor.getLayoutY().getAsInt());
        } else {
            var bounds = targetAnchor.blockNode().getBounds();
            setDocumentScrollY(bounds.y());
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderSkyStoneBackground(guiGraphics);

        var context = new SimpleRenderContext(new LytRect(0, 0, width, height), guiGraphics);

        var documentRect = getDocumentRect();
        context.fillRect(documentRect, new ConstantColor(0x80333333));

        renderDocument(guiGraphics);

        var poseStack = guiGraphics.pose();
        poseStack.pushPose();
        poseStack.translate(0, 0, 200);

        renderTitle(documentRect, context);

        renderExternalPageSource(documentRect, context);

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        poseStack.popPose();

        renderDocumentTooltip(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderExternalPageSource(LytRect documentRect, SimpleRenderContext context) {
        // Render the source of the content
        var externalSource = getExternalSourceName();
        if (externalSource != null) {
            var paragraph = new LytParagraph();
            paragraph.appendText(GuidebookText.ContentFrom.text().getString() + " ");
            var sourceSpan = new LytFlowSpan();

            sourceSpan.appendText(externalSource);
            sourceSpan.setStyle(TextStyle.builder().italic(true).build());
            paragraph.append(sourceSpan);
            paragraph.setStyle(TextStyle.builder().alignment(TextAlignment.RIGHT).build());
            var layoutContext = new LayoutContext(new MinecraftFontMetrics());
            paragraph.layout(layoutContext, documentRect.x(), documentRect.bottom(), documentRect.width());
            var buffers = context.beginBatch();
            paragraph.renderBatch(context, buffers);
            context.endBatch(buffers);
        }
    }

    /**
     * Gets a readable name for the source of the page (i.e. resource pack name, mod name) if the page has been
     * contributed externally.
     */
    @Nullable
    private String getExternalSourceName() {
        var sourcePackId = currentPage.sourcePack();
        // If the pages came directly from a mod resource pack, we have to use the mod-list to resolve its name
        if (sourcePackId.startsWith("mod:") || sourcePackId.startsWith("mod/")) {
            var modId = sourcePackId.substring("mod:".length());

            // Only show the source marker for pages that are not native to the guides mod
            if (guide.getDefaultNamespace().equals(modId)) {
                return null;
            }

            return ModList.get().getModContainerById(modId)
                    .map(ModContainer::getModInfo)
                    .map(IModInfo::getDisplayName)
                    .orElse(null);
        }

        // Only show the source marker for pages that are not native to the guides mod
        if (guide.getDefaultNamespace().equals(sourcePackId)) {
            return null;
        }

        var pack = Minecraft.getInstance().getResourcePackRepository().getPack(sourcePackId);
        if (pack != null) {
            return pack.getDescription().getString();
        }

        return null;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Stub this out otherwise vanilla renders a background on top of our content
    }

    private void renderTitle(LytRect documentRect, SimpleRenderContext context) {
        var buffers = context.beginBatch();
        pageTitle.renderBatch(context, buffers);
        context.endBatch(buffers);
        context.fillRect(
                documentRect.x(),
                documentRect.y() - 1,
                documentRect.width(),
                1,
                SymbolicColor.HEADER1_SEPARATOR);
    }

    private void renderSkyStoneBackground(GuiGraphics guiGraphics) {
        RenderSystem.setShaderColor(0.25F, 0.25F, 0.25F, 1.0F);
        guiGraphics.blit(BACKGROUND_TEXTURE, 0, 0, 0, 0.0F, 0.0F, this.width, this.height, 32, 32);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    public void navigateTo(ResourceLocation pageId) {
        navigateTo(PageAnchor.page(pageId));
    }

    @Override
    public void navigateTo(PageAnchor anchor) {
        GuideNavigation.navigateTo(guide, anchor);
    }

    private void loadPageAndScrollTo(PageAnchor anchor) {
        loadPage(anchor.pageId());

        setDocumentScrollY(0);
        updateDocumentLayout();

        pendingScrollToAnchor = anchor.anchor();
    }

    @Override
    public void reloadPage() {
        loadPage(currentPage.id());
        updateDocumentLayout();
    }

    private void loadPage(ResourceLocation pageId) {
        GuidePageTexture.releaseUsedTextures();
        var page = guide.getParsedPage(pageId);

        if (page == null) {
            // Build a "not found" page dynamically
            page = buildNotFoundPage(pageId);
        }

        currentPage = PageCompiler.compile(guide, guide.getExtensions(), page);

        // Find and pull out the first heading
        pageTitle.clearContent();
        for (var flowContent : extractPageTitle(currentPage)) {
            pageTitle.append(flowContent);
        }
    }

    private Iterable<LytFlowContent> extractPageTitle(GuidePage page) {
        for (var block : page.document().getBlocks()) {
            if (block instanceof LytHeading heading) {
                if (heading.getDepth() == 1) {
                    page.document().removeChild(heading);
                    return heading.getContent();
                } else {
                    break; // Any heading other than depth 1 cancels this algo
                }
            }
        }
        return List.of();
    }

    private ParsedGuidePage buildNotFoundPage(ResourceLocation pageId) {
        String pageSource = "# Page not Found\n" +
                "\n" +
                "Page \"" + pageId + "\" could not be found.";

        return PageCompiler.parse(
                pageId.getNamespace(),
                pageId,
                pageSource);
    }

    @Override
    public void removed() {
        super.removed();
        GuidePageTexture.releaseUsedTextures();
    }

    /**
     * Sets a screen to return to when closing this guide.
     */
    public void setReturnToOnClose(@Nullable Screen screen) {
        this.returnToOnClose = screen;
    }

    public @Nullable Screen getReturnToOnClose() {
        return returnToOnClose;
    }

    @Override
    public LytRect getDocumentRect() {
        var margin = DOCUMENT_RECT_MARGIN;

        // The page title may need more space than the default margin provides
        var marginTop = Math.max(
                margin,
                5 + pageTitle.getBounds().height());

        return new LytRect(
                margin,
                marginTop,
                width - 2 * margin,
                height - margin - marginTop);
    }

    @Override
    protected void updateDocumentLayout() {
        super.updateDocumentLayout();

        // Update layout of page title, since it's used for the document rectangle
        updateTitleLayout();
    }

    @Override
    protected LytDocument getDocument() {
        return currentPage.document();
    }

    private void updateTitleLayout() {
        var context = new LayoutContext(new MinecraftFontMetrics());
        // Compute the fake layout to find out how high it would be
        int availableWidth = width;

        // Remove the document viewport margin
        availableWidth -= 2 * DOCUMENT_RECT_MARGIN;

        // Account for the navigation buttons on the right
        availableWidth -= GuideIconButton.WIDTH * 2 + 5;

        // Remove 2 * 5 as margin
        availableWidth -= 10;

        if (availableWidth < 0) {
            availableWidth = 0;
        }

        pageTitle.layout(context, 0, 0, availableWidth);
        var height = pageTitle.getBounds().height();

        // Now compute the real layout
        var documentRect = getDocumentRect();

        int titleY = (documentRect.y() - height) / 2;

        pageTitle.layout(context, documentRect.x() + 5, titleY, availableWidth);
    }

    public void scrollToAnchor(@Nullable String anchor) {
        pendingScrollToAnchor = anchor;
        if (anchor == null) {
            setDocumentScrollY(0);
        }
    }

    @Override
    public PageCollection getGuide() {
        return guide;
    }

    @Override
    public ResourceLocation getCurrentPageId() {
        return currentPage.id();
    }

    @Override
    public void onClose() {
        if (minecraft != null && minecraft.screen == this && this.returnToOnClose != null) {
            minecraft.setScreen(this.returnToOnClose);
            this.returnToOnClose = null;
            return;
        }
        super.onClose();
    }
}
