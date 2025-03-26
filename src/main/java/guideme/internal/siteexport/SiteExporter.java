package guideme.internal.siteexport;

import com.google.common.io.MoreFiles;
import com.google.common.io.RecursiveDeleteOption;
import guideme.Guide;
import guideme.GuidePage;
import guideme.compiler.PageCompiler;
import guideme.compiler.ParsedGuidePage;
import guideme.document.block.recipes.RecipeDisplayHolder;
import guideme.indices.CategoryIndex;
import guideme.indices.ItemIndex;
import guideme.internal.GuideOnStartup;
import guideme.internal.siteexport.mdastpostprocess.PageExportPostProcessor;
import guideme.internal.util.Platform;
import guideme.navigation.NavigationNode;
import guideme.siteexport.ExportableResourceProvider;
import guideme.siteexport.ResourceExporter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntUnaryOperator;
import net.minecraft.ChatFormatting;
import net.minecraft.DetectedVersion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.FurnaceRecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SmithingRecipeDisplay;
import net.minecraft.world.item.crafting.display.StonecutterRecipeDisplay;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.display.FluidStackContentsFactory;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.MustBeInvokedByOverriders;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Exports a data package for use by the website.
 */
public class SiteExporter implements ResourceExporter {

    private static final Logger LOG = LoggerFactory.getLogger(SiteExporter.class);

    private static final int ICON_DIMENSION = 128;

    private final Minecraft client;
    private final Map<ResourceLocation, String> exportedTextures = new HashMap<>();

    private final Path outputFolder;

    private final Guide guide;

    private ParsedGuidePage currentPage;

    private final Set<RecipeDisplayHolder<?>> recipes = new HashSet<>();

    private final Set<Item> items = new HashSet<>();

    private final Set<Fluid> fluids = new HashSet<>();

    public SiteExporter(Minecraft client, Path outputFolder, Guide guide) {
        this.client = client;
        this.outputFolder = outputFolder;
        this.guide = guide;
    }

    /**
     * Export on the next tick.
     */
    public void exportOnNextTickAndExit() {
        var exportDone = new MutableBoolean();
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post evt) -> {
            if (client.getOverlay() instanceof LoadingOverlay) {
                return; // Do nothing while it's loading
            }

            if (!exportDone.getValue()) {
                exportDone.setTrue();

                try {
                    export();
                } catch (Exception e) {
                    e.printStackTrace();
                    System.exit(1);
                }
                client.stop();
            }
        });
    }

    public void export(ExportFeedbackSink feedback) {
        try {
            export();

            feedback.sendFeedback(Component.literal("Guide data exported to ")
                    .append(Component.literal("[" + outputFolder.getFileName().toString() + "]")
                            .withStyle(style -> style
                                    .withClickEvent(
                                            new ClickEvent.OpenFile(outputFolder.toString()))
                                    .withHoverEvent(
                                            new HoverEvent.ShowText(Component.literal("Click to open export folder")))
                                    .applyFormats(ChatFormatting.UNDERLINE, ChatFormatting.GREEN))));
        } catch (Exception e) {
            e.printStackTrace();
            feedback.sendError(Component.literal(e.toString()));
        }
    }

    @Override
    public void referenceItem(ItemStack stack) {
        if (!stack.isEmpty()) {
            items.add(stack.getItem());
            if (!stack.getComponentsPatch().isEmpty()) {
                LOG.error("Couldn't handle stack with NBT tag: {}", stack);
            }
        }
    }

    @Override
    public void referenceFluid(Fluid fluid) {
        fluids.add(fluid);
    }

    public void referenceFluid(FluidStack fluid) {
        fluids.add(fluid.getFluid());
    }

    private void referenceIngredient(SlotDisplay display) {
        for (var stack : display.resolveForStacks(Platform.getSlotDisplayContext())) {
            referenceItem(stack);
        }
    }

    @Override
    public void referenceRecipe(RecipeDisplayHolder<?> holder) {
        if (!recipes.add(holder)) {
            return; // Already added
        }

        visitDisplays(holder.value(), display -> {
            display.resolve(Platform.getSlotDisplayContext(), SlotDisplay.ItemStackContentsFactory.INSTANCE)
                    .forEach(this::referenceItem);
            display.resolve(Platform.getSlotDisplayContext(), FluidStackContentsFactory.INSTANCE)
                    .forEach(this::referenceFluid);
        });
    }

    @MustBeInvokedByOverriders
    protected void visitDisplays(RecipeDisplay recipe, Consumer<SlotDisplay> visitor) {
        visitor.accept(recipe.result());
        visitor.accept(recipe.craftingStation());

        switch (recipe) {
            case ShapedCraftingRecipeDisplay craftingRecipe -> {
                for (var display : craftingRecipe.ingredients()) {
                    visitor.accept(display);
                }
            }
            case ShapelessCraftingRecipeDisplay craftingRecipe -> {
                for (var display : craftingRecipe.ingredients()) {
                    visitor.accept(display);
                }
            }
            case SmithingRecipeDisplay smithingTransformRecipe -> {
                visitor.accept(smithingTransformRecipe.base());
                visitor.accept(smithingTransformRecipe.template());
                visitor.accept(smithingTransformRecipe.addition());
            }
            case FurnaceRecipeDisplay furnaceRecipeDisplay -> {
                visitor.accept(furnaceRecipeDisplay.ingredient());
                visitor.accept(furnaceRecipeDisplay.result());
            }
            case StonecutterRecipeDisplay stonecutterRecipeDisplay -> {
                visitor.accept(stonecutterRecipeDisplay.input());
                visitor.accept(stonecutterRecipeDisplay.result());
            }
            default -> {
            }
        }
    }

    private void dumpRecipes(SiteExportWriter writer) {
        for (var holder : recipes) {
            var id = holder.id();
            var recipe = holder.value();

            if (recipe instanceof ShapedCraftingRecipeDisplay craftingRecipe) {
                writer.addRecipe(id, craftingRecipe);
            } else if (recipe instanceof ShapelessCraftingRecipeDisplay cookingRecipe) {
                writer.addRecipe(id, cookingRecipe);
            } else if (recipe instanceof SmithingRecipeDisplay smithingTransformRecipe) {
                writer.addRecipe(id, smithingTransformRecipe);
            } else if (recipe instanceof FurnaceRecipeDisplay smithingTrimRecipe) {
                writer.addRecipe(id, smithingTrimRecipe);
            } else if (recipe instanceof StonecutterRecipeDisplay stonecutterRecipeDisplay) {
                writer.addRecipe(id, stonecutterRecipeDisplay);
            } else {
                var recipeFields = getCustomRecipeFields(id, recipe);
                if (recipeFields != null) {
                    writer.addRecipe(id, recipe, recipeFields);
                } else {
                    LOG.warn("Unable to handle recipe {} of type {}", holder.id(), recipe.type());
                }
            }
        }
    }

    /**
     * Override this method to handle custom recipes by returning additional JSON fields to be added to the exported
     * recipe object. Returning null will skip exporting this recipe.
     */
    @Nullable
    @ApiStatus.OverrideOnly
    protected Map<String, Object> getCustomRecipeFields(ResourceLocation id, RecipeDisplay recipe) {
        return null;
    }

    @ApiStatus.OverrideOnly
    protected void postProcess(SiteExportWriter writer) {
    }

    protected String getModVersion() {
        return System.getProperty("appeng.version", "unknown");
    }

    @Override
    public Path copyResource(ResourceLocation id) {
        try {
            var pagePath = getPathForWriting(id);
            byte[] bytes = guide.loadAsset(id);
            if (bytes == null) {
                throw new IllegalArgumentException("Couldn't find asset " + id);
            }
            return CacheBusting.writeAsset(pagePath, bytes);
        } catch (IOException e) {
            throw new RuntimeException("Failed to copy resource " + id, e);
        }
    }

    @Override
    public Path getPathForWriting(ResourceLocation assetId) {
        try {
            var path = resolvePath(assetId);
            Files.createDirectories(path.getParent());
            return path;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Path getOutputFolder() {
        return outputFolder;
    }

    @Override
    public ResourceLocation getPageSpecificResourceLocation(String suffix) {
        var path = currentPage.getId().getPath();
        var idx = path.lastIndexOf('.');
        if (idx != -1) {
            path = path.substring(0, idx);
        }
        return ResourceLocation.fromNamespaceAndPath(currentPage.getId().getNamespace(), path + "_" + suffix);
    }

    @Override
    public Path getPageSpecificPathForWriting(String suffix) {
        // Build filename
        var pageFilename = currentPage.getId().getPath();
        var filename = FilenameUtils.getBaseName(pageFilename) + "_" + suffix;

        var pagePath = resolvePath(currentPage.getId());
        var path = pagePath.resolveSibling(filename);

        try {
            Files.createDirectories(path.getParent());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return path;
    }

    @Override
    public @Nullable ResourceLocation getCurrentPageId() {
        return currentPage != null ? currentPage.getId() : null;
    }

    private void export() throws Exception {
        if (Files.isDirectory(outputFolder)) {
            MoreFiles.deleteDirectoryContents(outputFolder, RecursiveDeleteOption.ALLOW_INSECURE);
        } else {
            Files.createDirectories(outputFolder);
        }

        // Load data packs if needed
        if (client.level == null) {
            LOG.info("Reloading datapacks to get recipes");
            GuideOnStartup.runDatapackReload();
            LOG.info("Completed datapack reload");
        }

        // Reference all navigation node icons
        guide.getNavigationTree().getRootNodes().forEach(this::visitNavigationNodeIcons);

        var indexWriter = new SiteExportWriter(guide);

        for (var page : guide.getPages()) {
            currentPage = page;

            LOG.debug("Compiling {}", page);
            var compiledPage = PageCompiler.compile(guide, guide.getExtensions(), page);

            processPage(indexWriter, page, compiledPage);

            // Post-Process the parsed Markdown AST and export it as JSON into the index directly
            ExportableResourceProvider.visit(compiledPage.document(), SiteExporter.this);
        }

        dumpRecipes(indexWriter);

        processItems(client, indexWriter, outputFolder);
        processFluids(client, indexWriter, outputFolder);

        indexWriter.addIndex(guide, ItemIndex.class);
        indexWriter.addIndex(guide, CategoryIndex.class);

        postProcess(indexWriter);

        var guideContent = outputFolder.resolve("guide.json.gz");
        byte[] content = indexWriter.toByteArray();

        guideContent = CacheBusting.writeAsset(guideContent, content);

        // Write an uncompressed summary
        writeSummary(guideContent.getFileName().toString());
    }

    private void visitNavigationNodeIcons(NavigationNode navigationNode) {
        referenceItem(navigationNode.icon());
        navigationNode.children().forEach(this::visitNavigationNodeIcons);
    }

    private void processPage(SiteExportWriter exportWriter,
            ParsedGuidePage page,
            GuidePage compiledPage) {

        // Run post-processors on the AST
        PageExportPostProcessor.postprocess(this, page, compiledPage);

        exportWriter.addPage(page);
    }

    private void writeSummary(String guideDataFilename) throws IOException {
        var modVersion = getModVersion();
        var generated = Instant.now().toEpochMilli();
        var gameVersion = DetectedVersion.tryDetectVersion().getName();

        // This file is not accessed via the CDN and thus doesn't need a cache-busting name
        try (var writer = Files.newBufferedWriter(outputFolder.resolve("index.json"), StandardCharsets.UTF_8)) {
            var jsonWriter = SiteExportWriter.GSON.newJsonWriter(writer);
            jsonWriter.beginObject();
            jsonWriter.name("format").value(1);
            jsonWriter.name("generated").value(generated);
            jsonWriter.name("gameVersion").value(gameVersion);
            jsonWriter.name("modVersion").value(modVersion);
            jsonWriter.name("guideDataPath").value(guideDataFilename);
            jsonWriter.endObject();
        }
    }

    private Path resolvePath(ResourceLocation id) {
        return outputFolder.resolve(id.getNamespace() + "/" + id.getPath());
    }

    private void processItems(Minecraft client,
            SiteExportWriter siteExport,
            Path outputFolder) throws IOException {
        var iconsFolder = outputFolder.resolve("!items");
        if (Files.exists(iconsFolder)) {
            MoreFiles.deleteRecursively(iconsFolder, RecursiveDeleteOption.ALLOW_INSECURE);
        }

        try (var renderer = new OffScreenRenderer(ICON_DIMENSION, ICON_DIMENSION)) {
            var guiGraphics = new GuiGraphics(client, client.renderBuffers().bufferSource());

            renderer.setupItemRendering();

            LOG.info("Exporting items...");
            for (var item : items) {
                var stack = new ItemStack(item);

                var itemId = getItemId(stack.getItem()).toString();
                var baseName = "!items/" + itemId.replace(':', '/');

                // Guess used sprites from item model
                var renderState = new ItemStackRenderState();
                client.getItemModelResolver().appendItemLayers(renderState, stack, ItemDisplayContext.GUI, null, null,
                        0);

                var quadLists = new HashSet<List<BakedQuad>>();
                for (var layer : renderState.layers) {
                    quadLists.add(layer.prepareQuadList());
                }

                var sprites = guessSprites(quadLists);

                var iconPath = renderAndWrite(renderer, baseName, () -> {
                    guiGraphics.renderItem(stack, 0, 0);
                    guiGraphics.renderItemDecorations(client.font, stack, 0, 0, "");
                }, sprites, true);

                String absIconUrl = "/" + outputFolder.relativize(iconPath).toString().replace('\\', '/');
                siteExport.addItem(itemId, stack, absIconUrl);
            }
        }
    }

    private Set<TextureAtlasSprite> guessSprites(Collection<List<BakedQuad>> quadLists) {
        var result = Collections.newSetFromMap(new IdentityHashMap<TextureAtlasSprite, Boolean>());

        for (var quadList : quadLists) {
            for (var quad : quadList) {
                result.add(quad.sprite());
            }
        }

        return result;
    }

    private void processFluids(Minecraft client,
            SiteExportWriter siteExport,
            Path outputFolder) throws IOException {
        var fluidsFolder = outputFolder.resolve("!fluids");
        if (Files.exists(fluidsFolder)) {
            MoreFiles.deleteRecursively(fluidsFolder, RecursiveDeleteOption.ALLOW_INSECURE);
        }

        try (var renderer = new OffScreenRenderer(ICON_DIMENSION, ICON_DIMENSION)) {
            var guiGraphics = new GuiGraphics(client, client.renderBuffers().bufferSource());

            renderer.setupItemRendering();

            LOG.info("Exporting fluids...");
            for (var fluid : fluids) {
                var fluidVariant = new FluidStack(fluid, 1);
                String fluidId = BuiltInRegistries.FLUID.getKey(fluid).toString();

                var props = IClientFluidTypeExtensions.of(fluidVariant.getFluid());

                var sprite = Minecraft.getInstance()
                        .getTextureAtlas(TextureAtlas.LOCATION_BLOCKS)
                        .apply(props.getStillTexture(fluidVariant));
                var color = props.getTintColor(fluidVariant);

                var baseName = "!fluids/" + fluidId.replace(':', '/');
                var iconPath = renderAndWrite(
                        renderer,
                        baseName,
                        () -> {
                            if (sprite != null) {
                                guiGraphics.blitSprite(RenderType::guiTextured, sprite, 0, 0, 16, 16, color);
                            }
                        },
                        sprite != null ? Set.of(sprite) : Set.of(),
                        false /*
                               * no alpha for fluids since water is translucent but there's nothing behind it in our
                               * icons
                               */
                );

                String absIconUrl = "/" + outputFolder.relativize(iconPath).toString().replace('\\', '/');
                siteExport.addFluid(fluidId, fluidVariant, absIconUrl);
            }
        }

    }

    public Path renderAndWrite(OffScreenRenderer renderer,
            String baseName,
            Runnable renderRunnable,
            Collection<TextureAtlasSprite> sprites,
            boolean withAlpha) throws IOException {
        String extension;
        byte[] content;
        if (renderer.isAnimated(sprites)) {
            extension = ".webp";
            content = renderer.captureAsWebp(
                    renderRunnable,
                    sprites,
                    withAlpha ? WebPExporter.Format.LOSSLESS_ALPHA : WebPExporter.Format.LOSSLESS);
        } else {
            extension = ".png";
            content = renderer.captureAsPng(renderRunnable);
        }

        var iconPath = outputFolder.resolve(baseName + extension);
        Files.createDirectories(iconPath.getParent());
        return CacheBusting.writeAsset(iconPath, content);
    }

    @Override
    public String exportTexture(ResourceLocation textureId) {
        var exportedPath = exportedTextures.get(textureId);
        if (exportedPath != null) {
            return exportedPath;
        }

        ResourceLocation id = textureId;
        if (!id.getPath().endsWith(".png")) {
            id = ResourceLocation.fromNamespaceAndPath(id.getNamespace(), id.getPath() + ".png");
        }

        var outputPath = getPathForWriting(id);

        var texture = Minecraft.getInstance().getTextureManager().getTexture(textureId);

        if (texture instanceof TextureAtlas textureAtlas) {
            for (var sprite : textureAtlas.sprites) {
                if (sprite.animatedTexture != null) {
                }
            }
        }

        byte[] imageContent;
        try (var nativeImage = TextureDownloader.downloadTexture(texture.getTexture(), 0,
                IntUnaryOperator.identity())) {
            imageContent = Platform.exportAsPng(nativeImage);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try {
            outputPath = CacheBusting.writeAsset(outputPath, imageContent);
        } catch (IOException e) {
            throw new RuntimeException("Failed to export texture " + textureId, e);
        }
        exportedPath = getPathRelativeFromOutputFolder(outputPath);
        exportedTextures.put(textureId, exportedPath);
        return exportedPath;
    }

    private static ResourceLocation getItemId(Item item) {
        return BuiltInRegistries.ITEM.getKey(item);
    }

    private static ResourceLocation getFluidId(Fluid fluid) {
        return BuiltInRegistries.FLUID.getKey(fluid);
    }
}
