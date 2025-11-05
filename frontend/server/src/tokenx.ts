import { getToken, requestTokenxOboToken } from "@navikt/oasis";
import type { NextFunction, Request, Response } from "express";
import { logger } from "@navikt/pino-logger";

type TokenXMiddlewareOptions = {
  audience?: string;
  enabled: boolean;
  localSubjectToken?: string;
};

export const tokenXMiddleware =
  ({ audience, enabled, localSubjectToken }: TokenXMiddlewareOptions) =>
  async (req: Request, res: Response, next: NextFunction) => {
    try {
      delete req.headers.cookie;

      if (!enabled) {
        const token = localSubjectToken ?? process.env.LOCAL_SUBJECT_TOKEN ?? "faketoken";
        req.headers.authorization = `Bearer ${token}`;
        return next();
      }

      if (!audience) {
        logger.error("TokenX OBO mangler konfigurasjon for audience.");
        res.status(500).json({ message: "Mangler konfigurasjon for TokenX." });
        return;
      }

      const subjectToken = getToken(req);
      if (!subjectToken) {
        res.status(401).json({ message: "Mangler innloggings-token." });
        return;
      }

      const oboResult = await requestTokenxOboToken(subjectToken, audience);
      if (!oboResult.ok) {
        logger.error({ error: oboResult.error }, "TokenX OBO feilet");
        res.status(401).json({ message: "Kunne ikke hente tilgangstoken." });
        return;
      }

      req.headers.authorization = `Bearer ${oboResult.token}`;
      return next();
    } catch (error) {
      logger.error({ error }, "Uventet feil ved TokenX OBO");
      res.status(500).json({ message: "Uventet feil ved tokenutveksling." });
    }
  };
