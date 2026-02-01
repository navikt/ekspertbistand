interface ImportMetaEnv {
  readonly VITE_APP_BASE_PATH?: string;
  readonly VITE_ENABLE_MOCKS?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
