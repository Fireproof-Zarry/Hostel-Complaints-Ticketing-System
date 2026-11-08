import { useCallback, useEffect, useRef, useState } from 'react';
import { Navigate, useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import {
  AlertCircle,
  Bell,
  CheckCircle2,
} from 'lucide-react';

import CommunityDashboard from '../../components/CommunityDashboard';
import DeleteComplaintDialog from '../../components/DeleteComplaintDialog';
import NotificationToast from '../../components/NotificationToast';
import SubmissionConfirmation from '../../components/SubmissionConfirmation';
import StudentComplaintDetails from '../../components/StudentComplaintDetails';
import StudentSidebar from '../../components/StudentSidebar';
import { STUDENT_NAVIGATION } from '../../components/studentNavigation';
import { apiFetch, getToken, logout } from '../../auth/session';

import ComplaintForm from './sections/ComplaintForm';
import ComplaintList from './sections/ComplaintList';
import StudentDashboard from './sections/StudentDashboard';
import StudentHeader from './components/StudentHeader';
import useComplaintForm from './hooks/useComplaintForm';
import useStudentComplaints from './hooks/useStudentComplaints';
import usePushNotifications from '../../notifications/usePushNotifications';
import { removeFcmToken } from '../../notifications/pushNotifications';
import useForegroundNotifications from '../../notifications/useForegroundNotifications';
import { isDraftEmpty } from '../../utils/complaintDraft';

const apiBaseUrl = (import.meta.env.VITE_API_URL || '/api').replace(/\/$/, '');

const TAB_STORAGE_KEY = 'studentActiveTab';
const TAB_IDS = new Set(STUDENT_NAVIGATION.map((item) => item.id).filter((id) => id !== 'profile'));

// The tab open before a page refresh; per browser tab, so it resets once the tab is closed
function readSavedTab() {
  try {
    const saved = sessionStorage.getItem(TAB_STORAGE_KEY);
    return TAB_IDS.has(saved) ? saved : null;
  } catch {
    return null;
  }
}

/** The student dashboard: the sidebar, each tab, and the complaint dialogs. */
export default function StudentHomePage() {
  const location = useLocation();
  const navigate = useNavigate();
  const token = getToken();

  const [activeTab, setActiveTab] = useState(
    () => location.state?.tab ?? readSavedTab() ?? 'home'
  );
  const [submittedComplaint, setSubmittedComplaint] = useState(null);
  const [complaintToDelete, setComplaintToDelete] = useState(null);
  const [isDeleting, setIsDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState('');
  const [success, setSuccess] = useState('');
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [statusUpdateCount, setStatusUpdateCount] = useState(0);
  const [searchParams, setSearchParams] = useSearchParams();
  // Set by a notification click (?complaint=id); cleared once that card is on screen
  const [scrollToComplaintId, setScrollToComplaintId] = useState(null);

  // Opening the Community tab refreshes its counts in place (see below)
  const [communityVisits, setCommunityVisits] = useState(0);

  useEffect(() => {
    try {
      sessionStorage.setItem(TAB_STORAGE_KEY, activeTab);
    } catch {
      // Without storage a refresh just opens Home.
    }
  }, [activeTab]);

  const imageUploadRef = useRef(null);

  const {
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
  } = useStudentComplaints(token, search, statusFilter);

  // An id, so a refresh of the list also updates the open popup
  const [viewedComplaintId, setViewedComplaintId] = useState(null);
  const viewedComplaint = complaints.find(
    (complaint) => complaint.id === viewedComplaintId
  );
  const closeComplaintDetails = useCallback(
    () => setViewedComplaintId(null),
    []
  );
  const handleViewComplaint = useCallback(
    (complaint) => setViewedComplaintId(complaint.id),
    []
  );


  const form = useComplaintForm({
    token,
    profile,
    imageUploadRef,
    requestMyComplaints,
    setComplaints,
    setError,
    setSuccess,
    setSubmittedComplaint,
  });

  // A status update arrived, so show it without the refresh spinner or errors.
  const handleForegroundMessage = useCallback(async () => {
    setStatusUpdateCount((count) => count + 1);

    try {
      setComplaints(await requestMyComplaints());
    } catch {
      // The manual Refresh button still works if this quiet update fails.
    }
  }, [requestMyComplaints, setComplaints]);

  const notifications = usePushNotifications(profile?.id);
  const { notificationToast, dismissNotificationToast } =
    useForegroundNotifications(handleForegroundMessage);

  const handleOpenNotificationToast = useCallback(() => {
    dismissNotificationToast();
    setError('');
    setSuccess('');
    if (notificationToast?.url) navigate(notificationToast.url);
    else setActiveTab('home');
  }, [dismissNotificationToast, notificationToast, navigate, setError]);

  // From a notification: My Complaints, with nothing filtered out, and the
  // latest statuses (the page may have been in the background)
  const complaintLink = searchParams.get('complaint');
  useEffect(() => {
    if (!complaintLink) return;
    setActiveTab('complaints');
    setStatusFilter('ALL');
    setSearch('');
    setScrollToComplaintId(Number(complaintLink));
    setSearchParams({}, { replace: true });
    requestMyComplaints().then(setComplaints).catch(() => {});
  }, [complaintLink, requestMyComplaints, setComplaints, setSearchParams]);

  // Scrolls once the card is rendered; gives up if the complaint is gone
  useEffect(() => {
    if (scrollToComplaintId === null || activeTab !== 'complaints') return;
    const card = document.getElementById(`complaint-${scrollToComplaintId}`);
    if (card) {
      card.scrollIntoView({ behavior: 'smooth', block: 'center' });
      setScrollToComplaintId(null);
    } else if (!isLoading && !complaints.some((complaint) => complaint.id === scrollToComplaintId)) {
      setScrollToComplaintId(null);
    }
  }, [scrollToComplaintId, activeTab, filteredComplaints, complaints, isLoading]);

  // A click on a notification while this page is already open (see the service worker)
  useEffect(() => {
    const handleWorkerMessage = (event) => {
      if (event.data?.type === 'OPEN_URL') navigate(event.data.url);
    };
    navigator.serviceWorker?.addEventListener('message', handleWorkerMessage);
    return () => navigator.serviceWorker?.removeEventListener('message', handleWorkerMessage);
  }, [navigate]);

  const handleNudge = useCallback(
    async (complaint) => {
      const response = await apiFetch(
        `${apiBaseUrl}/complaints/${complaint.id}/nudge`,
        { method: 'POST' }
      );

      // Also after a refusal: the page may be out of date, and the refreshed
      // complaint updates the button and explains why.
      try {
        setComplaints(await requestMyComplaints());
      } catch {
        // The manual Refresh button still works if this quiet update fails.
      }

      if (!response.ok) {
        throw new Error('Could not nudge the administrators. Please try again later.');
      }
    },
    [requestMyComplaints, setComplaints]
  );

  const handleLogout = useCallback(async () => {
    await removeFcmToken();
    logout();
    navigate('/login', { replace: true });
  }, [navigate]);

  const handleOpenNotifications = useCallback(() => {
    setActiveTab('notifications');
    setError('');
    setSuccess('');
  }, [setError]);

  const handleOpenComplaints = useCallback(() => {
    setActiveTab('complaints');
    setStatusFilter('ALL');
    setSearch('');
    setError('');
    setSuccess('');
  }, [setError]);

  const handleFileComplaint = useCallback(() => {
    setActiveTab('submit');
    setError('');
    setSuccess('');
  }, [setError]);

  const handleRefresh = useCallback(async () => {
    try {
      await refreshComplaints();
    } catch {
      // The hook stores the refresh error for the page-level alert.
    }
  }, [refreshComplaints]);

  const openProfileFromComplaint = useCallback(() => {
    navigate('/profile', { state: { returnTo: 'submit' } });
  }, [navigate]);

  const closeDeleteDialog = useCallback(() => {
    setComplaintToDelete(null);
    setDeleteError('');
  }, []);

  const closeConfirmation = useCallback(() => {
    setSubmittedComplaint(null);
  }, []);

  const handleDeleteComplaint = useCallback(
    (complaint) => {
      setError('');
      setSuccess('');
      setDeleteError('');
      setComplaintToDelete(complaint);
    },
    [setError]
  );

  const handleSaveDescription = useCallback(
    async (complaint, description) => {
      const response = await apiFetch(
        `${apiBaseUrl}/complaints/${complaint.id}/description`,
        {
          method: 'PATCH',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ description }),
        }
      );

      if (response.status === 409) {
        setComplaints(await requestMyComplaints());
        throw new Error('This complaint is closed, so it can no longer be edited.');
      }
      if (!response.ok) {
        throw new Error(`Could not save your changes (HTTP ${response.status}). Please try again.`);
      }

      setComplaints((current) =>
        current.map((item) => (item.id === complaint.id ? { ...item, description } : item))
      );
    },
    [requestMyComplaints, setComplaints]
  );

  const handleConfirmDelete = useCallback(async () => {
    if (!complaintToDelete) return;

    const complaint = complaintToDelete;
    setIsDeleting(true);
    setDeleteError('');

    try {
      const response = await apiFetch(`${apiBaseUrl}/complaints/${complaint.id}`, {
        method: 'DELETE',
      });

      if (response.status === 409) {
        const updated = await requestMyComplaints();
        setComplaints(updated);
        throw new Error(
          'This complaint is already being handled, so it can no longer be deleted.'
        );
      }

      if (!response.ok && response.status !== 404) {
        throw new Error(
          `The complaint could not be deleted (HTTP ${response.status}). Please try again.`
        );
      }

      // A 404 means the complaint is already gone, so remove it locally too.
      setComplaints((current) =>
        current.filter((item) => item.id !== complaint.id)
      );
      setComplaintToDelete(null);
      setSuccess(`Complaint #${complaint.id} was deleted.`);
    } catch (deleteFailure) {
      setDeleteError(
        deleteFailure instanceof Error
          ? deleteFailure.message
          : 'Network error. Please try again.'
      );
    } finally {
      setIsDeleting(false);
    }
  }, [complaintToDelete, requestMyComplaints, setComplaints]);

  if (!token) {
    return <Navigate to="/login" replace />;
  }

  // Registration failed. A failed switch-off keeps the switch instead.
  const canRetryNotifications =
    notifications.notificationPermission === 'granted' &&
    notifications.notificationError &&
    !notifications.isNotificationEnabled;

  return (
    <div className="min-h-screen bg-slate-100 text-slate-900 md:flex">
      <StudentSidebar
        activeId={activeTab}
        onSelect={(id) => {
          if (id === 'profile') {
            if (activeTab === 'submit' && !isDraftEmpty(form.formData)) {
              openProfileFromComplaint();
            } else {
              navigate('/profile');
            }
            return;
          }

          if (id === 'community' && activeTab !== 'community') {
            setCommunityVisits((count) => count + 1);
          }
          setActiveTab(id);
          setError('');
          setSuccess('');
        }}
      />

      <main className="min-w-0 flex-1">
        <StudentHeader
          title={STUDENT_NAVIGATION.find((item) => item.id === activeTab)?.label}
          isNotificationsActive={activeTab === 'notifications'}
          onOpenNotifications={handleOpenNotifications}
          onLogout={handleLogout}
        />

        <div className="mx-auto max-w-6xl space-y-6 p-4 md:p-8">
          {error && (
            <div
              role="alert"
              className="flex items-start gap-3 rounded-lg border border-rose-200 bg-rose-50 p-4 text-sm text-rose-800"
            >
              <AlertCircle className="mt-0.5 h-5 w-5 shrink-0" />
              <p>{error}</p>
            </div>
          )}

          {success && (
            <div
              role="status"
              className="flex items-start gap-3 rounded-lg border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-800"
            >
              <CheckCircle2 className="mt-0.5 h-5 w-5 shrink-0" />
              <p>{success}</p>
            </div>
          )}

          {activeTab === 'home' && (
            <StudentDashboard
              complaints={complaints}
              activeCount={activeCount}
              isLoading={isLoading}
              isRefreshing={isRefreshing}
              onRefresh={handleRefresh}
              onFileComplaint={handleFileComplaint}
              onOpenComplaints={handleOpenComplaints}
            />
          )}

          {activeTab === 'submit' && (
            <ComplaintForm
              formData={form.formData}
              setFormData={form.setFormData}
              updateForm={form.updateForm}
              handleSubmit={form.handleSubmit}
              draftRestored={form.draftRestored}
              hasRestoredDraft={form.hasRestoredDraft}
              onDismissDraft={form.dismissDraft}
              missingProfileRoom={form.missingProfileRoom}
              openProfileFromComplaint={openProfileFromComplaint}
              profileRoom={form.profileRoom}
              profileRoomFloor={form.profileRoomFloor}
              visibleDuplicates={form.visibleDuplicates}
              duplicateMode={form.duplicateMode}
              upvotingId={form.upvotingId}
              handleUpvoteDuplicate={form.handleUpvoteDuplicate}
              onDismissDuplicates={form.dismissDuplicates}
              imageUploadRef={imageUploadRef}
              isSubmitting={form.isSubmitting}
            />
          )}

          {activeTab === 'complaints' && (
            <ComplaintList
              complaints={complaints}
              filteredComplaints={filteredComplaints}
              isLoading={isLoading}
              isRefreshing={isRefreshing}
              search={search}
              setSearch={setSearch}
              statusFilter={statusFilter}
              setStatusFilter={setStatusFilter}
              onRefresh={handleRefresh}
              onFileComplaint={handleFileComplaint}
              onViewComplaint={handleViewComplaint}
              onDeleteComplaint={handleDeleteComplaint}
              onNudgeComplaint={handleNudge}
            />
          )}

          {activeTab === 'notifications' && (
            <section className="mx-auto max-w-3xl space-y-5">
              <div>
                <h3 className="text-2xl font-bold text-slate-900">
                  Push Notifications
                </h3>
                <p className="mt-1 text-sm text-slate-500">
                  Get notified when the status of your complaints changes.
                </p>
              </div>

              <div className="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
                <div className="flex items-start gap-4">
                  <div className="rounded-full bg-indigo-50 p-3 text-indigo-600">
                    <Bell className="h-6 w-6" />
                  </div>
                  <div className="min-w-0 flex-1">
                    <h4 className="font-semibold text-slate-900">
                      Browser notifications
                    </h4>
                    <p className="mt-1 text-sm leading-6 text-slate-600">
                      Enable browser notifications to receive updates when your
                      complaint moves to a new status, even when you are not
                      viewing the portal.
                    </p>
                    {canRetryNotifications ? (
                      <button
                        type="button"
                        onClick={notifications.handleEnableNotifications}
                        disabled={
                          notifications.isEnablingNotifications || !profile?.id
                        }
                        className="mt-5 inline-flex items-center justify-center rounded-lg bg-blue-600 px-5 py-2.5 text-sm font-semibold text-white transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-50"
                      >
                        {notifications.isEnablingNotifications
                          ? 'Enabling...'
                          : 'Try Again'}
                      </button>
                    ) : (
                      ['default', 'granted'].includes(
                        notifications.notificationPermission
                      ) && (
                        <div className="mt-5 flex items-center gap-3">
                          <button
                            type="button"
                            role="switch"
                            aria-checked={notifications.isNotificationEnabled}
                            aria-label="Push notifications"
                            onClick={
                              notifications.isNotificationEnabled
                                ? notifications.handleDisableNotifications
                                : notifications.handleEnableNotifications
                            }
                            disabled={
                              notifications.isEnablingNotifications ||
                              notifications.isDisablingNotifications ||
                              !profile?.id
                            }
                            className={`relative h-6 w-11 shrink-0 rounded-full border-0 p-0 transition-colors disabled:cursor-not-allowed disabled:opacity-50 ${
                              notifications.isNotificationEnabled
                                ? 'bg-blue-600'
                                : 'bg-slate-300'
                            }`}
                          >
                            <span
                              className={`absolute left-0.5 top-0.5 h-5 w-5 rounded-full bg-white shadow transition-transform ${
                                notifications.isNotificationEnabled
                                  ? 'translate-x-5'
                                  : ''
                              }`}
                            />
                          </button>

                          <span className="text-sm font-medium text-slate-700">
                            {notifications.isDisablingNotifications
                              ? 'Turning off...'
                              : notifications.isNotificationEnabled
                                ? 'Notifications on'
                                : 'Enable Notifications'}
                          </span>
                        </div>
                      )
                    )}

                    {notifications.notificationPermission === 'granted' &&
                      notifications.isEnablingNotifications &&
                      !notifications.notificationError && (
                        <p role="status" className="mt-3 text-sm text-slate-600">
                          Setting up notifications...
                        </p>
                      )}

                    {notifications.notificationPermission === 'denied' && (
                      <p role="alert" className="mt-3 text-sm leading-6 text-amber-700">
                        Notifications are blocked for this site. To turn them
                        on, click the icon beside the address bar, open the site
                        settings, and set Notifications to Allow. This page
                        updates when you return to it.
                      </p>
                    )}

                    {notifications.notificationPermission === 'unsupported' && (
                      <p role="status" className="mt-3 text-sm text-slate-600">
                        This browser does not support notifications.
                      </p>
                    )}

                    {notifications.notificationMessage && (
                      <p role="status" className="mt-3 text-sm text-green-700">
                        {notifications.notificationMessage}
                      </p>
                    )}

                    {notifications.notificationError && (
                      <p role="alert" className="mt-3 text-sm text-red-600">
                        {notifications.notificationError}
                      </p>
                    )}

                    {canRetryNotifications && (
                        <p className="mt-1 text-sm text-slate-600">
                          If Try Again doesn&apos;t work, reload the page.
                        </p>
                      )}
                  </div>
                </div>
              </div>
            </section>
          )}

          {/* Always mounted, so the feed loads with the page and is ready when the
              tab opens; each visit then updates the counts without a spinner. */}
          <div hidden={activeTab !== 'community'}>
            <CommunityDashboard refreshSignal={statusUpdateCount + communityVisits} />
          </div>
        </div>
      </main>

      {notificationToast && (
        <NotificationToast
          toast={notificationToast}
          onOpen={handleOpenNotificationToast}
          onDismiss={dismissNotificationToast}
        />
      )}

      {viewedComplaint && (
        <StudentComplaintDetails
          complaint={viewedComplaint}
          onClose={closeComplaintDetails}
          onSaveDescription={handleSaveDescription}
        />
      )}

      {complaintToDelete && (
        <DeleteComplaintDialog
          complaint={complaintToDelete}
          isDeleting={isDeleting}
          error={deleteError}
          onCancel={closeDeleteDialog}
          onConfirm={handleConfirmDelete}
        />
      )}

      {submittedComplaint && (
        <SubmissionConfirmation
          complaintId={submittedComplaint.id}
          photoError={submittedComplaint.photoError}
          onViewComplaints={() => {
            setSubmittedComplaint(null);
            setActiveTab('complaints');
          }}
          onFileAnother={closeConfirmation}
        />
      )}
    </div>
  );
}
