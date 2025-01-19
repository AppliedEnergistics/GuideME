package guideme.internal;

import guideme.MutableGuide;
import guideme.compiler.PageCompiler;
import guideme.compiler.ParsedGuidePage;
import java.io.IOException;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GuideReloadListener
        extends SimplePreparableReloadListener<Map<MutableGuide, Map<ResourceLocation, ParsedGuidePage>>> {
    private static final Logger LOG = LoggerFactory.getLogger(GuideReloadListener.class);

    @Override
    protected Map<MutableGuide, Map<ResourceLocation, ParsedGuidePage>> prepare(ResourceManager resourceManager,
            ProfilerFiller profiler) {
        profiler.startTick();
        IdentityHashMap<MutableGuide, Map<ResourceLocation, ParsedGuidePage>> pages = new IdentityHashMap<>();

        // Unload all data-driven guides now
        GuideRegistry.setDataDriven(Map.of());

        // Reload pages for all code-driven guides
        for (var guide : GuideRegistry.getAll()) {
            var folder = guide.getContentRootFolder();

            var pagesForGuide = new HashMap<ResourceLocation, ParsedGuidePage>();

            var resources = resourceManager.listResources(folder,
                    location -> location.getPath().endsWith(".md"));

            for (var entry : resources.entrySet()) {
                var pageId = ResourceLocation.fromNamespaceAndPath(
                        entry.getKey().getNamespace(),
                        entry.getKey().getPath().substring((folder + "/").length()));

                String sourcePackId = entry.getValue().sourcePackId();
                try (var in = entry.getValue().open()) {
                    pagesForGuide.put(pageId, PageCompiler.parse(sourcePackId, pageId, in));
                } catch (IOException e) {
                    LOG.error("Failed to load guidebook page {} from pack {}", pageId, sourcePackId, e);
                }
            }

            pages.put(guide, pagesForGuide);
        }

        profiler.endTick();
        return pages;
    }

    @Override
    protected void apply(Map<MutableGuide, Map<ResourceLocation, ParsedGuidePage>> pagesByGuide,
            ResourceManager resourceManager,
            ProfilerFiller profiler) {
        profiler.startTick();

        for (var entry : pagesByGuide.entrySet()) {
            var guide = entry.getKey();
            var pagesForGuide = entry.getValue();
            profiler.push(guide.getId().toString());
            guide.setPages(pagesForGuide);
            profiler.pop();
        }
        profiler.endTick();
    }

    @Override
    public String getName() {
        return "GuideME Reload Listener";
    }
}
