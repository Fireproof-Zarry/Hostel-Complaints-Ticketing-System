import { ChevronDown } from 'lucide-react';

// The categories on the complaint form, with the issues that belong in each
const CATEGORY_GUIDE = [
  { name: 'Electrical', examples: 'Lights, fans, switches, sockets, geysers, wiring' },
  { name: 'Plumbing', examples: 'Taps, leaks, blocked drains, flush, water supply, water dispensers' },
  { name: 'Carpentry', examples: 'Doors, locks, cupboards, beds, tables, windows' },
  { name: 'Cleaning', examples: 'Washrooms, corridors, garbage, pests' },
  { name: 'IT', examples: 'Wi-Fi, LAN ports, network access' },
];

// Each answer matches a rule the app enforces; keep them in step with the backend
const FAQS = [
  {
    question: 'Should I file a room complaint or a community area complaint?',
    answer:
      'Choose your room for problems inside it. It uses the room number in My Profile, so add that first. '
      + 'Choose a community area for shared places such as corridors, washrooms or common rooms, and enter the floor and wing. '
      + 'Room complaints are private; community complaints appear on the Community page without your name.',
  },
  {
    question: 'How many complaints can I have open?',
    answer:
      'Up to 5 at a time: Pending, Assigned and In Progress complaints all count. '
      + 'Once one is resolved or rejected, you can file another. You can also file at most 5 in one minute.',
  },
  {
    question: 'What do the statuses mean?',
    answer:
      'Pending: waiting to be picked up. Assigned: someone has taken responsibility for it. '
      + 'In Progress: work has started. Resolved: it has been fixed. Rejected: the hostel office closed it without action.',
  },
  {
    question: 'How do I know who is handling my complaint?',
    answer:
      'Open the complaint with View. While someone is working on it, you will see their name and phone number there.',
  },
  {
    question: 'I made a mistake in my complaint. Can I change it?',
    answer:
      'You can edit the description until the complaint is resolved or rejected: open it with View and use Edit complaint text. '
      + 'To change anything else, delete it while it is still Pending and file it again.',
  },
  {
    question: 'Can I delete a complaint?',
    answer:
      'Only while it is Pending, before anyone has picked it up.',
  },
  {
    question: 'Why did I see a "Has this already been reported?" warning?',
    answer:
      'A similar open complaint already exists. If it is the same issue, upvote it instead of filing a new one, '
      + 'so the hostel office can see how many students it affects. If yours is different, choose "No, mine is different".',
  },
  {
    question: 'My complaint has been open for a long time. What can I do?',
    answer:
      'Use Nudge on it in My Complaints to remind the hostel office. It turns on at 5 AM on the 7th day after you filed it, '
      + 'and after a nudge you can nudge again 7 days later.',
  },
  {
    question: 'How do I get notified when my complaint changes?',
    answer:
      'Turn notifications on in the Notifications tab and allow them when your browser asks. They stop on that device '
      + 'when you log out, and only the device you turned them on most recently gets them. On an iPhone, add HostelFix '
      + 'to your Home Screen first.',
  },
  {
    question: 'I closed the form before submitting. Is my complaint lost?',
    answer: 'No. Your unfinished complaint is saved as a draft in this browser for 7 days.',
  },
];

/** The student Help page: which category to choose, and answers to common questions. */
export default function StudentHelp() {
  return (
    <section className="mx-auto max-w-3xl space-y-8">
      <div>
        <h3 className="m-0 text-2xl font-bold text-slate-900">Help</h3>
        <p className="m-0 mt-1 text-sm text-slate-500">Which category to choose, and answers to common questions.</p>
      </div>

      <div className="space-y-3">
        <h4 className="m-0 mb-3 text-lg font-semibold text-slate-900">Which category should I choose?</h4>
        <ul className="m-0 grid list-none gap-3 p-0 sm:grid-cols-2">
          {CATEGORY_GUIDE.map((category) => (
            <li key={category.name} className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
              <p className="m-0 font-semibold text-slate-900">{category.name}</p>
              <p className="m-0 mt-1 text-sm text-slate-600">{category.examples}</p>
            </li>
          ))}
        </ul>
        <p className="m-0 mt-4 text-sm text-slate-500">
          Not sure? Pick the closest one. The hostel office sees every complaint and can still handle it.
        </p>
      </div>

      <div className="space-y-3">
        <h4 className="m-0 mb-3 text-lg font-semibold text-slate-900">Frequently asked questions</h4>
        <div className="divide-y divide-slate-100 overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
          {FAQS.map((faq) => (
            <details key={faq.question} className="group px-4 py-3">
              <summary className="flex cursor-pointer list-none items-center justify-between gap-3 font-medium text-slate-900 [&::-webkit-details-marker]:hidden">
                {faq.question}
                <ChevronDown className="h-4 w-4 shrink-0 text-slate-400 transition-transform group-open:rotate-180" aria-hidden="true" />
              </summary>
              <p className="m-0 mt-2 text-sm leading-6 text-slate-600">{faq.answer}</p>
            </details>
          ))}
        </div>
      </div>
    </section>
  );
}
