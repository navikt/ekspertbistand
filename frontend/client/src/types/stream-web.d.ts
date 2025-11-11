declare module "node:stream/web" {
  export const ReadableStream: typeof globalThis.ReadableStream;
  export const TransformStream: typeof globalThis.TransformStream;
  export const WritableStream: typeof globalThis.WritableStream;
}
