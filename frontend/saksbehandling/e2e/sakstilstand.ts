import { mutate } from "swr";
import type { SakDetaljer } from "../src/hooks/useSak";
import { SAKSBEHANDLING_SAK_URL } from "../src/utils/constants";

/**
 * Kjøres i nettleseren via `import("/e2e/sakstilstand.ts")`. Vite gir samme SWR-instans som appen,
 * så endringen vises som om saken ble oppdatert av en annen mens siden var åpen.
 */
export async function endreSak(sakId: string, endring: Partial<SakDetaljer>) {
  await mutate<SakDetaljer>(
    SAKSBEHANDLING_SAK_URL(sakId),
    (sak) => (sak ? { ...sak, ...endring } : sak),
    { revalidate: false }
  );
}
