package guideme.siteexport.web;

import org.jetbrains.annotations.ApiStatus;

/**
 * The data model for item information that was previously exported.
 */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
public interface ExportedItemInfo {
    /**
     * {@return the item id, i.e. {@code minecraft:stick}}
     */
    String id();

    /**
     * {@return the path of the exported item icon, which can be resolved using
     * {@link guideme.siteexport.WebRenderingContext#getAssetUrl(String)}}
     */
    String icon();

    String displayName();

    /**
     * {@return the serialized name of the items rarity, i.e. {@code common} or {@code epic}}
     */
    String rarity();
}
