import TextureManager from "./TextureManager.ts";
import * as THREE from "three";
import { Group, Object3D, Sprite } from "three";
import diamond from "@assets/diamond.png";
import diamondColored from "@assets/diamond_colored.png";
import { OverlayAnnotation } from "./modelViewer.ts";

export default async function buildOverlayAnnotation(
  textureManager: TextureManager,
  annotation: OverlayAnnotation,
): Promise<Object3D> {
  // Add "diamond overlays"
  const diamondTexture = await textureManager.get(
    diamond.src,
    false,
    false,
    true,
  );
  diamondTexture.wrapS = THREE.ClampToEdgeWrapping;
  diamondTexture.wrapT = THREE.ClampToEdgeWrapping;

  const diamondColoredTexture = await textureManager.get(
    diamondColored.src,
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

  const group = new Group();

  const spriteScale = 1 / 4;

  const annotationNodeBottom = new Sprite(diamondMaterial);
  annotationNodeBottom.position.set(
    annotation.position[0],
    annotation.position[1],
    annotation.position[2],
  );
  annotationNodeBottom.scale.set(spriteScale, spriteScale, 1);
  annotationNodeBottom.userData.annotation = annotation;
  annotationNodeBottom.renderOrder = 999999;
  group.add(annotationNodeBottom);

  const diamondColoredMaterial = new THREE.SpriteMaterial({
    map: diamondColoredTexture,
    transparent: true,
    depthTest: false,
    sizeAttenuation: false,
    fog: false,
    color: annotation.color,
  });
  const annotationNodeTop = new Sprite(diamondColoredMaterial);
  annotationNodeTop.position.set(
    annotation.position[0],
    annotation.position[1],
    annotation.position[2],
  );
  annotationNodeTop.renderOrder = 999999;
  annotationNodeTop.scale.set(spriteScale, spriteScale, 1);
  annotationNodeTop.userData.annotation = annotation;
  group.add(annotationNodeTop);
  return group;
}
