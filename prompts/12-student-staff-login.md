# Student Login and Staff Login

| | |
|---|---|
| **Goal** | Separate sign-in for students, who must use smail, and hostel staff, who use Gmail accounts an admin has added. |
| **Date** | 2026-11-05 |
| **Pull requests** | #32 |
| **Commits** | `43683c8` |

## Context

One login served everyone, and every request decided the role.

## Prompt

```text
Context: We are updating the HostelFix Google OAuth login flow to handle university students and hostel staff differently. Students use the smail.iitm.ac.in domain, while staff use standard Gmail accounts.

Task 1: Backend Auth Logic
Update AuthController.java and AuthService.java (wherever Google JWTs are verified).
Constraints:

The token verification endpoint must accept the Google JWT and a new loginType parameter (STUDENT or ADMIN).

Student Flow: Validate the token and enforce that the extracted email ends with @smail.iitm.ac.in. If it does, auto-create the user if they don't exist, assigning the STUDENT role. If the domain is wrong, throw a 401 Unauthorized.

Admin Flow: Validate the token, but DO NOT auto-create the user. Query the users table. The login must only succeed if the email already exists in the database AND has a staff/admin role (ADMIN, IN_CHARGE). Otherwise, throw a 403 Forbidden exception ("Unauthorized staff email").

Task 2: React Frontend Buttons
Update the Login.jsx UI.
Constraints:

Create two distinct login toggle tabs or sections: "Student Login" and "Staff Login". (They can have same UI but different api endpoints i guess like /student/login and /admin/login, just change the words to student login and staff login if its already present in the UI and that students should use only their smail accounts).
For the Student Google login instance, pass the hd: 'smail.iitm.ac.in' configuration parameter to the Google Identity Service so the browser enforces the university domain.
Ensure both buttons send the correct loginType flag to the backend upon a successful Google popup response.
```

## Constraints

- Students only with `@smail.iitm.ac.in`
- Staff and admins must already be approved
- Pass `hd` to Google for the student tab

## Refinements

- **There was no login endpoint,** so a refused account could only ever get a 401. A public `POST /api/auth/login` was added, while every other request is still checked.
- **"Do not auto-create staff" would have locked out new admins,** because only an admin can add staff. Admins listed in `ADMIN_EMAILS` are created on their first Staff Login.
- **`IN_CHARGE` did not exist,** so `STAFF` was used.
- **We explained the real situation before approving:** students use smail only, the admins stay the same for years, and staff change and use Gmail.
- **Gmail ignores dots,** so an admin typing a different form of a staff email would see a refusal. This was documented rather than coded around.

## Outcome

Fifteen HTTP tests cover each student and staff case, including forged and unverified tokens. Staff Login was then confirmed in the app with a real Gmail staff account.
