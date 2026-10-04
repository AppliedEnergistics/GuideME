package guideme.siteexport.web;

import org.jetbrains.annotations.ApiStatus;

/**
 * The data model for fluid information that was previously exported.
 */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
public interface ExportedFluidInfo {
    /**
     * {@return the fluid id, i.e. {@code minecraft:water}}
     */
    String id();

    /**
     * {@return the path of the exported fluid icon, which can be resolved using
     * {@link guideme.siteexport.WebRenderingContext#getAssetUrl(String)}}
     */
    String icon();

    String displayName();
}
