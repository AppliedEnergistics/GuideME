/**
 * Contains information about the different shaders used in Minecraft,
 * and how that translates into properties of how we render the geometry
 * using them.
 */

export type ShaderProps = {
  /**
   * "lightmap" means the lighting is already pre-baked into the vertex colors.
   * This has the same effect as "none", since both will not apply additional
   * diffuse lighting.
   */
  lighting: "lightmap" | "diffuse" | "none";
  alphaTest: number | null;
  vertexColor: boolean;
  textured: boolean;
};

// Properties from Shaders found in MC
// For "lighting", check if the vertex-shader samples the lightmap (i.e. via minecraft_sample_lightmap)
// vertexColor should be true if the shader uses vertex-specific color (and i.e. maintains it even after sampling
// the lightmap)
// "textured" indicates that the fragment shader samples the texture and uses its color
// "alphaTest" indicates the alpha value under which the fragment is discarded
const shaderInfos: Record<string, ShaderProps> = {
  rendertype_solid: {
    lighting: "lightmap",
    vertexColor: true,
    textured: true,
    alphaTest: null,
  },
  rendertype_cutout: {
    lighting: "lightmap",
    alphaTest: 0.1,
    vertexColor: true,
    textured: true,
  },
  rendertype_cutout_mipped: {
    lighting: "lightmap",
    alphaTest: 0.5,
    vertexColor: true,
    textured: true,
  },
  rendertype_translucent: {
    lighting: "lightmap",
    alphaTest: null,
    vertexColor: true,
    textured: true,
  },
  rendertype_tripwire: {
    lighting: "lightmap",
    alphaTest: 0.1,
    vertexColor: true,
    textured: true,
  },
  rendertype_entity_solid: {
    lighting: "diffuse",
    alphaTest: null,
    vertexColor: true,
    textured: true,
  },
  rendertype_entity_cutout: {
    lighting: "diffuse",
    alphaTest: 0.1,
    vertexColor: true,
    textured: true,
  },
  position_color: {
    lighting: "none",
    alphaTest: 0,
    vertexColor: true,
    textured: false,
  },
  rendertype_entity_cutout_no_cull: {
    lighting: "diffuse",
    alphaTest: 0.1,
    vertexColor: true,
    textured: true,
  },
  rendertype_entity_translucent_cull: {
    lighting: "diffuse",
    alphaTest: 0.1,
    vertexColor: true,
    textured: true,
  },
  rendertype_text: {
    lighting: "none",
    alphaTest: 0.1,
    vertexColor: true,
    textured: true,
  },
};

export default shaderInfos;
