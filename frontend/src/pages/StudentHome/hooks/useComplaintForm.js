import { useCallback, useEffect, useState } from 'react';

import { apiFetch, decodeTokenPayload } from '../../../auth/session';
import { useDebouncedValue } from '../../../hooks/useDebouncedValue';
import {
  clearComplaintDraft,
  isDraftEmpty,
  loadComplaintDraft,
  saveComplaintDraft,
} from '../../../utils/complaintDraft';
import { getFloorForRoom } from '../../../utils/hostelLocation';
import { INITIAL_COMPLAINT_FORM } from '../constants/studentConstants';

const apiBaseUrl = (import.meta.env.VITE_API_URL || '/api').replace(/\/$/, '');

export default function useComplaintForm({
  token,
  profile,
  imageUploadRef,
  requestMyComplaints,
  setComplaints,
  setError,
  setSuccess,
  setSubmittedComplaint,
}) {
  // Keep drafts associated with the signed-in account.
  const [draftOwner] = useState(
    () => decodeTokenPayload(token)?.email ?? null
  );

  // Restore the saved draft once when this hook is initialized.
  const [initialDraft] = useState(
    () => loadComplaintDraft(INITIAL_COMPLAINT_FORM, draftOwner)
  );
  const [formData, setFormData] = useState(initialDraft);
  const [draftRestored, setDraftRestored] = useState(
    () => !isDraftEmpty(initialDraft)
  );
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [duplicateResult, setDuplicateResult] = useState({
    key: '',
    candidates: [],
  });
  const [dismissedDuplicateKey, setDismissedDuplicateKey] = useState('');
  const [upvotingId, setUpvotingId] = useState(null);

  useEffect(() => {
    saveComplaintDraft(formData, draftOwner);
  }, [draftOwner, formData]);

  const profileRoom = profile?.roomNumber ?? '';
  const profileRoomFloor = getFloorForRoom(profileRoom);
  const missingProfileRoom =
    !formData.isCommunityArea && profile !== null && !profileRoomFloor;

  const duplicateMode = formData.isCommunityArea ? 'community' : 'room';
  const hasProfileRoom = Boolean(getFloorForRoom(profile?.roomNumber));

  const duplicateQueryKey =
    formData.title.trim().length >= 4 &&
    (formData.isCommunityArea || hasProfileRoom)
      ? JSON.stringify([
          duplicateMode,
          formData.title.trim(),
          formData.floor,
          formData.wing.trim(),
        ])
      : '';

  const debouncedDuplicateKey = useDebouncedValue(duplicateQueryKey, 500);

  useEffect(() => {
    if (!token || !debouncedDuplicateKey) return undefined;

    const [mode, title, floor, wing] = JSON.parse(debouncedDuplicateKey);
    const params = new URLSearchParams({
      title,
      communityArea: String(mode === 'community'),
    });

    if (mode === 'community') {
      params.set('floor', floor);
      if (wing) params.set('wing', wing);
    }

    const controller = new AbortController();

    apiFetch(
      `${apiBaseUrl}/complaints/potential-duplicates?${params}`,
      { signal: controller.signal }
    )
      .then((response) => (response.ok ? response.json() : []))
      .then((candidates) => {
        setDuplicateResult({
          key: debouncedDuplicateKey,
          candidates: Array.isArray(candidates) ? candidates : [],
        });
      })
      .catch(() => {
        // Duplicate detection must not block complaint submission.
      });

    return () => controller.abort();
  }, [debouncedDuplicateKey, token]);

  const visibleDuplicates =
    debouncedDuplicateKey &&
    JSON.parse(debouncedDuplicateKey)[0] === duplicateMode &&
    duplicateResult.key === debouncedDuplicateKey &&
    dismissedDuplicateKey !== debouncedDuplicateKey
      ? duplicateResult.candidates
      : [];

  const updateForm = useCallback(
    (field) => (event) => {
      const value = event.target.value;
      setFormData((current) => ({ ...current, [field]: value }));
    },
    []
  );

  const resetComplaintForm = useCallback(() => {
    clearComplaintDraft(draftOwner);
    setDraftRestored(false);
    setFormData(INITIAL_COMPLAINT_FORM);
    imageUploadRef.current?.reset();
    setDuplicateResult({ key: '', candidates: [] });
  }, [draftOwner, imageUploadRef]);

  const dismissDraft = useCallback(() => {
    setDraftRestored(false);
  }, []);

  const dismissDuplicates = useCallback(() => {
    setDismissedDuplicateKey(debouncedDuplicateKey);
  }, [debouncedDuplicateKey]);

  const handleSubmit = useCallback(
    async (event) => {
      event.preventDefault();
      setError('');
      setSuccess('');

      if (missingProfileRoom) {
        setError(
          'Add your room number in My Profile before filing a complaint about your room.'
        );
        return;
      }

      if (formData.isCommunityArea && !formData.wing.trim()) {
        setError('Enter the wing or area for a community-area complaint.');
        return;
      }

      setIsSubmitting(true);

      try {
        const response = await apiFetch(`${apiBaseUrl}/complaints`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            title: formData.title,
            description: formData.description,
            category: formData.category,
            isCommunityArea: formData.isCommunityArea,
            ...(formData.isCommunityArea && {
              floor: formData.floor,
              wing: formData.wing,
            }),
            availableTimeSlot: formData.availableTimeSlot,
            keyWithSecurity:
              !formData.isCommunityArea && formData.keyWithSecurity,
          }),
        });

        if (response.status === 429) {
          const retryAfter = Number(response.headers.get('Retry-After'));
          setError(
            retryAfter > 0
              ? `You're filing complaints too quickly. Please try again in ${retryAfter} seconds. Your draft is saved.`
              : 'You have reached the limit of 5 open complaints. Please wait for some to be resolved.'
          );
          return;
        }

        if (response.status === 422) {
          throw new Error(
            'Add a valid room number in My Profile before filing a complaint about your room.'
          );
        }

        if (!response.ok) {
          throw new Error(
            'Failed to submit the complaint. Please check your details and try again.'
          );
        }

        const createdComplaint = await response.json();
        let photoError = '';

        // The complaint is created first because the image endpoint needs its ID.
        // An image upload failure does not undo the complaint itself.
        if (imageUploadRef.current?.hasFile()) {
          const upload = await imageUploadRef.current.upload(
            `${apiBaseUrl}/complaints/${createdComplaint.id}/image`,
            token
          );

          if (!upload.ok) photoError = upload.error;
        }

        resetComplaintForm();
        setSubmittedComplaint({ id: createdComplaint.id, photoError });

        const updatedComplaints = await requestMyComplaints();
        setComplaints(updatedComplaints);
      } catch (submitError) {
        setError(
          submitError instanceof Error
            ? submitError.message
            : 'Network error. Please try again later.'
        );
      } finally {
        setIsSubmitting(false);
      }
    },
    [
      formData,
      imageUploadRef,
      missingProfileRoom,
      requestMyComplaints,
      resetComplaintForm,
      setComplaints,
      setError,
      setSuccess,
      setSubmittedComplaint,
      token,
    ]
  );

  const handleUpvoteDuplicate = useCallback(
    async (candidate) => {
      setError('');
      setSuccess('');
      setUpvotingId(candidate.id);

      try {
        const upvote = () =>
          apiFetch(`${apiBaseUrl}/complaints/${candidate.id}/upvote`, {
            method: 'POST',
          });

        let response = await upvote();
        if (!response.ok) {
          throw new Error(
            'That complaint can no longer be upvoted. You can still submit your own complaint.'
          );
        }

        let result = await response.json();

        // The endpoint toggles an upvote. If the first request removed an
        // existing upvote, send it again to leave the student upvoting it.
        if (!result.upvoted) {
          response = await upvote();
          if (!response.ok) {
            throw new Error(
              'Your upvote could not be saved. Please try again.'
            );
          }
          result = await response.json();
        }

        resetComplaintForm();
        setSuccess(
          `Thanks! Your upvote was added to "${candidate.title}" (#${candidate.id}), which now has ${result.upvoteCount} ` +
            `${result.upvoteCount === 1 ? 'upvote' : 'upvotes'}. You don't need to file a new complaint.`
        );
      } catch (upvoteError) {
        setError(
          upvoteError instanceof Error
            ? upvoteError.message
            : 'Network error. Please try again later.'
        );
      } finally {
        setUpvotingId(null);
      }
    },
    [resetComplaintForm, setError, setSuccess]
  );

  return {
    formData,
    setFormData,
    updateForm,
    draftRestored,
    hasRestoredDraft: draftRestored && !isDraftEmpty(formData),
    dismissDraft,
    isSubmitting,
    handleSubmit,
    resetComplaintForm,
    profileRoom,
    profileRoomFloor,
    missingProfileRoom,
    visibleDuplicates,
    duplicateMode,
    dismissDuplicates,
    upvotingId,
    handleUpvoteDuplicate,
  };
}
