import { useEffect, useId, useImperativeHandle, useRef, useState } from 'react';
import { AlertCircle, ImagePlus, RefreshCw, X } from 'lucide-react';

const ACCEPTED_TYPES = ['image/jpeg', 'image/png', 'image/webp'];
// Matches spring.servlet.multipart.max-file-size on the backend
const MAX_FILE_BYTES = 5 * 1024 * 1024;

function validateFile(file) {
  if (!ACCEPTED_TYPES.includes(file.type)) {
    return 'Only JPEG, PNG, or WebP images are allowed.';
  }
  if (file.size > MAX_FILE_BYTES) {
    return `This image is ${(file.size / 1024 / 1024).toFixed(1)} MB. The maximum size is 5 MB.`;
  }
  return '';
}

function uploadErrorMessage(status) {
  switch (status) {
    case 401:
      return 'Your session expired before the photo could be uploaded. Sign in again to retry.';
    case 404:
      return 'The complaint for this photo could not be found.';
    case 413:
      return 'The image is too large. The maximum size is 5 MB.';
    case 415:
      return 'The file is not a valid JPEG, PNG, or WebP image.';
    case 502:
    case 503:
      return 'Photo storage is unavailable right now. Please try again later.';
    default:
      return `The photo could not be uploaded (HTTP ${status}).`;
  }
}

/**
 * Image picker with preview. The parent triggers the upload through the ref:
 *   const result = await ref.current.upload(url, token); // { ok, error?, data? }
 * Also exposes hasFile() and reset().
 */
export default function ImageUpload({ ref, disabled = false, label = 'Photo (optional)' }) {
  const [selectedFile, setSelectedFile] = useState(null);
  const [previewUrl, setPreviewUrl] = useState('');
  const [isUploading, setIsUploading] = useState(false);
  const [uploadError, setUploadError] = useState('');
  const [isDragging, setIsDragging] = useState(false);
  const inputRef = useRef(null);
  const previewUrlRef = useRef('');
  const inputId = useId();

  // Object URLs hold the file in memory until revoked
  const replacePreview = (file) => {
    if (previewUrlRef.current) URL.revokeObjectURL(previewUrlRef.current);
    previewUrlRef.current = file ? URL.createObjectURL(file) : '';
    setPreviewUrl(previewUrlRef.current);
  };

  useEffect(() => () => {
    if (previewUrlRef.current) URL.revokeObjectURL(previewUrlRef.current);
  }, []);

  const clearFile = () => {
    replacePreview(null);
    setSelectedFile(null);
    setUploadError('');
    if (inputRef.current) inputRef.current.value = '';
  };

  const selectFile = (file) => {
    if (!file) return;
    const validationError = validateFile(file);
    if (validationError) {
      replacePreview(null);
      setSelectedFile(null);
      setUploadError(validationError);
      if (inputRef.current) inputRef.current.value = '';
      return;
    }
    setUploadError('');
    replacePreview(file);
    setSelectedFile(file);
  };

  useImperativeHandle(ref, () => ({
    hasFile: () => Boolean(selectedFile),
    reset: clearFile,
    upload: async (url, token) => {
      if (!selectedFile) return { ok: true };

      setIsUploading(true);
      setUploadError('');
      try {
        const body = new FormData();
        body.append('file', selectedFile);
        // No Content-Type header: the browser sets the multipart boundary
        const response = await fetch(url, {
          method: 'POST',
          headers: { Authorization: `Bearer ${token}` },
          body,
        });
        if (!response.ok) {
          const message = uploadErrorMessage(response.status);
          setUploadError(message);
          return { ok: false, error: message };
        }
        return { ok: true, data: await response.json() };
      } catch {
        const message = 'Network error while uploading the photo.';
        setUploadError(message);
        return { ok: false, error: message };
      } finally {
        setIsUploading(false);
      }
    },
  }), [selectedFile]);

  const isDisabled = disabled || isUploading;

  return (
    <div>
      <label htmlFor={inputId} className="mb-1.5 block text-sm font-medium text-slate-700">{label}</label>

      {selectedFile && previewUrl ? (
        <div className="flex items-center gap-4 rounded-lg border border-slate-300 p-3">
          <img
            src={previewUrl}
            alt="Selected complaint photo preview"
            className="h-24 w-24 shrink-0 rounded-md object-cover"
          />
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm font-medium text-slate-800">{selectedFile.name}</p>
            <p className="mt-0.5 text-xs text-slate-500">
              {(selectedFile.size / 1024).toFixed(0)} KB
              {isUploading && ' · Uploading…'}
            </p>
          </div>
          {isUploading ? (
            <RefreshCw className="h-5 w-5 shrink-0 animate-spin text-blue-600" aria-label="Uploading" />
          ) : (
            <button
              type="button"
              onClick={clearFile}
              disabled={isDisabled}
              aria-label="Remove selected photo"
              className="rounded-lg p-2 text-slate-500 transition hover:bg-slate-100 hover:text-rose-700 disabled:opacity-50"
            >
              <X className="h-4 w-4" />
            </button>
          )}
        </div>
      ) : (
        <label
          htmlFor={inputId}
          onDragOver={(event) => {
            event.preventDefault();
            if (!isDisabled) setIsDragging(true);
          }}
          onDragLeave={() => setIsDragging(false)}
          onDrop={(event) => {
            event.preventDefault();
            setIsDragging(false);
            if (!isDisabled) selectFile(event.dataTransfer.files?.[0]);
          }}
          className={`flex cursor-pointer flex-col items-center justify-center gap-2 rounded-lg border-2 border-dashed px-4 py-6 text-center transition ${
            isDragging ? 'border-blue-500 bg-blue-50' : 'border-slate-300 hover:border-blue-400 hover:bg-slate-50'
          } ${isDisabled ? 'pointer-events-none opacity-60' : ''}`}
        >
          <ImagePlus className="h-7 w-7 text-slate-400" />
          <span className="text-sm font-medium text-slate-700">
            <span className="text-blue-600">Choose a photo</span> or drag it here
          </span>
          <span className="text-xs text-slate-500">JPEG, PNG, or WebP · up to 5 MB</span>
        </label>
      )}

      <input
        ref={inputRef}
        id={inputId}
        type="file"
        accept="image/jpeg, image/png, image/webp"
        disabled={isDisabled}
        onChange={(event) => selectFile(event.target.files?.[0])}
        className="sr-only"
      />

      {uploadError && (
        <p role="alert" className="mt-1.5 flex items-start gap-1.5 text-sm text-rose-700">
          <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" />
          {uploadError}
        </p>
      )}
    </div>
  );
}
