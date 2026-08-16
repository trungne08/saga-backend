# Self Profile V1 API guide

This document supersedes the prior baseline statement that Self Profile update did not
exist. Source/test and DEC-103 are the authority if this guide conflicts with them.

## Browser contract

`PATCH /api/auth/me` is available only to the currently authenticated `STUDENT` or
`LECTURER`. Use the existing browser `JSESSIONID` with `credentials: "include"` and
the existing CSRF cookie/header (`XSRF-TOKEN` / `X-XSRF-TOKEN`). Do not send a Bearer
token, actor ID, student ID, lecturer ID, or target profile ID.

```json
{
  "fullName": "Edited display name",
  "avatarUrl": "https://cdn.example.test/avatar.png"
}
```

The request is sparse: omitted fields are preserved. `fullName`, when present, is
trimmed and must not be blank. `avatarUrl` may be explicitly `null` to clear the local
avatar; otherwise it must be an absolute HTTP(S) URL with host, without user-info, and
at most 2048 characters. Backend only validates the string; it never fetches the URL,
uploads a file, contacts Google, calls Cognito, or handles provider tokens.

Only `fullName` and `avatarUrl` are editable. `cognitoSub`, `email`, `studentCode`,
`applicationRole`, `accountStatus`, `localProfileId`, Team role, and Course membership
are read-only. Unknown/forbidden JSON fields are rejected by the existing strict JSON
binding policy.

The success response and `GET /api/auth/me` use the canonical `AuthMeResponse`:

```ts
type AuthMeResponse = {
  cognitoSub: string;
  email: string;
  fullName: string;
  applicationRole: "ADMIN" | "LECTURER" | "STUDENT";
  localProfileId: string;
  accountStatus: "ACTIVE" | "INACTIVE" | "SUSPENDED" | "PENDING" | null;
  avatarUrl: string | null;
  studentCode: string | null; // canonical value for STUDENT; null otherwise
};
```

`INACTIVE` and `SUSPENDED` sessions (and the existing blocked `PENDING` Student
status) remain gated by DEC-101 before the controller: session is invalidated and the
response is `401 ACCOUNT_DISABLED`. `GET /api/auth/me` has the same disabled behavior;
it is not an exemption. `GET /api/auth/csrf` and `POST /api/auth/logout` keep their
existing CSRF/logout exceptions only.

## Authority at login

OIDC/Cognito remains authoritative for Cognito subject, email, application role, and
the Student identity/studentCode contract. A newly created local profile initializes
`fullName` and a valid OIDC `picture` as `avatarUrl`. For an existing profile, later
OIDC logins synchronize identity only and never overwrite the locally managed
`fullName` or `avatarUrl`.

No migration is required: Student and Lecturer already contain `full_name` and nullable
`avatar_url` columns.
