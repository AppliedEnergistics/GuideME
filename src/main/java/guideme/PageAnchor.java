package guideme;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Points to a guidebook page with an optional anchor within that page.
 *
 * @param pageId
 * @param anchor ID of an anchor in the page.
 */
public record PageAnchor(Identifier pageId, @Nullable String anchor) {
    public static PageAnchor page(Identifier pageId) {
        return new PageAnchor(pageId, null);
    }

    public static PageAnchor parse(String anchor) {
        int sep = anchor.indexOf('#');
        Identifier pageId = null;
        String fragment = null;
        if (sep != -1) {
            pageId = Identifier.parse(anchor.substring(0, sep));
            fragment = anchor.substring(sep + 1);
        } else {
            pageId = Identifier.parse(anchor);
        }
        return new PageAnchor(pageId, fragment);
    }

    @Override
    public String toString() {
        if (anchor != null) {
            return pageId.toString() + "#" + anchor;
        } else {
            return pageId.toString();
        }
    }
}
