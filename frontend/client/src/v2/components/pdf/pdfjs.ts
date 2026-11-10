import { Document, Page, pdfjs } from "react-pdf";

const workerUrl = new URL("pdfjs-dist/build/pdf.worker.min.mjs", import.meta.url).toString();

// Workeren må settes i samme modul som importerer react-pdf, ellers kan standardverdien overskrive den.
pdfjs.GlobalWorkerOptions.workerSrc = workerUrl;

// Firefox starter ikke en modul-worker direkte fra URL når en service worker (MSW) kontrollerer siden.
// Da starter vi workeren via en blob som importerer pdf.js-workeren. Gjelder bare mock, så CSP i prod er uendret.
if (typeof navigator !== "undefined" && navigator.serviceWorker?.controller) {
  const wrapper = new Blob([`import ${JSON.stringify(workerUrl)};`], { type: "text/javascript" });
  pdfjs.GlobalWorkerOptions.workerPort = new Worker(URL.createObjectURL(wrapper), {
    type: "module",
  });
}

export { Document, Page };
