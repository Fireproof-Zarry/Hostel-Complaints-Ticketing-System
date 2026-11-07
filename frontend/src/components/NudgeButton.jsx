import { useState } from 'react';
import { BellRing } from 'lucide-react';

import { nudgeTooltip } from '../utils/nudge';

/** Lets the owner remind the administrators; greyed out until the server allows it. */
export default function NudgeButton({ complaint, onNudge }) {
  const [isNudging, setIsNudging] = useState(false);
  const [error, setError] = useState('');
  const tooltip = nudgeTooltip(complaint);

  const handleNudge = async () => {
    setIsNudging(true);
    setError('');
    try {
      await onNudge(complaint);
    } catch (nudgeError) {
      setError(nudgeError.message);
    } finally {
      setIsNudging(false);
    }
  };

  return (
    <div className="flex flex-col items-end">
      {/* The wrapper takes the hover: a disabled button may not, and it cannot take focus */}
      <span className="group relative inline-block" tabIndex={tooltip ? 0 : undefined}>
        <button
          type="button"
          onClick={handleNudge}
          disabled={!complaint.canNudge || isNudging}
          className="inline-flex items-center gap-1.5 rounded-lg bg-indigo-600 px-4 py-1.5 text-sm font-semibold text-white transition hover:bg-indigo-700 disabled:cursor-not-allowed disabled:bg-slate-300"
        >
          <BellRing className="h-4 w-4" aria-hidden="true" />
          {isNudging ? 'Nudging...' : 'Nudge'}
        </button>
        {tooltip && (
          <span
            role="tooltip"
            className="pointer-events-none absolute bottom-full right-0 z-10 mb-1 hidden w-max max-w-xs rounded bg-slate-800 px-2 py-1 text-xs text-white group-hover:block group-focus:block"
          >
            {tooltip}
          </span>
        )}
      </span>
      {error && <p role="alert" className="m-0 mt-1 text-xs text-red-600">{error}</p>}
    </div>
  );
}
