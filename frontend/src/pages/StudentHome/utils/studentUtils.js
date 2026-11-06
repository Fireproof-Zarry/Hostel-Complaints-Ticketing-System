/**
 * Formats a date for display in the student dashboard.
 *
 * @param {string | Date | null | undefined} value
 * @returns {string}
 */
export function formatDate(value) {
  if (!value) return 'Date unavailable';

  const date = new Date(value);

  return Number.isNaN(date.getTime())
    ? 'Date unavailable'
    : date.toLocaleDateString();
}

/**
 * Checks whether a complaint is still active.
 *
 * RESOLVED and REJECTED complaints are considered inactive.
 *
 * @param {object} complaint
 * @returns {boolean}
 */
export function isActiveComplaint(complaint) {
  return !['RESOLVED', 'REJECTED'].includes(
    complaint.status?.toUpperCase()
  );
}

/**
 * Sorts complaints by creation date, newest first.
 *
 * @param {Array} complaints
 * @returns {Array}
 */
export function sortComplaintsByDate(complaints = []) {
  return [...complaints].sort(
    (a, b) =>
      new Date(b.createdAt || 0) - new Date(a.createdAt || 0)
  );
}