package guideme.indices;

import guideme.PageAnchor;
import guideme.compiler.IdUtils;
import guideme.compiler.ParsedGuidePage;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.IdentifierException;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An index of Minecraft items to the main guidebook page describing it.
 * <p/>
 * This index is installed by default on all {@linkplain guideme.Guide guides}.
 */
public class ItemIndex extends UniqueIndex<Identifier, PageAnchor> {
    private static final Logger LOG = LoggerFactory.getLogger(ItemIndex.class);

    public ItemIndex() {
        super(
                "Item Index",
                ItemIndex::getItemAnchors,
                (writer, value) -> writer.value(value.toString()),
                (writer, value) -> writer.value(value.toString()));
    }

    private static List<Pair<Identifier, PageAnchor>> getItemAnchors(ParsedGuidePage page) {
        var itemIdsNode = page.getFrontmatter().additionalProperties().get("item_ids");
        if (itemIdsNode == null) {
            return List.of();
        }

        if (!(itemIdsNode instanceof List<?> itemIdList)) {
            LOG.warn("Page {} contains malformed item_ids frontmatter", page.getId());
            return List.of();
        }

        var itemAnchors = new ArrayList<Pair<Identifier, PageAnchor>>();

        for (var listEntry : itemIdList) {
            if (listEntry instanceof String itemIdStr) {
                Identifier itemId;
                try {
                    itemId = IdUtils.resolveId(itemIdStr, page.getId().getNamespace());
                } catch (IdentifierException e) {
                    LOG.warn("Page {} contains a malformed item_ids frontmatter entry: {}", page.getId(),
                            listEntry);
                    continue;
                }

                if (BuiltInRegistries.ITEM.containsKey(itemId)) {
                    // add a link to the top of the page
                    itemAnchors.add(Pair.of(
                            itemId, new PageAnchor(page.getId(), null)));
                } else {
                    LOG.warn("Page {} references an unknown item {} in its item_ids frontmatter",
                            page.getId(), itemId);
                }
            } else {
                LOG.warn("Page {} contains a malformed item_ids frontmatter entry: {}", page.getId(), listEntry);
            }
        }

        return itemAnchors;
    }
}
