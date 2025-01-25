package guideme.internal.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import guideme.Guide;
import guideme.Guides;
import guideme.PageAnchor;
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
import guideme.render.SimpleRenderContext;
import guideme.scene.LytItemImage;
import guideme.style.BorderStyle;
import guideme.ui.GuideUiHost;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GuideSearchScreen extends DocumentScreen {
    private static final Logger LOG = LoggerFactory.getLogger(GuideSearchScreen.class);

    // 20 virtual px margin around the document
    public static final int DOCUMENT_RECT_MARGIN = 20;
    private static final ResourceLocation BACKGROUND_TEXTURE = GuideME.makeId("textures/guide/background.png");

    private final EditBox searchField;

    private final Guide guide;

    @Nullable
    private Screen returnToOnClose;

    private final LytDocument searchResultsDoc = new LytDocument();

    private final List<GuideSearch.SearchResult> searchResults = new ArrayList<>();

    GuideSearchScreen(Guide guide) {
        super(Component.literal("AE2 Guidebook Search"));
        this.guide = guide;

        // Trigger indexing of this guide
        GuideMEClient.instance().getSearch().index(guide);

        searchField = new EditBox(
                Minecraft.getInstance().font,
                DOCUMENT_RECT_MARGIN + 16,
                6,
                0,
                DOCUMENT_RECT_MARGIN,
                GuidebookText.Search.text());
        searchField.setBordered(false);
        searchField.setHint(GuidebookText.Search.text().withStyle(ChatFormatting.DARK_GRAY));
        searchField.setResponder(this::search);
        setInitialFocus(searchField);
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

        // Center them vertically in the margin
        var closeButton = new GuideIconButton(
                width - DOCUMENT_RECT_MARGIN - GuideIconButton.WIDTH,
                2,
                GuideIconButton.Role.CLOSE,
                this::onClose);
        addRenderableWidget(closeButton);

        searchField.setWidth(closeButton.getX() - DOCUMENT_RECT_MARGIN - 16);
    }

    private void search(String query) {
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
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderSkyStoneBackground(guiGraphics);

        var context = new SimpleRenderContext(new LytRect(0, 0, width, height), guiGraphics);
        Blitter.texture(GuideME.makeId("textures/guide/buttons.png"), 64, 64)
                .src(GuideIconButton.Role.SEARCH.iconSrcX, GuideIconButton.Role.SEARCH.iconSrcY, 16, 16)
                .dest(DOCUMENT_RECT_MARGIN, 2, 16, 16)
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
            renderDocument(guiGraphics);
        }

        var poseStack = guiGraphics.pose();
        poseStack.pushPose();
        poseStack.translate(0, 0, 200);

        renderTitle(documentRect, context);

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        poseStack.popPose();

        renderDocumentTooltip(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public LytRect getDocumentRect() {
        return new LytRect(
                DOCUMENT_RECT_MARGIN,
                DOCUMENT_RECT_MARGIN,
                width - 2 * DOCUMENT_RECT_MARGIN,
                height - 2 * DOCUMENT_RECT_MARGIN);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Stub this out otherwise vanilla renders a background on top of our content
    }

    private void renderTitle(LytRect documentRect, SimpleRenderContext context) {
        var buffers = context.beginBatch();
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

    /**
     * Sets a screen to return to when closing this guide.
     */
    public void setReturnToOnClose(@Nullable Screen screen) {
        this.returnToOnClose = screen;
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
