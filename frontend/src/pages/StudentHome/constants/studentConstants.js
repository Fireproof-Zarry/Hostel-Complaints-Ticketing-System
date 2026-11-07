// An empty complaint form
export const INITIAL_COMPLAINT_FORM = {
  title: '',
  description: '',
  category: 'Electrical',
  isCommunityArea: false,
  floor: 'Second',
  wing: '',
  availableTimeSlot: '',
  keyWithSecurity: false,
};

// The status choices in My Complaints
export const STUDENT_STATUS_FILTERS = [
  { value: 'ALL', label: 'All statuses' },
  { value: 'PENDING', label: 'Pending' },
  { value: 'ASSIGNED', label: 'Assigned' },
  { value: 'IN_PROGRESS', label: 'In progress' },
  { value: 'RESOLVED', label: 'Resolved' },
  { value: 'REJECTED', label: 'Rejected' },
];