declare module "*.css" {
  const content: void;
  export default content;
}

declare module "*.png" {
  const src: string;
  export { src };
}
