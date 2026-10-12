import { Document, Page, pdfjs } from "react-pdf";

// Workeren må settes i samme modul som importerer react-pdf, ellers kan standardverdien overskrive den.
pdfjs.GlobalWorkerOptions.workerSrc = new URL(
  "pdfjs-dist/build/pdf.worker.min.mjs",
  import.meta.url
).toString();

export { Document, Page };
