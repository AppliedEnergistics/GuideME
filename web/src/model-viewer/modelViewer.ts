import * as THREE from "three";
import {
  Box3,
  Camera,
  Euler,
  GridHelper,
  LineBasicMaterial,
  MathUtils,
  Raycaster,
  Scene,
  Vector2,
  Vector3,
} from "three";

import { OrbitControls } from "three/addons/controls/OrbitControls.js";
import loadScene from "./loadScene.ts";

import { buildInWorldAnnotation } from "./buildInWorldAnnotation.ts";
import addLevelLighting from "./addSceneLighting.ts";
import buildOverlayAnnotation from "./buildOverlayAnnotations.ts";
import TextureManager from "./TextureManager.ts";
import tippy from "tippy.js";

const DEBUG = false;

interface ControlInterface {
  zoomIn(): void;

  zoomOut(): void;

  resetView(): void;

  dispose(): void;
}

export type Annotation = OverlayAnnotation | InWorldAnnotation;

export type OverlayAnnotation = {
  type: "overlay";
  position: [number, number, number];
  color: string;
  contentTemplateId: string;
};

export type InWorldAnnotation = InWorldBoxAnnotation | InWorldLineAnnotation;

export type InWorldBoxAnnotation = {
  type: "box";
  minCorner: [number, number, number];
  maxCorner: [number, number, number];
  color: string;
  thickness?: number;
  contentTemplateId: string;
  alwaysOnTop: boolean;
};

export type InWorldLineAnnotation = {
  type: "line";
  from: [number, number, number];
  to: [number, number, number];
  color: string;
  thickness?: number;
  contentTemplateId: string;
  alwaysOnTop: boolean;
};

const raycaster = new Raycaster();

function getTooltipContent(
  mousePos: Vector2,
  camera: Camera,
  scene: Scene,
): string | undefined {
  raycaster.setFromCamera(mousePos, camera);
  const intersections = raycaster.intersectObjects(scene.children);
  for (const intersection of intersections) {
    const object = intersection.object;

    const annotation = object.userData.annotation as Annotation;
    if (annotation && annotation.contentTemplateId) {
      return annotation.contentTemplateId;
    }
  }
}

async function initialize(
  assetBaseUrl: string,
  source: string,
  viewportEl: HTMLDivElement,
  cameraControls: boolean,
  inWorldAnnotations: InWorldAnnotation[] | undefined = [],
  overlayAnnotations: OverlayAnnotation[] | undefined = [],
  mousePosRef: { current: Vector2 | null },
  setTooltipObject: (templateId: string | undefined) => void,
  abortSignal: AbortSignal,
  originalWidth: number,
): Promise<ControlInterface> {
  const renderer = new THREE.WebGLRenderer({
    alpha: true,
    premultipliedAlpha: false,
  });
  renderer.useLegacyLights = true;
  renderer.outputColorSpace = THREE.LinearSRGBColorSpace;

  const textureManager = new TextureManager(assetBaseUrl);

  const { cameraProps, group, animatedTextureParts } = await loadScene(
    textureManager,
    source,
    abortSignal,
  );

  // Center the scene
  const sceneBounds = new Box3();
  sceneBounds.expandByObject(group);
  const sceneCenter = sceneBounds.getCenter(new Vector3());
  group.position.copy(sceneCenter.clone().negate());

  // Add a plane for orientation if camera controls are enabled
  if (cameraControls) {
    // Get the extent on the x/z axis
    const sceneSize = sceneBounds.getSize(new Vector3());
    const gridDim = Math.max(sceneSize.x, sceneSize.z) + 2;

    const grid = new GridHelper(gridDim, gridDim, 0xffffffff, 0xffffffff);
    grid.material = new LineBasicMaterial({
      transparent: true,
      opacity: 0.5,
    });
    grid.position.copy(new Vector3(sceneCenter.x, 0, sceneCenter.z));
    group.add(grid);
  }

  const scene = new THREE.Scene();
  addLevelLighting(scene);
  scene.add(group);

  for (const annotation of inWorldAnnotations) {
    group.add(buildInWorldAnnotation(annotation));
  }

  for (const annotation of overlayAnnotations) {
    group.add(await buildOverlayAnnotation(textureManager, annotation));
  }

  const camera = new THREE.OrthographicCamera();
  camera.near = 0;
  camera.far = 30000;

  const updateViewportSize = (width: number, height: number) => {
    renderer.setSize(width, height);
    renderer.setPixelRatio(window.devicePixelRatio);
    // We only scale down, not up
    const scaling = Math.min(1, width / (originalWidth * 3));
    camera.zoom = (1 / 0.625) * 16 * cameraProps.zoom * scaling;
    camera.left = -width / 2;
    camera.right = width / 2;
    camera.top = height / 2;
    camera.bottom = -height / 2;
    camera.updateProjectionMatrix();
  };
  updateViewportSize(viewportEl.offsetWidth, viewportEl.offsetHeight);

  camera.position.set(0, 0, 15);
  // We are rotating the camera position here instead of the scene,
  // which is why the angles are in reverse
  camera.position.applyEuler(
    new Euler(
      MathUtils.degToRad(-cameraProps.pitch),
      MathUtils.degToRad(-cameraProps.yaw),
      MathUtils.degToRad(-cameraProps.roll),
      "YXZ",
    ),
  );
  camera.updateProjectionMatrix();
  scene.add(camera);

  if (DEBUG) {
    const axesHelper = new THREE.AxesHelper(32);
    axesHelper.material = new LineBasicMaterial({
      vertexColors: true,
      toneMapped: false,
      depthTest: false,
      depthWrite: false,
    });
    scene.add(axesHelper);
  }

  let controls: OrbitControls | undefined;
  if (cameraControls) {
    controls = new OrbitControls(camera, viewportEl);
    controls.enableZoom = false;
    controls.update();
  } else {
    camera.lookAt(new Vector3());
  }

  // Declare a resize observer to automatically resize the viewport
  let resizeObserver: ResizeObserver | undefined;
  if (typeof ResizeObserver !== "undefined") {
    resizeObserver = new ResizeObserver((entries) => {
      for (const entry of entries) {
        if (entry.contentBoxSize) {
          const { inlineSize: width, blockSize: height } =
            entry.contentBoxSize[0]!;

          updateViewportSize(width, height);

          if (cameraControls) {
            controls?.dispose();
            controls = new OrbitControls(camera, viewportEl);
            controls.enableZoom = false;
            controls.update();
          } else {
            camera.lookAt(new Vector3());
          }
        }
      }
    });
    resizeObserver.observe(viewportEl, {
      box: "content-box",
    });
  }

  let disposed = false;

  let nextTick = 0;
  const animate = function (time: number) {
    if (disposed) {
      return;
    }

    controls?.update();

    // Update textures

    if (time > nextTick) {
      nextTick = time + 1000 / 20;

      for (const animatedPart of animatedTextureParts) {
        const { x, y, frameTextures, frames, currentFrame } = animatedPart;
        const frame = frames[currentFrame];
        if (!frame) {
          continue;
        }
        if (++animatedPart.subFrame >= frame.time) {
          animatedPart.currentFrame = (currentFrame + 1) % frames.length;
          animatedPart.subFrame = 0;
        }

        for (const targetTexture of animatedPart.targetTextures) {
          renderer.copyTextureToTexture(
            new Vector2(x, y),
            frameTextures[frame.index]!,
            targetTexture,
          );
        }
      }
    }

    renderer.render(scene, camera);

    // Update what's under the mouse
    const mousePos = mousePosRef.current;
    if (mousePos) {
      setTooltipObject(getTooltipContent(mousePos, camera, scene));
    } else {
      setTooltipObject(undefined);
    }
  };

  renderer.setAnimationLoop(animate);

  viewportEl.append(renderer.domElement);

  return {
    dispose(): void {
      if (!disposed) {
        console.debug("Disposing model viewer for %s", source);
        disposed = true;
        if (resizeObserver) {
          resizeObserver.disconnect();
        }
        viewportEl.removeChild(renderer.domElement);
        renderer.dispose();
        controls?.dispose();
        setTooltipObject(undefined);
      }
    },
    resetView(): void {
      controls?.reset();
    },
    zoomIn(): void {
      if (controls) {
        controls.enableZoom = true;
        try {
          for (let i = 0; i < 5; i++) {
            const e = new WheelEvent("wheel", {
              deltaY: -120,
            });
            controls.domElement.dispatchEvent(e);
          }
        } finally {
          controls.enableZoom = false;
        }
      }
    },
    zoomOut(): void {
      if (controls) {
        controls.enableZoom = true;
        try {
          for (let i = 0; i < 5; i++) {
            const e = new WheelEvent("wheel", {
              deltaY: 120,
            });
            controls.domElement.dispatchEvent(e);
          }
        } finally {
          controls.enableZoom = false;
        }
      }
    },
  };
}

export async function setupGameScene(element: HTMLElement) {
  const sceneAssetPrefix = element.dataset.sceneAssetPrefix;
  if (sceneAssetPrefix === undefined) {
    console.error(
      "Scene %o is missing required 'data-scene-asset-prefix' attribute",
      element,
    );
    return;
  }
  const sceneSrc = element.dataset.sceneSrc;
  if (!sceneSrc) {
    console.error(
      "Scene %o is missing required 'data-scene-src' attribute",
      element,
    );
    return;
  }
  const sceneBackground = element.dataset.sceneBackground; // Optional
  let widthStr = element.dataset.sceneWidth;
  if (!widthStr) {
    console.error(
      "Scene %o is missing required 'data-scene-width' attribute",
      element,
    );
    return;
  }
  let heightStr = element.dataset.sceneHeight;
  if (!heightStr) {
    console.error(
      "Scene %o is missing required 'data-scene-height' attribute",
      element,
    );
    return;
  }
  const width = parseInt(widthStr);
  const height = parseInt(heightStr);
  const interactive = "true" === element.dataset.sceneInteractive;

  const document = element.ownerDocument;
  const wrapperElement = document.createElement("div");
  wrapperElement.classList.add("game-scene-wrapper");
  if (sceneBackground) {
    wrapperElement.style.background = sceneBackground;
  }
  wrapperElement.style.setProperty(
    "--modelviewer-width",
    `calc(${width}px * var(--gui-scale))`,
  );
  wrapperElement.style.setProperty(
    "--modelviewer-height",
    `calc(${height}px * var(--gui-scale))`,
  );
  wrapperElement.style.setProperty(
    "--modelviewer-aspect-ratio",
    (width / height).toString(),
  );

  const rootElement = document.createElement("div");
  rootElement.className = "root";
  wrapperElement.append(rootElement);

  const viewportElement = document.createElement("div");
  viewportElement.className = "viewport";
  rootElement.append(viewportElement);

  let currentTooltipTemplateId: string | undefined = undefined;
  const setTooltipContent = (templateId: string | undefined) => {
    if (templateId !== currentTooltipTemplateId) {
      currentTooltipTemplateId = templateId;
      console.info("Setting tooltip content to template %s", templateId);
    }
  };

  const mousePos: { current: Vector2 | null } = {
    current: null,
  };
  rootElement.addEventListener("mousemove", (e) => {
    const canvas = viewportElement.querySelector("canvas");
    if (!canvas) {
      return;
    }

    const clientRect = canvas.getBoundingClientRect();
    let x = (e.clientX - clientRect.x) / clientRect.width;
    let y = (e.clientY - clientRect.y) / clientRect.height;

    x = x * 2 - 1;
    y = -(y * 2 - 1);

    mousePos.current = new Vector2(x, y);
  });
  rootElement.addEventListener("mouseleave", () => {
    mousePos.current = null;
    setTooltipContent(undefined);
  });

  console.debug("Initializing game scene from %s on %o", sceneSrc, element);
  const abortController = new AbortController();
  const controller = await initialize(
    sceneAssetPrefix,
    sceneSrc,
    viewportElement,
    interactive,
    [],
    [],
    mousePos,
    setTooltipContent,
    abortController.signal,
    width,
  );

  if (interactive) {
    const controlsWrapper = document.createElement("div");
    controlsWrapper.className = "controls";
    wrapperElement.append(controlsWrapper);

    const zoomInButton = document.createElement("button");
    zoomInButton.className = "minecraft-tooltip";
    zoomInButton.dataset.tooltipText = "Zoom in";
    zoomInButton.append("+");
    zoomInButton.addEventListener("click", (e) => {
      e.preventDefault();
      controller.zoomIn();
    });
    tippy(zoomInButton, {
      content: "Zoom in",
    });
    controlsWrapper.append(zoomInButton);

    const zoomOutButton = document.createElement("button");
    zoomOutButton.className = "minecraft-tooltip";
    zoomOutButton.dataset.tooltipText = "Zoom out";
    zoomOutButton.append("-");
    zoomOutButton.addEventListener("click", (e) => {
      e.preventDefault();
      controller.zoomOut();
    });
    tippy(zoomOutButton, {
      content: "Zoom out",
    });
    controlsWrapper.append(zoomOutButton);

    const resetButton = document.createElement("button");
    resetButton.append("R");
    resetButton.addEventListener("click", (e) => {
      e.preventDefault();
      controller.resetView();
    });
    controlsWrapper.append(resetButton);
    tippy(resetButton, {
      content: "Reset view",
    });
  }

  element.insertAdjacentElement("afterend", wrapperElement);
  element.remove();
}
