package guideme.siteexport;

import guideme.Guide;
import guideme.extensions.Extension;
import guideme.extensions.ExtensionPoint;

/**
 * This is an extension point for mods that wish to export additional resources alongside their guide.
 */
public interface AdditionalResourceExporter extends Extension {
    ExtensionPoint<AdditionalResourceExporter> EXTENSION_POINT = new ExtensionPoint<AdditionalResourceExporter>(
            AdditionalResourceExporter.class);

    /**
     * Called before the export concludes to allow a mod to write additional resources to the export.
     */
    void addResources(Guide guide, ResourceExporter exporter);
}
