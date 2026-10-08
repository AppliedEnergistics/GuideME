import { ungzip } from "pako";

export default async function decompressFallback(
  blob: Blob,
): Promise<Uint8Array> {
  const data = await blob.arrayBuffer();
  return ungzip(data);
}

export type DecompressFallback = typeof decompressFallback;
