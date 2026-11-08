import { Wrench } from 'lucide-react';
import { STUDENT_NAVIGATION } from './studentNavigation';

/**
 * Sidebar shared by the student dashboard and the Profile page, styled like the
 * admin sidebar (AdminHome.css). Tailwind's base reset is not loaded, so buttons,
 * headings and paragraphs are reset explicitly: border, background, font and margins.
 */
export default function StudentSidebar({ activeId, onSelect }) {
  const activeItem = STUDENT_NAVIGATION.find((item) => item.id === activeId);

  return (
    <aside className="flex w-full flex-col border-r border-[#222b3c] bg-[#111827] text-[#c4cada] [font-family:Inter,ui-sans-serif,system-ui,sans-serif] md:sticky md:top-0 md:h-screen md:w-[252px] md:shrink-0">
      <div className="flex min-h-[82px] items-center gap-3 border-b border-[#263044] px-[22px]">
        <span className="grid h-10 w-10 shrink-0 place-items-center rounded-xl bg-gradient-to-br from-[#7664f2] to-[#4e3ac1] text-white shadow-[0_6px_18px_rgba(89,66,214,0.32)]">
          <Wrench className="h-5 w-5" aria-hidden="true" />
        </span>
        <span className="flex flex-col">
          <h1 className="m-0 text-base font-bold leading-tight text-white">HostelFix</h1>
          <p className="m-0 mt-[3px] text-[9px] font-bold uppercase tracking-[0.15em] text-[#8490a5]">Student Portal</p>
        </span>
      </div>

      <p className="m-0 mx-[18px] mb-2.5 mt-[26px] hidden text-[10px] font-bold uppercase tracking-[0.13em] text-[#778399] md:block">
        Workspace
      </p>

      <nav
        aria-label="Student dashboard"
        className="flex gap-1.5 overflow-x-auto px-3 py-3 md:flex-1 md:flex-col md:overflow-visible md:py-0"
      >
        {STUDENT_NAVIGATION.map(({ id, label, icon: Icon }) => {
          const isActive = activeId === id;
          return (
            <button
              key={id}
              type="button"
              aria-current={isActive ? 'page' : undefined}
              onClick={() => onSelect(id)}
              className={`m-0 flex min-h-11 shrink-0 cursor-pointer items-center gap-3 rounded-[9px] border border-solid px-3 text-left text-[13px] [font-family:inherit] transition-colors md:w-full ${
                isActive
                  ? 'border-[#463a8e] bg-[#28234f] font-semibold text-[#d7d1ff]'
                  : 'border-transparent bg-transparent font-medium text-[#aeb8ca] hover:bg-[#1b2536] hover:text-white'
              }`}
            >
              <Icon className={`h-[19px] w-[19px] shrink-0 ${isActive ? 'text-[#a99cff]' : ''}`} aria-hidden="true" />
              {label}
            </button>
          );
        })}
      </nav>

      <div className="mt-auto hidden border-t border-[#263044] px-3 pb-[18px] pt-3.5 md:block">
        <p className="m-0 rounded-[10px] bg-[#1b2536] p-2.5 text-xs leading-5 text-[#8e9ab0]">
          {activeItem?.description}
        </p>
      </div>
    </aside>
  );
}
