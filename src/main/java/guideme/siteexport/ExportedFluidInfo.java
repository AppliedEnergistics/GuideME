package guideme.siteexport;

import org.jetbrains.annotations.ApiStatus;

/**
 * The data model for fluid information that was previously exported.
 */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
public interface ExportedFluidInfo {
    String icon();

    String displayName();
}
