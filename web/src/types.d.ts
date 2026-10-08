declare module "*.css" {
  const content: void;
  export default content;
}

// esbuild's file loader exports the URL of the file relative to the bundle
declare module "*.png" {
  const url: string;
  export default url;
}
