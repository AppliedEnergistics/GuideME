package guideme.siteexport;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import java.util.List;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Describes how the shaders of a render pipeline process geometry, so that the web viewer can approximate them.
 *
 * @param lighting    The type of lighting applied by the shader.
 * @param alphaTest   Fragments with an alpha below this value are discarded. 0 means no alpha test.
 * @param vertexColor Whether the shader multiplies the vertex color into the output color.
 * @param textured    Whether the shader samples the texture bound to {@code Sampler0}.
 * @see SceneShaderInfoProvider
 */
public record SceneShaderInfo(Lighting lighting, float alphaTest, boolean vertexColor, boolean textured) {

    private static final Logger LOG = LoggerFactory.getLogger(SceneShaderInfo.class);

    /**
     * Vanilla fragment shaders (by path prefix) that discard fragments with alpha < 0.1 without using the ALPHA_CUTOUT
     * define.
     */
    private static final List<String> HARDCODED_ALPHA_TEST_SHADERS = List.of(
            "core/rendertype_text",
            "core/particle",
            "core/glint",
            "core/rendertype_crumbling");

    public enum Lighting {
        /**
         * Lighting is pre-baked into the vertex colors by sampling the lightmap. The web viewer does not apply any
         * additional lighting.
         */
        LIGHTMAP,
        /**
         * Directional diffuse lighting is applied based on the vertex normals.
         */
        DIFFUSE,
        /**
         * No lighting is applied.
         */
        NONE
    }

    /**
     * Derives the shader info from the pipeline definition, mirroring the behavior of the vanilla core shaders. This is
     * used for all pipelines that no {@link SceneShaderInfoProvider} handles.
     */
    public static SceneShaderInfo fromPipeline(RenderPipeline pipeline) {
        var defines = pipeline.getShaderDefines();
        var samplers = pipeline.getSamplers();

        // Entity and item shaders apply directional lighting based on the Lighting uniform
        var usesDirectionalLight = pipeline.getUniforms().stream().anyMatch(u -> u.name().equals("Lighting"))
                && !defines.flags().contains("NO_CARDINAL_LIGHTING");
        Lighting lighting;
        if (usesDirectionalLight) {
            lighting = Lighting.DIFFUSE;
        } else if (samplers.contains("Sampler2")) {
            lighting = Lighting.LIGHTMAP;
        } else {
            lighting = Lighting.NONE;
        }

        return new SceneShaderInfo(
                lighting,
                getAlphaTest(pipeline),
                pipeline.getVertexFormat().contains(VertexFormatElement.COLOR),
                samplers.contains("Sampler0"));
    }

    private static float getAlphaTest(RenderPipeline pipeline) {
        var alphaCutout = pipeline.getShaderDefines().values().get("ALPHA_CUTOUT");
        if (alphaCutout != null) {
            try {
                return Float.parseFloat(alphaCutout);
            } catch (NumberFormatException e) {
                LOG.warn("Failed to parse ALPHA_CUTOUT value {} of pipeline {}", alphaCutout, pipeline.getLocation());
            }
        }

        // Some vanilla fragment shaders discard with a hardcoded threshold
        var fragmentShader = pipeline.getFragmentShader();
        if (fragmentShader.getNamespace().equals(Identifier.DEFAULT_NAMESPACE)
                && HARDCODED_ALPHA_TEST_SHADERS.stream().anyMatch(fragmentShader.getPath()::startsWith)) {
            return 0.1f;
        }

        return 0;
    }
}
