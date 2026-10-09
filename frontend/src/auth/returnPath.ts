/** Where to go after signing in: only paths inside this app, never another origin (no open redirect). */
export function safeReturnPath(from: unknown): string {
  if (typeof from !== 'string' || !from.startsWith('/') || from.startsWith('//') || from.startsWith('/\\')) {
    return '/'
  }
  return from
}
