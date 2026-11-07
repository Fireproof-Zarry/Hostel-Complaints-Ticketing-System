// Keeps an unfinished complaint as a draft in localStorage, per account, so it
// survives a trip to the Profile page, a reload, closing the browser and
// logging out and back in. Drafts expire 7 days after they were last saved.
// A selected photo cannot be stored and has to be re-attached.

// Drafts older than this are discarded
export const DRAFT_TTL_MS = 7 * 24 * 60 * 60 * 1000;
const KEY_PREFIX = 'hostelfix.complaintDraft.';
// Earlier versions kept a single draft per browser tab in sessionStorage
const LEGACY_SESSION_KEY = 'hostelfix.complaintDraft';
const TEXT_FIELDS = ['title', 'description', 'wing', 'availableTimeSlot'];

// ---- Pure helpers (unit-tested) ----

/** True when nothing worth keeping has been entered (defaults like category don't count). */
export function isDraftEmpty(form) {
  return !form || (TEXT_FIELDS.every((field) => !String(form[field] ?? '').trim()) && !form.keyWithSecurity);
}

/** The draft's fields merged over the defaults, keeping only known fields of the right type. */
export function mergeDraftFields(fields, defaults) {
  const merged = { ...defaults };
  if (!fields || typeof fields !== 'object') return merged;
  for (const [key, value] of Object.entries(defaults)) {
    if (typeof fields[key] === typeof value) merged[key] = fields[key];
  }
  return merged;
}

/** The storage key for one account's draft. */
export function draftKey(owner) {
  return KEY_PREFIX + String(owner).trim().toLowerCase();
}

/** The stored draft as a form, or null if it is corrupt or older than DRAFT_TTL_MS. */
export function parseStoredDraft(raw, defaults, now) {
  let stored;
  try {
    stored = JSON.parse(raw);
  } catch {
    return null;
  }
  if (!stored || typeof stored.savedAt !== 'number' || now - stored.savedAt > DRAFT_TTL_MS) {
    return null;
  }
  return mergeDraftFields(stored.form, defaults);
}

// ---- localStorage access (owner = the signed-in account's email) ----

/** Removes every account's drafts that have expired or are unreadable. */
function purgeExpiredDrafts(now) {
  for (let index = localStorage.length - 1; index >= 0; index -= 1) {
    const key = localStorage.key(index);
    if (key?.startsWith(KEY_PREFIX) && parseStoredDraft(localStorage.getItem(key), {}, now) === null) {
      localStorage.removeItem(key);
    }
  }
}

/** This account's saved draft merged over the empty form, or the empty form. */
export function loadComplaintDraft(defaults, owner, now = Date.now()) {
  if (!owner) return { ...defaults };
  try {
    // Carry over a draft saved by the earlier per-tab version
    const legacy = sessionStorage.getItem(LEGACY_SESSION_KEY);
    if (legacy !== null) {
      sessionStorage.removeItem(LEGACY_SESSION_KEY);
      if (localStorage.getItem(draftKey(owner)) === null) {
        let legacyFields = null;
        try {
          legacyFields = JSON.parse(legacy);
        } catch {
          // unreadable: drop it
        }
        saveComplaintDraft(mergeDraftFields(legacyFields, defaults), owner, now);
      }
    }

    purgeExpiredDrafts(now);
    const raw = localStorage.getItem(draftKey(owner));
    return (raw && parseStoredDraft(raw, defaults, now)) || { ...defaults };
  } catch {
    return { ...defaults };
  }
}

/** Saves the form as a draft; an empty form removes it. */
export function saveComplaintDraft(form, owner, now = Date.now()) {
  if (!owner) return;
  try {
    if (isDraftEmpty(form)) localStorage.removeItem(draftKey(owner));
    else localStorage.setItem(draftKey(owner), JSON.stringify({ form, savedAt: now }));
  } catch {
    // Storage unavailable (private mode, quota): the draft just isn't kept
  }
}

/** Removes this account's draft. */
export function clearComplaintDraft(owner) {
  if (!owner) return;
  try {
    localStorage.removeItem(draftKey(owner));
  } catch {
    // ignore
  }
}
