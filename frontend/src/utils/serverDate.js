// Timestamps the server sets itself (created, updated, changed) arrive as UTC
// with no offset. Work order deadlines do not: they are IST wall-clock times
// picked in the browser, so they must not go through here.

/** A server timestamp as a Date, or null if it is missing or invalid. */
export function parseServerDate(value) {
  if (!value) return null;
  const hasOffset = /(?:Z|[+-]\d{2}:\d{2})$/i.test(value);
  const date = new Date(hasOffset ? value : `${value}Z`);
  return Number.isNaN(date.getTime()) ? null : date;
}

/** A server timestamp in the viewer's local time. */
export function formatServerDateTime(value) {
  const date = parseServerDate(value);
  return date ? date.toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' }) : '—';
}
