import { getToken, requestAzureOboToken } from "@navikt/oasis";
import type { NextFunction, Request, Response } from "express";
import { logger } from "@navikt/pino-logger";

type AzureOboMiddlewareOptions = {
  audience?: string;
  enabled: boolean;
  localSubjectToken?: string;
};

export const azureOboMiddleware =
  ({ audience, enabled, localSubjectToken }: AzureOboMiddlewareOptions) =>
  async (req: Request, res: Response, next: NextFunction) => {
    try {
      delete req.headers.cookie;

      if (!enabled) {
        const token = localSubjectToken ?? process.env.LOCAL_SUBJECT_TOKEN ?? "faketoken";
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

      const oboResult = await requestAzureOboToken(subjectToken, audience);
      if (!oboResult.ok) {
        logger.error({ error: oboResult.error }, "Azure OBO feilet");
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
