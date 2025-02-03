package guideme.internal.screen;

import guideme.Guide;
import guideme.Guides;
import guideme.PageAnchor;
import guideme.PageCollection;
import guideme.color.ConstantColor;
import guideme.color.SymbolicColor;
import guideme.compiler.ParsedGuidePage;
import guideme.document.DefaultStyles;
import guideme.document.LytRect;
import guideme.document.block.AlignItems;
import guideme.document.block.LytDocument;
import guideme.document.block.LytHBox;
import guideme.document.block.LytParagraph;
import guideme.document.flow.LytFlowBreak;
import guideme.document.flow.LytFlowLink;
import guideme.internal.GuideME;
import guideme.internal.GuideMEClient;
import guideme.internal.GuidebookText;
import guideme.internal.search.GuideSearch;
import guideme.internal.util.Blitter;
import guideme.internal.util.NavigationUtil;
import guideme.render.RenderContext;
import guideme.scene.LytItemImage;
import guideme.style.BorderStyle;
import guideme.ui.GuideUiHost;
import guideme.ui.UiPoint;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public class GuideSearchScreen extends DocumentScreen {
    /**
     * This ID refers to this screen as a built-in page.
     */
    public static final ResourceLocation PAGE_ID = GuideME.makeId("search");

    // 20 virtual px margin around the document
    private static final ResourceLocation BACKGROUND_TEXTURE = GuideME.makeId("textures/guide/background.png");

    private final EditBox searchField;

    private final Guide guide;

    private final NavigationToolbar toolbar;

    @Nullable
    private Screen returnToOnClose;

    private final LytDocument searchResultsDoc = new LytDocument();

    private final List<GuideSearch.SearchResult> searchResults = new ArrayList<>();

    GuideSearchScreen(Guide guide) {
        super(Component.literal("AE2 Guidebook Search"));
        this.guide = guide;
        this.toolbar = new NavigationToolbar(guide);
        this.toolbar.setCloseCallback(this::onClose);

        // Trigger indexing of this guide
        GuideMEClient.instance().getSearch().index(guide);

        searchField = new EditBox(
                Minecraft.getInstance().font,
                getMarginLeft() + 16,
                6,
                0,
                getMarginTop(),
                GuidebookText.Search.text());
        searchField.setBordered(false);
        searchField.setHint(
                GuidebookText.Search.text().withStyle(ChatFormatting.DARK_GRAY).withStyle(ChatFormatting.ITALIC));
        searchField.setResponder(this::search);
        setInitialFocus(searchField);
    }

    public static GuideSearchScreen open(Guide guide, @Nullable String anchor) {
        var history = GlobalInMemoryHistory.get(guide);
        history.push(new PageAnchor(PAGE_ID, anchor));

        var screen = new GuideSearchScreen(guide);
        if (anchor != null) {
            screen.searchField.setValue(anchor);
        }
        return screen;
    }

    @Override
    protected LytDocument getDocument() {
        return searchResultsDoc;
    }

    @Override
    public void navigateTo(ResourceLocation pageId) {
    }

    @Override
    public void navigateTo(PageAnchor anchor) {
    }

    @Override
    protected void init() {
        super.init();

        addRenderableWidget(searchField);

        int toolbarMarginRight = isFullWidthLayout() ? getMarginRight() : 0;
        toolbar.addToScreen(this::addRenderableWidget, 2, screenRect.right() - toolbarMarginRight);

        searchField.setX(screenRect.x() + getMarginLeft() + 16);
        searchField.setWidth(toolbar.getLeft() - searchField.getX());
        searchField.setCursorPosition(searchField.getCursorPosition());
    }

    private void search(String query) {
        // Update history such that forward/backwards will remember the current search query
        GlobalInMemoryHistory.get(guide).push(makeSearchAnchor());

        var search = GuideMEClient.instance().getSearch();
        searchResults.clear();
        searchResults.addAll(search.searchGuide(query, guide));

        searchResultsDoc.clearContent();

        for (var searchResult : searchResults) {
            var searchResultItem = new LytHBox();
            searchResultItem.setFullWidth(true);
            searchResultItem.setGap(5);
            searchResultItem.setAlignItems(AlignItems.CENTER);

            var guide = Guides.getById(searchResult.guideId());
            if (guide == null) {
                continue;
            }

            var page = guide.getParsedPage(searchResult.pageId());
            if (page == null) {
                continue;
            }
            var icon = NavigationUtil.createNavigationIcon(page);

            var image = new LytItemImage();
            if (!icon.isEmpty()) {
                image.setItem(icon);
            }
            searchResultItem.append(image);

            var summary = new LytParagraph();
            var documentLink = buildLinkToSearchResult(searchResult, guide, page);
            summary.append(documentLink);
            summary.append(new LytFlowBreak());
            summary.append(searchResult.text());
            summary.setPaddingTop(2);
            summary.setPaddingBottom(2);
            searchResultItem.append(summary);

            searchResultItem.setBorderBottom(new BorderStyle(SymbolicColor.TABLE_BORDER, 1));

            searchResultsDoc.append(searchResultItem);
        }

        updateDocumentLayout();
    }

    private LytFlowLink buildLinkToSearchResult(GuideSearch.SearchResult searchResult, Guide guide,
            ParsedGuidePage page) {
        var documentLink = new LytFlowLink();
        documentLink.appendText(searchResult.pageTitle());
        documentLink.setClickCallback(ignored -> {
            // Append this search page to the guides history
            var history = GlobalInMemoryHistory.get(guide);
            history.push(makeSearchAnchor());

            // Reuse the same guide screen if it's within the same guide
            if (returnToOnClose instanceof GuideUiHost guideHost && guideHost.getGuide() == guide) {
                onClose();
                guideHost.navigateTo(page.getId());
            } else {
                returnToOnClose = GuideScreen.openNew(guide, PageAnchor.page(page.getId()));
                onClose();
            }
        });
        return documentLink;
    }

    @Override
    protected void scaledRender(GuiGraphics guiGraphics, RenderContext context, int mouseX, int mouseY,
            float partialTick) {
        renderBlurredBackground(partialTick);

        context.fillTexturedRect(screenRect, BACKGROUND_TEXTURE, SymbolicColor.GUIDE_SCREEN_BACKGROUND);

        Blitter.texture(GuideME.makeId("textures/guide/buttons.png"), 64, 64)
                .src(GuideIconButton.Role.SEARCH.iconSrcX, GuideIconButton.Role.SEARCH.iconSrcY, 16, 16)
                .dest(screenRect.x() + getMarginLeft(), 2, 16, 16)
                .colorArgb(context.resolveColor(SymbolicColor.ICON_BUTTON_NORMAL))
                .blit(guiGraphics);

        var documentRect = getDocumentRect();
        context.fillRect(documentRect, new ConstantColor(0x80333333));

        if (searchField.getValue().isEmpty()) {
            context.renderTextCenteredIn(
                    GuidebookText.SearchNoQuery.text().getString(),
                    DefaultStyles.BODY_TEXT.mergeWith(DefaultStyles.BASE_STYLE),
                    documentRect);
        } else if (searchResults.isEmpty()) {
            context.renderTextCenteredIn(
                    GuidebookText.SearchNoResults.text().getString(),
                    DefaultStyles.BODY_TEXT.mergeWith(DefaultStyles.BASE_STYLE),
                    documentRect);
        } else {
            renderDocument(context);
        }

        var poseStack = guiGraphics.pose();
        poseStack.pushPose();
        poseStack.translate(0, 0, 200);

        renderTitle(documentRect, context);

        super.scaledRender(guiGraphics, context, mouseX, mouseY, partialTick);

        poseStack.popPose();

        renderDocumentTooltip(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Stub this out otherwise vanilla renders a background on top of our content
    }

    private void renderTitle(LytRect documentRect, RenderContext context) {
        var separatorRect = new LytRect(
                documentRect.x(),
                documentRect.y() - 1,
                documentRect.width(),
                1);
        if (!isFullWidthLayout()) {
            separatorRect = separatorRect.withWidth(screenRect.width());
        }
        context.fillRect(separatorRect, SymbolicColor.HEADER1_SEPARATOR);
    }

    private PageAnchor makeSearchAnchor() {
        if (searchField.getValue().isBlank()) {
            return PageAnchor.page(PAGE_ID);
        } else {
            return new PageAnchor(PAGE_ID, searchField.getValue());
        }
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

    @Override
    public void onClose() {
        if (minecraft != null && minecraft.screen == this && this.returnToOnClose != null) {
            minecraft.setScreen(this.returnToOnClose);
            this.returnToOnClose = null;
            return;
        }
        super.onClose();
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
    public PageCollection getGuide() {
        return guide;
    }

    @Override
    public void reloadPage() {
    }
}
