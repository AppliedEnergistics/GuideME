import { ExpShaderInfo } from "@generated/scene/exp-shader-info.ts";
import { ExpShaderLighting } from "@generated/scene/exp-shader-lighting.ts";

/**
 * Contains information about the different shaders used in Minecraft,
 * and how that translates into properties of how we render the geometry
 * using them.
 *
 * Newer scene exports include this information directly (see {@link fromExpShaderInfo}).
 * The known shaders below are only used as a fallback for older exports.
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

  // Minecraft 26.1+ exports name the render pipeline instead of the shader.
  // Values are copied from Minecraft's RenderPipelines class.
  "minecraft:pipeline/solid_block": {
    lighting: "lightmap",
    alphaTest: null,
    vertexColor: true,
    textured: true,
  },
  "minecraft:pipeline/cutout_block": {
    lighting: "lightmap",
    alphaTest: 0.5,
    vertexColor: true,
    textured: true,
  },
  "minecraft:pipeline/translucent_block": {
    lighting: "lightmap",
    alphaTest: 0.01,
    vertexColor: true,
    textured: true,
  },
  "minecraft:pipeline/entity_solid": {
    lighting: "diffuse",
    alphaTest: null,
    vertexColor: true,
    textured: true,
  },
  "minecraft:pipeline/entity_cutout": {
    lighting: "diffuse",
    alphaTest: 0.1,
    vertexColor: true,
    textured: true,
  },

  // Note: some 26.1 exports draw blocks with entity_cutout_cull / entity_translucent_cull.
  // Those blocks already have their shading in the vertex colors, so they use "lightmap"
  //
  // Once GuideME changes roll out to use the _block pipelines for blocks & AE2 publishes new data,
  // lighting can/should change to "diffuse" (and entity_translucent_cull can be removed)
  "minecraft:pipeline/entity_cutout_cull": {
    lighting: "lightmap",
    alphaTest: 0.1,
    vertexColor: true,
    textured: true,
  },
  "minecraft:pipeline/entity_translucent_cull": {
    lighting: "lightmap",
    alphaTest: 0.1,
    vertexColor: true,
    textured: true,
  },
  "minecraft:pipeline/item_cutout": {
    lighting: "diffuse",
    alphaTest: 0.1,
    vertexColor: true,
    textured: true,
  },
  "minecraft:pipeline/item_translucent": {
    lighting: "diffuse",
    alphaTest: 0.1,
    vertexColor: true,
    textured: true,
  },
  "minecraft:pipeline/text": {
    lighting: "none",
    alphaTest: 0.1,
    vertexColor: true,
    textured: true,
  },
  "minecraft:pipeline/text_polygon_offset": {
    lighting: "none",
    alphaTest: 0.1,
    vertexColor: true,
    textured: true,
  },
  "ae2:pipeline/storage_cell_leds": {
    lighting: "none",
    alphaTest: 0,
    vertexColor: true,
    textured: false,
  },
};

export default shaderInfos;

/**
 * Converts shader properties that were exported alongside the scene.
 */
export function fromExpShaderInfo(expShaderInfo: ExpShaderInfo): ShaderProps {
  let lighting: ShaderProps["lighting"];
  switch (expShaderInfo.lighting()) {
    case ExpShaderLighting.LIGHTMAP:
      lighting = "lightmap";
      break;
    case ExpShaderLighting.DIFFUSE:
      lighting = "diffuse";
      break;
    case ExpShaderLighting.NONE:
    default:
      lighting = "none";
      break;
  }

  const alphaTest = expShaderInfo.alphaTest();
  return {
    lighting,
    alphaTest: alphaTest > 0 ? alphaTest : null,
    vertexColor: expShaderInfo.vertexColor(),
    textured: expShaderInfo.textured(),
  };
}
