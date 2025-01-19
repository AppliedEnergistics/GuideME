package guideme.compiler.tags;

import guideme.compiler.PageCompiler;
import guideme.document.flow.LytFlowLink;
import guideme.document.flow.LytFlowParent;
import guideme.document.flow.LytTooltipSpan;
import guideme.document.interaction.ItemTooltip;
import guideme.indices.ItemIndex;
import guideme.libs.mdast.mdx.model.MdxJsxElementFields;
import java.util.Set;

public class ItemLinkCompiler extends FlowTagCompiler {
    @Override
    public Set<String> getTagNames() {
        return Set.of("ItemLink");
    }

    @Override
    public void compile(PageCompiler compiler, LytFlowParent parent, MdxJsxElementFields el) {
        var itemAndId = MdxAttrs.getRequiredItemAndId(compiler, parent, el, "id");
        if (itemAndId == null) {
            return;
        }
        var id = itemAndId.getLeft();
        var item = itemAndId.getRight();

        var linksTo = compiler.getIndex(ItemIndex.class).get(id);
        // We'll error out for item-links to our own mod because we expect them to have a page
        // while we don't have pages for Vanilla items or items from other mods.
        if (linksTo == null && id.getNamespace().equals(compiler.getPageId().getNamespace())) {
            parent.append(compiler.createErrorFlowContent("No page found for item " + id, el));
            return;
        }

        var stack = item.getDefaultInstance();

        // If the item link is already on the page we're linking to, replace it with an underlined
        // text that has a tooltip.
        if (linksTo == null || linksTo.anchor() == null && compiler.getPageId().equals(linksTo.pageId())) {
            var span = new LytTooltipSpan();
            span.modifyStyle(style -> style.italic(true));
            span.appendComponent(stack.getHoverName());
            span.setTooltip(new ItemTooltip(stack));
            parent.append(span);
        } else {
            var link = new LytFlowLink();
            link.setClickCallback(screen -> {
                screen.navigateTo(linksTo);
            });
            link.appendComponent(stack.getHoverName());
            link.setTooltip(new ItemTooltip(stack));
            parent.append(link);
        }
    }

}
