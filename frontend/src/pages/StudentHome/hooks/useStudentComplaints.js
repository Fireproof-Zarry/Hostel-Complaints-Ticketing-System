import { useCallback, useEffect, useMemo, useState } from 'react';

import { apiFetch } from '../../../auth/session';
import { isActiveComplaint } from '../utils/studentUtils';

const apiBaseUrl = (import.meta.env.VITE_API_URL || '/api').replace(/\/$/, '');

// Shown straight away when the student returns from My Profile, while the
// fresh list loads. The token ties it to the current login.
let lastComplaints = null;

export default function useStudentComplaints(
  token,
  search = '',
  statusFilter = 'ALL'
) {
  const [complaints, setComplaints] = useState(() =>
    lastComplaints?.token === token ? lastComplaints.complaints : []
  );
  const [profile, setProfile] = useState(null);
  const [isLoading, setIsLoading] = useState(
    () => lastComplaints?.token !== token
  );
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

    apiFetch(`${apiBaseUrl}/auth/me`, { signal: controller.signal })
      .then((response) => (response.ok ? response.json() : null))
      .then((data) => {
        if (data) setProfile(data);
      })
      .catch(() => {
        // Profile loading should not prevent the dashboard from rendering.
      });

    return () => controller.abort();
  }, [token]);

  // Load the student's complaints after authentication.
  useEffect(() => {
    if (!token) {
      setIsLoading(false);
      return undefined;
    }

    let isActive = true;

    requestMyComplaints()
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
  }, [requestMyComplaints, token]);

  useEffect(() => {
    if (token && !isLoading && !error) {
      lastComplaints = { token, complaints };
    }
  }, [complaints, error, isLoading, token]);

  const refreshComplaints = useCallback(async () => {
    setIsRefreshing(true);

    try {
      const data = await requestMyComplaints();
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
