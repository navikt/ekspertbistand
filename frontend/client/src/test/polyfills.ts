import { ReadableStream, TransformStream, WritableStream } from "node:stream/web";

if (typeof globalThis.TransformStream === "undefined") {
  globalThis.TransformStream = TransformStream;
}

if (typeof globalThis.ReadableStream === "undefined") {
  globalThis.ReadableStream = ReadableStream;
}

if (typeof globalThis.WritableStream === "undefined") {
  globalThis.WritableStream = WritableStream;
}

// jsdom defines scrollTo but only logs "Not implemented".
if (typeof window !== "undefined") {
  window.scrollTo = () => {};
}
