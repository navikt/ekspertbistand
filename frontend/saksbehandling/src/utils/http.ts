type ErrorPayload = {
  message: string | null;
  begrunnelse: string | null;
};

export async function parseErrorPayload(response: Response): Promise<ErrorPayload> {
  try {
    const payload = await response.json();
    return {
      message: typeof payload?.message === "string" ? payload.message : null,
      begrunnelse: typeof payload?.begrunnelse === "string" ? payload.begrunnelse : null,
    };
  } catch {
    // Ignore parse failures and let caller use a fallback.
    return { message: null, begrunnelse: null };
  }
}

export async function parseErrorMessage(response: Response): Promise<string | null> {
  return (await parseErrorPayload(response)).message;
}

type HttpErrorOptions = {
  status: number;
  statusText?: string;
  begrunnelse?: string | null;
};

export class HttpError extends Error {
  status: number;
  statusText?: string;
  begrunnelse?: string;

  constructor(message: string, options: HttpErrorOptions) {
    super(message);
    this.name = "HttpError";
    this.status = options.status;
    this.statusText = options.statusText;
    this.begrunnelse = options.begrunnelse ?? undefined;
  }
}
