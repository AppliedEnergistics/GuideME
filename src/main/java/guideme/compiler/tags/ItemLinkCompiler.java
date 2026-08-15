package guideme.compiler.tags;

import guideme.compiler.PageCompiler;
import guideme.document.flow.LytFlowLink;
import guideme.document.flow.LytFlowParent;
import guideme.document.flow.LytFlowSpan;
import guideme.document.flow.LytTooltipSpan;
import guideme.document.interaction.ItemTooltip;
import guideme.indices.ItemIndex;
import guideme.libs.mdast.mdx.model.MdxJsxElementFields;
import java.util.Set;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.apache.commons.lang3.tuple.Pair;

public class ItemLinkCompiler extends FlowTagCompiler {
    @Override
    public Set<String> getTagNames() {
        return Set.of("ItemLink");
    }

    @Override
    public void compile(PageCompiler compiler, LytFlowParent parent, MdxJsxElementFields el) {
        var fallback = MdxAttrs.getString(compiler, parent, el, "fallback", null);
        Pair<Identifier, ItemStack> itemAndId;
        if (fallback != null) {
            itemAndId = MdxAttrs.getItemStackAndId(compiler, parent, el);
        } else {
            itemAndId = MdxAttrs.getRequiredItemStackAndId(compiler, parent, el);
        }
        if (itemAndId == null) {
            if (fallback != null) {
                var span = new LytFlowSpan();
                span.modifyStyle(style -> style.italic(true));
                span.appendText(fallback);
                parent.append(span);
            }
            return;
        }
        var id = itemAndId.getLeft();
        var stack = itemAndId.getRight();

        var linksTo = compiler.getIndex(ItemIndex.class).get(id);

        // We'll error out for item-links to our own mod because we expect them to have a page
        // while we don't have pages for Vanilla items or items from other mods.
        // But authors can opt-in or out of this behavior.
        boolean defaultOptional = !id.getNamespace().equals(compiler.getPageId().getNamespace());
        var optional = MdxAttrs.getBoolean(compiler, parent, el, "optional", defaultOptional);
        if (linksTo == null && !optional) {
            parent.append(compiler.createErrorFlowContent("No page found for item " + id, el));
            return;
        }

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
