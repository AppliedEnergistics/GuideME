import { ExpMaterial } from "@generated/scene/exp-material.ts";
import {
  AdditiveBlending,
  CustomBlending,
  DstColorFactor,
  Material,
  MeshBasicMaterial,
  MeshBasicMaterialParameters,
  MeshLambertMaterial,
  MeshLambertMaterialParameters,
  NoBlending,
  OneFactor,
  OneMinusSrcAlphaFactor,
  SrcAlphaFactor,
  SrcColorFactor,
  Texture,
  ZeroFactor,
} from "three";
import shaderInfos from "./shaderInfo.ts";
import TextureManager from "./TextureManager.ts";
import { ExpTransparency } from "@generated/scene/exp-transparency.ts";

function setBlending(
  expMaterial: ExpMaterial,
  materialParams: MeshLambertMaterialParameters,
) {
  switch (expMaterial.transparency()) {
    case ExpTransparency.DISABLED:
      materialParams.blending = NoBlending;
      break;
    case ExpTransparency.ADDITIVE:
      materialParams.blending = AdditiveBlending;
      break;
    case ExpTransparency.LIGHTNING:
      materialParams.blending = CustomBlending;
      materialParams.blendSrc = SrcAlphaFactor;
      materialParams.blendDst = OneFactor;
      break;
    case ExpTransparency.GLINT:
      materialParams.blending = CustomBlending;
      materialParams.blendSrc = SrcColorFactor;
      materialParams.blendDst = OneFactor;
      materialParams.blendSrcAlpha = ZeroFactor;
      materialParams.blendDstAlpha = OneFactor;
      break;
    case ExpTransparency.CRUMBLING:
      materialParams.blending = CustomBlending;
      materialParams.blendSrc = DstColorFactor;
      materialParams.blendDst = SrcColorFactor;
      materialParams.blendSrcAlpha = OneFactor;
      materialParams.blendDstAlpha = ZeroFactor;
      break;
    case ExpTransparency.TRANSLUCENT:
      materialParams.blending = CustomBlending;
      materialParams.blendSrc = SrcAlphaFactor;
      materialParams.blendDst = OneMinusSrcAlphaFactor;
      materialParams.blendSrcAlpha = OneFactor;
      materialParams.blendDstAlpha = OneMinusSrcAlphaFactor;
      break;
  }
}

export default async function loadMaterial(
  textureManager: TextureManager,
  expMaterial: ExpMaterial,
  texturesById: Map<string, Texture[]>,
): Promise<Material> {
  const samplers: (Texture | null)[] = [];
  for (let i = 0; i < expMaterial.samplersLength(); ++i) {
    const sampler = expMaterial.samplers(i);
    if (!sampler) {
      console.warn("Material is missing sampler build-data %d", i);
      continue;
    }

    const textureUrl = sampler.texture();
    if (!textureUrl) {
      console.warn("Sampler %o is missing texture.", i);
      samplers[i] = null;
      continue;
    }

    const texture = await textureManager.get(
      textureUrl,
      sampler.linearFiltering(),
      sampler.useMipmaps(),
    );
    samplers.push(texture);

    const textureId = sampler.textureId();
    if (textureId) {
      const textures = texturesById.get(textureId);
      if (!textures) {
        texturesById.set(textureId, [texture]);
      } else if (!textures.includes(texture)) {
        textures.push(texture);
      }
    }
  }

  // These parameters are usually set via the shader
  const materialName = expMaterial.name();
  const shaderName = expMaterial.shaderName() ?? "none";
  const shaderProps = shaderInfos[shaderName];
  const materialParams:
    | MeshLambertMaterialParameters
    | MeshBasicMaterialParameters = {};
  setBlending(expMaterial, materialParams);
  let material: Material;

  if (!shaderProps) {
    console.warn("Unknown shader: %s", shaderName);
    material = new MeshBasicMaterial({ color: "red" });
  } else {
    if (shaderProps.alphaTest !== null) {
      materialParams.alphaTest = shaderProps.alphaTest;
    }
    materialParams.vertexColors = shaderProps.vertexColor;

    if (shaderProps.textured) {
      materialParams.map = samplers[0];
    }

    switch (shaderProps.lighting) {
      // Uses pre-baked diffuse lighting, meaning it's effectively unlit,
      // since we don't actually do a lightmap here
      case "lightmap":
      case "none":
        material = new MeshBasicMaterial(
          materialParams as MeshBasicMaterialParameters,
        );
        break;
      case "diffuse":
        material = new MeshLambertMaterial(materialParams);
        break;
    }
  }

  material.name = materialName ?? "";

  return material;
}
