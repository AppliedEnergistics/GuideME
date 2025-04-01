package guideme.siteexport;

import com.google.gson.JsonObject;
import guideme.extensions.Extension;
import guideme.extensions.ExtensionPoint;

/**
 * Extension to allow a mod to post-process the exported main JSON file right before the export is finalized.
 */
public interface ExportPostProcessor extends Extension {
    ExtensionPoint<ExportPostProcessor> EXTENSION_POINT = new ExtensionPoint<>(ExportPostProcessor.class);

    void postProcess(JsonObject rootNode);

}
