import { RefreshCw } from 'lucide-react';

import DuplicateWarning from '../../../components/DuplicateWarning';
import ImageUpload from '../../../components/ImageUpload';
import { HOSTEL_FLOORS } from '../../../utils/hostelLocation';

export default function ComplaintForm({
    formData,
    setFormData,
    updateForm,
    handleSubmit,
    draftRestored,
    hasRestoredDraft,
    onDismissDraft,
    missingProfileRoom,
    openProfileFromComplaint,
    profileRoom,
    profileRoomFloor,
    visibleDuplicates,
    duplicateMode,
    upvotingId,
    handleUpvoteDuplicate,
    onDismissDuplicates,
    imageUploadRef,
    isSubmitting,
}) {
    return (
        <section className="mx-auto max-w-3xl rounded-xl border border-slate-200 bg-white p-5 shadow-sm md:p-8">
            <div className="mb-6">
                <h3 className="text-2xl font-bold text-slate-900">
                    File a new complaint
                </h3>
                <p className="mt-1 text-sm text-slate-500">
                    Include the location and details so the issue can be routed correctly.
                </p>
            </div>

            {draftRestored && hasRestoredDraft && (
                <div
                    role="status"
                    className="mb-5 flex flex-wrap items-center justify-between gap-3 rounded-lg border border-blue-200 bg-blue-50 p-3 text-sm text-blue-900"
                >
                    <p>
                        We restored your unfinished complaint. If you had attached a photo,
                        please attach it again.
                    </p>
                    <button
                        type="button"
                        onClick={onDismissDraft}
                        className="font-medium text-blue-700 hover:underline"
                    >
                        Dismiss
                    </button>
                </div>
            )}

            <form onSubmit={handleSubmit} className="space-y-5">
                <div>
                    <label
                        htmlFor="complaint-title"
                        className="mb-1.5 block text-sm font-medium text-slate-700"
                    >
                        Issue title
                    </label>
                    <input
                        id="complaint-title"
                        required
                        maxLength={150}
                        value={formData.title}
                        onChange={updateForm('title')}
                        placeholder="e.g. Ceiling light not working"
                        className="w-full rounded-lg border border-slate-300 px-4 py-2.5 text-sm outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-200"
                    />
                </div>

                <div>
                    <label
                        htmlFor="complaint-category"
                        className="mb-1.5 block text-sm font-medium text-slate-700"
                    >
                        Category
                    </label>
                    <select
                        id="complaint-category"
                        value={formData.category}
                        onChange={updateForm('category')}
                        className="w-full rounded-lg border border-slate-300 bg-white px-4 py-2.5 text-sm outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-200"
                    >
                        <option>Electrical</option>
                        <option>Plumbing</option>
                        <option>Carpentry</option>
                        <option>Cleaning</option>
                        <option>IT</option>
                    </select>
                </div>

                <fieldset>
                    <legend className="mb-1.5 block text-sm font-medium text-slate-700">
                        Where is the issue?
                    </legend>

                    <div className="grid gap-3 sm:grid-cols-2">
                        {[
                            {
                                value: false,
                                title: 'My room',
                                hint: 'Uses the room number from your profile',
                            },
                            {
                                value: true,
                                title: 'Community area',
                                hint: 'Corridor, washroom, common room…',
                            },
                        ].map((option) => (
                            <label
                                key={option.title}
                                className={`flex cursor-pointer items-start gap-3 rounded-lg border p-3 text-sm transition ${formData.isCommunityArea === option.value
                                    ? 'border-blue-500 bg-blue-50 ring-2 ring-blue-200'
                                    : 'border-slate-300 hover:border-blue-300'
                                    }`}
                            >
                                <input
                                    type="radio"
                                    name="complaint-location-type"
                                    checked={formData.isCommunityArea === option.value}
                                    onChange={() =>
                                        setFormData((current) => ({
                                            ...current,
                                            isCommunityArea: option.value,
                                        }))
                                    }
                                    className="mt-0.5 accent-blue-600"
                                />
                                <span>
                                    <span className="block font-medium text-slate-800">
                                        {option.title}
                                    </span>
                                    <span className="text-slate-500">{option.hint}</span>
                                </span>
                            </label>
                        ))}
                    </div>
                </fieldset>

                {formData.isCommunityArea ? (
                    <div className="grid gap-5 sm:grid-cols-2">
                        <div>
                            <label
                                htmlFor="complaint-floor"
                                className="mb-1.5 block text-sm font-medium text-slate-700"
                            >
                                Floor
                            </label>
                            <select
                                id="complaint-floor"
                                value={formData.floor}
                                onChange={updateForm('floor')}
                                className="w-full rounded-lg border border-slate-300 bg-white px-4 py-2.5 text-sm outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-200"
                            >
                                {HOSTEL_FLOORS.map(({ name: floor }) => (
                                    <option key={floor}>{floor}</option>
                                ))}
                            </select>
                        </div>

                        <div>
                            <label
                                htmlFor="complaint-wing"
                                className="mb-1.5 block text-sm font-medium text-slate-700"
                            >
                                Wing / area
                            </label>
                            <input
                                id="complaint-wing"
                                required
                                maxLength={100}
                                value={formData.wing}
                                onChange={updateForm('wing')}
                                placeholder="e.g. East wing washroom"
                                className="w-full rounded-lg border border-slate-300 px-4 py-2.5 text-sm outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-200"
                            />
                        </div>
                    </div>
                ) : missingProfileRoom ? (
                    <div
                        role="alert"
                        className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900"
                    >
                        <p>
                            Add your room number to your profile to file a complaint about
                            your room.
                        </p>
                        <button
                            type="button"
                            onClick={openProfileFromComplaint}
                            className="rounded-lg bg-amber-600 px-3 py-1.5 font-semibold text-white hover:bg-amber-700"
                        >
                            Add room number
                        </button>
                    </div>
                ) : (
                    <p className="rounded-lg border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-600">
                        {profileRoomFloor ? (
                            <>
                                Room <strong className="text-slate-900">{profileRoom}</strong>{' '}
                                ({profileRoomFloor} floor), from your profile.
                            </>
                        ) : (
                            'Loading your room from your profile…'
                        )}{' '}
                        <button
                            type="button"
                            onClick={openProfileFromComplaint}
                            className="font-medium text-blue-700 hover:underline"
                        >
                            Change
                        </button>
                    </p>
                )}

                {visibleDuplicates.length > 0 && (
                    <DuplicateWarning
                        scope={duplicateMode}
                        candidates={visibleDuplicates}
                        upvotingId={upvotingId}
                        onUpvote={handleUpvoteDuplicate}
                        onDismiss={onDismissDuplicates}
                    />
                )}

                <div>
                    <label
                        htmlFor="complaint-description"
                        className="mb-1.5 block text-sm font-medium text-slate-700"
                    >
                        Description
                    </label>
                    <textarea
                        id="complaint-description"
                        required
                        maxLength={1000}
                        rows={5}
                        value={formData.description}
                        onChange={updateForm('description')}
                        placeholder="Describe what happened and when you first noticed it."
                        className="w-full resize-y rounded-lg border border-slate-300 px-4 py-2.5 text-sm outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-200"
                    />
                </div>

                <div>
                    <label
                        htmlFor="complaint-available-time"
                        className="mb-1.5 block text-sm font-medium text-slate-700"
                    >
                        Available time{' '}
                        <span className="font-normal text-slate-500">(optional)</span>
                    </label>
                    <input
                        id="complaint-available-time"
                        maxLength={500}
                        value={formData.availableTimeSlot}
                        onChange={updateForm('availableTimeSlot')}
                        placeholder="e.g. Mon: 2 PM - 5 PM, Tue: 12 PM - 5 PM"
                        className="w-full rounded-lg border border-slate-300 px-4 py-2.5 text-sm outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-200"
                    />
                </div>

                {!formData.isCommunityArea && (
                    <label className="flex cursor-pointer items-start gap-3 rounded-lg border border-slate-200 p-3 text-sm text-slate-700">
                        <input
                            type="checkbox"
                            checked={formData.keyWithSecurity}
                            onChange={(event) =>
                                setFormData((current) => ({
                                    ...current,
                                    keyWithSecurity: event.target.checked,
                                }))
                            }
                            className="mt-0.5 h-4 w-4 accent-blue-600"
                        />
                        I am not available in my room during working hours and will leave my
                        key with security
                    </label>
                )}

                <ImageUpload ref={imageUploadRef} disabled={isSubmitting} />

                <button
                    type="submit"
                    disabled={isSubmitting || missingProfileRoom}
                    className="inline-flex w-full items-center justify-center gap-2 rounded-xl bg-blue-600 px-5 py-3 font-semibold text-white shadow-sm transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:bg-blue-400"
                >
                    {isSubmitting && <RefreshCw className="h-4 w-4 animate-spin" />}
                    {isSubmitting ? 'Submitting…' : 'Submit complaint'}
                </button>
            </form>
        </section>
    );
}