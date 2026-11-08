# Complaint photo upload

| | |
|---|---|
| **Goal** | Let students attach a photo to a complaint, stored in Supabase Storage, and show it to admins. |
| **Date** | 2026-10-20 |
| **Pull requests** | #17 |
| **Commits** | `4dbfba2` |

## Context

Complaints had text only. Two prompts: the backend endpoint, then the React upload component.

## Prompt

### Prompt 1

```text
Context: We are adding Image Upload functionality to our HostelFix Spring Boot application. We are using Supabase Storage with a public bucket named complaint-images. The complaints database table has already been updated with an image_url column.

Task 1: Entity Update
Update Complaint.java to include a new String imageUrl field.

Task 2: Storage Service
Create SupabaseStorageService.java in the service package.
Constraints:

Write public String uploadImage(MultipartFile file).

Security Check: At the top of the method, verify file.getContentType() is exactly image/jpeg, image/png, or image/webp. If not, throw a ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE).

Read supabase.url and supabase.key from application.properties. (use placeholders and then configure it in the .env file)

Generate a unique file name using UUID.randomUUID() combined with the original file extension.

Upload the file to the Supabase REST API endpoint (/storage/v1/object/complaint-images/) using RestTemplate or WebClient.

Return the constructed public URL of the uploaded image.

Task 3: Controller Update
Update ComplaintController.java to add a new endpoint: POST /api/complaints/{id}/image.
Constraints:

Accept @PathVariable UUID id and @RequestParam("file") MultipartFile file.

Fetch the complaint, call the storage service, update imageUrl, and save the complaint.

Ensure it runs smoothly and returns the updated Complaint
```

### Prompt 2

```text
Context: We are adding Image Upload functionality to our React (Vite) frontend for the HostelFix project. The backend endpoint POST /api/complaints/{id}/image is ready and accepts a multipart/form-data request with a file parameter.
Task 1: Context Gathering
Read the existing React component for the complaint submission form (likely in src/components/ or src/pages/) to understand the current state management and styling conventions. Note that we are using Tailwind CSS.
Task 2: Build the Upload Component
Create a new reusable React component ImageUpload.jsx.
Constraints:
Use an <input type="file" accept="image/jpeg, image/png, image/webp" />.
Add state to hold the selected file (const [selectedFile, setSelectedFile] = useState(null)).
Add state for loading (isUploading) and error messages (uploadError).
Display a preview of the selected image before upload if possible (using URL.createObjectURL()).
Style it cleanly using Tailwind CSS (e.g., a dashed border drop zone or a styled button).
Task 3: Integration and API Call
Integrate ImageUpload.jsx into the main complaint submission form.
Constraints:
The upload should happen after the initial complaint is created (since the backend needs the complaintId).
Once the complaint is successfully created via the existing POST /api/complaints call, trigger a second fetch call to POST /api/complaints/{complaintId}/image using the FormData object containing the selected file.
Handle potential errors gracefully (e.g., if the file is too large or the wrong format, display an error message without breaking the whole form).
```

## Constraints

- Supabase Storage, public bucket `complaint-images`
- JPEG, PNG or WebP only, up to 5 MB
- The storage key must never reach the frontend

## Refinements

- **Real file checks.** The service checks the file's signature bytes as well as its declared type, so a renamed file is rejected.
- **The photo uploads after the complaint is created.** The endpoint needs the new complaint's id. If the upload fails, the complaint is kept and the student is warned, rather than losing it.
- **Which Supabase key.** We asked which key to use; it is the service role key, kept in the backend's `.env` only.
- **Admin view added.** We asked whether admins could see the photo; the admin complaint popup was updated to show it.
- **Build failure.** The backend failed to start locally because the port was already in use, which was diagnosed and fixed.

## Outcome

Backend and frontend tests passed, and we checked uploading and viewing photos as both a student and an admin before committing.
