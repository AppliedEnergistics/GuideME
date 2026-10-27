package guideme.internal.siteexport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import com.mojang.blaze3d.platform.NativeImage;
import guideme.siteexport.NamedPixelModifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceMetadata;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;

/**
 * Documents the paths under which textures and sprites end up in the site export. All exported images are cache-busted
 * by inserting a hash of their content into the filename ({@code {hash}} in the expected paths below).
 */
@ExtendWith(EphemeralTestServerProvider.class)
class SiteExporterTextureExportTest {
    private static final ResourceLocation BLOCK_ATLAS = ResourceLocation
            .withDefaultNamespace("textures/atlas/blocks.png");
    private static final NamedPixelModifier GRAYSCALE = new TestModifier("grayscale", pixel -> {
        var gray = ((pixel & 0xFF) + ((pixel >> 8) & 0xFF) + ((pixel >> 16) & 0xFF)) / 3;
        return (pixel & 0xFF000000) | (gray << 16) | (gray << 8) | gray;
    });

    @TempDir
    Path outputFolder;

    private SiteExporter exporter;

    private MockedStatic<Minecraft> minecraftMock;
    private MockedStatic<TextureDownloader> textureDownloaderMock;

    public SiteExporterTextureExportTest(MinecraftServer server) {
    }

    @BeforeEach
    void setUp() {
        // exportTexture downloads the texture from the GPU, which we substitute with a fixed test image
        var minecraft = mock(Minecraft.class, RETURNS_DEEP_STUBS);
        minecraftMock = mockStatic(Minecraft.class);
        minecraftMock.when(Minecraft::getInstance).thenReturn(minecraft);
        textureDownloaderMock = mockStatic(TextureDownloader.class);
        textureDownloaderMock.when(() -> TextureDownloader.downloadTexture(any(), anyInt(), any()))
                .thenAnswer(invocation -> createTestImage(invocation.getArgument(2)));

        exporter = new SiteExporter(minecraft, outputFolder, null);
    }

    @AfterEach
    void tearDown() {
        textureDownloaderMock.close();
        minecraftMock.close();
    }

    static Stream<Arguments> exports() {
        return Stream.of(
                export("exportTexture(id)",
                        e -> e.exportTexture(ResourceLocation.withDefaultNamespace("textures/block/stone.png")),
                        "/minecraft/textures/block/stone.{hash}.png"),
                export("exportTexture(id without extension)",
                        e -> e.exportTexture(ResourceLocation.withDefaultNamespace("textures/block/stone")),
                        "/minecraft/textures/block/stone.{hash}.png"),
                export("exportTexture(id, modifier)",
                        e -> e.exportTexture(ResourceLocation.withDefaultNamespace("textures/block/stone.png"),
                                GRAYSCALE),
                        "/minecraft/textures/block/stone_grayscale.{hash}.png"),
                export("exportTexture(id without extension, modifier)",
                        e -> e.exportTexture(ResourceLocation.withDefaultNamespace("textures/block/stone"), GRAYSCALE),
                        "/minecraft/textures/block/stone_grayscale.{hash}.png"),
                export("exportSprite(sprite)",
                        e -> e.exportSprite(
                                createSprite(BLOCK_ATLAS, ResourceLocation.withDefaultNamespace("block/stone"))),
                        "/minecraft/block/stone.{hash}.png"),
                export("exportSprite(sprite, modifier)",
                        e -> e.exportSprite(
                                createSprite(BLOCK_ATLAS, ResourceLocation.withDefaultNamespace("block/stone")),
                                GRAYSCALE),
                        "/minecraft/block/stone_grayscale.{hash}.png"),
                export("exportModdedSprite(sprite, modifier)",
                        e -> e.exportSprite(
                                createSprite(BLOCK_ATLAS, ResourceLocation.fromNamespaceAndPath("mod", "block/stone")),
                                GRAYSCALE),
                        "/mod/block/stone_grayscale.{hash}.png"));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("exports")
    void testExportedPath(Function<SiteExporter, String> export, String expectedPath) throws Exception {
        var exportedPath = export.apply(exporter);

        var content = Files.readAllBytes(outputFolder.resolve(exportedPath.substring(1)));
        assertThat(exportedPath).isEqualTo(expectedPath.replace("{hash}", CacheBusting.create(content)));
        // Exporting the same resource again reuses the previous export
        assertThat(export.apply(exporter)).isEqualTo(exportedPath);
    }

    @Test
    void testExportingSpritesDoesNotAffectExportOfTheirAtlas() throws Exception {
        var stonePath = exporter
                .exportSprite(createSprite(BLOCK_ATLAS, ResourceLocation.withDefaultNamespace("block/stone")));
        var dirtPath = exporter
                .exportSprite(createSprite(BLOCK_ATLAS, ResourceLocation.withDefaultNamespace("block/dirt")));

        var atlasPath = exporter.exportTexture(BLOCK_ATLAS);

        var content = Files.readAllBytes(outputFolder.resolve(atlasPath.substring(1)));
        assertThat(atlasPath)
                .isEqualTo("/minecraft/textures/atlas/blocks." + CacheBusting.create(content) + ".png")
                .isNotIn(stonePath, dirtPath);
        // The sprites still resolve to their own exports after the atlas was exported
        assertThat(
                exporter.exportSprite(createSprite(BLOCK_ATLAS, ResourceLocation.withDefaultNamespace("block/stone"))))
                .isEqualTo(stonePath);
    }

    private static Arguments export(String description, Function<SiteExporter, String> export, String expectedPath) {
        return Arguments.of(Named.of(description, export), expectedPath);
    }

    private static NativeImage createTestImage(IntUnaryOperator pixelOp) {
        var image = new NativeImage(2, 2, false);
        image.setPixelRGBA(0, 0, pixelOp.applyAsInt(0xFFFF0000));
        image.setPixelRGBA(1, 0, pixelOp.applyAsInt(0xFF00FF00));
        image.setPixelRGBA(0, 1, pixelOp.applyAsInt(0xFF0000FF));
        image.setPixelRGBA(1, 1, pixelOp.applyAsInt(0x80FFFFFF));
        return image;
    }

    private static TextureAtlasSprite createSprite(ResourceLocation atlasId, ResourceLocation spriteId) {
        var contents = new SpriteContents(spriteId, new FrameSize(2, 2), createTestImage(IntUnaryOperator.identity()),
                ResourceMetadata.EMPTY);
        return new TextureAtlasSprite(atlasId, contents, 2, 2, 0, 0) {
        };
    }

    private record TestModifier(String name, IntUnaryOperator op) implements NamedPixelModifier {
        @Override
        public int applyAsInt(int pixel) {
            return op.applyAsInt(pixel);
        }
    }
}
