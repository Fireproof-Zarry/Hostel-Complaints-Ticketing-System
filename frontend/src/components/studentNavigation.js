import { Bell, ClipboardList, FilePlus2, Home, UserRound, Users } from 'lucide-react';

// The student sidebar: each page and the line shown for it at the bottom
export const STUDENT_NAVIGATION = [
  { id: 'home', label: 'Home', icon: Home, description: 'A quick overview of your complaints.' },
  { id: 'submit', label: 'File Complaint', icon: FilePlus2, description: 'Report a problem in your room or a shared area.' },
  { id: 'complaints', label: 'My Complaints', icon: ClipboardList, description: 'Your submitted complaints and their current status.' },
  { id: 'community', label: 'Community', icon: Users, description: 'Issues in shared areas. Upvote the ones that affect you.' },
  { id: 'notifications', label: 'Notifications', icon: Bell, description: 'Manage browser notifications for complaint status updates.' },
  { id: 'profile', label: 'My Profile', icon: UserRound, description: 'Keep your details current so workers can reach you.' },
];
