interface ImportMetaEnv {
  readonly VITE_APP_BASE_PATH?: string;
  readonly VITE_ENABLE_MOCKS?: string;
  readonly VITE_LOKAL_INNLOGGING?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
