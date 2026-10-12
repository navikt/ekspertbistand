import { getToken, requestAzureOboToken, validateAzureToken } from "@navikt/oasis";
import type { NextFunction, Request, Response } from "express";
import { logger } from "@navikt/pino-logger";
import { lesLokalBruker, lokaltSubjectToken } from "./lokal-bruker.js";

type AzureOboMiddlewareOptions = {
  audience?: string;
  enabled: boolean;
  localSubjectToken?: string;
  /** Lar cookien `lokal-bruker` velge bruker når Azure er av. Bare for lokal kjøring. */
  lokalBrukerbytte?: boolean;
};

export const azureOboMiddleware =
  ({ audience, enabled, localSubjectToken, lokalBrukerbytte = false }: AzureOboMiddlewareOptions) =>
  async (req: Request, res: Response, next: NextFunction) => {
    try {
      const cookie = req.headers.cookie;
      delete req.headers.cookie;

      if (!enabled) {
        const token = lokaltSubjectToken(
          localSubjectToken ?? process.env.LOCAL_SUBJECT_TOKEN ?? "faketoken",
          lokalBrukerbytte ? lesLokalBruker(cookie) : undefined
        );
        req.headers.authorization = `Bearer ${token}`;
        return next();
      }

      if (!audience) {
        logger.error("Azure OBO mangler konfigurasjon for audience.");
        res.status(500).json({ message: "Mangler konfigurasjon for Azure OBO." });
        return;
      }

      const subjectToken = getToken(req);
      if (!subjectToken) {
        res.status(401).json({ message: "Mangler innloggings-token." });
        return;
      }

      const validation = await validateAzureToken(subjectToken);
      if (!validation.ok) {
        logger.warn(
          { errorType: validation.errorType, message: validation.error.message },
          "Ugyldig Azure subject-token."
        );
        res.status(401).json({ message: "Ugyldig innloggings-token." });
        return;
      }

      const oboResult = await requestAzureOboToken(subjectToken, audience);
      if (!oboResult.ok) {
        logger.error(
          { error: String(oboResult.error), message: oboResult.error?.message ?? "unknown" },
          "Azure OBO feilet"
        );
        res.status(401).json({ message: "Kunne ikke hente tilgangstoken." });
        return;
      }

      req.headers.authorization = `Bearer ${oboResult.token}`;
      return next();
    } catch (error) {
      logger.error({ error }, "Uventet feil ved Azure OBO");
      res.status(500).json({ message: "Uventet feil ved tokenutveksling." });
    }
  };
