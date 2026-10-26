package guideme.internal.screen;

import com.mojang.blaze3d.Blaze3D;
import guideme.color.SymbolicColor;
import guideme.document.LytPoint;
import guideme.document.LytRect;
import guideme.document.block.LytParagraph;
import guideme.document.flow.LytFlowSpan;
import guideme.internal.GuideMEClient;
import guideme.internal.util.Transition;
import guideme.layout.LayoutContext;
import guideme.layout.MinecraftFontMetrics;
import guideme.navigation.NavigationNode;
import guideme.navigation.NavigationTree;
import guideme.render.GuiAssets;
import guideme.render.GuiSprite;
import guideme.render.SimpleRenderContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2f;

public class GuideNavBar extends AbstractWidget {
    public static final int WIDTH_OPEN = 150;
    public static final int WIDTH_CLOSED = 15;
    private static final int ROW_INDENT = 10;
    private static final int PARENT_ROW_INDENT = 8;
    /**
     * The number of navigation tree levels shown by default. Pages can change how many levels are shown below them with
     * {@link NavigationNode#maxDepth()}. Pages that are not listed remain reachable through links.
     */
    public static final int DEFAULT_MAX_DEPTH = 2;
    /**
     * White expand/collapse chevrons, tinted with the text color when drawn.
     */
    static final GuiSprite CHEVRON_COLLAPSED = GuiAssets.sprite("nav_chevron_collapsed");
    static final GuiSprite CHEVRON_EXPANDED = GuiAssets.sprite("nav_chevron_expanded");
    private static boolean neverInteracted = true;

    private final Transition widthTransition = new Transition(
            WIDTH_CLOSED,
            WIDTH_OPEN,
            0.1,
            () -> width,
            value -> width = (int) Math.round(value));

    private NavigationTree navTree;

    private final List<Row> rows = new ArrayList<>();

    private final GuideScreen screen;

    /**
     * The page whose row was last revealed in the navigation tree. Used to only auto-expand when the page changes, so
     * that the user can still collapse the ancestors of the current page.
     */
    @Nullable
    private Identifier revealedPageId;

    private int scrollOffset;

    private State state = State.CLOSED;
    private boolean pinned;

    public GuideNavBar(GuideScreen screen) {
        super(0, 0, WIDTH_CLOSED, screen.height, Component.literal("Navigation Tree"));
        this.screen = screen;
    }

    @Override
    public void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        if (state != State.OPENING && state != State.OPEN) {
            return;
        }

        var vpPos = getViewportPoint(event.x(), event.y());
        var row = pickRow(event.x(), event.y());
        if (row == null || vpPos == null) {
            return;
        }

        var handler = Minecraft.getInstance().getSoundManager();
        handler.play(SimpleSoundInstance.forUI(GuideMEClient.GUIDE_CLICK_EVENT, 1.0F));

        if (row.isOnExpandIndicator(vpPos.x()) || row.node.pageId() == null) {
            // The indicator only toggles, so the tree can be browsed without navigating
            row.expanded = !row.expanded;
            updateLayout();
        } else {
            // Clicking the label navigates, and opens the children of the target page
            if (row.hasChildren && !row.expanded) {
                row.expanded = true;
                updateLayout();
            }
            screen.navigateTo(row.node.pageId());
        }
    }

    @Override
    public void playDownSound(SoundManager handler) {
        // Mute default sound
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (state != State.OPENING && state != State.OPEN) {
            return false;
        }

        setScrollOffset((int) Math.round(scrollOffset - dragY));
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (state != State.OPENING && state != State.OPEN) {
            return false;
        }

        setScrollOffset((int) Math.round(scrollOffset - deltaY * 20));
        return true;
    }

    private void setScrollOffset(int offset) {
        var maxScrollOffset = 0;
        var visibleRows = rows.stream().filter(Row::isVisible).toList();
        if (!visibleRows.isEmpty()) {
            var contentHeight = visibleRows.getLast().bottom - visibleRows.getFirst().top;
            maxScrollOffset = Math.max(0, contentHeight - height);
        }
        scrollOffset = Mth.clamp(offset, 0, maxScrollOffset);
    }

    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // Check if we need to re-layout
        var currentNavTree = screen.getGuide().getNavigationTree();
        if (currentNavTree != this.navTree) {
            recreateRows();
        }

        var currentPageId = screen.getCurrentPageId();
        if (!Objects.equals(currentPageId, revealedPageId)) {
            revealedPageId = currentPageId;
            revealPage(currentPageId);
        }

        // stop reacting to mouse events
        active = !this.rows.isEmpty();

        if (this.rows.isEmpty()) {
            return; // do not render the navbar if there are no nodes.
        }

        widthTransition.update();

        if (state != State.CLOSED) {
            updateLayout();
        }

        var renderContext = new SimpleRenderContext(graphics);

        double currentTime = Blaze3D.getTime();

        boolean containsMouse = (mouseX >= getX() && mouseY >= getY() && mouseX < getX() + width
                && mouseY <= getY() + height);
        switch (state) {
            case CLOSED -> {
                if (containsMouse) {
                    state = State.OPENING;
                    widthTransition.set(WIDTH_OPEN);
                    neverInteracted = false;
                } else if (neverInteracted) {
                    // Show animation on 6 second interval
                    var ot = Math.abs((currentTime % 6) / 3 - 1);
                    // only play the animation for 6/10*2=1.2 seconds of it
                    if (ot >= 0.9) {
                        var t = (ot - 0.9) * 10;
                        var f = easeOutBounce(t);
                        width = (int) Math.round(WIDTH_CLOSED + f * (WIDTH_OPEN - WIDTH_CLOSED) * 0.075);
                    } else {
                        width = WIDTH_CLOSED;
                    }
                }
            }
            case OPENING -> {
                if (!containsMouse) {
                    state = State.CLOSING;
                    widthTransition.set(WIDTH_CLOSED);
                } else if (width >= WIDTH_OPEN) {
                    width = WIDTH_OPEN;
                    state = State.OPEN;
                }
            }
            case OPEN -> {
                if (!containsMouse && !pinned) {
                    state = State.CLOSING;
                    widthTransition.set(WIDTH_CLOSED);
                }
            }
            case CLOSING -> {
                if (containsMouse) {
                    state = State.OPENING;
                    widthTransition.set(WIDTH_OPEN);
                } else if (width <= WIDTH_CLOSED) {
                    width = WIDTH_CLOSED;
                    state = State.CLOSED;
                }
            }
        }

        updateMousePos(mouseX, mouseY);

        if (state == State.CLOSED) {
            renderContext.fillGradientHorizontal(getX(), getY(), width, height, SymbolicColor.NAVBAR_BG_TOP,
                    SymbolicColor.NAVBAR_BG_BOTTOM);

            var p1 = new Vector2f(width - WIDTH_CLOSED + WIDTH_CLOSED - 4, height / 2f);
            var p2 = new Vector2f(width - WIDTH_CLOSED + 4, height / 2f - 5);
            var p3 = new Vector2f(width - WIDTH_CLOSED + 4, height / 2f + 5);

            renderContext.fillTriangle(p1, p2, p3, SymbolicColor.NAVBAR_EXPAND_ARROW);
        } else if (!isPinned()) {
            renderContext.fillGradientVertical(getX(), getY(), width, height, SymbolicColor.NAVBAR_BG_TOP,
                    SymbolicColor.NAVBAR_BG_BOTTOM);
        }

        if (state != State.CLOSED) {
            graphics.enableScissor(getX(), getY(), getX() + width, getY() + height);

            var pose = graphics.pose();
            pose.pushMatrix();
            pose.translate(getX(), getY() - scrollOffset);

            var viewport = renderContext.viewport();

            // Draw a backdrop on the hovered row before starting batch rendering
            var hoveredRow = pickRow(mouseX, mouseY);
            if (hoveredRow != null) {
                renderContext.fillRect(hoveredRow.getBounds(), SymbolicColor.NAVBAR_ROW_HOVER);
            }

            // Highlight the row of the current page on top of the hover backdrop
            var currentRow = findRow(currentPageId);
            if (currentRow != null && currentRow.isVisible()) {
                renderContext.fillRect(currentRow.getBounds(), SymbolicColor.NAVBAR_ROW_CURRENT);
            }

            // Render decorations, icons, etc.
            for (var row : rows) {
                if (!row.isVisible(viewport)) {
                    continue; // Cull this row, it's not in the viewport
                }

                row.paragraph.render(renderContext);

                if (row.hasChildren) {
                    var color = row == hoveredRow ? SymbolicColor.LINK : SymbolicColor.BODY_TEXT;
                    var bounds = row.getBounds();
                    if (row.expanded) {
                        // Both chevrons share the same center column, leaving a gap to the icon
                        renderContext.drawIcon(bounds.x() + 3, bounds.y() + 2, CHEVRON_EXPANDED, color);
                    } else {
                        renderContext.drawIcon(bounds.x() + 5, bounds.y(), CHEVRON_COLLAPSED, color);
                    }
                }

                if (row.node.iconFactory() != null) {
                    var icon = row.node.iconFactory().get();
                    renderContext.renderItem(icon, row.paragraph.getBounds().x() - 9,
                            row.paragraph.getBounds().y(), 1,
                            8,
                            8);
                }
            }

            pose.popMatrix();

            graphics.disableScissor();
        }
    }

    public static double easeOutBounce(double x) {
        return Math.sqrt(1 - Math.pow(x - 1, 2));
    }

    @Nullable
    private Row pickRow(double x, double y) {
        var vpPos = getViewportPoint(x, y);
        if (vpPos != null) {
            for (var row : rows) {
                if (row.isVisible() && vpPos.y() >= row.top && vpPos.y() < row.bottom) {
                    return row;
                }
            }
        }
        return null;
    }

    private void updateMousePos(double x, double y) {
        var vpPos = getViewportPoint(x, y);
        for (Row row : rows) {
            if (!row.isVisible()) {
                continue;
            }

            if (vpPos != null && row.contains(vpPos.x(), vpPos.y())) {
                row.paragraph.onMouseEnter(row.span);
            } else {
                row.paragraph.onMouseLeave();
            }
        }
    }

    private void recreateRows() {
        this.navTree = screen.getGuide().getNavigationTree();
        // Save Freeze expanded / scroll position
        this.rows.clear();
        this.revealedPageId = null;

        for (var rootNode : this.navTree.getRootNodes()) {
            addRow(rootNode, null);
        }

        updateLayout();
    }

    private void addRow(NavigationNode node, @Nullable Row parent) {
        var row = new Row(node, parent);
        rows.add(row);
        if (row.levelsBelow > 0) {
            for (var child : node.children()) {
                addRow(child, row);
            }
        }
    }

    @Nullable
    private Row findRow(@Nullable Identifier pageId) {
        if (pageId == null) {
            return null;
        }
        for (var row : rows) {
            if (pageId.equals(row.node.pageId())) {
                return row;
            }
        }
        return null;
    }

    /**
     * Expands all ancestors of the given page's row and scrolls it into view.
     */
    private void revealPage(@Nullable Identifier pageId) {
        var row = findRow(pageId);
        if (row == null) {
            return;
        }

        for (var ancestor = row.parent; ancestor != null; ancestor = ancestor.parent) {
            ancestor.expanded = true;
        }
        updateLayout();

        if (row.top < scrollOffset || row.bottom > scrollOffset + height) {
            setScrollOffset(row.top - height / 2);
        }
    }

    private void updateLayout() {
        var context = new LayoutContext(new MinecraftFontMetrics());

        var currentY = 0;
        for (var row : this.rows) {
            if (!row.isVisible()) {
                continue;
            }

            // Reserve the expand/collapse indicator column for every row so sibling icons and text align.
            int indent = row.depth * ROW_INDENT + PARENT_ROW_INDENT;

            if (row.node.iconFactory() != null) {
                indent += 8; // Indent for icon;
            }

            var x = indent;
            var width = WIDTH_OPEN - indent;
            var bounds = row.paragraph.layout(context, x, currentY, width);
            row.top = bounds.y();
            row.bottom = bounds.bottom();
            currentY = bounds.bottom();
        }
    }

    /**
     * Gets a point in the coordinate space of the scrollable viewport.
     */
    @Nullable
    private LytPoint getViewportPoint(double screenX, double screenY) {
        if (state != State.OPENING && state != State.OPEN) {
            return null;
        }

        if (screenX >= getX() && screenX < getX() + width
                && screenY >= getY() && screenY < getY() + height) {
            var vpX = (int) Math.round(screenX - getX());
            var vpY = (int) Math.round(screenY + scrollOffset - getY());
            return new LytPoint(vpX, vpY);
        }

        return null; // Outside the viewport
    }

    public void setPinned(boolean pinned) {
        this.pinned = pinned;
        if (pinned) {
            this.state = State.OPEN;
            this.setWidth(WIDTH_OPEN);
            this.widthTransition.set(WIDTH_OPEN);
        }
    }

    public boolean isPinned() {
        return pinned;
    }

    private class Row {
        private final NavigationNode node;
        private final LytParagraph paragraph = new LytParagraph();
        public final LytFlowSpan span;
        private boolean expanded;
        @Nullable
        private final Row parent;
        private final int depth;
        /**
         * How many levels of descendants of this row are shown.
         */
        private final int levelsBelow;
        private final boolean hasChildren;
        public int top;
        public int bottom;

        public Row(NavigationNode node, @Nullable Row parent) {
            this.node = node;
            this.parent = parent;
            this.depth = parent == null ? 0 : parent.depth + 1;
            if (node.maxDepth() != null) {
                this.levelsBelow = node.maxDepth();
            } else {
                this.levelsBelow = parent == null ? DEFAULT_MAX_DEPTH - 1 : parent.levelsBelow - 1;
            }
            this.hasChildren = levelsBelow > 0 && !node.children().isEmpty();

            span = new LytFlowSpan();
            span.appendText(node.title());
            span.modifyHoverStyle(style -> style.color(SymbolicColor.LINK));
            this.paragraph.setPaddingLeft(5);
            this.paragraph.append(span);
        }

        public LytRect getBounds() {
            int x = depth * ROW_INDENT;
            return new LytRect(x, top, Math.max(0, width - x), bottom - top);
        }

        public boolean contains(float x, float y) {
            return x >= 0 && x < width && y >= top && y < bottom;
        }

        public boolean isOnExpandIndicator(float x) {
            return hasChildren && x < depth * ROW_INDENT + PARENT_ROW_INDENT + 4;
        }

        public boolean isVisible() {
            for (var ancestor = parent; ancestor != null; ancestor = ancestor.parent) {
                if (!ancestor.expanded) {
                    return false;
                }
            }
            return true;
        }

        // Is this row visible in the given viewport?
        public boolean isVisible(LytRect viewport) {
            return isVisible() && bottom > viewport.y() && top < viewport.bottom();
        }
    }

    enum State {
        CLOSED,
        OPENING,
        OPEN,
        CLOSING
    }
}
