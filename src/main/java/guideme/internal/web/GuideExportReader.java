package guideme.internal.web;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import guideme.compiler.MdAstNodeAdapter;
import guideme.internal.siteexport.model.IndexModel;
import guideme.internal.siteexport.model.SiteExportJson;
import guideme.libs.mdast.model.MdAstNode;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;

/**
 * Reads a guide that was exported from the game, including exports made by older versions.
 */
final class GuideExportReader {
    /**
     * Older AE2 exports stored the default config values at the top-level, before mod-specific data was introduced.
     */
    private static final String LEGACY_AE2_CONFIG_VALUES = "defaultConfigValues";
    private static final String AE2_CONFIG_VALUES_KEY = "ae2:default-config-values";

    private GuideExportReader() {
    }

    static IndexModel readIndex(Path dataFolder) {
        var path = dataFolder.resolve("index.json");
        try (var reader = Files.newBufferedReader(path)) {
            return new Gson().fromJson(reader, IndexModel.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read the index file " + path, e);
        }
    }

    static ExportedGuideImpl readGuide(Path dataFolder, IndexModel index) {
        var path = dataFolder.resolve(index.guideDataPath());
        JsonObject json;
        try (var input = Files.newInputStream(path);
                var gzipInput = new GZIPInputStream(new BufferedInputStream(input));
                var reader = new BufferedReader(new InputStreamReader(gzipInput, StandardCharsets.UTF_8))) {
            json = JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read the guide data " + path, e);
        }

        var model = new GsonBuilder()
                .registerTypeHierarchyAdapter(MdAstNode.class, new MdAstNodeAdapter())
                .create()
                .fromJson(json, SiteExportJson.class);

        var legacyConfigValues = json.get(LEGACY_AE2_CONFIG_VALUES);
        if (legacyConfigValues != null && !model.modData.containsKey(AE2_CONFIG_VALUES_KEY)) {
            model.modData.put(AE2_CONFIG_VALUES_KEY, new Gson().fromJson(legacyConfigValues, Object.class));
        }

        return new ExportedGuideImpl(index, model);
    }
}
