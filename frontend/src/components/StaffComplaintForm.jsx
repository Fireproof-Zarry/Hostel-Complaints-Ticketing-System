import { useEffect, useRef, useState } from 'react';
import { RefreshCw } from 'lucide-react';
import { apiFetch, getToken } from '../auth/session';
import DuplicateWarning from './DuplicateWarning';
import ImageUpload from './ImageUpload';
import { useDebouncedValue } from '../hooks/useDebouncedValue';
import { getFloorForRoom, HOSTEL_FLOORS } from '../utils/hostelLocation';

const apiBaseUrl = (import.meta.env.VITE_API_URL || '/api').replace(/\/$/, '');

const CATEGORIES = ['Electrical', 'Plumbing', 'Carpentry', 'Cleaning', 'IT'];
const MIN_DUPLICATE_TITLE_LENGTH = 4;

const initialForm = {
  title: '',
  description: '',
  category: CATEGORIES[0],
  isCommunityArea: true,
  floor: HOSTEL_FLOORS[0].name,
  wing: '',
  room: '',
};

const inputClasses = 'w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm outline-none focus:border-indigo-500';
const labelClasses = 'block space-y-1 text-sm font-medium text-slate-700';

/** Search parameters for similar complaints, or '' until there is enough to search on. */
function duplicateQuery(form) {
  const title = form.title.trim();
  if (title.length < MIN_DUPLICATE_TITLE_LENGTH) return '';
  if (!form.isCommunityArea && !getFloorForRoom(form.room.trim())) return '';
  const params = new URLSearchParams({ title, communityArea: String(form.isCommunityArea) });
  if (form.isCommunityArea) {
    params.set('floor', form.floor);
    params.set('wing', form.wing.trim());
  } else {
    params.set('room', form.room.trim());
  }
  return params.toString();
}

/**
 * Filing a complaint as hostel staff or an admin, about a shared area or any
 * room: staff have no room of their own, so they enter it here.
 */
export default function StaffComplaintForm({ onFiled }) {
  const [form, setForm] = useState(initialForm);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [duplicates, setDuplicates] = useState({ query: '', candidates: [] });
  const [dismissedQuery, setDismissedQuery] = useState('');
  const imageUploadRef = useRef(null);

  const debouncedQuery = useDebouncedValue(duplicateQuery(form), 500);

  useEffect(() => {
    if (!debouncedQuery) return undefined;
    const controller = new AbortController();
    apiFetch(`${apiBaseUrl}/complaints/potential-duplicates?${debouncedQuery}`, { signal: controller.signal })
      .then((response) => (response.ok ? response.json() : []))
      .then((candidates) => setDuplicates({ query: debouncedQuery, candidates }))
      // A failed check must never block filing the complaint
      .catch(() => {});
    return () => controller.abort();
  }, [debouncedQuery]);

  const visibleDuplicates = debouncedQuery
    && duplicates.query === debouncedQuery
    && dismissedQuery !== debouncedQuery
    ? duplicates.candidates
    : [];

  const update = (field) => (event) => setForm((current) => ({ ...current, [field]: event.target.value }));

  const handleSubmit = async (event) => {
    event.preventDefault();
    setError('');
    setNotice('');
    if (form.isCommunityArea && !form.wing.trim()) {
      setError('Enter the wing or area.');
      return;
    }
    if (!form.isCommunityArea && !getFloorForRoom(form.room.trim())) {
      setError('Enter a hostel room from 101 to 799.');
      return;
    }

    setIsSubmitting(true);
    try {
      const response = await apiFetch(`${apiBaseUrl}/complaints`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          title: form.title,
          description: form.description,
          category: form.category,
          isCommunityArea: form.isCommunityArea,
          ...(form.isCommunityArea
            ? { floor: form.floor, wing: form.wing }
            : { room: form.room.trim() }),
        }),
      });

      if (response.status === 429) {
        // Retry-After marks the filing rate limit; without it, it is the open-complaint limit
        const retryAfter = Number(response.headers.get('Retry-After'));
        throw new Error(retryAfter > 0
          ? `You're filing complaints too quickly. Please try again in ${retryAfter} seconds.`
          : 'You have reached the limit of 50 open complaints. Please wait for some to be resolved.');
      }
      if (!response.ok) {
        throw new Error('The complaint could not be filed. Please check the details and try again.');
      }

      const created = await response.json();

      // A failed upload keeps the complaint; resubmitting would create a duplicate
      let photoError = '';
      if (imageUploadRef.current?.hasFile()) {
        const upload = await imageUploadRef.current.upload(
          `${apiBaseUrl}/complaints/${created.id}/image`,
          getToken(),
        );
        if (!upload.ok) photoError = upload.error;
      }

      setForm(initialForm);
      imageUploadRef.current?.reset();
      setNotice(photoError
        ? `Complaint #${created.id} was filed, but its photo was not saved: ${photoError}`
        : `Complaint #${created.id} was filed. You can follow it under My complaints.`);
      onFiled?.();
    } catch (submitError) {
      setError(submitError.message);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <section className="mx-auto max-w-2xl space-y-5">
      <div>
        <h2 className="m-0 text-2xl font-bold text-slate-900">File a complaint</h2>
        <p className="m-0 mt-1 text-sm text-slate-500">Report an issue in a shared area or in any room.</p>
      </div>

      {notice && <p role="status" className="m-0 rounded-lg border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-800">{notice}</p>}
      {error && <p role="alert" className="m-0 rounded-lg border border-rose-200 bg-rose-50 p-3 text-sm text-rose-800">{error}</p>}

      <form onSubmit={handleSubmit} className="space-y-4 rounded-xl border border-slate-200 bg-white p-5">
        <fieldset className="m-0 border-0 p-0">
          <legend className="mb-2 p-0 text-sm font-medium text-slate-700">Where is the issue?</legend>
          <div className="flex flex-wrap gap-4 text-sm text-slate-700">
            <label className="inline-flex items-center gap-2">
              <input
                type="radio"
                name="complaint-location"
                checked={form.isCommunityArea}
                onChange={() => setForm((current) => ({ ...current, isCommunityArea: true }))}
              />
              Shared area
            </label>
            <label className="inline-flex items-center gap-2">
              <input
                type="radio"
                name="complaint-location"
                checked={!form.isCommunityArea}
                onChange={() => setForm((current) => ({ ...current, isCommunityArea: false }))}
              />
              A room
            </label>
          </div>
        </fieldset>

        {form.isCommunityArea ? (
          <div className="grid gap-4 sm:grid-cols-2">
            <label className={labelClasses}>
              <span>Floor</span>
              <select value={form.floor} onChange={update('floor')} className={inputClasses}>
                {HOSTEL_FLOORS.map((floor) => <option key={floor.name}>{floor.name}</option>)}
              </select>
            </label>
            <label className={labelClasses}>
              <span>Wing or area</span>
              <input
                required
                maxLength={100}
                value={form.wing}
                onChange={update('wing')}
                placeholder="e.g. East wing washroom"
                className={inputClasses}
              />
            </label>
          </div>
        ) : (
          <label className={labelClasses}>
            <span>Room number</span>
            <input
              required
              inputMode="numeric"
              maxLength={3}
              value={form.room}
              onChange={update('room')}
              placeholder="e.g. 204"
              className={inputClasses}
            />
          </label>
        )}

        <label className={labelClasses}>
          <span>Category</span>
          <select value={form.category} onChange={update('category')} className={inputClasses}>
            {CATEGORIES.map((category) => <option key={category}>{category}</option>)}
          </select>
        </label>

        <label className={labelClasses}>
          <span>Title</span>
          <input required maxLength={255} value={form.title} onChange={update('title')} className={inputClasses} />
        </label>

        {visibleDuplicates.length > 0 && (
          <DuplicateWarning
            scope={form.isCommunityArea ? 'community' : 'room'}
            candidates={visibleDuplicates}
            upvotingId={null}
            onDismiss={() => setDismissedQuery(debouncedQuery)}
          />
        )}

        <label className={labelClasses}>
          <span>Description</span>
          <textarea
            required
            rows={4}
            maxLength={1000}
            value={form.description}
            onChange={update('description')}
            className={inputClasses}
          />
        </label>

        <ImageUpload ref={imageUploadRef} disabled={isSubmitting} />

        <button
          type="submit"
          disabled={isSubmitting}
          className="inline-flex items-center gap-2 rounded-lg bg-indigo-600 px-4 py-2.5 text-sm font-semibold text-white hover:bg-indigo-700 disabled:opacity-60"
        >
          {isSubmitting && <RefreshCw className="h-4 w-4 animate-spin" aria-hidden="true" />}
          {isSubmitting ? 'Filing…' : 'File complaint'}
        </button>
      </form>
    </section>
  );
}
