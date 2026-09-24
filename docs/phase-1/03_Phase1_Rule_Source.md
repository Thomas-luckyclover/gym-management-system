# Phụ lục — Nguyên văn rule dùng trong Phase 1

Trích nguyên văn (tiếng Anh) từ `GMS_Rules_and_Parameters` v24, để dán/đối chiếu khi viết SRS/SDS. Bản tóm tắt tiếng Việt + cách code: `01_Phase1_Rules.md`.

## GBR

| ID | Rule | Description | Applies to |
|---|---|---|---|
| GBR-01 | Language & locale | The interface is Vietnamese. Dates are dd/mm/yyyy, times are 24-hour HH:mm, and all times use the gym’s time zone (UTC+7). Enumerated values are stored in English and displayed in Vietnamese. | All screens |
| GBR-07 | Audit of sensitive actions | Every sensitive action — price change, account action, deletion, approval or rejection — is written to the audit log. | All modules |
| GBR-08 | Readable audit sentences | Each audit line reads as a plain sentence: who – did what – to what – from which value to which value – when – why. Only the Admin can read the log. | Audit log |
| GBR-10 | Role-based access | Each action can be performed only by the roles allowed in the role & permission matrix (Guest, Customer, PT, Receptionist, Manager, Admin); users see and change only the data their role allows. | All screens |
| GBR-12 | One profile per person | Everyone who uses a service — members, dependants and walk-ins — has exactly one active customer profile. | All customer-facing features |
| GBR-13 | Server-side enforcement | Every rule checked on a screen is checked again on the server; the server never trusts values sent by the interface. Competing actions on the same record are resolved by database constraints — the later request is refused. | All modules |
| GBR-15 | Communication channels | Verification codes and password links are sent by email only; there is no SMS. In-app notifications reach users who have an account; dependants are informed outside the system. | Notifications, sign-up, recovery |
| GBR-17 | Required fields | Required fields are marked *. Validation runs when leaving a field and again on submit; each error appears in red under its field. | All forms |
| GBR-19 | Search | Keywords are trimmed, case-insensitive and diacritic-insensitive (“nguyen” matches “Nguyễn”). | All lists |
| GBR-21 | Empty states | An empty list shows a short message: nothing yet, no result for the search, or no data in the chosen period. | All lists and reports |
| GBR-22 | Saving | The submit button is disabled while a request is processed; success shows a toast; on error the form keeps the user’s input. | All forms |
| GBR-24 | Unsaved changes | Leaving a form with unsaved changes asks the user to confirm. | All forms |
| GBR-25 | Read-only fields | Fields the current user may not edit are greyed out with a hint saying who can change them. | All forms |
| GBR-27 | Countdowns | Time-limited states (payment deadline, slot hold, verification code, temporary lock) show an mm:ss countdown and refresh when it reaches zero. | Payment, booking, sign-in |
| GBR-30 | Password fields | Password fields are masked with a show/hide toggle. | Sign-in, sign-up, account |
| GBR-31 | Session expiry | When the sign-in session expires, the user signs in again and returns to the same screen. | All signed-in screens |
| GBR-34 | Toasts & notifications | Toasts disappear after 3–5 seconds, error toasts stay until closed; user notifications keep a read / unread state. | All screens |

## CBR

| ID | Business Rule | Use cases |
|---|---|---|
| CBR-17 | A customer profile and a login account are separate things: a profile may exist without an account. | UC-04, UC-05, UC-16 |
| CBR-18 | A login account is optional. Dependent profiles and profiles that only buy day passes have no account. | UC-05, UC-16, UC-Claim |
| CBR-19 | If the Receptionist enters a phone number that matches an active profile, the system opens the existing profile instead of creating a new one. | UC-16, UC-IDENT |
| CBR-20 | If a guest signs up with the phone number of an active self-owned profile that has no account and enters the same email as the profile, the system sends a verification code to that email instead of creating a new profile; after the correct code and a password, the account is attached to the existing profile. | UC-05, UC-Claim |
| CBR-21 | A Dependent profile has no login account. | UC-04, UC-05 |
| CBR-23 | If a phone number only matches Inactive profiles, a new profile is created normally. | UC-16, UC-IDENT |
| CBR-24 | When the Receptionist creates a profile for a customer buying a package of one month or longer, the Receptionist also creates the login account with the profile's email; the customer receives an email to set the password. | UC-16, UC-20 |
| CBR-25 | A guest signing up with the phone number of a self-owned profile that has no account, but with a different email from the one on the profile, cannot activate it. The system says the phone number is registered with another email (masked); the guest uses that email or asks the Receptionist to verify and update it. | UC-05, UC-Claim |
| CBR-26 | If a guest signs up with the phone number of a profile that already has an account, no new account is created; the system says the number already has an account and points to Sign In / Forgot Password. | UC-03, UC-05 |
| CBR-27 | A self-owned profile without an email cannot be activated online; the customer goes to the counter so the Receptionist can add the email and create the account (CBR-24). | UC-05, UC-16, UC-Claim |
| CBR-28 | Duplicate checks during self sign-up only compare against the phone number on Person; guardian numbers recorded on Dependent profiles neither block nor activate anything — the guardian registers as a new customer. | UC-05, UC-IDENT |
| CBR-29 | When the email of a profile that has an account changes (by the Receptionist or the customer), the system notifies the old email and signs out every session of the account. | UC-04, UC-16 |
| CBR-30 | In their own account a customer can change only the avatar, email, phone number and password. Full name, date of birth, check-in photo and guardian details are changed by the Receptionist (UC-16), because they are used for check-in verification and the PT age rule. | UC-04, UC-16 |
| CBR-31 | An account created by the Receptionist (CBR-24) cannot sign in until the customer sets a password through the emailed link; the link expires per P-15, after which the customer uses Forgot Password. | UC-01, UC-03, UC-16 |
| CBR-37 | The avatar uploaded by the customer is for display only and is never used for check-in verification. | UC-04, UC-16, UC-19 |
| CBR-38 | The check-in photo belongs to the customer profile, not to the account. Only the Receptionist can take or retake it; the customer cannot change or delete it, even with an account. | UC-04, UC-16, UC-19 |
| CBR-91 | A PT can edit only the private notes and workout/meal plans they created; everyone can edit only their own account profile. | UC-04, UC-24, UC-32, UC-33, UC-34, UC-40 |
| CBR-93 | After a successful password change, every previous session is signed out. | UC-03, UC-04 |
| CBR-94 | A verification or recovery code allows 5 wrong entries (P-17). On the 5th wrong entry the code is cancelled and verification for that email is locked for 5 minutes (P-18) — no entry and no new code during the lock. Requesting a new code is allowed 60 seconds after the previous one; the 5th request within an hour triggers the same 5-minute lock. A new code always cancels the previous one. | UC-03, UC-04, UC-05, UC-Claim |
| CBR-95 | Staff accounts (Admin, Manager, PT, Receptionist) are created only by the Admin. Customer accounts come from exactly two sources: online self sign-up (UC-05) or creation by the Receptionist at the counter (UC-16). The Admin never creates customer accounts. | UC-05, UC-07, UC-16 |
| CBR-96 | An account is locked only in three cases: the employee has left (Inactive), the Admin locks it, or a customer account is temporarily locked under BR-01. | UC-01, UC-07 |
| CBR-100 | A blog post switched to Inactive disappears from the public site immediately. | UC-06, UC-45 |

## BR

| ID | Business Rule |
|---|---|
| BR-01 | A customer account entering a wrong password 5 times in a row is locked for 5 minutes (P-17, P-18). Staff accounts are never locked automatically — counter machines are shared, and a wrong lock would stop a whole shift from taking payments or checking customers in. |
| BR-02 | When the temporary lock (BR-01) ends, the customer account unlocks and the failure counter resets to 0. A successful sign-in also resets the counter. |
| BR-03 | Password recovery verifies only through the registered email of that account. |
| BR-04 | A password recovery code expires after 15 minutes (P-15). |
| BR-05 | A customer changing their own email or phone number must verify with a code sent to the new email; the new value still goes through the duplicate checks (DBR-22, DBR-23). |

## DBR

| ID | Data | Rule | Use cases / applies to |
|---|---|---|---|
| DBR-01 | Full name | String, 2–50 characters; Unicode letters including Vietnamese diacritics, spaces, apostrophe and hyphen; no digits or other symbols; trimmed on save. | Customer, Employee |
| DBR-02 | Password | Masked, 8–64 characters, at least one letter and one digit, different from the current password; stored hashed and never displayed or logged. (Length proposed by the team, to be confirmed.) | Account |
| DBR-03 | Verification code | Exactly 6 digits, single use. | Account |
| DBR-04 | Date of birth | Not in the future; age ≤ 100. | Customer, Employee |
| DBR-08 | Images | JPG or PNG, max 5 MB, max 2000 × 2000 px. | Customer avatar and check-in photo, blog cover |
| DBR-09 | System codes | Customer code KH-nnnn, employee code NV-nnnn, order code DH-yyyy-nnnn: generated by the system, unique, and never changed during the life of the record. | Customer, Employee, Order |
| DBR-17 | Phone numbers | A phone number must follow the Vietnamese format: 10 digits, starting with 0, with a valid carrier prefix. | UC-01, UC-03, UC-04, UC-05, UC-07, UC-13, UC-16 |
| DBR-18 | Email | An email must follow the format name@domain. | UC-01, UC-03, UC-04, UC-05, UC-07, UC-13, UC-16 |
| DBR-22 | Person.Phone | A phone number is stored on Person and must not duplicate any other active person, customer or staff alike. A self-owned customer profile must have one; it is what the Receptionist uses to look for duplicates. | UC-05, UC-16, UC-IDENT |
| DBR-23 | Person.Email | An email is stored on Person and must not duplicate another active person. It is mandatory for anyone who has a login account and optional otherwise. | UC-05, UC-16, UC-20, UC-IDENT |
| DBR-24 | Customer (dependent) — guardian fields | A Dependent customer profile has no phone of its own on Person; it is reached through the guardian phone numbers recorded on the profile, which several dependants may share — siblings may use the same guardian number — as long as the guardian's full name and relationship are recorded. | UC-16 |
| DBR-25 | Customer (dependent) — guardian fields | A Dependent profile records the guardian's full name, relationship and up to two guardian phone numbers, so a child can be reached through either parent. | UC-16 |
| DBR-26 | Customer.Email (dependent) | A Dependent profile does not need an email. | UC-16 |
| DBR-28 | Phone / email of inactive records | When a profile or account becomes Inactive, its phone number and email are released immediately; the person can get a new profile without waiting. | UC-05, UC-07, UC-16 |
| DBR-29 | Person.Email | The Person of every login account must have an email, customer and staff alike, because password recovery and verification codes are sent by email. | UC-05, UC-07, UC-16 |
| DBR-34 | Customer.Status | A customer profile has exactly five states: New, Active, Frozen, Expired, Inactive. | UC-16, UC-19, UC-41 |

## Tham số

| ID | Parameter | Used by | Default | Unit | Changed by |
|---|---|---|---|---|---|
| P-05 | Minimum age for PT services | CBR-13, CBR-14, CBR-15 | 10 | years | Manager (4.1.2) |
| P-15 | Validity of verification / recovery codes and password-setup links | BR-04, CBR-31 | 15 | minutes | Manager (4.1.2) |
| P-16 | Valid mobile phone prefixes | DBR-17 | Current carrier list | list | Deployment configuration |
| P-17 | Wrong attempts before a temporary lock (sign-in and verification codes) | BR-01, CBR-94 | 5 | times | Manager (4.1.2) |
| P-18 | Temporary lock duration (sign-in and verification codes) | BR-01, CBR-94 | 5 | minutes | Manager (4.1.2) |
| P-19 | Default opening and closing time | BR-12 | Set at go-live | time | Manager (4.1.3) |

Cố định, không cấu hình (SRS System Parameters): link thanh toán online 5 phút; khoảng cách tối thiểu 60 giây giữa 2 lần xin mã.
