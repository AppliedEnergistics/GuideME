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
import guideme.render.RenderContext;
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
    private static final int FULL_SCREEN_MARGIN = 20;

    private static final int CENTERED_HORIZONTAL_MARGIN = 10;

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

    private LytRect screenRect = LytRect.empty();

    private boolean fullScreen = false;
    private GuideNavBar navbar;

    private GuideScreen(Guide guide, PageAnchor anchor) {
        super(Component.literal("AE2 Guidebook"));
        this.guide = guide;

        this.pageTitle = new LytParagraph();
        this.pageTitle.setStyle(DefaultStyles.HEADING1);

        toolbar = new NavigationToolbar(guide);
        toolbar.setCloseCallback(this::onClose);

        navbar = new GuideNavBar(this);

        loadPageAndScrollTo(anchor);
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

        if (fullScreen) {
            screenRect = new LytRect(0, 0, width, height);
        } else {
            var screenWidth = Math.min(400, width);
            var left = (width - screenWidth) / 2;
            screenRect = new LytRect(left, 0, screenWidth, height);
        }

        updateDocumentLayout();

        navbar.setY(screenRect.y());
        navbar.setHeight(screenRect.height());
        addRenderableWidget(navbar);

        // If there is enough space, always expand the navbar
        if (screenRect.x() >= GuideNavBar.WIDTH_OPEN) {
            navbar.setPinned(true);
            navbar.setX(screenRect.x() - navbar.getWidth());
        } else {
            navbar.setPinned(false);
            navbar.setX(0);
        }

        toolbar.addToScreen(this::addRenderableWidget, 2, screenRect.right() - getMarginRight());

        updateDocumentLayout();
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
    public void scaledRender(GuiGraphics guiGraphics, RenderContext context, int mouseX, int mouseY,
            float partialTick) {
        renderBlurredBackground(partialTick);
        renderSkyStoneBackground(guiGraphics);

        var documentRect = getDocumentRect();
        context.fillRect(documentRect, new ConstantColor(0x80333333));

        renderDocument(context);

        var poseStack = guiGraphics.pose();
        poseStack.pushPose();
        poseStack.translate(0, 0, 200);

        renderTitle(documentRect, context);

        if (hasFooter()) {
            renderFooter(documentRect, context);
        }

        super.scaledRender(guiGraphics, context, mouseX, mouseY, partialTick);

        poseStack.popPose();

        renderDocumentTooltip(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderFooter(LytRect documentRect, RenderContext context) {
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

    private boolean hasFooter() {
        return getExternalSourceName() != null;
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

    private void renderTitle(LytRect documentRect, RenderContext context) {
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
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(0.25F, 0.25F, 0.25F, 0.9F);
        guiGraphics.blit(BACKGROUND_TEXTURE, screenRect.x(), screenRect.y(), 0, 0.0F, 0.0F, screenRect.width(), screenRect.height(), 32, 32);
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

    void loadPageAndScrollTo(PageAnchor anchor) {
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
        return new LytRect(
                screenRect.x() + getMarginLeft(),
                getMarginTop(),
                screenRect.width() - getMarginLeft() - getMarginRight(),
                screenRect.height() - getMarginBottom() - getMarginTop());
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
        // Account for the navigation buttons on the right
        var availableWidth = toolbar.getLeft() - DOCUMENT_RECT_MARGIN - 5;

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

    private int getMarginRight() {
        return fullScreen ? FULL_SCREEN_MARGIN : CENTERED_HORIZONTAL_MARGIN;
    }

    private int getMarginLeft() {
        return fullScreen ? FULL_SCREEN_MARGIN : CENTERED_HORIZONTAL_MARGIN;
    }

    private int getMarginTop() {
        // The page title may need more space than the default margin provides
        return Math.max(20, 5 + pageTitle.getBounds().height());
    }

    private int getMarginBottom() {
        return hasFooter() ? FULL_SCREEN_MARGIN : 0;
    }
}
