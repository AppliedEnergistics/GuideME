import {
  BufferGeometry,
  InterleavedBuffer,
  InterleavedBufferAttribute,
  Uint16BufferAttribute,
  Uint32BufferAttribute,
} from "three";
import { ExpIndexElementType } from "@generated/scene/exp-index-element-type.ts";
import { ExpMesh } from "@generated/scene/exp-mesh.ts";
import { ExpVertexElementUsage } from "@generated/scene/exp-vertex-element-usage.ts";
import { ExpVertexElementType } from "@generated/scene/exp-vertex-element-type.ts";

interface TypedArrayCtor<T> {
  readonly BYTES_PER_ELEMENT: number;

  new (buffer: ArrayBufferLike, byteOffset?: number, length?: number): T;
}

function castArray<T extends ArrayLike<number>>(
  base: ArrayBufferView,
  ctor: TypedArrayCtor<T>,
): T {
  return new ctor(
    base.buffer,
    base.byteOffset,
    base.byteLength / ctor.BYTES_PER_ELEMENT,
  );
}

export default function loadGeometry(
  expMesh: ExpMesh,
): BufferGeometry | undefined {
  const vertexBufferData = expMesh.vertexBufferArray();
  const indexBufferData = expMesh.indexBufferArray();
  if (!vertexBufferData || !indexBufferData) {
    console.warn("Missing vertex or index buffer build-data");
    return;
  }
  console.debug(
    "Vertex buffer size: %d, Index buffer size: %d",
    vertexBufferData.byteLength,
    indexBufferData.byteLength,
  );
  const expVertexFormat = expMesh.vertexFormat();
  if (!expVertexFormat) {
    console.warn("Missing vertex format");
    return;
  }
  console.debug("Vertex format, stride=%d", expVertexFormat.vertexSize());

  const geometry = new BufferGeometry();

  if (expMesh.indexType() == ExpIndexElementType.UINT) {
    const typedData = castArray(indexBufferData, Uint32Array);
    geometry.setIndex(new Uint32BufferAttribute(typedData, 1, false));
  } else if (expMesh.indexType() == ExpIndexElementType.USHORT) {
    const typedData = castArray(indexBufferData, Uint16Array);
    geometry.setIndex(new Uint16BufferAttribute(typedData, 1, false));
  } else {
    console.warn("Unknown index type: %o", expMesh.indexType());
    return;
  }

  for (let j = 0; j < expVertexFormat.elementsLength(); j++) {
    const vertexEl = expVertexFormat.elements(j);
    if (!vertexEl) {
      continue;
    }

    // We don't support uv2 (lightmap)
    if (vertexEl.index() > 0) {
      continue;
    }

    let attributeName: string;
    switch (vertexEl.usage()) {
      case ExpVertexElementUsage.POSITION:
        attributeName = "position";
        break;
      case ExpVertexElementUsage.NORMAL:
        attributeName = "normal";
        break;
      case ExpVertexElementUsage.COLOR:
        attributeName = "color";
        break;
      case ExpVertexElementUsage.UV:
        attributeName = "uv";
        break;
      default:
        console.warn(
          "Unsupported vertex format attribute usage: %o",
          vertexEl.usage(),
        );
        continue;
    }

    console.debug(
      "Element %s, Count=%o, Offset=%o, Normalized=%o",
      attributeName,
      vertexEl.count(),
      vertexEl.offset(),
      vertexEl.normalized(),
    );

    let interleavedCtor: any;
    switch (vertexEl.type()) {
      case ExpVertexElementType.FLOAT:
        interleavedCtor = Float32Array;
        break;
      case ExpVertexElementType.UBYTE:
        interleavedCtor = Uint8Array;
        break;
      case ExpVertexElementType.BYTE:
        interleavedCtor = Int8Array;
        break;
      case ExpVertexElementType.USHORT:
        interleavedCtor = Uint16Array;
        break;
      case ExpVertexElementType.SHORT:
        interleavedCtor = Int16Array;
        break;
      case ExpVertexElementType.UINT:
        interleavedCtor = Uint32Array;
        break;
      case ExpVertexElementType.INT:
        interleavedCtor = Int32Array;
        break;
      default:
        console.warn("Unsupported element type: %o", vertexEl.type());
        continue;
    }

    const bytesPerEl = interleavedCtor.BYTES_PER_ELEMENT;
    const array = castArray(vertexBufferData, interleavedCtor);

    const interleavedBuffer = new InterleavedBuffer(
      array,
      expVertexFormat.vertexSize() / bytesPerEl,
    );

    geometry.setAttribute(
      attributeName,
      new InterleavedBufferAttribute(
        interleavedBuffer,
        vertexEl.count(),
        vertexEl.offset() / bytesPerEl,
        vertexEl.normalized(),
      ),
    );
  }

  return geometry;
}
