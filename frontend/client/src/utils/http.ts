export async function parseErrorPayload(
  response: Response
): Promise<{ message: string | null; field: string | null }> {
  try {
    const payload: unknown = await response.json();
    if (typeof payload === "object" && payload !== null) {
      const details = payload as { message?: unknown; felt?: unknown };
      return {
        message: typeof details.message === "string" ? details.message : null,
        field: typeof details.felt === "string" ? details.felt : null,
      };
    }
  } catch {
    // Ignore JSON parse failures; caller can fallback to generic error.
  }
  return { message: null, field: null };
}

export async function parseErrorMessage(response: Response): Promise<string | null> {
  return (await parseErrorPayload(response)).message;
}

type HttpErrorOptions = {
  status: number;
  statusText?: string;
  field?: string;
};

export class HttpError extends Error {
  status: number;
  statusText?: string;
  field?: string;

  constructor(message: string, options: HttpErrorOptions) {
    super(message);
    this.name = "HttpError";
    this.status = options.status;
    this.statusText = options.statusText;
    this.field = options.field;
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
