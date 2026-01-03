import {
  Box3,
  BoxGeometry,
  BufferAttribute,
  BufferGeometry,
  Color,
  GreaterDepth,
  Group,
  Mesh,
  MeshBasicMaterial,
  MeshLambertMaterial,
  Uint16BufferAttribute,
  Vector3,
} from "three";
import { InWorldAnnotation } from "./modelViewer.ts";

const DEFAULT_THICKNESS = 0.5 / 16;

class GeometryBuilder {
  private positions: number[] = [];
  private normals: number[] = [];
  private indices: number[] = [];
  private vertexCount = 0;

  quad(
    faceNormal: Vector3,
    v1: Vector3,
    v2: Vector3,
    v3: Vector3,
    v4: Vector3,
  ): void {
    const startIndex = this.positions.length;

    faceNormal.toArray(this.normals, startIndex);
    faceNormal.toArray(this.normals, startIndex + 3);
    faceNormal.toArray(this.normals, startIndex + 6);
    faceNormal.toArray(this.normals, startIndex + 9);
    v1.toArray(this.positions, startIndex);
    v2.toArray(this.positions, startIndex + 3);
    v3.toArray(this.positions, startIndex + 6);
    v4.toArray(this.positions, startIndex + 9);

    // Push indices for two tris building the quad
    const startVertex = this.vertexCount;
    this.indices.push(
      startVertex,
      startVertex + 1,
      startVertex + 2,
      startVertex + 2,
      startVertex + 3,
      startVertex,
    );

    this.vertexCount += 4;
  }

  build() {
    // After all annotations have been processed, we create a BufferGeometry and add it to the scene.
    const geometry = new BufferGeometry();
    geometry.setIndex(new Uint16BufferAttribute(this.indices, 1));
    geometry.setAttribute(
      "position",
      new BufferAttribute(new Float32Array(this.positions), 3),
    );
    geometry.setAttribute(
      "normal",
      new BufferAttribute(new Float32Array(this.normals), 3),
    );
    return geometry;
  }
}

function strut(
  builder: GeometryBuilder,
  from: Vector3,
  to: Vector3,
  thickness: number,
  startCap: boolean,
  endCap: boolean,
): void {
  const norm = new Vector3().subVectors(to, from).normalize();
  const prefUp =
    Math.abs(from.x - to.x) < 0.01 && Math.abs(from.z - to.z) < 0.01
      ? new Vector3(1, 0, 0)
      : new Vector3(0, 1, 0);

  const rightNorm = new Vector3().crossVectors(norm, prefUp).normalize();
  const leftNorm = rightNorm.clone().negate();
  const upNorm = new Vector3().crossVectors(rightNorm, norm).normalize();
  const downNorm = upNorm.clone().negate();

  const up = upNorm.clone().multiplyScalar(thickness * 0.5);
  const right = rightNorm.clone().multiplyScalar(thickness * 0.5);

  if (startCap) {
    builder.quad(
      downNorm,
      new Vector3().addVectors(from, up).sub(right),
      new Vector3().subVectors(from, up).sub(right),
      new Vector3().subVectors(from, up).add(right),
      new Vector3().addVectors(from, up).add(right),
    );
  }

  if (endCap) {
    builder.quad(
      norm,
      new Vector3().addVectors(to, up).add(right),
      new Vector3().subVectors(to, up).add(right),
      new Vector3().subVectors(to, up).sub(right),
      new Vector3().addVectors(to, up).sub(right),
    );
  }

  builder.quad(
    leftNorm,
    from.clone().sub(right).add(up),
    to.clone().sub(right).add(up),
    to.clone().sub(right).sub(up),
    from.clone().sub(right).sub(up),
  );
  builder.quad(
    rightNorm,
    to.clone().add(right).sub(up),
    to.clone().add(right).add(up),
    from.clone().add(right).add(up),
    from.clone().add(right).sub(up),
  );
  builder.quad(
    upNorm,
    from.clone().add(up).sub(right),
    from.clone().add(up).add(right),
    to.clone().add(up).add(right),
    to.clone().add(up).sub(right),
  );
  builder.quad(
    downNorm,
    to.clone().sub(up).sub(right),
    to.clone().sub(up).add(right),
    from.clone().sub(up).add(right),
    from.clone().sub(up).sub(right),
  );
}

function buildBoxOutlineGeometry(
  builder: GeometryBuilder,
  min: Vector3,
  max: Vector3,
  thickness: number,
): void {
  const thickHalf = thickness * 0.5;

  const u = new Vector3(max.x - min.x, 0, 0);
  const v = new Vector3(0, max.y - min.y, 0);
  const t = new Vector3(0, 0, max.z - min.z);

  const uNorm = u.clone().normalize();
  const vNorm = v.clone().normalize();
  const tNorm = t.clone().normalize();

  // Compute the 8 corners of the box
  const corners = [
    min.clone(),
    min.clone().add(u),
    min.clone().add(v),
    min.clone().add(t),
    max.clone(),
    max.clone().sub(u),
    max.clone().sub(v),
    max.clone().sub(t),
  ] as const;

  // Along X-Axis
  // Extend these out to cover past the corner (half the extrude thickness)
  strut(
    builder,
    corners[0].clone().addScaledVector(uNorm, -thickHalf),
    corners[1].clone().addScaledVector(uNorm, thickHalf),
    thickness,
    true,
    true,
  );
  strut(
    builder,
    corners[2].clone().addScaledVector(uNorm, -thickHalf),
    corners[7].clone().addScaledVector(uNorm, thickHalf),
    thickness,
    true,
    true,
  );
  strut(
    builder,
    corners[3].clone().addScaledVector(uNorm, -thickHalf),
    corners[6].clone().addScaledVector(uNorm, thickHalf),
    thickness,
    true,
    true,
  );
  strut(
    builder,
    corners[5].clone().addScaledVector(uNorm, -thickHalf),
    corners[4].clone().addScaledVector(uNorm, thickHalf),
    thickness,
    true,
    true,
  );

  // Along Y-Axis
  strut(
    builder,
    corners[0].clone().addScaledVector(vNorm, thickHalf),
    corners[2].clone().addScaledVector(vNorm, -thickHalf),
    thickness,
    false,
    false,
  );
  strut(
    builder,
    corners[1].clone().addScaledVector(vNorm, thickHalf),
    corners[7].clone().addScaledVector(vNorm, -thickHalf),
    thickness,
    false,
    false,
  );
  strut(
    builder,
    corners[3].clone().addScaledVector(vNorm, thickHalf),
    corners[5].clone().addScaledVector(vNorm, -thickHalf),
    thickness,
    false,
    false,
  );
  strut(
    builder,
    corners[6].clone().addScaledVector(vNorm, thickHalf),
    corners[4].clone().addScaledVector(vNorm, -thickHalf),
    thickness,
    false,
    false,
  );

  // Along Z-Axis
  strut(
    builder,
    corners[0].clone().addScaledVector(tNorm, thickHalf),
    corners[3].clone().addScaledVector(tNorm, -thickHalf),
    thickness,
    false,
    false,
  );
  strut(
    builder,
    corners[1].clone().addScaledVector(tNorm, thickHalf),
    corners[6].clone().addScaledVector(tNorm, -thickHalf),
    thickness,
    false,
    false,
  );
  strut(
    builder,
    corners[2].clone().addScaledVector(tNorm, thickHalf),
    corners[5].clone().addScaledVector(tNorm, -thickHalf),
    thickness,
    false,
    false,
  );
  strut(
    builder,
    corners[7].clone().addScaledVector(tNorm, thickHalf),
    corners[4].clone().addScaledVector(tNorm, -thickHalf),
    thickness,
    false,
    false,
  );
}

function buildRenderGeometry(annotation: InWorldAnnotation) {
  const builder = new GeometryBuilder();

  const { thickness = DEFAULT_THICKNESS } = annotation;

  if (annotation.type === "box") {
    buildBoxOutlineGeometry(
      builder,
      new Vector3(...annotation.minCorner),
      new Vector3(...annotation.maxCorner),
      thickness,
    );
  } else if (annotation.type === "line") {
    strut(
      builder,
      new Vector3(...annotation.from),
      new Vector3(...annotation.to),
      thickness,
      true,
      true,
    );
  }

  return builder.build();
}

/**
 * Builds geometry that is only to be used for hit-testing against this annotation.
 */
function buildHitTestGeometry(annotation: InWorldAnnotation): {
  geometry: BufferGeometry;
  position: Vector3;
} {
  let geometry: BufferGeometry;
  const position = new Vector3();
  if (annotation.type === "box") {
    const min = new Vector3(...annotation.minCorner);
    const max = new Vector3(...annotation.maxCorner);
    const box = new Box3(min, max);
    const size = box.getSize(new Vector3());
    geometry = new BoxGeometry(size.x, size.y, size.z);
    box.getCenter(position);
  } else if (annotation.type === "line") {
    geometry = buildRenderGeometry(annotation);
  } else {
    throw new Error("Unhandled annotation type: " + JSON.stringify(annotation));
  }
  return { geometry, position };
}

function normalizeAnnotation(annotation: InWorldAnnotation) {
  if (annotation.type === "box") {
    const minCorner = annotation.minCorner;
    const maxCorner = annotation.maxCorner;
    annotation.minCorner = [
      Math.min(minCorner[0], maxCorner[0]),
      Math.min(minCorner[1], maxCorner[1]),
      Math.min(minCorner[2], maxCorner[2]),
    ];
    annotation.maxCorner = [
      Math.max(minCorner[0], maxCorner[0]),
      Math.max(minCorner[1], maxCorner[1]),
      Math.max(minCorner[2], maxCorner[2]),
    ];
  }
}

export function buildInWorldAnnotation(annotation: InWorldAnnotation) {
  normalizeAnnotation(annotation);

  const geometry = buildRenderGeometry(annotation);

  const group = new Group();

  // Don't render occlusion for always-on-top annotations
  if (!annotation.alwaysOnTop) {
    const color = new Color(annotation.color);
    color.multiplyScalar(0.5);

    const occludedMaterial = new MeshLambertMaterial({
      color,
      depthWrite: false,
      depthFunc: GreaterDepth,
      transparent: true,
      vertexColors: false,
    });

    const occludedMesh = new Mesh(geometry, occludedMaterial);
    group.add(occludedMesh);
  }

  const material = new MeshLambertMaterial({
    color: annotation.color,
    transparent: true,
    vertexColors: false,
    depthTest: !annotation.alwaysOnTop,
  });
  const mesh = new Mesh(geometry, material);
  group.add(mesh);

  // Add a box geometry that will be used by the ray-caster to show the tooltip
  if (annotation.contentTemplateId) {
    const { geometry, position } = buildHitTestGeometry(annotation);
    const boxNode = new Mesh(geometry, new MeshBasicMaterial());
    boxNode.visible = false;
    boxNode.position.copy(position);
    boxNode.userData.annotation = annotation;
    group.add(boxNode);
  }

  return group;
}
