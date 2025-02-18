package guideme.internal;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import guideme.Guide;
import guideme.compiler.PageCompiler;
import guideme.compiler.ParsedGuidePage;
import guideme.internal.datadriven.DataDrivenGuide;
import guideme.internal.util.LangUtil;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.metadata.language.LanguageMetadataSection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class GuideReloadListener extends SimplePreparableReloadListener<GuideReloadListener.Result> {
    private static final Logger LOG = LoggerFactory.getLogger(GuideReloadListener.class);

    private static final Gson GSON = new Gson();

    @Override
    protected Result prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        profiler.startTick();
        var guidePages = new IdentityHashMap<ResourceLocation, Map<ResourceLocation, ParsedGuidePage>>();

        String language = Minecraft.getInstance().getLanguageManager().getSelected();
        if (GuideMEClient.instance().isIgnoreTranslatedGuides()) {
            language = null;
        }

        // Load available languages to know which can be ignored
        var languages = getAllLanguages(resourceManager);

        // Discover data driven guides now
        var dataDrivenGuides = loadDataDrivenGuides(resourceManager);

        // Reload pages for data-driven guides first
        for (var guide : dataDrivenGuides.values()) {
            guidePages.put(guide.getId(), loadPages(resourceManager, guide.getContentRootFolder(),
                    guide.getDefaultLanguage(), language, languages));
        }
        for (var guide : GuideRegistry.getStaticGuides()) {
            if (!guidePages.containsKey(guide.getId())) {
                guidePages.put(guide.getId(), loadPages(resourceManager, guide.getContentRootFolder(),
                        guide.getDefaultLanguage(), language, languages));
            }
        }

        profiler.endTick();
        return new Result(dataDrivenGuides, guidePages, languages);
    }

    /**
     * This code is copied from the MC language manager to retrieve the list of all supported languages.
     */
    private static Set<String> getAllLanguages(ResourceManager resourceManager) {
        var result = new HashSet<String>();
        var it = resourceManager.listPacks().iterator();
        while (it.hasNext()) {
            try {
                var section = it.next().getMetadataSection(LanguageMetadataSection.TYPE);
                if (section != null) {
                    result.addAll(section.languages().keySet());
                }
            } catch (Exception ignored) {
                // Minecraft itself will already warn about this
            }
        }
        return result;
    }

    @Override
    protected void apply(Result result, ResourceManager resourceManager, ProfilerFiller profiler) {
        profiler.startTick();

        LOG.info("Data driven guides: {}", result.dataDrivenGuides.keySet());

        GuideRegistry.setDataDriven(result.dataDrivenGuides);

        for (var guide : GuideRegistry.getAll()) {
            var pagesForGuide = result.guidePages.getOrDefault(guide.getId(), Map.of());
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

    private static Map<ResourceLocation, MutableGuide> loadDataDrivenGuides(ResourceManager resourceManager) {
        var dataDrivenGuideJsons = new HashMap<ResourceLocation, JsonElement>();
        SimpleJsonResourceReloadListener.scanDirectory(resourceManager, "guideme_guides", GSON, dataDrivenGuideJsons);

        // Load the data driven guides
        Map<ResourceLocation, MutableGuide> dataDrivenGuides = new HashMap<>();
        for (var entry : dataDrivenGuideJsons.entrySet()) {
            var guideId = entry.getKey();

            var result = DataDrivenGuide.CODEC.parse(JsonOps.INSTANCE, entry.getValue());
            if (result instanceof DataResult.Error<?> error) {
                LOG.error("Failed to load data driven guide {}: {}", guideId, error.message());
                continue;
            }

            var guideSpec = result.getOrThrow();

            var guide = (MutableGuide) Guide.builder(guideId)
                    .register(false)
                    .itemSettings(guideSpec.itemSettings())
                    .build();
            dataDrivenGuides.put(guideId, guide);
        }
        return dataDrivenGuides;
    }

    private static Map<ResourceLocation, ParsedGuidePage> loadPages(ResourceManager resourceManager,
            String contentRoot,
            String defaultLanguage,
            @Nullable String currentLanguage,
            Set<String> languages) {
        var pagesForGuide = new HashMap<ResourceLocation, ParsedGuidePage>();

        var resources = resourceManager.listResources(contentRoot, location -> location.getPath().endsWith(".md"));

        for (var entry : resources.entrySet()) {
            var pageId = ResourceLocation.fromNamespaceAndPath(
                    entry.getKey().getNamespace(),
                    entry.getKey().getPath().substring((contentRoot + "/").length()));
            var resource = entry.getValue();

            if (LangUtil.getLangFromPageId(pageId, languages) != null) {
                continue; // Skip translated pages
            }

            // Check for translated versions of this page
            String language = defaultLanguage;
            if (currentLanguage != null) {
                var translatedPage = resources.get(LangUtil.getTranslatedAsset(pageId, currentLanguage));
                if (translatedPage != null) {
                    language = currentLanguage;
                    resource = translatedPage;
                }
            }

            String sourcePackId = resource.sourcePackId();
            try (var in = resource.open()) {
                pagesForGuide.put(pageId, PageCompiler.parse(sourcePackId, language, pageId, in));
            } catch (IOException e) {
                LOG.error("Failed to load guidebook page {} from pack {}", pageId, sourcePackId, e);
            }
        }

        return pagesForGuide;
    }

    protected record Result(
            Map<ResourceLocation, MutableGuide> dataDrivenGuides,
            Map<ResourceLocation, Map<ResourceLocation, ParsedGuidePage>> guidePages, Set<String> languages) {
    }
}
