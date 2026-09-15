# Messaging Implementation Notes

This document explains the main modules added or changed in the current implementation, what each class/method does, and how the register/media/company/admin flows work.

## Backend Auth Module

Package: `com.messaging.auth`

### Controllers

`AuthController`
- `POST /api/auth/register`
  - Creates a user.
  - If `profilePictureMediaId` and `profilePictureUploadToken` are present, completes the pre-register profile-picture media and attaches it to the new user.
  - Creates auth cookies/session.
  - Publishes OTP email request through Kafka.
- `POST /api/auth/login`
  - Validates email/password.
  - Creates auth cookies/session.
- `POST /api/auth/refresh`
  - Reads refresh cookie.
  - Validates active session.
  - Issues new cookies.
- `POST /api/auth/verify-email`
  - Verifies the OTP for the current logged-in user.
- `POST /api/auth/logout`
  - Revokes the refresh session and clears cookies.

### DTOs

`RegisterRequest`
- Fields: `email`, `name`, `profilePictureMediaId`, `profilePictureUploadToken`, `username`, `password`.
- The profile-picture fields are optional and only used when the register page already uploaded a pending media file.

`LoginRequest`
- Fields: `email`, `password`.

`VerifyEmailRequest`
- Field: six-digit `otp`.

`AuthUserResponse`
- Response user shape: `userId`, `username`, `email`, `verified`.

### Services

`AuthService`
- `register(...)`
  - Creates user through `UserService`.
  - Calls `attachPreRegisterProfilePicture(...)` if upload token exists.
  - Issues auth cookies/session.
  - Requests email OTP.
- `attachPreRegisterProfilePicture(...)`
  - Validates media id/token through `MediaService`.
  - Completes pending media.
  - Attaches media to the newly created user.
- `login(...)`
  - Validates password and creates session.
- `refresh(...)`
  - Refreshes active session using refresh cookie.
- `verifyEmail(...)`
  - Verifies OTP and marks user verified.
- `logout(...)`
  - Revokes refresh-token session and clears cookies.

`AuthOtpService`
- Creates and verifies OTPs.
- Stores OTP hash in Redis.
- Publishes OTP email event to Kafka critical topic.

`OtpRequestedEventHandler`
- Handles Kafka OTP events.
- Sends email through notification service.

## Backend Media Module

Package: `com.messaging.media`

### Controllers

`MediaController`
- `POST /api/v1/media/uploads`
  - Single API for getting a signed upload URL.
  - Request tells backend what is being uploaded with `purpose`.
  - Backend creates a pending media row and returns signed upload details.
  - If caller is authenticated, temp path is user-scoped.
  - If caller is anonymous, only `USER_PROFILE` is allowed and response includes `uploadToken`.
- `POST /api/v1/media/{mediaId}/complete`
  - For authenticated media uploads after file has been uploaded to storage.
  - Validates object metadata.
  - Moves file from temp key to final key.
  - Marks media `ACTIVE`.
- `GET /api/v1/media/{mediaId}/access`
  - Creates signed download URL for active owned media.
- `DELETE /api/v1/media/{mediaId}`
  - Marks media deleted and attempts physical storage delete.

`UserMediaController`
- `PUT /api/v1/users/me/profile-picture/{mediaId}`
  - Attaches an already active owned `USER_PROFILE` media to current user.
  - Useful for updating profile picture after registration.

### DTOs

`InitiateMediaUploadRequest`
- Fields: `fileName`, `contentType`, `sizeBytes`, `purpose`.

`InitiateMediaUploadResponse`
- Fields: `mediaId`, `uploadToken`, `uploadUrl`, `expiresAt`, `requiredHeaders`.
- `uploadToken` is `null` for authenticated uploads.
- `uploadToken` is present for anonymous register profile-picture upload.

`MediaResponse`
- Full media details including ids, object key, content type, purpose, status, timestamps.

`MediaAccessResponse`
- Signed download URL and expiry.

`ProfilePictureResponse`
- Fields: `userId`, `mediaId`.

### Services

`MediaService`
- `initiateUpload(...)`
  - Validates file name/type/size.
  - Creates pending media.
  - Uses temp object key.
  - Returns signed upload URL.
  - Anonymous uploads are allowed only for `USER_PROFILE` and get an `uploadToken`.
- `createPendingMedia(...)`
  - Creates authenticated pending media row.
  - Temp key format: `tmp/uploads/{userId}/{mediaId}.{ext}`.
- `createPreRegisterPendingMedia(...)`
  - Creates anonymous pending profile-picture media row.
  - Owner id is temporary sentinel `0`.
  - Temp key format: `tmp/register/{mediaId}.{ext}`.
- `completeUpload(...)`
  - Authenticated complete endpoint.
  - Loads owned pending media.
  - Calls common completion logic.
- `completePreRegisterProfilePictureUpload(...)`
  - Used by register flow.
  - Validates `mediaId + uploadToken` from Redis.
  - Completes pending anonymous media for the newly created user id.
  - Deletes token after successful completion.
- `completePendingMedia(...)`
  - Validates uploaded storage object exists.
  - Validates uploaded object content type and size.
  - Moves object to final key.
  - Marks media active.
- `createAccessUrl(...)`
  - Creates signed download URL for active owned media.
- `delete(...)`
  - Marks media deleted and attempts storage delete.
- `loadOwnedMedia(...)`
  - Loads media and checks ownership.
- `getActiveOwnedMedia(...)`
  - Loads owned media and requires `ACTIVE`.
- `markFailed(...)`
  - Marks pending media failed.
- `markDeleted(...)`
  - Marks media deleted.

`MediaObjectKeyFactory`
- `createTemporaryObjectKey(...)`
  - Builds temp key for authenticated uploads.
- `createPreRegisterObjectKey(...)`
  - Builds temp key for anonymous register profile-picture uploads.
- `createUserObjectKey(...)`
  - Builds final user-scoped object key by media purpose.

`MediaValidationService`
- Validates file name, mime type, size, and uploaded object metadata.
- Uses env-driven limits from `app.media.validation`.

`MediaAuthorizationService`
- Checks media ownership.

`MediaCleanupService`
- Cleans expired pending uploads in batches.
- Deletes temp storage object.
- Marks media `DELETED`.

`MediaCleanupScheduler`
- Runs cleanup after an initial delay, then repeatedly by fixed delay.
- Defaults:
  - `MEDIA_PENDING_CLEANUP_INITIAL_DELAY=1m`
  - `MEDIA_PENDING_CLEANUP_INTERVAL=12h`

### Storage Provider

`StorageProvider`
- `createSignedUpload(...)`
- `getObjectMetadata(...)`
- `move(...)`
- `createSignedDownloadUrl(...)`
- `delete(...)`

`SupabaseStorageProvider`
- Implements storage operations using Supabase Storage REST API.
- Signs upload URLs.
- Looks up metadata.
- Moves temp object to final object key on complete.
- Deletes objects.

## Backend User Module

Package: `com.messaging.user`

`UserService`
- `createUser(...)`
  - Normalizes email/username.
  - Checks uniqueness.
  - Saves user with encoded password.
- `getUser(...)`
  - Loads user by id.
- `getByEmail(...)`
  - Loads user by normalized email.
- `verifyEmail(...)`
  - Marks user verified.
- `attachCurrentUserProfilePicture(...)`
  - Used by profile update flow.
  - Requires active owned media with purpose `USER_PROFILE`.
- `attachUserProfilePicture(...)`
  - Used by register flow.
  - Attaches completed media to a specific user.

`CreateUserRequest`
- Fields: `email`, `name`, `username`, `password`, `twoFactorEnabled`.

## Backend Security Module

Package: `com.messaging.security`

`CurrentUserService`
- `currentUserId()`
  - Returns current authenticated user id or throws unauthorized.
- `currentUserIdOrEmpty()`
  - Returns `Optional<Long>`.
  - Used by media upload initiation so the same endpoint can support logged-in and anonymous register uploads.

`SecurityConfig`
- Permits:
  - `/api/auth/register`
  - `/api/auth/login`
  - `/api/auth/refresh`
  - `/api/v1/media/uploads`
- Requires authentication for everything else.

`JwtAuthenticationFilter`
- Reads access token cookie.
- Parses JWT.
- Sets Spring Security principal as user id.

## Backend Company Module

Package: `com.messaging.company`

### Purpose

Company is the workspace/business tenant module. It supports:
- companies/workspaces
- company members
- owner/manager/member roles
- role permissions
- per-member permission overrides
- temporary access profiles

### Controllers

`CompanyController`
- `POST /api/v1/companies`
  - Creates company.
  - Current user becomes `OWNER`.
- `GET /api/v1/companies/me`
  - Lists companies where current user is active member.

`CompanyAccessController`
- `GET /api/v1/companies/{companyId}/permissions/me`
  - Returns current user effective permissions.
- `POST /api/v1/companies/{companyId}/members/{userId}/permissions`
  - Adds member-specific allow/deny permission override.
- `POST /api/v1/companies/{companyId}/access-profiles`
  - Creates reusable permission bundle.
- `GET /api/v1/companies/{companyId}/access-profiles`
  - Lists access profiles.
- `POST /api/v1/companies/{companyId}/members/{userId}/access-profiles/{profileId}`
  - Grants access profile to member, optionally temporary.

### Services

`CompanyService`
- `create(...)`
  - Creates company slug.
  - Saves company.
  - Adds creator as active owner.
- `myCompanies()`
  - Lists current user companies.
- `getCompany(...)`
  - Loads company or throws not found.

`CompanyRoleDefaults`
- Defines default permission sets for:
  - `OWNER`
  - `MANAGER`
  - `MEMBER`

`CompanyPermissionService`
- `effectivePermissions(...)`
  - Calculates final permission set.
  - Order:
    1. active membership required
    2. owner gets all permissions
    3. start from default role permissions
    4. apply company role overrides
    5. add active access profile permissions
    6. apply member overrides, with deny removing permission last
- `hasPermission(...)`
  - Boolean helper around effective permissions.

`CompanyAuthorizationService`
- `requirePermission(...)`
  - Throws forbidden if user lacks a permission.

`CompanyAccessService`
- `myPermissions(...)`
  - Returns effective permissions for current user.
- `grantPermissionOverride(...)`
  - Requires `MEMBER_ROLE_UPDATE`.
  - Grants allow/deny override to active company member.
- `createProfile(...)`
  - Requires `SETTINGS_MANAGE`.
  - Creates access profile and its permissions.
- `grantAccessProfile(...)`
  - Requires `MEMBER_ROLE_UPDATE`.
  - Grants profile to active company member.
- `profiles(...)`
  - Requires `SETTINGS_VIEW`.
  - Lists profiles and permissions.

### Key Entities

`Company`
- Workspace record.

`CompanyMember`
- Joins user to company with role/status.

`CompanyRolePermission`
- Company-specific role allow/deny override.

`CompanyMemberPermissionOverride`
- User-specific allow/deny override with optional expiry.

`CompanyAccessProfile`
- Named bundle of permissions.

`CompanyAccessProfilePermission`
- Permissions inside an access profile.

`CompanyMemberAccessProfileGrant`
- Grants access profile to member, optionally temporary.

## Backend Admin Module

Package: `com.messaging.admin`

### Purpose

Platform admin module for internal/admin users.

Roles:
- `SUPER_ADMIN`
  - Can see all companies.
  - Can assign admins to companies.
- `ADMIN`
  - Can see assigned companies only.

### Controllers

`AdminCompanyController`
- `GET /api/v1/admin/companies`
  - Super admin: all companies.
  - Admin: assigned companies.
- `POST /api/v1/admin/company-assignments`
  - Super admin assigns an admin user to a company.

### Services

`PlatformAdminAuthorizationService`
- `isSuperAdmin(...)`
- `canAccessCompany(...)`
- `requireSuperAdmin(...)`
- `requireCompanyAccess(...)`

`AdminCompanyService`
- `visibleCompanies()`
  - Returns visible company list based on platform role.
- `assignCompany(...)`
  - Creates/updates admin company assignment.

### Entities

`PlatformAdmin`
- Platform admin user record.

`AdminCompanyAssignment`
- Which companies an admin can access.

## Frontend API Layer

Package: `frontend/src/lib/api`

`apiClient`
- Axios instance with:
  - `withCredentials: true`
  - response interceptor for `401`
  - refresh token retry
- Supports `_skipAuthRefresh`.
  - Used for register-page media upload because the user is anonymous.

`apiErrorMessage(...)`
- Extracts backend `error_message`.

## Frontend Auth Module

Package: `frontend/src/features/auth`

`authApi`
- `register(payload)`
  - Calls `/api/auth/register`.
- `login(payload)`
  - Calls `/api/auth/login`.
- `refresh()`
  - Calls `/api/auth/refresh`.
- `verifyEmail(otp)`
  - Calls `/api/auth/verify-email`.
- `logout()`
  - Calls `/api/auth/logout`.

`RegisterPayload`
- Fields:
  - `email`
  - `name`
  - `username`
  - `password`
  - optional `profilePictureMediaId`
  - optional `profilePictureUploadToken`

## Frontend Media Module

Package: `frontend/src/features/media`

`mediaApi`
- `initiateProfilePictureUpload(file)`
  - Calls `/api/v1/media/uploads`.
  - Sends file metadata and `purpose: USER_PROFILE`.
  - Uses `_skipAuthRefresh` so anonymous register uploads do not trigger refresh flow.
- `uploadFileToStorage(upload, file, onProgress)`
  - Uploads directly to returned signed URL.
  - Reports progress.

`InitiateMediaUploadResponse`
- Fields:
  - `mediaId`
  - optional `uploadToken`
  - `uploadUrl`
  - `expiresAt`
  - `requiredHeaders`

## Frontend Register Page

File: `frontend/src/pages/auth/Register.tsx`

Main behavior:
- Validates confirm password.
- Shows profile image picker with preview.
- On file change:
  1. calls `mediaApi.initiateProfilePictureUpload(file)`
  2. uploads file to signed URL
  3. stores `mediaId + uploadToken`
  4. shows progress
- On register submit:
  1. waits for upload promise if still in progress
  2. sends register payload with `profilePictureMediaId + profilePictureUploadToken`
  3. backend creates user and completes/moves media
  4. redirects to OTP page

## Register With Profile Picture Flow

### Step 1: User Selects Image

Frontend calls:

```http
POST /api/v1/media/uploads
```

Payload:

```json
{
  "fileName": "avatar.png",
  "contentType": "image/png",
  "sizeBytes": 12345,
  "purpose": "USER_PROFILE"
}
```

Backend:
- validates metadata
- creates `media` row with `PENDING`
- because request is anonymous, allows only `USER_PROFILE`
- creates temp object key:
  - `tmp/register/{mediaId}.png`
- creates `uploadToken` in Redis
- returns signed upload URL

### Step 2: Browser Uploads Directly To Storage

Frontend calls:

```http
PUT {uploadUrl}
```

Backend is not involved in the bytes upload.

### Step 3: User Clicks Register

Frontend calls:

```http
POST /api/auth/register
```

Payload includes:

```json
{
  "name": "Renuka",
  "email": "renuka@example.com",
  "username": "renuka",
  "password": "password123",
  "profilePictureMediaId": "123",
  "profilePictureUploadToken": "token"
}
```

Backend:
- creates user
- validates `mediaId + uploadToken`
- checks object exists in storage
- validates uploaded object metadata
- moves from:
  - `tmp/register/{mediaId}.png`
- to:
  - `users/{userId}/profile/{mediaId}.png`
- marks media `ACTIVE`
- attaches media to user profile
- creates session cookies
- sends OTP email through Kafka flow

### Step 4: User Verifies Email

Frontend calls:

```http
POST /api/auth/verify-email
```

Payload:

```json
{
  "otp": "123456"
}
```

Backend verifies OTP and marks user verified.

## Normal Authenticated Media Upload Flow

### Step 1: Get Signed URL

Frontend calls:

```http
POST /api/v1/media/uploads
```

Backend:
- requires auth except anonymous `USER_PROFILE` register case.
- creates temp key:
  - `tmp/uploads/{userId}/{mediaId}.{ext}`
- returns signed URL.

### Step 2: Upload To Storage

Frontend uploads directly:

```http
PUT {uploadUrl}
```

### Step 3: Complete Upload

Frontend calls:

```http
POST /api/v1/media/{mediaId}/complete
```

Backend:
- verifies ownership
- validates uploaded object
- moves object to final purpose key
- marks media `ACTIVE`

## Pending Upload Cleanup Flow

Scheduler:
- `MediaCleanupScheduler`
- Delay config:
  - `MEDIA_PENDING_CLEANUP_INITIAL_DELAY=1m`
  - `MEDIA_PENDING_CLEANUP_INTERVAL=12h`

Cleanup:
- finds expired `PENDING` media
- deletes temp storage object
- marks media `DELETED`

This prevents abandoned register uploads from consuming storage permanently.

## Verification Commands Used

Backend:

```bash
.\mvnw.cmd -q test
```

Frontend:

```bash
npm run lint
npm run build
```
