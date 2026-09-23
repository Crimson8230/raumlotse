import { z } from 'zod'

// Preserve the existing provisioning policy, including Java String.strip whitespace.
// eslint-disable-next-line no-control-regex -- these escaped ranges mirror Java's whitespace rules.
const boundaryWhitespace = new RegExp('^[\\u0009-\\u000d\\u001c-\\u0020\\u1680\\u2000-\\u2006\\u2008-\\u200a\\u2028\\u2029\\u205f\\u3000]+|[\\u0009-\\u000d\\u001c-\\u0020\\u1680\\u2000-\\u2006\\u2008-\\u200a\\u2028\\u2029\\u205f\\u3000]+$', 'g')
export const canonicalEmail = (email: string) => email.replace(boundaryWhitespace, '').toLowerCase()
// Java's default regex \s covers ASCII whitespace, not the full Unicode JS class.
// eslint-disable-next-line no-control-regex -- the Java-compatible pattern intentionally enumerates ASCII whitespace.
const emailPattern = new RegExp('^[^\\u0020\\u0009-\\u000d@]+@[^\\u0020\\u0009-\\u000d@]+\\.[^\\u0020\\u0009-\\u000d@]+$')
export const loginSchema = z.object({
  email: z.string().transform(canonicalEmail).pipe(z.string().min(1, 'Enter your email address.')
    .max(254, 'Email address is too long.').regex(emailPattern, 'Enter a valid email address.')),
  password: z.string().min(1, 'Enter your password.').max(1024, 'Password is too long.'),
})
