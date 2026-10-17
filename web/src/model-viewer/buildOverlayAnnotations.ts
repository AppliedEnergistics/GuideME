import TextureManager from "./TextureManager.ts";
import * as THREE from "three";
import { Group, Object3D, Sprite } from "three";
import diamond from "@assets/diamond.png";
import diamondColored from "@assets/diamond_colored.png";
import { OverlayAnnotation } from "./modelViewer.ts";
import { parseAnnotationColor } from "./annotationColor.ts";

/**
 * Bundled assets are relative to the bundle, not the page.
 */
function bundledAssetUrl(url: string): string {
  return new URL(url, import.meta.url).href;
}

export default async function buildOverlayAnnotation(
  textureManager: TextureManager,
  annotation: OverlayAnnotation,
): Promise<Object3D> {
  // Add "diamond overlays"
  const diamondTexture = await textureManager.get(
    bundledAssetUrl(diamond),
    false,
    false,
    true,
  );
  diamondTexture.wrapS = THREE.ClampToEdgeWrapping;
  diamondTexture.wrapT = THREE.ClampToEdgeWrapping;

  const diamondColoredTexture = await textureManager.get(
    bundledAssetUrl(diamondColored),
    false,
    false,
    true,
  );
  diamondColoredTexture.wrapS = THREE.ClampToEdgeWrapping;
  diamondColoredTexture.wrapT = THREE.ClampToEdgeWrapping;

  const diamondMaterial = new THREE.SpriteMaterial({
    map: diamondTexture,
    transparent: true,
    depthTest: false,
    sizeAttenuation: false,
    fog: false,
  });

  // The size of the sprites is updated for every frame, to keep it constant on screen (see modelViewer.ts)
  const group = new Group();

  const annotationNodeBottom = new Sprite(diamondMaterial);
  annotationNodeBottom.position.set(
    annotation.position[0],
    annotation.position[1],
    annotation.position[2],
  );
  annotationNodeBottom.userData.annotation = annotation;
  annotationNodeBottom.renderOrder = 999999;
  group.add(annotationNodeBottom);

  const diamondColoredMaterial = new THREE.SpriteMaterial({
    map: diamondColoredTexture,
    transparent: true,
    depthTest: false,
    sizeAttenuation: false,
    fog: false,
    color: parseAnnotationColor(annotation.color),
  });
  const annotationNodeTop = new Sprite(diamondColoredMaterial);
  annotationNodeTop.position.set(
    annotation.position[0],
    annotation.position[1],
    annotation.position[2],
  );
  annotationNodeTop.renderOrder = 999999;
  annotationNodeTop.userData.annotation = annotation;
  group.add(annotationNodeTop);
  return group;
}
