import json
OLD = json.load(open('docs/srs-update/build/data/entities.json'))
P, K, B, S = "Purpose", "Key business attributes", "Business identity", "Status / lifecycle"
OVR = {
"Account": [
 [P,"The single login credential of a Person, shared by every role the Person holds (customer and/or staff). Only Dependent profiles have no account; every Self profile created at the counter gets one with a temporary password (CBR-17, CBR-18, CBR-31)."],
 [K,"Password (BCrypt hash only), status, failed login count, locked-until time, last login time, created time"],
 [B,"Belongs to exactly one Person and serves every role of that Person that is not Inactive (DBR-65). The user signs in with email or phone number; an account with two or more roles chooses the working role on Select Role (/select-role), and the session holds exactly one role that can be switched from the account menu without signing in again (CBR-113). The login email is the Person's email (DBR-23)."],
 [S,"Active · Locked (disabled by the Admin) · Inactive (final; set only when every role of the Person is Inactive, CBR-86). A temporary lockout after repeated wrong passwords applies to customer-only accounts (BR-01) and is tracked in Locked Until; a password reset clears it (BR-02)."]],
"Package": [
 [P,"The catalogue of purchasable offerings — memberships, day passes, PT packages and single PT sessions — that orders are created from."],
 [K,"Package name (3–50), description (≤ 255), benefits (≤ 1000 characters, at most 10 lines of 3–100 characters), package type, duration unit and value, estimated session count (PT packages, 1–255), price (10,000–500,000,000 VND, multiple of 1,000), active flag (on sale), unlocks Exercise Library flag, created time, row version"],
 [B,"Five package types: Membership, DayPass, PTSingle, PTCouple, SingleSession. Duration: DayPass = 1 day; Membership = 1–36 months (catalogue offers 1, 3, 6, 12, 24, 36); PTSingle / PTCouple = 1–12 months (catalogue offers 1, 3, 6, 12); SingleSession has no duration (DBR-19). The name is unique among packages on sale (DBR-11)."],
 [S,"On sale (IsActive = 1) · Stopped (IsActive = 0): no longer sold, running orders and subscriptions unaffected (CBR-97, GBR-06). Single-session packages can be edited but not created."]],
"Subscription": [
 [P,"The usable entitlement a customer holds once an order is paid — the record consumed by check-ins and training sessions."],
 [K,"Subscription type, holder customer, PT in charge (PT packages and single sessions), start date, end date, fixed start time (most frequent session time, PT packages), generated session count, swaps used / max swaps snapshot (P-08 × package months, PT packages), freezes used / max freezes snapshot (P-01, memberships)"],
 [B,"Created when the order is paid, at most one per order, tied to the order and through it to the package. The end date is the last valid day (GBR-03); the generated session count is fixed at payment (BR-40). The PT in charge does not change when a single session is transferred (BR-65). Day passes and single sessions start and end on the same date."],
 [S,"Active · Frozen (Membership only) · Used (DayPass and SingleSession only, once used) · Expired · Cancelled (the order was cancelled). There is no Completed state."]],
"TimeSlot": [
 [P,"A time on a PT's calendar that can hold one session."],
 [K,"Date, start time (15-minute grid), end time (start + P-06 at publishing), status, order currently holding the slot, hold expiry time"],
 [B,"Tied to the PT who published it (or created by a PT-sale hold, BR-96). A PT cannot have two slots with the same start time on one date and slots must not overlap (DBR-33). A slot holds one customer, or one pair for a couple package (DBR-32)."],
 [S,"Free · Held (reserved by a Pending order: online until the order deadline, PT sale for 15 minutes) · Booked (order Paid) · Withdrawn (a Free slot removed by the PT; reopened if the PT publishes the same start time again). A slot returns to Free when its order is cancelled, released or expires, or when its session is moved or cancelled by the PT (DBR-36)."]],
"Session": [
 [P,"One PT session of a customer (or couple) on a PT's slot."],
 [K,"Status, session value, commission amount, PT who teaches, attendance marked time and auto-closed flag (set by JOB-04), missed reason (NoCheckIn / NoMembership / MembershipFrozen), cancel type (ByPt / ByCustomer — historical / GymClosed / PackageExpired), cancel reason, refund agreed, cancelled time, cancelled by"],
 [B,"Tied to the subscription it was generated from and the slot it occupies. The customer is derived from the subscription (a couple package also has the partner on the order). The PT who teaches is stored on the session and changes on a transfer, with the commission recomputed (BR-68). Session value = DBR-59; commission = DBR-58."],
 [S,"Scheduled · CheckedIn (closed by JOB-04: someone could use the gym and checked in that day) · Missed (closed by JOB-04: NoCheckIn, NoMembership or MembershipFrozen) · Cancelled (by the PT with a cash refund, or by the system — GymClosed, PackageExpired). CheckedIn, Missed and Cancelled are final (DBR-38). The PT never marks attendance."]],
"SessionMoveLog": [
 [P,"A record of every move of a session to another slot or PT."],
 [K,"Move type, old and new slot, old and new PT, requested by (account), reason / note, moved time"],
 [B,"Tied to one session; old and new slots differ; the PT changes only for PtTransfer. Only a Scheduled session that has not started can be moved (BR-70). Only the PT creates moves: PtReschedule (BR-64) and PtTransfer (BR-65); CustomerSwap and PtOnBehalfSwap remain only in historical rows."],
 [S,"No status — append-only history; used by My Bookings to explain sessions at a different time (BR-98). No move consumes a swap quota."]],
"LeaveRequest": [
 [P,"A staff member's request for time off, subject to Manager approval."],
 [K,"Leave type (Annual / Unpaid for new requests; Sick historical), from date, to date, shift (Morning / Afternoon / FullDay), days, reason, leave group id (two parts of a split request), assigned reviewer, reject reason, submitted time, reviewed time, reviewer, reminder-sent time"],
 [B,"Tied to the PT or Receptionist who submitted it. Submitted before the P-12 cut-off on the day before the leave (CBR-84); no Scheduled session of the PT in the shift (CBR-83); range rules DBR-74; annual quota P-23 with split into Unpaid (CBR-121); approval keeps minimum staffing (BR-14)."],
 [S,"Pending · Approved · Rejected (by a Manager with a reason). A Pending request may be withdrawn by its owner and is then removed (BR-83). There is no automatic rejection (CBR-85); JOB-07 reminds the Manager the day before."]],
"Facility": [
 [P,"A facility group — a type of machine, a set of weights or an area — shown in the operating catalogue and made of individual equipment items."],
 [K,"Facility name (3–100), category (Cardio / FreeWeights / Machines / Amenities), location, image, unit label (default \"máy\", ≤ 15), active flag, row version"],
 [B,"Identified by system ID; the name is descriptive. Its quantities are computed from its equipment items (BR-104); there is no stored quantity."],
 [S,"Shown (IsActive = 1) · Hidden (IsActive = 0, not visible to the Receptionist). Never deleted (CBR-99)."]],
"EquipmentItem": [
 [P,"One physical machine or item of a facility group (e.g. treadmill TM-003, dumbbell 12 kg) — the unit that is reported broken and repaired."],
 [K,"Item code, item label, placement note, status, row version"],
 [B,"Belongs to one facility group. The item code is unique in the system and the label is unique within the group (DBR-72). Has at most one open maintenance request (BR-105)."],
 [S,"Available · UnderMaintenance (set when a request is approved, CBR-101; back to Available when the last In Progress request is closed, CBR-102) · Retired (final, set by the Manager, CBR-99)."]],
"MaintenanceRequest": [
 [P,"A report that an equipment item needs repair, tracked from report through approval to close-out."],
 [K,"Request code (BT-nnnn), priority (Urgent / Normal — default / Low), incident type (NotWorking — default / StrangeNoise / BrokenOrWorn / SafetyHazard / Hygiene), affected-items note, warning tag placed, description (10–500), attached images, set under maintenance, Manager note, reject reason, acceptance note, reported / reviewed / closed time"],
 [B,"Identified by its request code. Tied to one equipment item, the Receptionist who reported it, the Manager who reviewed it and the Receptionist who closed it (DBR-73)."],
 [S,"Open · InProgress (approved by a Manager) · Rejected (with a reason) · Closed (Receptionist confirms the fix with an acceptance note). Allowed moves: Open → InProgress → Closed, or Open → Rejected (DBR-46)."]],
"Feedback": [
 [P,"A message submitted by a Customer through the suggestion box — the system's sole feedback channel — optionally rating one PT for the month."],
 [K,"Content (10–1000), rated PT, rating (1–5), rating month, submitted time, updated time, read time, read by (Manager)"],
 [B,"Identified by system ID; at most one rating per customer, PT and month (DBR-67). A rating is allowed only after a CheckedIn or Missed session with that PT in that month (CBR-116). Only the Manager reads feedback (CBR-106); the PT sees only the monthly average (DBR-68, DBR-69)."],
 [S,"Unread → Read; re-rating makes it Unread again (DBR-67)."]],
"NotificationRecipient": [
 [P,"Tracks whether a specific Account has read a specific Notification; resolves the many-to-many relationship between them."],
 [K,"Read at (empty = unread)"],
 [B,"Unique per (Notification, Account). Only Active accounts receive in-app notifications; dependants are informed outside the system (GBR-15)."],
 [S,"Unread → Read; \"is read\" is derived from Read At, not stored (DBR-76)."]],
"Shift": [
 [P,"A Receptionist's work shift, used to reconcile cash and transactions at open and close."],
 [K,"Work date, shift type (Morning: opening–13:00 / Afternoon: 13:00–closing), opening and closing Receptionist, opened / closed time, expected cash, counted cash, variance reason"],
 [B,"One record per work date and shift type, split at 13:00 (GBR-38, CBR-104), shared by every Receptionist on duty — one cash drawer per shift. Only one shift is open at a time. Every counter payment is recorded in the shift open at that moment (CBR-105); each payment keeps the Receptionist who received it."],
 [S,"NotOpened · Open · Closed"]],
"BodyMeasurement": [
 [P,"A body measurement a customer records for themselves to follow their progress."],
 [K,"Measured on (date), weight (kg), height (cm), body fat %, muscle %, waist (cm), note (≤ 200), created time"],
 [B,"Tied to one customer; at most one per customer per date — saving the same date overwrites it (BR-87, DBR-70). BMI is computed, not stored (BR-88). Visible only to that customer."],
 [S,"No status; can be deleted by the customer (BR-89)."]],
"Exercise": [
 [P,"A cached exercise from the public wger database, shown in the Exercise Library."],
 [K,"Exercise id (wger id), name (English), category id, muscle ids, equipment ids, image URL, description (English), synced time"],
 [B,"Identified by the wger id; category, muscle and equipment ids are wger codes, not foreign keys (DBR-71). The whole table is replaced when the cache is refreshed (BR-92)."],
 [S,"No status; refreshed at most once a day."]],
"LeaveRequestTransfer": [
 [P,"History of a leave request handed from one reviewer to another."],
 [K,"Leave request, from employee, to employee, time"],
 [B,"From and to employees differ. Kept for earlier requests only: the leave detail screen has no transfer action."],
 [S,"No status — append-only history."]],
"OperatingHourException": [
 [P,"Overrides the default operating hours (P-19) for one specific date (e.g. Tet, public holidays, maintenance)."],
 [K,"Apply date, open time, close time, closed-all-day flag, note, set by (Manager)"],
 [B,"Apply date is unique — at most one exception per date; either closed all day (no times) or open with closing time after opening time. PT package sessions are still generated on a closed day and must be rescheduled by the PT (CBR-56); no new PT slot can be published and no check-in or shift can be opened on it."],
 [S,"N/A"]],
}

PATCH = {
 "Person": [("JPG or PNG, max 2 MB, 200 × 200 to 2000 × 2000 px, stored as a 256 × 256 centre crop (DBR-64)", "JPG or PNG, max 5 MB, 200 × 200 to 2000 × 2000 px, stored as a 256 × 256 centre crop; without an avatar the initials are shown (DBR-64)"),
            ("Customers and staff change only their own avatar, email, phone and password (CBR-30, CBR-108)", "Customers and staff change only their own avatar (upload or remove), email, phone and password (CBR-30, CBR-108)")],
 "Customer": [("A Self profile uses the phone number on its Person, which is mandatory.", "A Self profile uses the phone number and the email on its Person, both mandatory, and always has a login account (CBR-18, CBR-24)."),
              ("no login account (CBR-21); an email is optional (DBR-26)", "no email and no login account (CBR-21, DBR-26)")],
 "Order": [("A Counter order has no payment deadline; its slot stays Held until the order is paid or cancelled, and the shift cannot close while it is Pending (CBR-78).",
            "A Counter order has no payment deadline; a PT-sale order holds its slots for 15 minutes (BR-93) and is cancelled if not paid in time; the shift cannot close while a counter order is Pending (CBR-78).")],
 "Transaction": [("recording the time, the Manager and an optional note; only a Success transaction flagged Needs Refund can be marked (BR-47).",
                  "recording the time, the Manager and a note of 5–500 characters; only a Success transaction flagged Needs Refund can be marked, once (BR-47, BR-81).")],
 "SystemConfig": [("P-01…P-21 (e.g., freeze limits, VAT %, default commission %, PT package durations).\"", "P-01…P-23 (e.g. freeze limits, VAT %, default commission %, package durations, rating threshold, annual leave quota).")],
}
def _patched(e):
    rows = [list(r) for r in OLD[e]]
    for a, b in PATCH.get(e, []):
        hit = False
        for r in rows:
            if a in r[1]: r[1] = r[1].replace(a, b); hit = True
        assert hit, (e, a[:50])
    return rows
ORDER = ["Person","Customer","Account","VerificationCode","Employee","Package","Voucher","Order","Transaction","Shift","Subscription","FreezeRecord","CheckIn","CustomerDeletionRequest","TimeSlot","Session","SessionMoveLog","LeaveRequest","LeaveRequestTransfer","Facility","EquipmentItem","MaintenanceRequest","StaffAttendance","PrivateNote","TrainingPlan","Feedback","BlogPost","Notification","NotificationRecipient","SystemConfig","OperatingHourException","AuditLog","BodyMeasurement","Exercise"]
NEW_ENT = {"EquipmentItem","BodyMeasurement","Exercise","LeaveRequestTransfer"}
def build():
    out=[]
    for e in ORDER:
        rows = OVR.get(e) or (_patched(e) if e in PATCH else OLD[e])
        status = "New" if e in NEW_ENT else ("Modified" if (e in OVR or e in PATCH) else "Unchanged")
        out.append((e, status, rows))
    return out
ENTITIES = build()
