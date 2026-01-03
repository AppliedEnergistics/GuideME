export function formatError(error: unknown): string {
  if (error instanceof Response) {
    return "HTTP Error " + error.status;
  }

  return "An unknown error occurred";
}
