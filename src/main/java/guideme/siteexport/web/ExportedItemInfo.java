package guideme.siteexport.web;

import org.jetbrains.annotations.ApiStatus;

/**
 * The data model for item information that was previously exported.
 */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
public interface ExportedItemInfo {
    String icon();

    String displayName();
}
