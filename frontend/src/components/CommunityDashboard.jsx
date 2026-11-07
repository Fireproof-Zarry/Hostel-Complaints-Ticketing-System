import { useEffect, useRef, useState } from 'react';
import { CalendarDays, MapPin, RefreshCw, ThumbsUp, Users } from 'lucide-react';
import { apiFetch } from '../auth/session';
import { formatStatus, statusClasses } from '../utils/complaintStatus';
import { HOSTEL_FLOORS } from '../utils/hostelLocation';
import { replaceComplaint, toggleUpvoteOptimistically } from '../utils/upvotes';

const apiBaseUrl = (import.meta.env.VITE_API_URL || '/api').replace(/\/$/, '');
const PAGE_SIZE = 20;

const SORT_OPTIONS = [
  { value: 'upvotes', label: 'Most upvoted' },
  { value: 'recent', label: 'Newest' },
];

function formatDate(value) {
  if (!value) return 'Date unavailable';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? 'Date unavailable' : date.toLocaleDateString();
}

async function fetchCommunityPage({ sort, floor, page }) {
  const params = new URLSearchParams({ sort, page: String(page), size: String(PAGE_SIZE) });
  if (floor) params.set('floor', floor);
  const response = await apiFetch(`${apiBaseUrl}/complaints/community?${params}`);
  if (!response.ok) {
    throw new Error('Could not load community issues. Please try again.');
  }
  return response.json();
}

/** Upvote toggle showing the current count. */
function UpvoteButton({ complaint, pending, onToggle }) {
  if (complaint.reportedByYou) {
    return (
      <span className="inline-flex shrink-0 items-center gap-1.5 rounded-lg border border-slate-200 px-3 py-1.5 text-sm text-slate-500">
        <ThumbsUp className="h-4 w-4" aria-hidden="true" />
        {complaint.upvoteCount}
        <span className="text-xs">· Your complaint</span>
      </span>
    );
  }

  const upvoted = complaint.upvotedByYou;
  return (
    <button
      type="button"
      onClick={() => onToggle(complaint)}
      disabled={pending}
      aria-pressed={upvoted}
      aria-label={`${upvoted ? 'Remove your upvote from' : 'Upvote'} "${complaint.title}" (${complaint.upvoteCount} ${complaint.upvoteCount === 1 ? 'upvote' : 'upvotes'})`}
      className={`inline-flex shrink-0 items-center gap-1.5 rounded-lg px-3 py-1.5 text-sm font-semibold transition disabled:cursor-wait ${
        upvoted
          ? 'bg-blue-600 text-white hover:bg-blue-700'
          : 'border border-slate-300 text-slate-700 hover:border-blue-400 hover:text-blue-700'
      }`}
    >
      <ThumbsUp className="h-4 w-4" fill={upvoted ? 'currentColor' : 'none'} aria-hidden="true" />
      {complaint.upvoteCount}
    </button>
  );
}

/** One community complaint with its upvote button. */
function CommunityComplaintCard({ complaint, pending, onToggle }) {
  return (
    <article className="flex flex-col gap-4 rounded-xl border border-slate-200 bg-white p-5 shadow-sm sm:flex-row">
      {complaint.imageUrl && (
        <img
          src={complaint.imageUrl}
          alt=""
          loading="lazy"
          className="h-28 w-full shrink-0 rounded-lg border border-slate-200 object-cover sm:w-36"
        />
      )}
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div className="min-w-0">
            <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">
              {complaint.category || 'Uncategorized'} · #{complaint.id}
            </p>
            <h3 className="mt-1 text-lg font-semibold text-slate-900">{complaint.title}</h3>
          </div>
          <span className={`rounded-full px-3 py-1 text-xs font-semibold ${statusClasses(complaint.status)}`}>
            {formatStatus(complaint.status)}
          </span>
        </div>
        <p className="mt-2 line-clamp-3 whitespace-pre-wrap text-sm leading-6 text-slate-600">{complaint.description}</p>
        <div className="mt-4 flex flex-wrap items-center justify-between gap-3">
          <div className="flex flex-wrap gap-x-5 gap-y-2 text-xs font-medium text-slate-500">
            <span className="inline-flex items-center gap-1.5">
              <MapPin className="h-4 w-4" aria-hidden="true" />
              {complaint.floor} floor{complaint.wing && ` · ${complaint.wing}`}
            </span>
            <span className="inline-flex items-center gap-1.5">
              <CalendarDays className="h-4 w-4" aria-hidden="true" />
              Reported {formatDate(complaint.createdAt)}
            </span>
          </div>
          <UpvoteButton complaint={complaint} pending={pending} onToggle={onToggle} />
        </div>
      </div>
    </article>
  );
}

/**
 * Community issues (corridors, washrooms, dispensers…) that students can
 * upvote. Self-contained: owns its data and upvote state, independent of the
 * student's personal complaints feed.
 */
export default function CommunityDashboard({ refreshSignal = 0 }) {
  const [sort, setSort] = useState('upvotes');
  const [floor, setFloor] = useState('');
  const [reloadCount, setReloadCount] = useState(0);
  const [feed, setFeed] = useState({ key: null, items: [], page: 0, totalPages: 0, totalItems: 0, error: '' });
  const [isLoadingMore, setIsLoadingMore] = useState(false);
  const [pendingIds, setPendingIds] = useState(() => new Set());
  // Mirrors pendingIds for click handlers, which may run before a re-render
  const pendingRef = useRef(new Set());
  const handledRefreshRef = useRef(refreshSignal);

  // Loading is derived: the feed is stale until it was fetched for the current filters
  const queryKey = JSON.stringify([sort, floor, reloadCount]);
  const isLoading = feed.key !== queryKey;

  useEffect(() => {
    let active = true;
    fetchCommunityPage({ sort, floor, page: 0 })
      .then((data) => {
        if (!active) return;
        setFeed({
          key: queryKey,
          items: data.items ?? [],
          page: 0,
          totalPages: data.totalPages ?? 0,
          totalItems: data.totalItems ?? 0,
          error: '',
        });
      })
      .catch((error) => {
        if (!active) return;
        setFeed({ key: queryKey, items: [], page: 0, totalPages: 0, totalItems: 0, error: error.message });
      });
    return () => {
      active = false;
    };
  }, [floor, queryKey, sort]);

  // A status changed: update the cards in place, keeping the list and any
  // pages loaded with Load more. The Refresh button still works if this fails.
  useEffect(() => {
    if (handledRefreshRef.current === refreshSignal) return undefined;
    handledRefreshRef.current = refreshSignal;

    let active = true;
    const pages = Array.from({ length: feed.page + 1 }, (_, page) => page);

    Promise.all(pages.map((page) => fetchCommunityPage({ sort, floor, page })))
      .then((results) => {
        if (!active) return;
        const fresh = new Map(
          results.flatMap((data) => data.items ?? []).map((item) => [item.id, item])
        );
        setFeed((current) => ({
          ...current,
          items: current.items.map((item) =>
            // An upvote in progress is newer than this response
            pendingRef.current.has(item.id) ? item : (fresh.get(item.id) ?? item)
          ),
        }));
      })
      .catch(() => {});

    return () => {
      active = false;
    };
  }, [feed.page, floor, refreshSignal, sort]);

  const handleLoadMore = async () => {
    const nextPage = feed.page + 1;
    setIsLoadingMore(true);
    try {
      const data = await fetchCommunityPage({ sort, floor, page: nextPage });
      setFeed((current) => {
        // Upvote counts can shift items between pages; skip any already shown
        const shown = new Set(current.items.map((item) => item.id));
        return {
          ...current,
          items: [...current.items, ...(data.items ?? []).filter((item) => !shown.has(item.id))],
          page: nextPage,
          totalPages: data.totalPages ?? current.totalPages,
          totalItems: data.totalItems ?? current.totalItems,
        };
      });
    } catch (error) {
      setFeed((current) => ({ ...current, error: error.message }));
    } finally {
      setIsLoadingMore(false);
    }
  };

  const setPending = (id, isPending) => {
    if (isPending) pendingRef.current.add(id);
    else pendingRef.current.delete(id);
    setPendingIds(new Set(pendingRef.current));
  };

  const handleToggleUpvote = async (complaint) => {
    if (pendingRef.current.has(complaint.id)) return;
    const before = complaint;

    // Optimistic: show the new state immediately
    setFeed((current) => ({
      ...current,
      items: replaceComplaint(current.items, complaint.id, toggleUpvoteOptimistically),
    }));
    setPending(complaint.id, true);

    try {
      const response = await apiFetch(`${apiBaseUrl}/complaints/${complaint.id}/upvote`, { method: 'POST' });
      if (!response.ok) throw new Error(`Upvote failed (HTTP ${response.status})`);
      const result = await response.json();
      // Adopt the server's numbers (other students may have voted meanwhile)
      setFeed((current) => ({
        ...current,
        items: replaceComplaint(current.items, complaint.id, (item) => ({
          ...item,
          upvotedByYou: result.upvoted,
          upvoteCount: result.upvoteCount,
        })),
      }));
    } catch {
      // Silently revert so the card matches the server again
      setFeed((current) => ({
        ...current,
        items: replaceComplaint(current.items, complaint.id, () => before),
      }));
    } finally {
      setPending(complaint.id, false);
    }
  };

  const hasMore = feed.page + 1 < feed.totalPages;

  return (
    <section className="space-y-5" aria-labelledby="community-heading">
      <div className="flex flex-col justify-between gap-4 sm:flex-row sm:items-end">
        <div>
          <h3 id="community-heading" className="text-2xl font-bold text-slate-900">Community issues</h3>
          <p className="mt-1 text-sm text-slate-500">
            Open issues in shared spaces. Upvote the ones that affect you too so they get fixed sooner.
          </p>
        </div>
        <button
          type="button"
          onClick={() => setReloadCount((count) => count + 1)}
          disabled={isLoading}
          className="inline-flex items-center justify-center gap-2 rounded-lg border border-slate-300 bg-white px-4 py-2.5 text-sm font-medium text-slate-700 transition hover:bg-slate-50 disabled:opacity-50"
        >
          <RefreshCw className={`h-4 w-4 ${isLoading ? 'animate-spin' : ''}`} aria-hidden="true" />
          Refresh
        </button>
      </div>

      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div className="inline-flex rounded-lg border border-slate-300 bg-white p-1" role="group" aria-label="Sort community issues">
          {SORT_OPTIONS.map((option) => (
            <button
              key={option.value}
              type="button"
              onClick={() => setSort(option.value)}
              aria-pressed={sort === option.value}
              className={`rounded-md px-3 py-1.5 text-sm font-medium transition ${
                sort === option.value ? 'bg-indigo-600 text-white' : 'text-slate-600 hover:text-slate-900'
              }`}
            >
              {option.label}
            </button>
          ))}
        </div>
        <label className="flex items-center gap-2 text-sm text-slate-600">
          <span>Floor</span>
          <select
            value={floor}
            onChange={(event) => setFloor(event.target.value)}
            className="rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-200"
          >
            <option value="">All floors</option>
            {HOSTEL_FLOORS.map(({ name }) => (
              <option key={name} value={name}>{name}</option>
            ))}
          </select>
        </label>
      </div>

      {feed.error && !isLoading && (
        <p role="alert" className="rounded-lg border border-rose-200 bg-rose-50 p-4 text-sm text-rose-800">{feed.error}</p>
      )}

      {isLoading ? (
        <p className="rounded-xl border border-slate-200 bg-white p-6 text-sm text-slate-500" role="status">
          Loading community issues…
        </p>
      ) : feed.items.length === 0 && !feed.error ? (
        <div className="rounded-xl border border-slate-200 bg-white p-8 text-center">
          <Users className="mx-auto h-8 w-8 text-slate-300" aria-hidden="true" />
          <p className="mt-3 font-medium text-slate-800">No open community issues{floor && ` on the ${floor.toLowerCase()} floor`}.</p>
          <p className="mt-1 text-sm text-slate-500">Spotted one? File a complaint and choose “Community area”.</p>
        </div>
      ) : (
        <>
          <p className="text-sm text-slate-500">
            Showing {feed.items.length} of {feed.totalItems} open {feed.totalItems === 1 ? 'issue' : 'issues'}
          </p>
          <div className="space-y-4">
            {feed.items.map((complaint) => (
              <CommunityComplaintCard
                key={complaint.id}
                complaint={complaint}
                pending={pendingIds.has(complaint.id)}
                onToggle={handleToggleUpvote}
              />
            ))}
          </div>
          {hasMore && (
            <div className="flex justify-center">
              <button
                type="button"
                onClick={handleLoadMore}
                disabled={isLoadingMore}
                className="inline-flex items-center gap-2 rounded-lg border border-slate-300 bg-white px-4 py-2.5 text-sm font-medium text-slate-700 transition hover:bg-slate-50 disabled:opacity-50"
              >
                {isLoadingMore && <RefreshCw className="h-4 w-4 animate-spin" aria-hidden="true" />}
                Load more
              </button>
            </div>
          )}
        </>
      )}
    </section>
  );
}
