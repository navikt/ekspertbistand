export async function parseErrorMessage(response: Response): Promise<string | null> {
  try {
    const payload = await response.json();
    if (typeof payload?.message === "string") {
      return payload.message;
    }
  } catch {
    // Ignore JSON parse failures; caller can fallback to generic error.
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

export function isUnauthorizedError(error: unknown): error is HttpError {
  return error instanceof HttpError && error.status === 401;
}

export type ApiErrorInfo = {
  message: string;
  requiresLogin: boolean;
};

export function resolveApiError(error: unknown, fallbackMessage: string): ApiErrorInfo {
  if (error instanceof Error) {
    return {
      message: error.message,
      requiresLogin: isUnauthorizedError(error),
    };
  }
  return {
    message: fallbackMessage,
    requiresLogin: false,
  };
}
