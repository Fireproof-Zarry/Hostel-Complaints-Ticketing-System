import { useCallback, useEffect, useMemo, useState } from 'react';

import { apiFetch, decodeTokenPayload } from '../../../auth/session';
import { isActiveComplaint } from '../utils/studentUtils';
import { trackBackground } from '../../../utils/backgroundActivity';

const apiBaseUrl = (import.meta.env.VITE_API_URL || '/api').replace(/\/$/, '');

// The last complaints and profile, shown straight away after a page reload or
// a return from My Profile while fresh data loads. Kept in this browser tab's
// session storage (cleared on logout) and tied to the signed-in account.
export const STUDENT_CACHE_KEY = 'studentDashboardCache';

function accountOf(token) {
  return token ? decodeTokenPayload(token)?.email ?? null : null;
}

function readCache(token) {
  const account = accountOf(token);
  if (!account) return null;
  try {
    const cached = JSON.parse(sessionStorage.getItem(STUDENT_CACHE_KEY));
    return cached?.account === account && Array.isArray(cached.complaints) ? cached : null;
  } catch {
    return null;
  }
}

function writeCache(token, data) {
  const account = accountOf(token);
  if (!account) return;
  try {
    const current = readCache(token) ?? {};
    sessionStorage.setItem(STUDENT_CACHE_KEY, JSON.stringify({ ...current, ...data, account }));
  } catch {
    // Without storage the next reload simply shows Loading.
  }
}

/** Loads the student's complaints and profile, and applies the search and status filter. */
export default function useStudentComplaints(
  token,
  search = '',
  statusFilter = 'ALL'
) {
  const [cached] = useState(() => readCache(token));
  const [complaints, setComplaints] = useState(() => cached?.complaints ?? []);
  const [profile, setProfile] = useState(() => cached?.profile ?? null);
  const [isLoading, setIsLoading] = useState(() => !cached);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [error, setError] = useState('');

  const requestMyComplaints = useCallback(async () => {
    const response = await apiFetch(`${apiBaseUrl}/complaints/mine`);

    if (response.status === 403) {
      throw new Error(
        'Your account is signed in but is not allowed to view these complaints (403).'
      );
    }

    if (!response.ok) {
      throw new Error('Could not load your complaints. Please try again.');
    }

    const data = await response.json();

    if (!Array.isArray(data)) {
      throw new Error(
        'The server returned an unexpected complaints response.'
      );
    }

    return data;
  }, []);

  // Personal complaints use the room number stored in the student's profile.
  useEffect(() => {
    if (!token) return undefined;

    const controller = new AbortController();

    const request = apiFetch(`${apiBaseUrl}/auth/me`, { signal: controller.signal });
    // A cached profile is on screen: refresh it behind the corner "Updating…"
    if (cached?.profile) trackBackground(request);
    request
      .then((response) => (response.ok ? response.json() : null))
      .then((data) => {
        if (data) {
          setProfile(data);
          writeCache(token, { profile: data });
        }
      })
      .catch(() => {
        // Profile loading should not prevent the dashboard from rendering.
      });

    return () => controller.abort();
  }, [cached, token]);

  // Load the student's complaints after authentication.
  useEffect(() => {
    if (!token) {
      setIsLoading(false);
      return undefined;
    }

    let isActive = true;

    const request = requestMyComplaints();
    // Cached complaints are on screen: refresh them behind the corner "Updating…"
    if (cached) trackBackground(request);
    request
      .then((data) => {
        if (isActive) {
          setError('');
          setComplaints(data);
        }
      })
      .catch((fetchError) => {
        if (isActive) {
          setError(
            fetchError instanceof Error
              ? fetchError.message
              : 'Network error while loading complaints.'
          );
        }
      })
      .finally(() => {
        if (isActive) setIsLoading(false);
      });

    return () => {
      isActive = false;
    };
  }, [cached, requestMyComplaints, token]);

  useEffect(() => {
    if (token && !isLoading && !error) {
      writeCache(token, { complaints });
    }
  }, [complaints, error, isLoading, token]);

  const refreshComplaints = useCallback(async () => {
    setIsRefreshing(true);

    try {
      const data = await trackBackground(requestMyComplaints());
      setError('');
      setComplaints(data);
      return data;
    } catch (refreshError) {
      setError(
        refreshError instanceof Error
          ? refreshError.message
          : 'Network error while loading complaints.'
      );
      throw refreshError;
    } finally {
      setIsRefreshing(false);
    }
  }, [requestMyComplaints]);

  const activeCount = useMemo(
    () => complaints.filter(isActiveComplaint).length,
    [complaints]
  );

  const filteredComplaints = useMemo(() => {
    const query = search.trim().toLowerCase();

    return complaints.filter((complaint) => {
      const matchesSearch =
        !query ||
        [
          complaint.title,
          complaint.description,
          complaint.category,
          complaint.room,
          complaint.wing,
        ].some((value) =>
          String(value ?? '').toLowerCase().includes(query)
        );

      const matchesStatus =
        statusFilter === 'ALL' ||
        complaint.status?.toUpperCase() === statusFilter;

      return matchesSearch && matchesStatus;
    });
  }, [complaints, search, statusFilter]);

  return {
    complaints,
    setComplaints,
    profile,
    isLoading,
    isRefreshing,
    error,
    setError,
    activeCount,
    filteredComplaints,
    requestMyComplaints,
    refreshComplaints,
  };
}
