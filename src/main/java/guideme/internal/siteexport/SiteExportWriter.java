package guideme.internal.siteexport;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.internal.bind.JsonTreeWriter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import guideme.Guide;
import guideme.compiler.MdAstNodeAdapter;
import guideme.compiler.ParsedGuidePage;
import guideme.indices.PageIndex;
import guideme.internal.siteexport.model.ExportedPageJson;
import guideme.internal.siteexport.model.FluidInfoJson;
import guideme.internal.siteexport.model.ItemInfoJson;
import guideme.internal.siteexport.model.NavigationNodeJson;
import guideme.internal.siteexport.model.SiteExportJson;
import guideme.internal.util.Platform;
import guideme.libs.mdast.MdAstVisitor;
import guideme.libs.mdast.model.MdAstHeading;
import guideme.libs.mdast.model.MdAstNode;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.FurnaceRecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SmithingRecipeDisplay;
import net.minecraft.world.item.crafting.display.StonecutterRecipeDisplay;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SiteExportWriter {
    private static final Logger LOG = LoggerFactory.getLogger(SiteExportWriter.class);

    private abstract static class WriteOnlyTypeAdapter<T> extends TypeAdapter<T> {
        @Override
        public T read(JsonReader in) {
            throw new UnsupportedOperationException();
        }
    }

    public static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .registerTypeHierarchyAdapter(MdAstNode.class, new MdAstNodeAdapter())
            // Serialize ResourceLocation as strings
            .registerTypeAdapter(ResourceLocation.class, new WriteOnlyTypeAdapter<ResourceLocation>() {
                @Override
                public void write(JsonWriter out, ResourceLocation value) throws IOException {
                    out.value(value.toString());
                }
            })
            // Serialize Ingredient as arrays of the corresponding item IDs
            .registerTypeAdapter(SlotDisplay.class, new WriteOnlyTypeAdapter<SlotDisplay>() {
                @Override
                public void write(JsonWriter out, SlotDisplay value) throws IOException {
                    out.beginArray();
                    for (var item : value.resolveForStacks(Platform.getSlotDisplayContext())) {
                        var itemId = BuiltInRegistries.ITEM.getKey(item.getItem());
                        out.value(itemId.toString());
                    }
                    out.endArray();
                }
            })
            // Serialize Items & Fluids using their registered ID
            .registerTypeHierarchyAdapter(Item.class, new WriteOnlyTypeAdapter<Item>() {
                @Override
                public void write(JsonWriter out, Item value) throws IOException {
                    if (value == null) {
                        out.nullValue();
                    } else {
                        out.value(BuiltInRegistries.ITEM.getKey(value).toString());
                    }
                }
            })
            .registerTypeHierarchyAdapter(Fluid.class, new WriteOnlyTypeAdapter<Fluid>() {
                @Override
                public void write(JsonWriter out, Fluid value) throws IOException {
                    if (value == null) {
                        out.nullValue();
                    } else {
                        out.value(BuiltInRegistries.FLUID.getKey(value).toString());
                    }
                }
            })
            // ItemStacks use the Item, and a normalized NBT format
            .registerTypeAdapter(ItemStack.class, new WriteOnlyTypeAdapter<ItemStack>() {
                @Override
                public void write(JsonWriter out, ItemStack value) throws IOException {
                    if (value == null || value.isEmpty()) {
                        out.nullValue();
                    } else {
                        out.value(BuiltInRegistries.ITEM.getKey(value.getItem()).toString());
                    }
                }
            })
            // Boolean
            .registerTypeAdapter(Boolean.class, new WriteOnlyTypeAdapter<Boolean>() {
                @Override
                public void write(JsonWriter out, Boolean value) throws IOException {
                    out.value(value.booleanValue());
                }
            })
            .create();

    private final SiteExportJson siteExport = new SiteExportJson();

    public SiteExportWriter(Guide guide) {
        siteExport.defaultNamespace = guide.getDefaultNamespace();
        siteExport.navigationRootNodes = guide.getNavigationTree().getRootNodes()
                .stream()
                .map(NavigationNodeJson::of)
                .toList();
    }

    public void addItem(String id, ItemStack stack, String iconPath) {
        var itemInfo = new ItemInfoJson();
        itemInfo.id = id;
        itemInfo.icon = iconPath;
        itemInfo.displayName = stack.getHoverName().getString();
        itemInfo.rarity = stack.getRarity().name().toLowerCase(Locale.ROOT);

        siteExport.items.put(itemInfo.id, itemInfo);
    }

    public void addFluid(String id, FluidStack fluid, String iconPath) {
        var fluidInfo = new FluidInfoJson();
        fluidInfo.id = id;
        fluidInfo.icon = iconPath;
        fluidInfo.displayName = fluid.getHoverName().getString();
        siteExport.fluids.put(fluidInfo.id, fluidInfo);
    }

    public void addRecipe(ResourceLocation id, ShapelessCraftingRecipeDisplay recipe) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("shapeless", true);

        var resultItem = recipe.result().resolveForFirstStack(Platform.getSlotDisplayContext());
        fields.put("resultItem", resultItem);
        fields.put("resultCount", resultItem.getCount());
        fields.put("ingredients", recipe.ingredients());

        addRecipe(id, recipe, fields);
    }

    public void addRecipe(ResourceLocation id, ShapedCraftingRecipeDisplay recipe) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("shapeless", false);
        fields.put("width", recipe.width());
        fields.put("height", recipe.height());

        var resultItem = recipe.result().resolveForFirstStack(Platform.getSlotDisplayContext());
        fields.put("resultItem", resultItem);
        fields.put("resultCount", resultItem.getCount());
        fields.put("ingredients", recipe.ingredients());

        addRecipe(id, recipe, fields);
    }

    public void addRecipe(ResourceLocation id, FurnaceRecipeDisplay recipe) {
        var resultItem = recipe.result().resolveForFirstStack(Platform.getSlotDisplayContext());

        addRecipe(id, recipe, Map.of(
                "resultItem", resultItem,
                "ingredient", recipe.ingredient()));
    }

    public void addRecipe(ResourceLocation id, SmithingRecipeDisplay recipe) {
        var resultItem = recipe.result().resolveForFirstStack(Platform.getSlotDisplayContext());

        addRecipe(id, recipe, Map.of(
                "resultItem", resultItem,
                "base", recipe.base(),
                "addition", recipe.addition(),
                "template", recipe.template()));
    }

    public void addRecipe(ResourceLocation id, StonecutterRecipeDisplay recipe) {
        var resultItem = recipe.result().resolveForFirstStack(Platform.getSlotDisplayContext());

        addRecipe(id, recipe,
                Map.of(
                        "resultItem", resultItem,
                        "ingredient", recipe.input()));
    }

    public void addRecipe(ResourceLocation id, RecipeDisplay recipe, Map<String, Object> element) {
        // Auto-transform ingredients
        var jsonElement = GSON.toJsonTree(element);

        var type = BuiltInRegistries.RECIPE_DISPLAY.getKey(recipe.type()).toString();
        jsonElement.getAsJsonObject().addProperty("type", type);

        if (siteExport.recipes.put(id.toString(), jsonElement) != null) {
            throw new RuntimeException("Duplicate recipe id " + id);
        }
    }

    public void addModData(String key, Object data) {
        siteExport.modData.put(key, data);
    }

    public byte[] toByteArray() throws IOException {
        var bout = new ByteArrayOutputStream();
        try (var out = new GZIPOutputStream(bout);
                var writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8))) {
            GSON.toJson(siteExport, writer);
        }
        return bout.toByteArray();
    }

    public void addPage(ParsedGuidePage page) {
        var exportedPage = new ExportedPageJson();
        // Default to the title found in navigation when linking to this page,
        // but use the extracted h1-page title instead, otherwise
        if (page.getFrontmatter().navigationEntry() != null) {
            exportedPage.title = page.getFrontmatter().navigationEntry().title();
        } else {
            exportedPage.title = extractPageTitle(page);
            if (exportedPage.title.isEmpty()) {
                LOG.warn("Unable to determine page title for {}: {}", page.getId(), exportedPage.title);
            }
        }
        exportedPage.astRoot = page.getAstRoot();
        exportedPage.frontmatter.putAll(page.getFrontmatter().additionalProperties());

        siteExport.pages.put(page.getId(), exportedPage);
    }

    private String extractPageTitle(ParsedGuidePage page) {
        var pageTitle = new StringBuilder();
        page.getAstRoot().visit(new MdAstVisitor() {
            @Override
            public Result beforeNode(MdAstNode node) {
                if (node instanceof MdAstHeading heading) {
                    if (heading.depth == 1) {
                        pageTitle.append(heading.toText());
                    }
                    return Result.STOP;
                }
                return Result.CONTINUE;
            }
        });
        return pageTitle.toString();
    }

    public String addItem(ItemStack stack) {
        var itemId = stack.getItem().builtInRegistryHolder().key().location().toString().replace(':', '-');
        if (stack.getComponentsPatch().isEmpty()) {
            return itemId;
        }

        var serializedTag = (CompoundTag) stack.save(Platform.getClientRegistryAccess());

        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        try (var out = new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(), digest))) {
            NbtIo.write(serializedTag, out);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return itemId + "-" + HexFormat.of().formatHex(digest.digest());
    }

    public void addIndex(Guide guide, Class<? extends PageIndex> indexClass) {
        try (var jsonWriter = new JsonTreeWriter()) {
            var index = guide.getIndex(indexClass);
            index.export(jsonWriter);
            siteExport.pageIndices.put(indexClass.getName(), jsonWriter.get());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
