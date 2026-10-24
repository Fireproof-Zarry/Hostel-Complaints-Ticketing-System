import { Wrench } from 'lucide-react';
import { STUDENT_NAVIGATION } from './studentNavigation';

/** Sidebar shared by the student dashboard and the Profile page. */
export default function StudentSidebar({ activeId, onSelect }) {
  return (
    <aside className="flex w-full flex-col bg-slate-900 text-slate-300 md:min-h-screen md:w-64 md:shrink-0">
      <div className="flex items-center gap-3 p-5 md:p-6">
        <div className="rounded-lg bg-indigo-500 p-2 text-white">
          <Wrench className="h-6 w-6" />
        </div>
        <div>
          <h1 className="font-bold leading-tight text-white">HostelFix</h1>
          <p className="text-xs text-slate-400">Student Portal</p>
        </div>
      </div>

      <nav aria-label="Student dashboard" className="flex gap-2 overflow-x-auto px-4 pb-4 md:flex-1 md:flex-col md:gap-1 md:pt-5">
        {STUDENT_NAVIGATION.map(({ id, label, icon: Icon }) => (
          <button
            key={id}
            type="button"
            aria-current={activeId === id ? 'page' : undefined}
            onClick={() => onSelect(id)}
            className={`flex shrink-0 items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-colors md:w-full ${
              activeId === id ? 'bg-indigo-600 text-white' : 'hover:bg-slate-800 hover:text-white'
            }`}
          >
            <Icon className="h-5 w-5" />
            {label}
          </button>
        ))}
      </nav>

      <div className="hidden border-t border-slate-800 p-4 text-xs leading-5 text-slate-400 md:block">
        Your submitted complaints and their current status.
      </div>
    </aside>
  );
}
