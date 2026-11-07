import { Bell } from 'lucide-react';

/** Lets staff and admins hear about new assignments on this browser. */
export default function StaffNotificationSwitch({ notifications }) {
  const {
    notificationPermission,
    isEnablingNotifications,
    isDisablingNotifications,
    isNotificationEnabled,
    notificationError,
    handleEnableNotifications,
    handleDisableNotifications,
  } = notifications;

  if (notificationPermission === 'unsupported') return null;

  const isBusy = isEnablingNotifications || isDisablingNotifications;

  return (
    <section className="flex items-start gap-3 rounded-xl border border-slate-200 bg-white p-4">
      <span className="rounded-full bg-indigo-50 p-2 text-indigo-600">
        <Bell className="h-4 w-4" aria-hidden="true" />
      </span>
      <div className="min-w-0 flex-1">
        <p className="m-0 text-sm font-semibold text-slate-900">Assignment notifications</p>
        <p className="m-0 mt-0.5 text-sm text-slate-600">
          Get a notification on this browser when a complaint is assigned to you.
        </p>
        {notificationPermission === 'denied' && (
          <p role="alert" className="m-0 mt-2 text-sm text-amber-700">
            Notifications are blocked for this site. Allow them in your browser's site settings.
          </p>
        )}
        {notificationError && <p role="alert" className="m-0 mt-2 text-sm text-rose-700">{notificationError}</p>}
      </div>
      {notificationPermission !== 'denied' && (
        <button
          type="button"
          role="switch"
          aria-checked={isNotificationEnabled}
          aria-label="Assignment notifications"
          onClick={isNotificationEnabled ? handleDisableNotifications : handleEnableNotifications}
          disabled={isBusy}
          className={`relative h-6 w-11 shrink-0 rounded-full border-0 p-0 transition-colors disabled:opacity-60 ${
            isNotificationEnabled ? 'bg-indigo-600' : 'bg-slate-300'
          }`}
        >
          <span
            className={`absolute left-0.5 top-0.5 h-5 w-5 rounded-full bg-white shadow transition-transform ${
              isNotificationEnabled ? 'translate-x-5' : ''
            }`}
          />
        </button>
      )}
    </section>
  );
}
