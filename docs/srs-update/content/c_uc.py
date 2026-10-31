import json
O = json.load(open('docs/srs-update/build/data/uc_old.json'))
OLDUC = {r[0]: r[3] for r in O['uc']}
def old(u): return OLDUC[u]
NI = ""
NF = ""
UC_COLS = ["ID", "Use Case", "Feature", "Use Case Description"]
UC_OVR = {
"UC-Claim":["UC-Claim","Claim An Existing Profile Via Verification Code","Guest","Activate online a Self customer profile that exists without a login account, so no duplicate customer record is created. The guest enters the profile's phone number and the email recorded on it; a 6-digit code is sent to that email and, after the correct code and a new password, the account is attached to the profile (CBR-20). If the email does not match, the guest is told the number is registered with another email (masked) and is asked to use it or update it at the counter (CBR-25); if the profile has no email, the claim must be completed at the counter (CBR-27); if the phone number already has an account — including every account created at the counter, which receives a temporary password by email (CBR-31) — the guest is sent to Sign In / Forgot Password (CBR-26)."],
"UC-05":["UC-05","Register Account","Guest",old('UC-05').rstrip('. ')+"."],
"UC-IDENT":["UC-IDENT","Check Phone/Email Duplication & Identify Profile","Guest (included in UC-05)",old('UC-IDENT')],
"UC-01":["UC-01","Login","System User","Authenticate with an email address or phone number plus a password; either identifies exactly one account. If the account serves more than one role that is not Inactive, the user chooses the working role on Select Role; the role can be switched later without signing in again. Customer-only accounts are locked temporarily after P-17 wrong passwords (BR-01). Customers whose account was created at the counter sign in with the temporary password received by email (CBR-31); the Sign In screen explains this in a hint box."],
"UC-03":["UC-03","Recover Password","System User, Email Gateway","Reset a forgotten password in three steps — enter the registered email, enter the 6-digit code (valid P-15 minutes), set a new password before the code deadline. The reset clears a temporary lock and signs out every session (CBR-93). Also used by customers who lost their counter temporary password."],
"UC-04":["UC-04","Manage Personal Account","System User","One Account Settings screen for all five roles. Change avatar (upload or remove), email and phone number (each with a 6-digit verification code — CBR-110) and password (current password required, new password different, other sessions signed out). Personnel data is read-only (CBR-108); customers also see a read-only Dependent profiles tab (CBR-111)."],
"UC-06":["UC-06","Explore Services & Pricing","Guest","Browse the home page (packages on sale with benefits, PTs, opening hours, 3 latest posts), the About page and the public blog (6 posts per page, category filter) before signing up."],
"UC-07":["UC-07","Manage System Accounts","Admin",old('UC-07')+NI],
"UC-08":["UC-08","View Audit Log","Admin",old('UC-08')+NI],
"UC-09":["UC-09","Configure System Parameters","Manager","Set system parameters P-01…P-23 — financial (VAT, default PT commission %) and operational (freeze limit and duration, minimum shift staffing, leave submission cut-off, code validity, lock policy, rating threshold, annual leave quota). A change applies only to records created afterwards (GBR-04)."+NI],
"UC-10":["UC-10","Manage Service Package Catalog","Manager","Create, edit, stop selling and sell again the packages the gym sells — membership packages and day passes (#17, #18) and PT packages (#19, #20) — with name, price, description, benefits, duration, estimated sessions (PT) and the Exercise Library benefit (BR-107). Stopping a package never affects packages already sold (CBR-97)."],
"UC-11":["UC-11","Configure Operating Hours","Manager","Define the default daily opening hours (P-19) and exceptions for specific dates (other hours or closed all day). Every open day is split into a morning and an afternoon shift at 13:00 (GBR-38) — there is no manual rostering. Opening hours are used to validate PT slots, bookings and check-ins; PT package sessions on a closed day must be rescheduled by the PT (CBR-56)."],
"UC-12":["UC-12","Manage Vouchers","Manager",old('UC-12')+NI],
"UC-13":["UC-13","Manage Personnel Records","Manager",old('UC-13')+NI],
"UC-14":["UC-14","Submit Leave Request","Staff","A PT or Receptionist requests Annual or Unpaid leave for one or more days (≤ 31, same year) and a shift, before the P-12 cut-off on the previous day. A PT cannot submit while still having a Scheduled session in that shift (CBR-83). If annual leave is not enough, the request is split into Annual + Unpaid after the employee agrees (CBR-121). A Pending request can be withdrawn (BR-83)."],
"UC-15":["UC-15","Process Staff Leave Requests","Manager","Review the queue (default Pending, leave-soon first), open a request or act on the row, approve one or several selected requests (1–50) or reject with a reason. Approval is re-checked against annual quota, minimum staffing (BR-14) and the PT's scheduled sessions. There is no approval deadline; JOB-07 reminds the Manager the day before, and a request whose leave has started can be approved late with a confirmation and reason (BR-86)."],
"UC-16":["UC-16","Manage Customer Profiles","Receptionist",old('UC-16')+" A Self profile always gets a login account with a temporary password emailed to the customer (CBR-24, CBR-31)."+NF],
"UC-17":["UC-17","Process Customer Deletion Requests","Manager",old('UC-17')+NF],
"UC-18":["UC-18","Freeze A Membership Package","Receptionist","Pause an active membership package (never a PT package or an expired package) for a fee of P-03 (default free), within the freeze count and length limits (P-01, P-02), starting tomorrow at the earliest. A freeze is never blocked by PT sessions: before saving, the system lists the Scheduled sessions inside the freeze period; they stay Scheduled with a warning and JOB-04 closes them after each session day (CBR-62). The package resumes automatically at the end of the freeze (JOB-01); the Receptionist may also end it early, and the end date moves out only by the days actually frozen (DBR-51). A Receptionist cannot freeze their own package (CBR-115)."],
"UC-19":["UC-19","Check A Customer In At The Gym","Receptionist","Look up a customer by customer code, phone number or name; the system picks the entitlement that allows entry today (membership or day pass, CBR-117). Verify the face against the check-in photo (take it if missing — BR-33) and record the check-in. A customer with only a PT package or no valid entitlement is refused; a day pass is used up by one entry."],
"UC-20":["UC-20","Create Order & Collect Payment At Counter","Receptionist","Sell a membership package, day pass or single PT session at the counter while a shift is open: choose the package, find or quick-create the customer (a Self profile gets an account with a temporary password — CBR-118, CBR-31), optionally apply a voucher, choose the start date (CBR-119) and collect cash or a confirmed bank transfer. Price, VAT and discount are snapshotted. The Receptionist also sells a single PT session for today at the counter (CBR-54, CBR-60). A wrong paid order can be cancelled only within the same shift."],
"UC-21":["UC-21","Sell A PT Package","Receptionist","Sell a single or couple PT package in four steps: package & PT → weekly schedule → customer(s) → hold & collect payment. The customer (both members) must hold a membership valid today, not be frozen and be at least P-05 years old. The schedule is a Monday–Friday template with per-date overrides on the PT's slots (CBR-43, BR-40, BR-41); times the PT has not opened need \"Confirmed with PT\" (BR-96). Holding reserves the slots for 15 minutes (BR-93); payment by cash or transfer creates the subscription and all sessions (CBR-50, BR-97). No voucher."],
"UC-22":["UC-22","Purchase A Package Via Self-Service","Customer, VNPay","Browse packages on sale (filters, sort, 8 per page) and buy a membership package or day pass online through VNPay; PT packages are shown with \"buy at the counter\". A failed or expired payment can be retried until the order deadline (P-09); a new order for the same package replaces the older Pending one. After payment the customer may move the start date of an unused membership once, within 7 days (CBR-119)."],
"UC-24":["UC-24","Look Up Personal Transaction History","Customer","View one's own orders and payment attempts (20 per page, status filter) with order detail."],
"UC-25":["UC-25","Open Shift, Reconcile Transactions & Close Shift","Receptionist","Open the current shift (shared by every Receptionist on duty — one cash drawer), view the transactions recorded in it with who received each one, confirm transfers, cancel a wrong order if needed, then count the cash in the drawer and close the shift; a difference from the expected cash requires a reason. The shift cannot be closed while a counter order from it is still Pending. Only one shift is open at a time; a shift left open is closed by the next Receptionist at hand-over, and an overdue open shift is flagged on every Receptionist screen (CBR-104, CBR-105, CBR-123, BR-46)."],
"UC-26":["UC-26","Look Up System-Wide Transaction History","Manager","View all orders and transactions with search, payment method, order status, refund-only and date filters (10 / 20 / 50 per page), export the filtered list to Excel, and confirm refunds paid outside the system for transactions on the refund list (BR-81)."],
"UC-27":["UC-27","Manage Facility/Equipment Catalog","Manager","Maintain facility groups (name, category, location, unit label, image, visible / hidden) and their individual equipment items (unique code, label, placement note, status) in one master–detail screen; retire an item instead of deleting it. Quantities are computed from items (BR-104). The Receptionist views the same catalogue read-only and reports faults from it."],
"UC-28":["UC-28","Create Facility Maintenance Request","Receptionist","Report one or more broken equipment items of a facility group in two steps (BR-105); each item gets its own request and the Managers are notified. Close an In Progress request after checking the repair, with an acceptance note (BR-48)."],
"UC-29":["UC-29","Process Facility Maintenance Requests","Manager","Review maintenance requests (status tabs, 10 / 20 / 50 per page), approve with an optional note and the \"set under maintenance\" choice, or reject with a reason; the reporting Receptionist is notified (BR-106)."],
"UC-30":["UC-30","Declare Available Time Slots","PT","On the weekly PT schedule (week or list view), add draft slots of P-06 minutes on a 15-minute grid within opening hours, withdraw free slots, copy a week to the next week, then publish all changes in one step (BR-94, BR-95). Slots in a shift covered by the PT's leave request are locked. Slots may be published for any future date and any day of the week."],
"UC-31":["UC-31","Mark Session No-Show","PT","Withdrawn — the PT does not mark attendance; sessions are closed automatically by the background job JOB-04 (CBR-42), which is not a use case."],
"UC-32":["UC-32","Track Teaching Schedule & Assigned Clients","PT","View the PT dashboard (7-day schedule, needs-attention list for 14 days, assigned packages with phone and Zalo, monthly result and commission, weather — BR-101) and the weekly PT schedule with every session and its status (BR-100)."],
"UC-33":["UC-33","Write Private Notes On Clients","PT",old('UC-33')+NI],
"UC-34":["UC-34","Draft Training Plans & Meal Plans","PT",old('UC-34')+NI],
"UC-35":["UC-35","Handle A Session The PT Cannot Teach","PT","From a session on the PT schedule (or a dashboard warning), after agreeing with the customer, the PT in charge reschedules it to one of their own free slots within 14 days and the package term (BR-64), transfers it to another PT free at the same time (BR-65, commission recomputed), or cancels it with a reason and the cash-refund confirmation (BR-66). Only a Scheduled session that has not started can be handled; the customer is notified (CBR-46)."],
"UC-36":["UC-36","Track Personal Training Schedule","Customer","View, read-only, My Bookings — PT packages and single sessions with the next session, progress, weekly template, session strip and one call to action (BR-98) — and My PT Sessions — week / month timetable with each session's status and reason, PT contact via Zalo and an add-to-calendar link (BR-99). Customers do not cancel or move sessions."],
"UC-37":["UC-37","Book A Single Session With A PT","Customer",old('UC-37')+NI],
"UC-38":["UC-38","Reschedule A Session In A PT Package","Customer","Withdrawn — customers do not swap sessions; they contact the PT, who reschedules through UC-35."],
"UC-39":["UC-39","Cancel A Booked Session","Customer","Withdrawn — customers do not cancel sessions; an absent session becomes Missed through JOB-04."],
"UC-40":["UC-40","View Training Plan & Meal Plan From PT","Customer",old('UC-40')+NI],
"UC-41":["UC-41","Track Membership & Package Status","Customer","View the customer dashboard: whether the customer can use the gym today, packages in use or not started with remaining PT sessions, the next 5 PT sessions, warning banners, attendance streak and latest notifications (BR-102); move the start date of an unused online membership once (CBR-119)."],
"UC-42":["UC-42","Submit Feedback","Customer",old('UC-42').rstrip('. ')+". A feedback may rate one PT (1–5 stars) once per month after a CheckedIn or Missed session with that PT (CBR-116, DBR-66, DBR-67)."],
"UC-43":["UC-43","View Customer Feedback","Manager",old('UC-43')+NI],
"UC-44":["UC-44","Send Notifications","Manager",old('UC-44')+NI],
"UC-45":["UC-45","Manage Blog Posts","Manager",old('UC-45')+NI],
"UC-46":["UC-46","View Aggregated Dashboard","Manager","View the operational and revenue report for any From–To range (daily or monthly chart — CBR-122) and export it to CSV."],
"UC-47":["UC-47","View Operational Reports & Audit Metrics","Admin","View the same operational report plus audit metrics visible only to the Admin (CBR-103)."],
}
UC_NEW = [
["UC-48","Browse Exercise Library","Customer, PT, wger API","Search and filter exercises from the wger database (muscle group, equipment, name, sort; 12 per page) with image, muscles and description. Customers need a package that unlocks the library (BR-90); PTs always have access. Data are cached daily (BR-91, BR-92)."],
["UC-49","Track Body Measurements","Customer","Record a measurement per day (weight, height, optional body fat, muscle, waist, note), see BMI and its group, five summary tiles, a chart of one metric over time and the history; delete a measurement (BR-87 – BR-89)."],
["UC-50","View In-App Notifications","System User","Open the bell to read the 10 latest notifications (system reminders, leave and maintenance decisions, session changes, payment results) and mark one or all as read (BR-103)."],
]
def build_uc():
    out=[]
    for r in O['uc']:
        if r[0] in UC_OVR: out.append(UC_OVR[r[0]])
        else: out.append(r[:4])
    return out+UC_NEW
UC = build_uc()

ACT_COLS = ["#", "Actor", "Description"]
ACT = [r[:3] for r in O['actors'][1:]]
A = {r[1].split(' (')[0].strip(): r for r in ACT}
A["Customer"][2] = "A person who has an account with the gym. Buys membership packages and day passes online, tracks their own PT schedule (view only), packages, membership status, body measurements and transaction history, browses the Exercise Library, reads notifications, views the training and meal plans published by the PT and submits feedback."
A["PT"][2] = "Publishes and withdraws slots for any future date on the weekly PT schedule, reschedules, transfers or cancels a session they cannot teach, tracks their teaching schedule, assigned clients and commission, browses the Exercise Library and submits leave requests; private notes and training plans are planned. The PT does not mark attendance — JOB-04 closes sessions. Inherits from Staff."
A["Receptionist"][2] = "Front-desk operator. Sells membership packages, day passes and PT packages at the counter and creates customer profiles with login accounts (temporary password by email), collects payment, checks customers in, views the facility catalogue, reports broken equipment and closes repaired requests, freezes memberships, submits leave requests and reconciles cash at shift close."
A["Manager"][2] = A["Manager"][2] + " Also confirms refunds of the refund list and approves leave requests (bulk or late approval)."
A["Payment Gateway"][2] = "External system. Receives online payment requests for self-service membership and day-pass purchases (UC-22) and single PT session bookings (UC-37), and returns the payment result through the IPN. Not used for PT packages sold at the counter (UC-21)."
A["Email / OTP Service"][2] = "External system (SMTP). Delivers 6-digit verification codes (registration, claim, recovery, email or phone change), temporary passwords of accounts created at the counter and the security notice to the old email. Not used for sign-in — login is email/phone plus password only, no OTP."
ACT.append(["11","Exercise Data Provider (wger)","External system. Public wger.de API providing exercises, muscles, equipment and images for the Exercise Library (API-05); read-only, no key."])

SCR_COLS = ["#", "Screen Name", "Feature", "Description"]
SCR_OVR = {
"4":"One screen for all roles: profile (personnel data read-only for staff), avatar upload / remove, email and phone change with code, password change; customers also see a read-only Dependent profiles tab",
"5":"Presents packages on sale with benefits, trainers, opening hours and the 3 latest posts to guests",
"6":"Lists published posts with category filter, 6 per page",
"17":"Lists membership packages and day passes with status, stop selling / sell again",
"18":"Adds or edits a membership package or day pass: name, duration (1 day or 1/3/6/12/24/36 months), VAT-inclusive price, description, benefits, Exercise Library benefit",
"19":"Lists single and couple PT packages and single-session packages",
"20":"Adds or edits a PT package: type, duration (1/3/6/12 months), estimated sessions, price, description, benefits (single-session packages: edit only)",
"25":"Lets a PT or Receptionist submit a leave request (range, shift, Annual / Unpaid with split), see the forecast in a hint box and withdraw Pending requests (/pt/leave-requests, /receptionist/leave-requests)",
"26":"Manager queue: Pending / Approved / Rejected tabs per request and All grouped by employee, leave-soon first, filters, row actions and bulk approval",
"27":"Detail of one request with the result of each approval check; Approve (late approval needs confirmation and reason) or Reject with reason",
"33":"Looks a customer up by code, phone or name, verifies photo and entitlement, takes a missing check-in photo, confirms entry",
"35":"Lets a customer browse, filter (type, purchase channel, duration, price) and sort packages, 8 per page, and buy a membership or day pass",
"37":"Counter sale: package → customer (search or quick-create with account) → voucher and start date → cash or transfer payment",
"38":"PT package sale (/receptionist/pt-sales): package & PT → weekly schedule with per-date overrides → customer(s) → 15-minute hold → payment",
"40":"Shows a customer their own orders and payment attempts, 20 per page",
"41":"Shows the Manager all orders and transactions with filters, Excel export and refund confirmation",
"42":"Read-only facility catalogue for the Receptionist (30 groups per page) and step 1 of reporting a fault (choose items)",
"43":"Master–detail facility management (/manager/facilities): facility groups with visibility, their equipment items with code, label, placement and status; add, edit, retire",
"44":"Merged into #43 (group and item forms are dialogs of the master–detail screen)",
"45":"Step 2 of reporting a fault (priority, incident type, description, warning tag, images) and the Receptionist's own requests with close-out",
"48":"Shows whether the customer can use the gym today, packages, next 5 sessions, warning banners, attendance streak, notifications and the start-date picker",
"49":"Shows the PT a 7-day schedule, needs-attention list, assigned packages with Zalo, monthly result and commission, weather",
"50":"\"My packages\": PT packages and single sessions with next session, progress, weekly template, session strip and one call to action (view only)",
"51":"\"My schedule\": week / month timetable of PT sessions with status, reason, PT contact and calendar link (view only)",
"53":"Withdrawn — customers do not swap sessions",
"54":"Weekly PT schedule (/pt/schedule): week or list view, add draft slots, withdraw free slots, copy week, publish; handle a session in a dialog (reschedule, transfer, cancel)",
"55":"Merged into #54 (add-slot form and click-to-add on the weekly grid)",
"56":"Withdrawn — attendance is closed by JOB-04; session handling moved to #54",
}
SCR = []
for r in O['screens'][1:] if O['screens'][0][0] in ('#','\\#') else O['screens']:
    r = r[:4]
    if r[0] in SCR_OVR: r = [r[0], r[1], r[2], SCR_OVR[r[0]]]
    SCR.append(r)
SCR += [
["68","Select Role","Authentication","Lets a user with several roles choose the working role after sign-in; reachable later from the account menu"],
["69","About","Public website","Presents the gym, its story and contact information"],
["70","Payment Result","Sales & payment","Shows the result of a VNPay payment (success, failed, cancelled, waiting for confirmation, needs refund) from the stored data"],
["71","Exercise Library","Training schedule & coaching","Searchable exercise catalogue from wger for members with a qualifying package and for PTs (/customer/exercises, /pt/exercises)"],
["72","Body Measurements","Training schedule & coaching","Lets a customer record body measurements, see BMI, tiles, a chart and the history (/customer/body-stats)"],
["73","Notification Bell","All roles","Drop-down of the 10 latest notifications with mark read / mark all read, in the header of every signed-in screen"],
]

API_COLS = ["#", "API Name", "Calling Path", "Description"]
API = [r[:4] for r in O['api'][1:]]
API[2][3] = "Sends 6-digit verification codes (SignUp, Claim, Recovery, EmailChange, PhoneChange), the temporary password of an account created at the counter (sent before the transaction commits; failure rolls everything back — CBR-31) and the old-email notice when the login email changes, through SMTP port 587 with TLS."
API.append(["5","API-05: Exercise data (wger)","GMS → wger.de","Reads exercises page by page (GET /api/v2/exerciseinfo/?language__code=en&limit=100) with English name and description, category, main muscles, equipment and main image; no key; data under CC BY-SA 4.0 and credited on the page. Cached in the Exercises table for 1 day; on failure the old cache is kept and the next attempt waits 5 minutes (BR-92)."])

JOB_COLS = ["#", "Job Name", "Group", "Description"]
JOB = [r[:4] for r in O['jobs'][1:]]
JOB[0][3] += ""
JOB[1][3] += ""
JOB[2][3] += ""
JOB[3] = ["4","JOB-04: Auto-close PT sessions","Scheduled","Runs: daily, right after JOB-03. Threshold: session still Scheduled whose date is before today (any number of days late). Result per session, in its own transaction with an audit line: date declared closed all day → Cancelled (GymClosed, no commission — CBR-56); nobody can use the gym and someone is frozen → Missed (MembershipFrozen); nobody can use the gym → Missed (NoMembership); someone checked in that day → CheckedIn; otherwise → Missed (NoCheckIn) (CBR-42). Slot and commission are not changed."]
JOB[4][3] += ""
JOB[5][3] += ""
JOB[6] = ["7","JOB-07: Remind Manager of leave requests starting tomorrow","Scheduled","Runs: every minute. Threshold: Pending leave request starting tomorrow that has not been reminded yet. Result: ReminderSentAt is set and the Managers receive one bell notification \"N leave requests from tomorrow are waiting\". No automatic rejection (CBR-85)."]
JOB.append(["8","JOB-08: Remind customers without gym access","Scheduled","Runs: daily, after JOB-04. Threshold: PT sessions Scheduled for tomorrow whose customer cannot use the gym tomorrow (CBR-117), excluding days closed all day. Result: one bell notification per customer per day (\"Nhắc buổi PT ngày dd/MM/yyyy\", earliest session of that day, advice depends on missing package or freeze), only for Active accounts, both members of a couple; never sent twice."])
