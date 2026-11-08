import { useEffect, useState } from 'react';
import { AlertCircle, Building2, FileUp, RefreshCw, Search, Trash2, UserPlus } from 'lucide-react';
import { apiFetch } from '../auth/session';

const apiBaseUrl = (import.meta.env.VITE_API_URL || '/api').replace(/\/$/, '');

async function errorMessage(response, fallback) {
  try {
    const body = await response.json();
    return body.message || body.detail || fallback;
  } catch {
    return fallback;
  }
}

const upper = (rollNumber) => rollNumber.toUpperCase();
const plural = (count, word) => `${count} ${word}${count === 1 ? '' : 's'}`;

const buttonBase = 'inline-flex items-center justify-center gap-2 rounded-lg px-4 py-2.5 text-sm font-semibold disabled:opacity-60';
const primaryButton = `${buttonBase} border-0 bg-indigo-600 text-white hover:bg-indigo-700`;
const secondaryButton = `${buttonBase} border border-slate-300 bg-white text-slate-700 hover:bg-slate-50`;
const dangerButton = `${buttonBase} border-0 bg-rose-600 text-white hover:bg-rose-700`;
const inputClass = 'min-w-0 flex-1 rounded-lg border border-slate-300 px-4 py-2.5 text-sm outline-none focus:border-indigo-500 focus:ring-2 focus:ring-indigo-200';

/** The result of checking an uploaded list, before it is saved. */
function PreviewSummary({ preview }) {
  return (
    <div className="space-y-2 text-sm text-slate-700">
      <p className="m-0">
        Found <strong>{preview.found}</strong> roll {preview.found === 1 ? 'number' : 'numbers'}.{' '}
        <strong>{preview.added}</strong> new, <strong>{preview.unchanged}</strong> already on the list.
        {preview.duplicates > 0 && ` ${plural(preview.duplicates, 'repeat')} counted once.`}
      </p>
      {preview.removed > 0 && (
        <p className="m-0 rounded-lg border border-amber-200 bg-amber-50 p-2 text-amber-900">
          <strong>{preview.removed}</strong> {preview.removed === 1 ? 'student' : 'students'} will lose access:{' '}
          {preview.removedSample.map(upper).join(', ')}
          {preview.removed > preview.removedSample.length && ' and more'}.
        </p>
      )}
      {preview.skippedCount > 0 && (
        <div>
          <p className="m-0">
            {plural(preview.skippedCount, 'line')} had no roll number and {preview.skippedCount === 1 ? 'was' : 'were'} skipped
            {preview.skippedCount > preview.skippedLines.length && ' (first few shown)'}:
          </p>
          <ul className="m-0 mt-1 list-none space-y-0.5 p-0 font-mono text-xs text-slate-500">
            {preview.skippedLines.map((line, index) => <li key={index} className="truncate">{line}</li>)}
          </ul>
        </div>
      )}
    </div>
  );
}

/**
 * Admin-only: the hostel's resident list. While it has anyone on it, only those
 * students can use the student portal; an empty list lets every smail student in.
 */
export default function ResidentList() {
  const [count, setCount] = useState(null);
  const [text, setText] = useState('');
  const [preview, setPreview] = useState(null);
  const [rollNumber, setRollNumber] = useState('');
  const [search, setSearch] = useState('');
  const [matches, setMatches] = useState([]);
  const [busy, setBusy] = useState('');
  const [confirmingClear, setConfirmingClear] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  // Bumped after any change to reload the count and search results
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    const query = search.trim() ? `?search=${encodeURIComponent(search.trim())}` : '';
    // Wait for typing to pause before searching
    const timer = setTimeout(() => {
      apiFetch(`${apiBaseUrl}/admin/residents${query}`, { signal: controller.signal })
        .then(async (response) => {
          if (!response.ok) throw new Error(await errorMessage(response, 'Could not load the resident list.'));
          const summary = await response.json();
          setCount(summary.count);
          setMatches(summary.matches);
        })
        .catch((loadError) => {
          if (loadError.name !== 'AbortError') setError(loadError.message);
        });
    }, query ? 250 : 0);
    return () => {
      clearTimeout(timer);
      controller.abort();
    };
  }, [search, reloadKey]);

  const run = async (action, request, fallback) => {
    setError('');
    setNotice('');
    setBusy(action);
    try {
      const response = await apiFetch(`${apiBaseUrl}/admin/residents${request.path ?? ''}`, {
        method: request.method,
        headers: request.body ? { 'Content-Type': 'application/json' } : undefined,
        body: request.body ? JSON.stringify(request.body) : undefined,
      });
      if (!response.ok) throw new Error(await errorMessage(response, fallback));
      return response.status === 204 ? {} : await response.json();
    } catch (requestError) {
      setError(requestError.message);
      return null;
    } finally {
      setBusy('');
    }
  };

  const handleFile = (event) => {
    const file = event.target.files?.[0];
    event.target.value = '';
    if (!file) return;
    setPreview(null);
    if (/\.(xlsx?|ods)$/i.test(file.name)) {
      setError('Excel files cannot be read directly. In Excel, select the roll number column, copy it and paste it below, or use File › Save As › CSV and choose that file.');
      return;
    }
    setError('');
    file.text().then(setText).catch(() => setError('Could not read that file.'));
  };

  const handleCheck = async (event) => {
    event.preventDefault();
    setPreview(null);
    const result = await run('check', { path: '/preview', method: 'POST', body: { text } }, 'Could not check this list.');
    if (result) setPreview(result);
  };

  const handleReplace = async () => {
    const result = await run('replace', { method: 'PUT', body: { text } }, 'Could not save this list.');
    if (!result) return;
    setPreview(null);
    setText('');
    setNotice(`The resident list now has ${plural(result.found, 'student')}.`
      + (result.removed > 0 ? ` ${result.removed} lost access.` : ''));
    setReloadKey((key) => key + 1);
  };

  const handleAdd = async (event) => {
    event.preventDefault();
    const result = await run('add', { method: 'POST', body: { rollNumber } }, 'Could not add this student.');
    if (!result) return;
    setRollNumber('');
    setNotice(`${upper(result.rollNumber)} can now sign in.`);
    setReloadKey((key) => key + 1);
  };

  const handleRemove = async (roll) => {
    const result = await run(`remove:${roll}`, { path: `/${roll}`, method: 'DELETE' }, 'Could not remove this student.');
    if (!result) return;
    setNotice(`${upper(roll)} was removed and can no longer sign in.`);
    setReloadKey((key) => key + 1);
  };

  const handleClear = async () => {
    const result = await run('clear', { method: 'DELETE' }, 'Could not clear the list.');
    setConfirmingClear(false);
    if (!result) return;
    setNotice('The list is empty. Every @smail.iitm.ac.in student can sign in again.');
    setReloadKey((key) => key + 1);
  };

  return (
    <section className="mx-auto max-w-3xl space-y-5">
      <div>
        <h2 className="m-0 text-2xl font-bold text-slate-900">Residents</h2>
        <p className="m-0 mt-1 text-sm text-slate-500">
          Only students on this list can use the student portal. Staff and admins are not affected.
        </p>
      </div>

      <div className="flex items-start gap-3 rounded-xl border border-slate-200 bg-white p-4">
        <Building2 className="mt-0.5 h-5 w-5 shrink-0 text-indigo-600" aria-hidden="true" />
        <p className="m-0 text-sm text-slate-700">
          {count === null ? 'Loading…'
            : count === 0 ? <><strong>No list yet.</strong> Every student with an @smail.iitm.ac.in account can sign in until you upload one.</>
              : <><strong>{plural(count, 'resident')}</strong> can sign in as students.</>}
        </p>
      </div>

      {error && (
        <p role="alert" className="m-0 flex items-start gap-2 rounded-lg border border-rose-200 bg-rose-50 p-3 text-sm text-rose-800">
          <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" /> {error}
        </p>
      )}
      {notice && (
        <p role="status" className="m-0 rounded-lg border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-800">{notice}</p>
      )}

      <form onSubmit={handleCheck} className="space-y-3 rounded-xl border border-slate-200 bg-white p-4">
        <div>
          <h3 className="m-0 text-base font-semibold text-slate-900">Replace the list</h3>
          <p className="m-0 mt-1 text-sm text-slate-500">
            Do this at the start of each year. Copy the roll number column from your Excel sheet and paste it here,
            or choose a CSV file. Other columns such as names and rooms are ignored.
          </p>
        </div>
        <label className="sr-only" htmlFor="resident-text">Roll numbers</label>
        <textarea
          id="resident-text"
          value={text}
          onChange={(event) => {
            setText(event.target.value);
            setPreview(null);
          }}
          rows={6}
          placeholder={'CS23B051\nEE21M012\nAE20D005'}
          className="box-border w-full rounded-lg border border-slate-300 px-4 py-2.5 font-mono text-sm outline-none focus:border-indigo-500 focus:ring-2 focus:ring-indigo-200"
        />
        <div className="flex flex-wrap gap-2">
          <label className={`${secondaryButton} cursor-pointer`}>
            <FileUp className="h-4 w-4" aria-hidden="true" /> Choose CSV file
            <input type="file" accept=".csv,.txt,text/csv,text/plain,.xlsx,.xls" onChange={handleFile} className="sr-only" />
          </label>
          <button type="submit" disabled={!text.trim() || busy === 'check'} className={primaryButton}>
            {busy === 'check' && <RefreshCw className="h-4 w-4 animate-spin" />} Check list
          </button>
        </div>

        {preview && (
          <div className="space-y-3 rounded-lg border border-slate-200 bg-slate-50 p-3">
            <PreviewSummary preview={preview} />
            <p className="m-0 text-sm text-slate-700">Nothing has changed yet.</p>
            <div className="flex flex-wrap gap-2">
              <button
                type="button"
                onClick={handleReplace}
                disabled={busy === 'replace'}
                className={preview.removed > 0 ? dangerButton : primaryButton}
              >
                {busy === 'replace' ? 'Saving…' : 'Replace the list'}
              </button>
              <button type="button" onClick={() => setPreview(null)} disabled={busy === 'replace'} className={secondaryButton}>
                Cancel
              </button>
            </div>
          </div>
        )}
      </form>

      <div className="space-y-3 rounded-xl border border-slate-200 bg-white p-4">
        <div>
          <h3 className="m-0 text-base font-semibold text-slate-900">One student</h3>
          <p className="m-0 mt-1 text-sm text-slate-500">For someone who moves in or out during the year.</p>
        </div>
        <form onSubmit={handleAdd} className="flex flex-col gap-3 sm:flex-row">
          <label className="sr-only" htmlFor="resident-roll">Roll number to add</label>
          <input
            id="resident-roll"
            value={rollNumber}
            onChange={(event) => setRollNumber(event.target.value)}
            placeholder="CS23B051"
            className={inputClass}
          />
          <button type="submit" disabled={!rollNumber.trim() || busy === 'add'} className={primaryButton}>
            <UserPlus className="h-4 w-4" aria-hidden="true" /> Add
          </button>
        </form>

        <div className="relative">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" aria-hidden="true" />
          <label className="sr-only" htmlFor="resident-search">Search roll numbers</label>
          <input
            id="resident-search"
            type="search"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            placeholder="Search, e.g. CS23"
            className={`${inputClass} box-border w-full pl-9`}
          />
        </div>
        {search.trim() && (
          matches.length === 0 ? (
            <p className="m-0 text-sm text-slate-500">No roll numbers on the list match “{search.trim()}”.</p>
          ) : (
            <ul className="m-0 list-none divide-y divide-slate-100 rounded-lg border border-slate-200 p-0">
              {matches.map((roll) => (
                <li key={roll} className="flex items-center justify-between gap-3 px-3 py-2">
                  <span className="font-mono text-sm text-slate-900">{upper(roll)}</span>
                  <button
                    type="button"
                    onClick={() => handleRemove(roll)}
                    disabled={busy === `remove:${roll}`}
                    className="inline-flex items-center gap-1.5 rounded-lg border-0 bg-transparent px-3 py-1.5 text-sm font-medium text-rose-700 hover:bg-rose-50 disabled:opacity-60"
                  >
                    <Trash2 className="h-4 w-4" aria-hidden="true" /> Remove
                  </button>
                </li>
              ))}
            </ul>
          )
        )}
      </div>

      {count > 0 && (
        <div className="space-y-3 rounded-xl border border-slate-200 bg-white p-4">
          <div>
            <h3 className="m-0 text-base font-semibold text-slate-900">Turn off the check</h3>
            <p className="m-0 mt-1 text-sm text-slate-500">Empties the list, so every @smail.iitm.ac.in student can sign in again.</p>
          </div>
          {confirmingClear ? (
            <div className="flex flex-wrap items-center gap-2">
              <span className="text-sm text-slate-700">Remove all {count} roll numbers?</span>
              <button type="button" onClick={handleClear} disabled={busy === 'clear'} className={dangerButton}>
                {busy === 'clear' ? 'Removing…' : 'Empty the list'}
              </button>
              <button type="button" onClick={() => setConfirmingClear(false)} className={secondaryButton}>Cancel</button>
            </div>
          ) : (
            <button type="button" onClick={() => setConfirmingClear(true)} className={secondaryButton}>Empty the list</button>
          )}
        </div>
      )}
    </section>
  );
}
