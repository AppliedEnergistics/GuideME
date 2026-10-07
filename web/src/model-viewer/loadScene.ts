import * as flatbuffers from "flatbuffers";
import { ExpScene } from "@generated/scene.ts";
import { Group, Material, Mesh, Texture, Vector3 } from "three";
import { ExpMaterial } from "@generated/scene/exp-material.ts";
import { ExpTransparency } from "@generated/scene/exp-transparency.ts";
import TextureManager from "./TextureManager.ts";
import loadGeometry from "./loadGeometry.ts";
import loadMaterial from "./loadMaterial.ts";
import decompress from "../decompress.ts";
import { fromExpShaderInfo, ShaderProps } from "./shaderInfo.ts";

type LoadedScene = {
  group: Group;
  cameraProps: CameraProps;
  animatedTextureParts: AnimatedTexturePart[];
};

export type CameraProps = {
  yaw: number;
  pitch: number;
  roll: number;
  zoom: number;
  /**
   * The world position shown at the center of the viewport.
   * Scenes exported by older versions don't include it.
   */
  center?: Vector3;
};

export type AnimatedTextureFrame = {
  index: number;
  time: number;
};

/**
 * Implements Minecraft animated sprites, which update parts of a larger atlas texture.
 */
export type AnimatedTexturePart = {
  targetTextures: Texture[];
  frameTextures: Texture[];
  frames: AnimatedTextureFrame[];
  x: number;
  y: number;
  currentFrame: number;
  subFrame: number;
};

async function decompressResponse(response: Response) {
  const blob = await response.blob();
  response = await decompress(blob);
  const sceneContent = await response.arrayBuffer();

  console.debug(
    "Loaded %s, %d byte compressed, %d byte uncompressed",
    response.url,
    blob.size,
    sceneContent.byteLength,
  );

  return sceneContent;
}

/**
 * Draw meshes in the same order Minecraft draws its layers: solid, then cutout, then blended.
 * Models often have coplanar overlay quads (i.e. emissive parts) that only show up if they're drawn
 * after the base quads. Newer exports don't order the meshes that way, and three.js would otherwise
 * draw opaque meshes in material creation order (i.e. the order they appear in the export).
 */
function getRenderOrder(expMaterial: ExpMaterial, material: Material): number {
  if (expMaterial.transparency() !== ExpTransparency.DISABLED) {
    return 2;
  } else if (material.alphaTest > 0) {
    return 1;
  } else {
    return 0;
  }
}

export default async function loadScene(
  textureManager: TextureManager,
  source: string,
  abortSignal: AbortSignal,
): Promise<LoadedScene> {
  const response = await fetch(source, { signal: abortSignal });
  if (!response.ok) {
    throw response;
  }

  const arrayBuffer = await decompressResponse(response);

  const data = new Uint8Array(arrayBuffer);
  const buf = new flatbuffers.ByteBuffer(data);

  const group = new Group();
  const texturesById = new Map<string, Texture[]>();
  const expScene = ExpScene.getRootAsExpScene(buf);

  const shaderInfos = new Map<string, ShaderProps>();
  for (let i = 0; i < expScene.shadersLength(); i++) {
    const expShaderInfo = expScene.shaders(i);
    const name = expShaderInfo?.name();
    if (expShaderInfo && name) {
      shaderInfos.set(name, fromExpShaderInfo(expShaderInfo));
    }
  }

  for (let i = 0; i < expScene.meshesLength(); i++) {
    const expMesh = expScene.meshes(i);
    if (!expMesh) {
      continue;
    }
    const expMaterial = expMesh.material();
    if (!expMaterial) {
      console.warn("Missing material for mesh %o", i);
      continue;
    }

    const geometry = loadGeometry(expMesh);
    const material = await loadMaterial(
      textureManager,
      expMaterial,
      texturesById,
      shaderInfos,
    );
    const mesh = new Mesh(geometry, material);
    mesh.frustumCulled = false;
    mesh.renderOrder = getRenderOrder(expMaterial, material);
    group.add(mesh);
  }

  const expCamera = expScene.camera();
  if (!expCamera) {
    throw new Error("Scene is missing camera settings");
  }

  const animatedTextureParts: AnimatedTexturePart[] = [];
  for (let i = 0; i < expScene.animatedTexturesLength(); i++) {
    const animatedTexture = expScene.animatedTextures(i);
    if (!animatedTexture) {
      continue;
    }

    const framesPath = animatedTexture.framesPath();
    if (!framesPath) {
      continue;
    }

    const fullUrl = textureManager.getFullUrl(framesPath);
    const sourceDataResponse = await fetch(fullUrl, { signal: abortSignal });
    if (!sourceDataResponse.ok) {
      console.error(
        "Failed to retrieve animated texture %s: %o",
        fullUrl,
        sourceDataResponse,
      );
      continue;
    }
    const sourceData = await sourceDataResponse.blob();

    // Splice it up into frames
    const sourceFramePromises: Promise<ImageBitmap>[] = [];
    for (let j = 0; j < animatedTexture.frameCount(); j++) {
      const frameX =
        (j % animatedTexture.framesPerRow()) * animatedTexture.width();
      const frameY =
        Math.floor(j / animatedTexture.framesPerRow()) *
        animatedTexture.height();

      sourceFramePromises.push(
        // Decode the frames like the texture they're copied into (see TextureManager)
        createImageBitmap(
          sourceData,
          frameX,
          frameY,
          animatedTexture.width(),
          animatedTexture.height(),
          { imageOrientation: "none", premultiplyAlpha: "none" },
        ),
      );
    }
    const frameTextures = (await Promise.allSettled(sourceFramePromises)).map(
      (result) => {
        if (result.status === "fulfilled") {
          return new Texture(result.value);
        } else {
          return null!; // TODO
        }
      },
    );

    const targetTextures =
      texturesById.get(animatedTexture.textureId() ?? "") ?? [];
    if (!targetTextures.length) {
      continue;
    }

    const frames: AnimatedTextureFrame[] = [];
    for (let j = 0; j < animatedTexture.framesLength(); j++) {
      const expFrame = animatedTexture.frames(j);
      if (expFrame) {
        frames.push({
          index: expFrame.index(),
          time: expFrame.time(),
        });
      }
    }

    animatedTextureParts.push({
      frameTextures,
      targetTextures,
      frames,
      x: animatedTexture.x(),
      y: animatedTexture.y(),
      currentFrame: 0,
      subFrame: 0,
    });
  }

  const cameraProps: CameraProps = {
    yaw: expCamera.yaw(),
    pitch: expCamera.pitch(),
    roll: expCamera.roll(),
    zoom: expCamera.zoom(),
  };

  const expCameraCenter = expScene.cameraCenter();
  if (expCameraCenter) {
    cameraProps.center = new Vector3(
      expCameraCenter.x(),
      expCameraCenter.y(),
      expCameraCenter.z(),
    );
  }

  return { cameraProps, group, animatedTextureParts };
}
