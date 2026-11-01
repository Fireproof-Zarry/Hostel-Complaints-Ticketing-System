// Keeps an unfinished complaint as a draft in sessionStorage (per browser tab),
// so it survives a trip to the Profile page or a reload. A selected photo
// cannot be stored and has to be re-attached.

const DRAFT_KEY = 'hostelfix.complaintDraft';
const TEXT_FIELDS = ['title', 'description', 'wing', 'availableTimeSlot'];

/** True when nothing worth keeping has been entered (defaults like category don't count). */
export function isDraftEmpty(form) {
  return !form || (TEXT_FIELDS.every((field) => !String(form[field] ?? '').trim()) && !form.keyWithSecurity);
}

/** The stored draft merged over the defaults, keeping only known fields of the right type. */
export function mergeDraft(raw, defaults) {
  let parsed;
  try {
    parsed = JSON.parse(raw);
  } catch {
    return { ...defaults };
  }
  if (!parsed || typeof parsed !== 'object') return { ...defaults };
  const merged = { ...defaults };
  for (const [key, value] of Object.entries(defaults)) {
    if (typeof parsed[key] === typeof value) merged[key] = parsed[key];
  }
  return merged;
}

export function loadComplaintDraft(defaults) {
  try {
    const raw = sessionStorage.getItem(DRAFT_KEY);
    return raw ? mergeDraft(raw, defaults) : { ...defaults };
  } catch {
    return { ...defaults };
  }
}

export function saveComplaintDraft(form) {
  try {
    if (isDraftEmpty(form)) sessionStorage.removeItem(DRAFT_KEY);
    else sessionStorage.setItem(DRAFT_KEY, JSON.stringify(form));
  } catch {
    // Storage unavailable (private mode, quota): the draft just isn't kept
  }
}

export function clearComplaintDraft() {
  try {
    sessionStorage.removeItem(DRAFT_KEY);
  } catch {
    // ignore
  }
}
