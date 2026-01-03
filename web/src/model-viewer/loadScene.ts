import * as flatbuffers from "flatbuffers";
import { ExpScene } from "@generated/scene.ts";
import { Group, Mesh, Texture } from "three";
import TextureManager from "./TextureManager.ts";
import loadGeometry from "./loadGeometry.ts";
import loadMaterial from "./loadMaterial.ts";
import decompress from "../decompress.ts";

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
    );
    const mesh = new Mesh(geometry, material);
    mesh.frustumCulled = false;
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
        createImageBitmap(
          sourceData,
          frameX,
          frameY,
          animatedTexture.width(),
          animatedTexture.height(),
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

  const cameraProps = {
    yaw: expCamera.yaw(),
    pitch: expCamera.pitch(),
    roll: expCamera.roll(),
    zoom: expCamera.zoom(),
  };

  return { cameraProps, group, animatedTextureParts };
}
