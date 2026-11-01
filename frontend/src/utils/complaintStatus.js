// Shared status badge helpers for complaint lists

export function statusClasses(status) {
  switch (status?.toUpperCase()) {
    case 'PENDING':
      return 'bg-amber-100 text-amber-800';
    case 'ASSIGNED':
      return 'bg-violet-100 text-violet-800';
    case 'IN_PROGRESS':
      return 'bg-blue-100 text-blue-800';
    case 'RESOLVED':
      return 'bg-emerald-100 text-emerald-800';
    case 'REJECTED':
      return 'bg-rose-100 text-rose-800';
    default:
      return 'bg-slate-100 text-slate-700';
  }
}

export function formatStatus(status) {
  return status?.toLowerCase().replaceAll('_', ' ').replace(/\b\w/g, (letter) => letter.toUpperCase()) || 'Unknown';
}
