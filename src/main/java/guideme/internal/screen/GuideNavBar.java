package guideme.internal.screen;

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
import guideme.render.SimpleRenderContext;
import guideme.ui.GuideUiHost;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec2;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

public class GuideNavBar extends AbstractWidget {
    public static final int WIDTH_OPEN = 150;
    private static final int WIDTH_CLOSED = 15;
    private static final int CHILD_ROW_INDENT = 10;
    private static final int PARENT_ROW_INDENT = 7;
    private static boolean neverInteracted = true;

    private final Transition widthTransition = new Transition(
            WIDTH_CLOSED,
            WIDTH_OPEN,
            0.1,
            () -> width,
            value -> width = (int) Math.round(value));

    private NavigationTree navTree;

    private final List<Row> rows = new ArrayList<>();

    private final GuideUiHost screen;

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
    public void onClick(double mouseX, double mouseY, int button) {
        if (state != State.OPENING && state != State.OPEN) {
            return;
        }

        var row = pickRow(mouseX, mouseY);
        if (row != null) {
            row.expanded = !row.expanded;
            updateLayout();

            var handler = Minecraft.getInstance().getSoundManager();
            handler.play(SimpleSoundInstance.forUI(GuideMEClient.GUIDE_CLICK_EVENT, 1.0F));
            if (row.node.pageId() != null) {
                screen.navigateTo(row.node.pageId());
            }
        }
    }

    @Override
    public void playDownSound(SoundManager handler) {
        // Mute default sound
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
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
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        updateLayout();

        widthTransition.update();

        var renderContext = new SimpleRenderContext(graphics);
        var viewport = renderContext.viewport();

        double currentTime = GLFW.glfwGetTime();

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

        // Check if we need to re-layout
        var currentNavTree = screen.getGuide().getNavigationTree();
        if (currentNavTree != this.navTree) {
            recreateRows();
        }

        if (this.rows.isEmpty()) {
            return; // do not render the navbar if there are no nodes.
        }

        if (state == State.CLOSED) {
            renderContext.fillGradientHorizontal(getX(), getY(), width, height, SymbolicColor.NAVBAR_BG_TOP,
                    SymbolicColor.NAVBAR_BG_BOTTOM);

            var p1 = new Vec2(width - WIDTH_CLOSED + WIDTH_CLOSED - 4, height / 2f);
            var p2 = new Vec2(width - WIDTH_CLOSED + 4, height / 2f - 5);
            var p3 = new Vec2(width - WIDTH_CLOSED + 4, height / 2f + 5);

            renderContext.fillTriangle(p1, p2, p3, SymbolicColor.NAVBAR_EXPAND_ARROW);
        } else if (!isPinned()) {
            renderContext.fillGradientVertical(getX(), getY(), width, height, SymbolicColor.NAVBAR_BG_TOP,
                    SymbolicColor.NAVBAR_BG_BOTTOM);
        }

        if (state != State.CLOSED) {
            graphics.enableScissor(getX(), getY(), getX() + width, getY() + height);

            var pose = graphics.pose();
            pose.pushPose();
            pose.translate(getX(), getY() - scrollOffset, 0);

            // Draw a backdrop on the hovered row before starting batch rendering
            var hoveredRow = pickRow(mouseX, mouseY);
            if (hoveredRow != null) {
                renderContext.fillRect(hoveredRow.getBounds(), SymbolicColor.NAVBAR_ROW_HOVER);
            }

            // Render Text in batch
            var buffers = renderContext.beginBatch();
            for (var row : rows) {
                if (!row.isVisible(viewport)) {
                    continue; // Cull this row, it's not in the viewport
                }
                row.paragraph.renderBatch(renderContext, buffers);
            }
            renderContext.endBatch(buffers);

            // Render decorations, icons, etc.
            for (var row : rows) {
                if (!row.isVisible(viewport)) {
                    continue; // Cull this row, it's not in the viewport
                }
                if (row.hasChildren) {
                    float x = row.getBounds().x();
                    x += 5;
                    float y = row.getBounds().y();
                    y += 2f;
                    Vec2 p1, p2, p3;
                    if (row.expanded) {
                        // Triangle points down
                        p1 = new Vec2(x + 5, y);
                        p2 = new Vec2(x, y);
                        p3 = new Vec2(x + 2.5f, y + 5);
                    } else {
                        // Triangle points right
                        p1 = new Vec2(x + 5, y + 2.5f);
                        p2 = new Vec2(x, y);
                        p3 = new Vec2(x, y + 5);
                    }

                    var color = row == hoveredRow ? SymbolicColor.LINK : SymbolicColor.BODY_TEXT;
                    renderContext.fillTriangle(p1, p2, p3, color);
                }

                var icon = row.node.icon();
                if (!icon.isEmpty()) {
                    renderContext.renderItem(icon, row.paragraph.getBounds().x() - 9, row.paragraph.getBounds().y(), 1,
                            8,
                            8);
                }
            }

            pose.popPose();

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

        for (var rootNode : this.navTree.getRootNodes()) {
            var row = new Row(rootNode, null);
            rows.add(row);

            // Add second level
            for (var child : row.node.children()) {
                row.hasChildren = true;
                var childRow = new Row(child, row);
                rows.add(childRow);
            }
        }

        updateLayout();
    }

    private void updateLayout() {
        var context = new LayoutContext(new MinecraftFontMetrics());

        var currentY = 0;
        for (var row : this.rows) {
            if (!row.isVisible()) {
                continue;
            }

            // Child-Rows should be indented and their parents
            // need an indent too for the expand/collapse indicator
            int indent;
            if (row.hasChildren) {
                indent = PARENT_ROW_INDENT;
            } else if (row.parent != null) {
                indent = CHILD_ROW_INDENT;
            } else {
                indent = 0;
            }

            if (!row.node.icon().isEmpty()) {
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
        private final Row parent;
        private boolean hasChildren;
        public int top;
        public int bottom;

        public Row(NavigationNode node, Row parent) {
            this.node = node;
            this.parent = parent;

            span = new LytFlowSpan();
            span.appendText(node.title());
            span.modifyHoverStyle(style -> style.color(SymbolicColor.LINK));
            this.paragraph.setPaddingLeft(5);
            this.paragraph.append(span);
        }

        public LytRect getBounds() {
            return new LytRect(0, top, width, bottom - top);
        }

        public boolean contains(float x, float y) {
            return x >= 0 && x < width && y >= top && y < bottom;
        }

        public boolean isVisible() {
            return parent == null || parent.expanded;
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
