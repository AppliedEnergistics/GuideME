package guideme.internal.scene;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import guideme.color.LightDarkMode;
import guideme.scene.GuidebookScene;
import guideme.scene.LytGuidebookScene;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.LightmapRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;

public class ScenePictureInPictureRenderer extends PictureInPictureRenderer<ScenePictureInPictureRenderer.State> {

    // RegisterPictureInPictureRenderersEvent now takes a Supplier, and PictureInPictureRenderer no
    // longer has a MultiBufferSource.BufferSource constructor argument (MultiBufferSource is gone).
    public ScenePictureInPictureRenderer() {
    }

    @Override
    public Class<State> getRenderStateClass() {
        return State.class;
    }

    /**
     * In 26.3 the actual draw calls for a picture-in-picture element happen inside
     * {@link PictureInPictureRenderer#prepare}, <em>after</em> {@link #renderToTexture} has returned:
     * {@code renderToTexture} only submits nodes, which are then prepared and executed in a render pass we
     * do not own. Everything the scene needs as ambient state - the fake client player, the level lightmap
     * and the scene's own projection matrix - therefore has to stay installed for the whole of
     * {@code super.prepare()} and be torn down here instead of inside the renderer.
     */
    @Override
    public void prepare(State state, GuiRenderState guiRenderState, FeatureRenderDispatcher featureRenderDispatcher,
            int guiScale) {
        var gameRenderer = Minecraft.getInstance().gameRenderer;

        // The scene installs its own projection matrix in renderToTexture (it has to run after
        // PictureInPictureRenderer set up its own), and nothing in vanilla restores it afterwards.
        RenderSystem.backupProjectionMatrix();

        // GameRenderer#getGameRenderState() -> GameRenderer#gameRenderState()
        var previousUseUiLightmap = gameRenderer.useUiLightmap;
        gameRenderer.useUiLightmap = false;
        var lightmapRenderState = new LightmapRenderState();
        lightmapRenderState.needsUpdate = true;
        gameRenderer.lightmap.render(lightmapRenderState);

        try (var ignored = FakeRenderEnvironment.create(state.guidebookScene().getLevel())) {
            super.prepare(state, guiRenderState, featureRenderDispatcher, guiScale);
        } finally {
            gameRenderer.useUiLightmap = previousUseUiLightmap;
            gameRenderer.gameRenderState().lightmapRenderState.needsUpdate = true;
            gameRenderer.lightmap.render(gameRenderer.gameRenderState().lightmapRenderState);
            RenderSystem.restoreProjectionMatrix();
        }
    }

    @Override
    protected void renderToTexture(State state, PoseStack pose, SubmitNodeCollector submitNodeCollector) {
        // The pose handed to us maps "model space" to the picture-in-picture texture's pixel space
        // (translate to the center, scale by the gui scale). The guidebook scene brings its own orthographic
        // projection and view matrix, which GuidebookLevelRenderer installs globally, so we start from an
        // identity pose here and let the scene camera do the work - exactly like the 26.1 code did.
        pose.pushPose();
        pose.setIdentity();
        state.renderer.render(state.lightDarkMode, pose, submitNodeCollector);
        pose.popPose();
    }

    @Override
    protected float getTranslateY(int height, int guiScale) {
        // Irrelevant, since renderToTexture resets the pose, but keep it centered for consistency.
        return height / 2.0F;
    }

    @Override
    protected String getTextureLabel() {
        return "GuideME game scene";
    }

    public record State(
            LightDarkMode lightDarkMode,
            Matrix3x2f pose,
            int x0, int y0,
            int x1, int y1,
            LytGuidebookScene layoutScene,
            GuidebookScene guidebookScene,
            ScreenRectangle bounds,
            @Nullable ScreenRectangle scissorArea,
            Renderer renderer) implements PictureInPictureRenderState {
        @Override
        public float scale() {
            return 1;
        }
    }

    @FunctionalInterface
    public interface Renderer {
        void render(LightDarkMode lightDarkMode, PoseStack pose, SubmitNodeCollector collector);
    }
}
