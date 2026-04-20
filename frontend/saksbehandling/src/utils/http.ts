export async function parseErrorMessage(response: Response): Promise<string | null> {
  try {
    const payload = await response.json();
    if (typeof payload?.message === "string") {
      return payload.message;
    }
  } catch {
    // Ignore parse failures and let caller use a fallback.
  }

  return null;
}

type HttpErrorOptions = {
  status: number;
  statusText?: string;
};

export class HttpError extends Error {
  status: number;
  statusText?: string;

  constructor(message: string, options: HttpErrorOptions) {
    super(message);
    this.name = "HttpError";
    this.status = options.status;
    this.statusText = options.statusText;
  }
}
