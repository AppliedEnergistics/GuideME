import { ungzip } from "pako";
import decompress from "./decompress.ts";

export default async function decompressFallback(
  blob: Blob,
): Promise<Uint8Array> {
  const data = await blob.arrayBuffer();
  return ungzip(data);
}

export type DecompressFallback = typeof decompressFallback;
