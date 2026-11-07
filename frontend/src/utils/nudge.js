import { parseServerDate } from './serverDate.js';

const CLOSED_STATUSES = ['RESOLVED', 'REJECTED'];
const DAY_MS = 24 * 60 * 60 * 1000;

const startOfLocalDay = (date) => new Date(date.getFullYear(), date.getMonth(), date.getDate());

/**
 * Calendar days from today until the day the button turns on (at 5 AM IST), in
 * the viewer's time zone: filed on the 6th, active on the 13th, so on the 7th it is 6.
 * 0 means today, or the wait ended while the page was open.
 */
export function nudgeDaysLeft(nextNudgeAt, now = Date.now()) {
  const next = startOfLocalDay(parseServerDate(nextNudgeAt));
  const today = startOfLocalDay(new Date(now));
  // Rounded, because a daylight-saving change makes a day 23 or 25 hours long
  return Math.max(0, Math.round((next - today) / DAY_MS));
}

/** Hover text for a greyed-out nudge button; null while it is active. */
export function nudgeTooltip(complaint, now = Date.now()) {
  if (complaint.canNudge) return null;
  if (!complaint.nextNudgeAt) return 'Only unresolved complaints can be nudged.';

  // The list only learns the button is on when it reloads
  const next = parseServerDate(complaint.nextNudgeAt);
  if (next.getTime() <= now) return 'Please reload to make it active';

  const days = nudgeDaysLeft(complaint.nextNudgeAt, now);
  if (days === 0) return 'Will be active today';
  return `Will be active ${days} ${days === 1 ? 'day' : 'days'} later`;
}

/** Whether admins should see a badge: a nudge stops mattering once the complaint is closed. */
export function isAwaitingNudgeResponse(complaint) {
  return Boolean(complaint.lastNudgedAt) && !CLOSED_STATUSES.includes(complaint.status);
}
