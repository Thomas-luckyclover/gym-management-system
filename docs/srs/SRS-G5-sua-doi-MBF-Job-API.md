---
title: "SRS G5 — Nội dung cần sửa (Main Business Flow, Rules, Job Inventory, External API)"
---

# Cách dùng file này

- Mỗi mục ghi rõ **vị trí trong SRS** và **thao tác**: *Thay toàn bộ*, *Thay đoạn*, hoặc *Thêm mới*. Copy phần nội dung bên dưới tiêu đề vào đúng vị trí.
- Số mục của phần II theo bản SRS của nhóm: **16. Background Job Inventory**, **17. External API Inventory** (đúng như Mục lục SRS).
- Phần **Z. Việc còn mở** dành cho nhóm, **không copy** vào SRS.
- Đối chiếu code: nhánh `develop` (commit fd14ed2).

# A. Change Log — Thêm 4 dòng

| Date | A\*, M, D | In charge | Change Description |
|---|---|---|---|
| 31/10/2026 | M | Le Cong Minh | Reorganize Main Business Flows into 11 cross-actor flows drawn in BPMN (one pool, lanes per actor, solid sequence flows); split into core and supporting flows |
| 31/10/2026 | A | Le Cong Minh | Add BF-02 Gym Check-in and BF-11 Gym Closure & Opening Hours Change; add CBR-124 (opening hours change) |
| 31/10/2026 | D | Le Cong Minh | Remove BF Customer Onboarding (sign-up is described in UC-05); merge Day Pass Check-in into BF-02 and Feedback Reading into BF-10 |
| 31/10/2026 | M | Le Cong Minh | Update CBR-42, CBR-56, GBR-04, GBR-11, UC-11, DBR-36, Entity OperatingHourException, Entity Order, JOB-04, JOB-05, JOB-08; withdraw MSG135; rewrite Background Job Inventory (16) and External API Inventory (17) |

# B. Definition and Acronyms — Thay dòng #5

| # | Acronym | Definition |
|---|---|---|
| 5 | BF | Business Flow — a cross-actor business process in section 2 (BF-01–BF-11), drawn in BPMN |

# C. Table of Contents — Thay phần "2. Main Business Flow"

2\. Main Business Flow

- 2.0 Notation
- Core flows: 2.1 Membership & Day Pass Purchase (BF-01) · 2.2 Gym Check-in (BF-02) · 2.3 Membership Freeze & Auto-Resume (BF-03) · 2.4 PT Package Lifecycle (BF-04) · 2.5 Single PT Session (BF-05) · 2.6 Payment Reconciliation & Refund (BF-06)
- Supporting flows: 2.7 Staff Leave Request Approval (BF-07) · 2.8 Facility Maintenance Request (BF-08) · 2.9 Member Deletion Approval (BF-09) · 2.10 Feedback & PT Rating (BF-10) · 2.11 Gym Closure & Opening Hours Change (BF-11)

# D. Section I.2 Main Business Flow — Thay toàn bộ

## 2.0 Notation

Each business flow is a BPMN process diagram with one pool (Super Gym) and one lane per actor. Every task belongs to exactly one use case (UC) or background job (JOB); a use case is split into several tasks only when a business decision lies between them. A manual task is work done outside the system. A call activity (thick border) reuses another business flow, for example Gym Check-in (BF-02). All sequence flows are solid lines; the main flow is drawn bold. Branch labels carry the code of the alternative (A) or exception (E) flow they belong to. Large flows have an overview figure and one figure per group of flows (a, b, c…). Each figure has its own legend.

Core flows (BF-01 to BF-06) create and deliver the gym's services; supporting flows (BF-07 to BF-11) keep staff, facilities, customer records and opening hours in order.

## 2.1 Membership & Day Pass Purchase (BF-01)

**Trigger:** A customer wants to buy a membership package or a day pass, at the counter or online.

**Pre-condition:** For a counter sale, a Receptionist shift is open (UC-25, CBR-105).

**Main flow (counter sale):**

1. The Receptionist finds the customer by name, phone number or customer code, or quick-creates the profile; a Self profile gets an account and a temporary password by email (UC-20, CBR-118, CBR-24, CBR-31).
2. The Receptionist chooses the package and the start date and applies a voucher if the customer has one (UC-20, UC-23, CBR-119).
3. The Receptionist collects cash or confirms the bank transfer (UC-20, CBR-77).
4. The system marks the order Paid in the open shift, creates the subscription and sets the customer Active (CBR-01, CBR-02, CBR-105).
5. The customer sees the package on the dashboard (UC-41).
6. The customer uses the gym through Gym Check-in (BF-02) while the package is valid.
7. After the end date, JOB-02 expires the membership; a customer with no valid package left becomes Expired (CBR-03). A renewal starts the flow again.

**Alternative flows:**

- A1 – At the start the customer buys online: chooses the package, the start date and a voucher (UC-22, UC-23), pays through VNPay (UC-22), and VNPay returns the result (UC-PAY). A successful payment continues at step 4. After payment the customer may move the start date of an unused membership once, within 7 days (CBR-119).
- A2 – In A1 the payment fails or the 5-minute payment link expires: the order stays Pending and the customer pays again while the order is before its deadline P-09 (CBR-69, CBR-70). Returns to the payment in A1.
- A3 – At step 6 the package is a day pass: it is valid only on the day it is paid (CBR-34) and is used up by one check-in (BF-02, BR-29); a day pass not used that day is expired by JOB-03 (BR-32). The flow ends.

**Exception flows:**

- E1 – In A2 the payment deadline passes: JOB-05 cancels the order (PaymentTimeout), expires its Pending transaction and returns the voucher use (CBR-70, BR-44). The flow ends.
- E2 – In A1 VNPay reports success but the order is already cancelled or the amount differs: the transaction is flagged for refund, the order is Cancelled (InvalidPayment) and no package is created (CBR-72); the refund follows BF-06. The flow ends.

**End conditions:**

- Success: the package is used until it expires (Package expired), or the day pass is used (Day pass used).
- Failure: Order cancelled (E1); Refund needed (E2).

**Note:** A wrong counter order is cancelled within the shift that created it (CBR-11, BF-06 A1). A Frozen or Inactive profile cannot buy online (CBR-120).

## 2.2 Gym Check-in (BF-02)

**Trigger:** A customer arrives at the counter to train.

**Pre-condition:** None. Check-in does not need an open shift (CBR-105).

**Main flow:**

1. The Receptionist looks the customer up by customer code, phone number or name (UC-19, BR-28).
2. The Receptionist compares the customer's face with the check-in photo and confirms it (manual task, BR-31).
3. The system records the check-in (UC-19).
4. The customer trains (manual task).

**Alternative flows:**

- A1 – After step 1 the customer has no check-in photo yet: the Receptionist takes it before continuing (UC-19, BR-33, CBR-35). Continues at step 2.
- A2 – After step 1 the customer enters with a day pass: no photo and no face check (BR-30); the check-in uses the day pass, which becomes Used (BR-29, CBR-36). Continues at step 3.

**Exception flows:**

- E1 – After step 1 the customer cannot use the gym today: no valid membership or day pass, the membership is frozen, the day pass is already used, or the customer holds only a PT package (CBR-117, BR-27, CBR-58, MSG83, MSG84). Entry is refused; the customer may buy a day pass (BF-01). The flow ends.

**End conditions:**

- Success: Checked in.
- Failure: Entry refused (E1).

**Note:** A membership allows unlimited check-ins within its validity (BR-34). JOB-04 uses the check-ins of the day to close PT sessions (CBR-42). This flow is reused as a call activity in BF-01, BF-04 and BF-05.

## 2.3 Membership Freeze & Auto-Resume (BF-03)

**Trigger:** A customer asks the Receptionist at the counter to freeze their membership.

**Pre-condition:** The customer holds an Active membership package.

**Main flow:**

1. The Receptionist looks up the membership and enters the freeze period and the reason (UC-18).
2. The Receptionist reviews the PT sessions listed inside the period, informs the customer and saves the freeze (UC-18, CBR-62).
3. The system sets the membership and the customer profile to Frozen (BR-20).
4. The customer sees the freeze period on the dashboard (UC-41, BR-102).
5. The PT sees the sessions inside the freeze period in Needs attention, if any (UC-32, BR-101, CBR-66).
6. After each session day, JOB-04 closes such a session as Missed / MembershipFrozen when nobody in it could use the gym (CBR-42, CBR-62, CBR-67).
7. At the end of the freeze, JOB-01 resumes the membership, adds the days frozen to its end date and sets the customer Active (BR-25, DBR-51).

**Alternative flows:**

- A1 – Before step 7 the customer comes back early: the Receptionist ends the freeze (UC-18). The membership resumes immediately and the end date is extended only by the days actually frozen; the freeze allowance stays used and sessions already closed are not restored (BR-26, CBR-68). The flow ends.

**Exception flows:**

- E1 – At step 1 the freeze is not allowed: the package is not a membership or is expired, the freeze allowance is used up, a freeze is already open, the period is longer than P-02 or starts before tomorrow, or the Receptionist is freezing their own package (BR-21, BR-23, BR-24, CBR-08, DBR-13, CBR-115). Nothing changes. The flow ends.

**End conditions:**

- Success: Membership active again (step 7 or A1).
- Failure: Freeze denied (E1).

**Note:** While frozen, the customer is refused at check-in (BF-02 E1). If the customer agrees, the PT may reschedule a session out of the freeze period (UC-35).

## 2.4 PT Package Lifecycle (BF-04)

**Goal:** Every session of a sold PT package reaches a final state (CheckedIn, Missed or Cancelled), so the customer's quota and the PT's commission are settled correctly.

**Trigger:** A customer wants a personal trainer and asks for a PT package.

**Pre-condition:** A Receptionist shift is open (CBR-105).

**Main flow:**

1. The PT consults the customer in person; the customer chooses the PT (manual task, BR-38, BR-39).
2. The Receptionist chooses single or couple, the PT and the customer(s) (UC-21).
3. The Receptionist sets the start date and the weekly Monday–Friday template (UC-21, BR-40, CBR-43).
4. The Receptionist holds the schedule: a Pending counter order without voucher is created and the slots are held for 15 minutes (UC-21, BR-93, CBR-76).
5. The Receptionist collects cash or confirms the bank transfer (UC-21, CBR-77).
6. The system marks the order Paid and creates one Scheduled session for each held date with its value and commission; the holder, the partner and the PT are notified (CBR-50, BR-97).
7. The customer follows the PT schedule (UC-36, BR-98, BR-99).
8. The PT follows the teaching schedule (UC-32, BR-100, BR-101).
9. On the session day the customer checks in (BF-02).
10. The PT teaches the session (manual task).
11. After the session day JOB-04 closes the session as CheckedIn, because the customer (couple: either member) checked in that day (CBR-42).

Steps 8–11 repeat for each session until none is Scheduled.

**Alternative flows:**

- A1 – At step 3 some dates cannot be scheduled: the Receptionist picks another time for those dates only, ticking "Confirmed with PT" for a time the PT has not opened, or leaves them without a session (BR-41, BR-96). Continues at step 4.
- A2 – At step 9 nobody checks in that day: JOB-04 closes the session as Missed / NoCheckIn; it is deducted and the PT still earns the commission (BR-52, BR-53, CBR-80).
- A3 – At step 9 nobody in the session can use the gym that day: JOB-04 closes it as Missed / MembershipFrozen if someone is frozen (BF-03), otherwise Missed / NoMembership; JOB-08 reminded the customer the day before (CBR-42, CBR-62, CBR-64, CBR-67).
- A4 – At step 8 the PT cannot teach a session, or the customer asks to move it: after agreeing with the customer (BR-63) the PT reschedules it to an own free slot within 14 days and the term (BR-64), transfers it to another free PT (BR-65, BR-68), or cancels it with a reason and the customer's agreement to a cash refund (BR-66, BR-67, BR-71) (UC-35). The customer (couple: both members) is notified (CBR-46). A moved session returns to step 8; a cancelled session is Cancelled (ByPt) and earns commission (CBR-81).
- A5 – At step 9 the session falls on a date declared closed, or outside that date's opening hours (BF-11): if the PT has not rescheduled it, JOB-04 cancels it as GymClosed, with no commission, no make-up and no term extension (CBR-56, CBR-124).
- A6 – After step 11 the package end date has passed while sessions are still Scheduled: JOB-02 expires the package and cancels those sessions (PackageExpired), with no refund and no term extension (CBR-55). The flow ends.

**Exception flows:**

- E1 – At step 2 a customer (couple: either member) has no membership valid today, is frozen, is under the minimum age P-05, or is the Receptionist's own profile: the sale is blocked; a membership package is sold first (BF-01) (CBR-59, BR-37, CBR-13, CBR-14, CBR-115). The flow ends.
- E2 – At step 4 the 15-minute hold expires, or the Receptionist releases it before payment: the order is Cancelled and the held slots are returned to the PT (BR-93). The flow ends.

**End conditions:**

- Success: All sessions settled — every session is CheckedIn, Missed or Cancelled (DBR-38) and quota and commission are settled; or Package expired (A6).
- Failure: Sale blocked (E1); Hold released (E2).

**Note:** The consultation and the PT's cash refund happen outside the system. Known limitation: a customer who checked in but did not train with the PT is still closed as CheckedIn, because the system only knows the check-in.

## 2.5 Single PT Session (BF-05)

**Trigger:** A customer wants one PT session and books it online, or asks for one at the counter (A3).

**Pre-condition:** Online: the customer is signed in with a Self profile. At the counter: a Receptionist shift is open (CBR-105).

**Main flow:**

1. The customer chooses a PT and a free time slot in the current week (from Saturday, also the next week) (UC-37, CBR-39).
2. The system creates a Pending order and holds the slot until the order's payment deadline P-09 (CBR-53, CBR-40, CBR-41).
3. The customer pays through VNPay (UC-37).
4. VNPay returns the payment result (UC-PAY).
5. The system marks the order Paid, the slot Booked and the session Scheduled (CBR-53, BR-80).
6. The customer follows the session (UC-36).
7. On the session day the customer checks in (BF-02).
8. The PT teaches the session (manual task).
9. After the session day JOB-04 closes the session as CheckedIn (CBR-42).

**Alternative flows:**

- A1 – At step 4 the payment fails or the link expires: the order stays Pending, the slot stays Held and the customer pays again before the deadline (CBR-69, CBR-40). Returns to step 3.
- A2 – At step 7 the customer does not check in, or cannot use the gym that day: JOB-04 closes the session as Missed (NoCheckIn, NoMembership or MembershipFrozen) (CBR-42, BR-52).
- A3 – At the start the customer is at the counter: the Receptionist sells a single session for today (UC-20): chooses the PT and a free slot today and finds or quick-creates the customer; a walk-in without a membership buys today's day pass first (CBR-60, BF-01). The system creates a Pending counter order and holds the slot until the payment deadline P-09, shown as an mm:ss countdown; the Receptionist collects cash or confirms the bank transfer in the open shift (CBR-54, CBR-49, CBR-77, CBR-105). Continues at step 5.

**Exception flows:**

- E1 – At step 1, or in A3, the customer cannot use the gym on that date or is under P-05 (BR-72, CBR-60, CBR-15): booking is blocked. The flow ends.
- E2 – In A1, or in A3 before the money is collected, the payment deadline passes: JOB-05 cancels the order (PaymentTimeout) and the slot returns to Free (CBR-70, CBR-54, DBR-36). The flow ends.
- E3 – At step 4 VNPay reports success but the order is already cancelled or the amount differs: the transaction is flagged for refund and the slot returns to Free (CBR-72); the refund follows BF-06. The flow ends.

**End conditions:**

- Success: Session closed (CheckedIn or Missed).
- Failure: Booking blocked (E1); Order cancelled (E2); Refund needed (E3).

## 2.6 Payment Reconciliation & Refund (BF-06)

**Trigger:** (1) The start time of a morning or afternoon shift is reached; or (2) VNPay reports a successful payment for an order that is already cancelled or whose amount differs (A4).

**Pre-condition:** None.

**Main flow (shift cash reconciliation):**

1. A Receptionist opens the shift; every Receptionist on duty works in the same shift (UC-25, CBR-104).
2. The Receptionists take counter payments, each recorded in the open shift with who received it (UC-20, UC-21, CBR-105).
3. At the end of the shift a Receptionist counts the cash in the drawer and closes the shift (UC-25, BR-46).
4. The system closes the shift as read-only and sends the shift report to the Manager (DBR-55, MSG274).
5. The Manager reviews the shift's transactions (UC-26).

**Alternative flows:**

- A1 – At step 2 a paid order was sold by mistake: the Receptionist cancels it within the same open shift with a reason and returns the cash at the counter (UC-25, CBR-11). Returns to step 2.
- A2 – At step 3 counter orders of the shift are still Pending: closing is blocked (MSG87) until each one is paid or cancelled (CBR-78). Returns to step 3.
- A3 – At step 3 the counted cash differs from the expected cash: the Receptionist enters a reason of 5–500 characters (BR-46, DBR-55). Continues at step 4.
- A4 – Online payment refund (trigger 2): the system flags the transaction Needs refund and cancels the order (InvalidPayment) (CBR-72); the Manager opens the refund list (UC-26), transfers the money back to the customer outside the system (manual task), and confirms the refund with a note of 5–500 characters, once (UC-26, BR-47, BR-81). The flow ends.

**Exception flows:** None.

**End conditions:**

- Success: Shift reconciled (main flow); Refund recorded (A4).

**Note:** A shift left open is closed by the next Receptionist at hand-over (BR-46, CBR-123); only one shift is open at a time (CBR-104).

## 2.7 Staff Leave Request Approval (BF-07)

**Trigger:** A PT or Receptionist needs time off.

**Pre-condition:** None.

**Main flow:**

1. The staff member chooses the dates, the shift, the leave type and the reason (UC-14, DBR-74, BR-82).
2. The staff member submits the request (UC-14).
3. If the request is still Pending the day before the leave, JOB-07 reminds the Managers (CBR-85).
4. The Manager reviews the request and approves it (UC-15, BR-14, BR-108).
5. The staff member reads the decision (UC-50).

**Alternative flows:**

- A1 – At step 1 the PT still has Scheduled sessions in that shift: the PT handles them first (UC-35, CBR-83). Returns to step 1.
- A2 – At step 1 the annual leave left is not enough: the staff member accepts the split into an Annual part and an Unpaid part (CBR-121). Continues at step 2.
- A3 – After step 2 the staff member withdraws the request while it is Pending; it is removed (BR-83). The flow ends.

**Exception flows:**

- E1 – At step 1 the submission cut-off P-12 of the previous day has passed: the request is not accepted (MSG192) and the employee informs the Manager directly (CBR-84). The flow ends.
- E2 – At step 4 the Manager rejects the request with a reason of 5–500 characters (UC-15, GBR-09); the staff member reads the rejection (UC-50). The flow ends.

**End conditions:**

- Success: Approved.
- Failure: Not submitted (E1); Withdrawn (A3); Rejected (E2).

**Note:** Approval re-checks the annual quota and minimum staffing (BR-14); the Manager may approve up to 50 requests at once (BR-85) and approve late with a reason (BR-86). There is no automatic rejection (CBR-85).

## 2.8 Facility Maintenance Request (BF-08)

**Trigger:** A Receptionist finds one or more equipment items broken.

**Pre-condition:** None.

**Main flow:**

1. The Receptionist finds the items in the facility catalogue (UC-27).
2. The Receptionist reports the broken items with priority, incident type, description and optional images (UC-28, BR-105).
3. The system creates one request per item and notifies every Manager (BR-105).
4. The Manager reviews the request and approves it (UC-29, BR-106).
5. The system sets the item Under maintenance, unless the Manager unticks this option (CBR-101).
6. After the repair, the Receptionist checks it and closes the request with an acceptance note (UC-28, BR-48).
7. The system returns the item to Available if it has no other request In Progress and is not Retired; the approving Manager is notified (CBR-102, BR-106).

**Alternative flows:** None.

**Exception flows:**

- E1 – At step 4 the Manager rejects the request with a reason (UC-29, BR-49); the Receptionist reads the rejection (UC-50). The flow ends.

**End conditions:**

- Success: Request closed.
- Failure: Request rejected (E1).

**Note:** The repair itself is done outside the system.

## 2.9 Member Deletion Approval (BF-09)

**Trigger:** A customer profile has to be deleted.

**Pre-condition:** None.

**Main flow:**

1. The Receptionist opens the profile, enters a reason and presses Delete (UC-16, BR-17).
2. The customer still has a running package or unused PT sessions, so the system creates a Pending deletion request (CBR-16, MSG96).
3. The Manager reviews the request and approves it (UC-17).
4. The system sets the profile Inactive and signs out every session of the account; the account becomes Inactive if the Person has no other active role (CBR-86, DBR-28).

**Alternative flows:**

- A1 – At step 2 the customer has no running package and no unused PT session: the profile is deleted immediately (BR-15). Continues at step 4.

**Exception flows:**

- E1 – At step 3 the Manager rejects the request with a reason; the profile is unchanged. The flow ends.

**End conditions:**

- Success: Profile deleted.
- Failure: Request rejected (E1).

**Note:** A staff member cannot delete, or approve the deletion of, their own customer profile (CBR-115). A profile has at most one Pending request (MSG101).

## 2.10 Feedback & PT Rating (BF-10)

**Trigger:** A customer wants to give feedback.

**Pre-condition:** The customer is signed in.

**Main flow:**

1. The customer writes feedback in the suggestion box (UC-42, DBR-66).
2. The customer chooses a PT and gives 1–5 stars (UC-42).
3. The customer sends the feedback (UC-42).
4. The system saves the feedback as Unread.
5. The Manager reads the feedback (UC-43, CBR-106).
6. The system marks it Read and counts the rating in the PT's monthly average (DBR-68).
7. The PT views the monthly average, shown only from P-22 ratings (UC-32, DBR-69).

**Alternative flows:**

- A1 – At step 2 the customer does not rate a PT: step 2 is skipped and the flow ends after step 6 (Feedback read).

**Exception flows:**

- E1 – At step 2 the customer has no CheckedIn or Missed session with that PT in that month: the rating is refused (CBR-116, MSG106). The flow ends.

**End conditions:**

- Success: Rating counted; Feedback read (A1).
- Failure: Rating refused (E1).

**Note:** Rating the same PT again in the same month overwrites the rating and makes the feedback Unread again (DBR-67). There is no reply step (CBR-107).

## 2.11 Gym Closure & Opening Hours Change (BF-11)

**Trigger:** The Manager decides to close the gym on a date or to change the opening hours.

**Pre-condition:** None.

**Main flow:**

1. The Manager declares the change: a date closed all day, a date with different hours, or new default hours P-19 (UC-11, BR-12, CBR-124).
2. The system saves the change immediately, withdraws Free slots outside the new hours and flags the Scheduled sessions outside them (CBR-124, CBR-56, CBR-66).
3. The PT sees each affected session in Needs attention when it is within the next 14 days (UC-32, BR-101).
4. The PT agrees a new time with the customer (manual task, BR-63).
5. The PT reschedules the session, or cancels it with a cash refund (UC-35, BR-64, BR-66).
6. The customer reads the change (UC-50, CBR-46).

Steps 3–6 repeat for each affected session.

**Alternative flows:**

- A1 – After step 3 the PT does not handle the session before it starts: after that day JOB-04 cancels it as GymClosed, with no commission, no make-up and no term extension (CBR-56, CBR-124). The flow ends.

**Exception flows:** None.

**End conditions:**

- Success: Session handled (step 6); Session cancelled as GymClosed (A1).

**Note:** On a date closed all day no shift is opened, no slot is published and nobody checks in. If the PT does not reschedule in time, the customer loses that session and the Manager compensates the customer outside the system (BR-69).

# E. Rules — Thay đoạn / Thêm mới

## E1. CBR-42 — Thay đoạn

Thay cụm *"a session on a date declared closed all day → Cancelled / GymClosed (CBR-56)"* bằng:

> a session on a date declared closed all day, or outside that date's opening hours → Cancelled / GymClosed (CBR-56, CBR-124)

## E2. CBR-56 — Thay toàn bộ

| CBR-ID | Rule Statement | Use Case Relevant |
|---|---|---|
| CBR-56 | Sessions are still generated on a date declared closed, and a session stays Scheduled when a later change of opening hours leaves it outside the hours of its date (CBR-124); the PT sale screen marks such dates. Before that date the PT must reschedule the session after agreeing with the customer (BR-63, BR-64, no swap quota used); the PT dashboard lists it and JOB-08 does not remind the customer for it. A session still Scheduled after that day is cancelled by JOB-04 as GymClosed: no commission, no make-up, no term extension; the Manager compensates the customer outside the system (BR-69). | UC-11, UC-21, UC-32, UC-35 |

## E3. CBR-124 — Thêm mới (sau CBR-123)

| CBR-ID | Rule Statement | Use Case Relevant |
|---|---|---|
| CBR-124 | The Manager's change of opening hours — the default hours P-19 or a date exception (closed all day or different hours) — is saved immediately and applies from then on. A Scheduled session that falls outside the opening hours of its date is handled like a session on a closed day (CBR-56): it is flagged on the PT's and the customer's screens, the PT reschedules it after agreeing with the customer, otherwise JOB-04 cancels it as GymClosed. Free slots outside the new hours are withdrawn automatically. | UC-11, UC-30, UC-32, UC-35 |

## E4. GBR-04 — Thêm vào cuối cột Rule Statement

> Opening hours are the exception: P-19 and date exceptions apply to every date from the change, including existing slots and sessions (CBR-124).

## E5. GBR-11 — Thay câu cuối của Rule Statement

> PT package sessions are generated on every chosen weekday of the term, including closed days; a session on a closed day or outside a changed opening time must be rescheduled by the PT (CBR-56, CBR-124).

## E6. UC-11 Configure Operating Hours — Thêm vào cuối Use Case Description

> When the hours change, Free slots outside the new hours are withdrawn and Scheduled sessions outside them are rescheduled by the PT (CBR-124, CBR-56).

## E7. DBR-36 TimeSlot.Status — Thay câu cuối

> A Free slot that the PT removes, or that falls outside changed opening hours (CBR-124), becomes Withdrawn and is kept; publishing the same start time again reopens it (BR-94, BR-95).

## E8. Entity OperatingHourException — Thay dòng Business identity

> Apply date is unique — at most one exception per date; either closed all day (no times) or open with closing time after opening time. PT package sessions are still generated on a closed day; sessions on a closed day or outside the hours of the date are rescheduled by the PT (CBR-56, CBR-124). No new PT slot can be published and no check-in or shift can be opened on a closed day.

## E9. Entity Order — Thay câu về hạn thanh toán trong dòng Business identity

Thay câu *"A Counter order has no payment deadline; a PT-sale order holds its slots for 15 minutes (BR-93) and is cancelled if not paid in time"* bằng:

> A Counter order has no payment deadline, except a single-session order, which copies P-09 as the hold time of its slot and is cancelled by JOB-05 if not paid in time (CBR-54); a PT-sale order holds its slots for 15 minutes (BR-93) and is cancelled if not paid in time.

## E10. System Messages — Thay dòng MSG135

| # | Message code | Message Type | Context | Content |
|---|---|---|---|---|
| 135 | MSG135 | — | Withdrawn — selling a single PT session at the counter is part of UC-20 (CBR-54), so a message saying it is unavailable is not used. | — |

# F. Section I.5.2 External API — Thay toàn bộ bảng

| # | API Name | Calling Path | Description |
|---|---|---|---|
| 1 | API-01: VNPay payment request | GMS → VNPay | Builds a signed VNPay 2.1.0 payment URL for one payment attempt of a Pending online order and redirects the customer's browser to the gateway; the link expires after 5 minutes, never beyond the order's payment deadline. |
| 2 | API-02: VNPay IPN webhook | VNPay → GMS | Receives the official payment result (GET /api/v1/commerce/payment/webhook/vnpay), verifies the signature and records the transaction; marks the order Paid only when it is still Pending and the amount matches. |
| 3 | API-03: Email delivery (SMTP) | GMS → Mail server | Sends 6-digit verification codes (SignUp, Claim, Recovery, EmailChange, PhoneChange), the temporary password of an account created at the counter (sent before the transaction commits; a failure rolls everything back — CBR-31) and the notice to the old email when the login email changes, through SMTP port 587 with TLS. |
| 4 | API-04: Weather forecast (Open-Meteo) | GMS → Open-Meteo | Reads today's weather at the gym for the PT Dashboard; cached for 30 minutes; the weather card is hidden if no reading of the day is available. |
| 5 | API-05: Exercise data (wger) | GMS → wger.de | Reads exercises page by page with English name and description, category, main muscles, equipment and main image; no key; data under CC BY-SA 4.0, credited on the page. Cached in the Exercises table for 1 day; on failure the old cache is kept and the next attempt waits 5 minutes (BR-92). |

# G. Section I.5.3 Background Job Inventory — Thay toàn bộ bảng

| # | Job Name | Group | Description |
|---|---|---|---|
| 1 | JOB-01: Resume expired membership freezes | Scheduled | Runs: daily at 00:05 (Asia/Ho_Chi_Minh), first in the daily chain. Threshold: freeze still in progress whose Planned End Date is before today. Result: package Frozen → Active and customer → Active; Planned End Date − Start Date + 1 frozen days are added to the package End Date (BR-25, DBR-51). |
| 2 | JOB-02: Expire membership and PT packages | Scheduled | Runs: daily, right after JOB-01. Threshold: package End Date is before today. Result: membership → Expired, a customer with no valid membership or day pass left → Expired (CBR-03); PT package → Expired and its leftover Scheduled sessions → Cancelled (PackageExpired, CBR-55). |
| 3 | JOB-03: Expire unused day passes | Scheduled | Runs: daily, right after JOB-02. Threshold: the pass's valid day has ended without a check-in. Result: day pass → Expired; a customer with no valid package left → Expired (BR-32). |
| 4 | JOB-04: Auto-close PT sessions | Scheduled | Runs: daily, right after JOB-03. Threshold: session still Scheduled whose date is before today (any number of days late). Result per session, in its own transaction with an audit line: date closed all day or session outside that date's opening hours → Cancelled (GymClosed, no commission — CBR-56, CBR-124); nobody can use the gym and someone is frozen → Missed (MembershipFrozen); nobody can use the gym → Missed (NoMembership); someone checked in that day → CheckedIn; otherwise → Missed (NoCheckIn) (CBR-42). Slot and commission are not changed. |
| 5 | JOB-05: Cancel overdue online orders | Scheduled | Runs: every minute. Threshold: online order, or counter single-session order (CBR-54), still Pending past the payment deadline stored on the order (creation + P-09, default 20 minutes) with no Success transaction. Result: order → Cancelled (PaymentTimeout); Pending transactions → Expired; voucher use returned; Held slot → Free; the customer is notified (CBR-70, BR-44). |
| 6 | JOB-06: Expire timed-out payment transactions | Scheduled | Runs: every minute. Threshold: transaction still Pending past its own deadline — 5 minutes after its payment link is created, never beyond the order's deadline. Result: transaction → Expired; the order stays Pending so the customer can pay again (CBR-69). |
| 7 | JOB-07: Remind Managers of leave requests starting tomorrow | Scheduled | Runs: every minute. Threshold: Pending leave request starting tomorrow that has not been reminded yet. Result: ReminderSentAt is set and the Managers receive one bell notification "N leave requests from tomorrow are waiting". No automatic rejection (CBR-85). |
| 8 | JOB-08: Remind customers without gym access | Scheduled | Runs: daily, after JOB-04 (last in the chain). Threshold: PT sessions Scheduled for tomorrow whose customer cannot use the gym tomorrow (CBR-117), excluding sessions on a closed day or outside that day's opening hours (CBR-56, CBR-124). Result: one bell notification per customer per day ("Nhắc buổi PT ngày dd/MM/yyyy", earliest session of that day, advice depends on missing package or freeze), only for Active accounts, both members of a couple; never sent twice. |

# H. Section II.16 Background Job Inventory — Thay toàn bộ

## 16.0 Common behavior for all jobs

All times use the Asia/Ho_Chi_Minh time zone (UTC+7). Jobs run inside the application on the JDK scheduler, started when the application starts and stopped when it stops (NFR-01).

- **Daily chain:** at 00:05 the daily jobs run one after another in this order: JOB-01 → JOB-02 → JOB-03 → JOB-04 → JOB-08. JOB-01 runs first so that the frozen days are added before expiry is evaluated; JOB-04 runs after the expiry jobs so that it reads the gym access left after them; JOB-08 runs last.
- **Minute jobs:** JOB-05, JOB-06 and JOB-07 each run every minute, independently of each other.
- **One record, one transaction:** every job processes records one by one, each in its own database transaction together with its audit line. An error on one record rolls back that record only; the others are still processed (NFR-09).
- **Retry:** if a daily job cannot reach the database, it is retried up to 3 times at 5-minute intervals; after that a critical error is logged and the job waits for the next daily run. A minute job is not retried within the run; the next run, one minute later, picks up the same records.
- **Idempotent:** every job re-checks the record status after locking it, so running a job twice changes nothing more.
- **Audit:** every state change made by a job is written to the audit log with no acting account and no acting role (GBR-08).

| Job | Schedule | Use cases | Main rules |
|---|---|---|---|
| JOB-01 Resume expired membership freezes | Daily 00:05, 1st | UC-18, UC-41 | BR-25, DBR-51 |
| JOB-02 Expire membership and PT packages | Daily, 2nd | UC-19, UC-36, UC-41 | CBR-03, CBR-55 |
| JOB-03 Expire unused day passes | Daily, 3rd | UC-19, UC-41 | BR-32 |
| JOB-04 Auto-close PT sessions | Daily, 4th | UC-32, UC-36 | CBR-42, CBR-56, CBR-124 |
| JOB-08 Remind customers without gym access | Daily, 5th (last) | UC-36, UC-50 | CBR-117, CBR-56 |
| JOB-05 Cancel overdue online orders | Every minute | UC-22, UC-37, UC-PAY | CBR-70, BR-44 |
| JOB-06 Expire timed-out payment transactions | Every minute | UC-22, UC-37, UC-PAY | CBR-69 |
| JOB-07 Remind Managers of leave requests starting tomorrow | Every minute | UC-14, UC-15 | CBR-85 |

## 16.1 Resume Expired Membership Freezes (JOB-01)

**Trigger:** Scheduled — daily at 00:05, first job of the daily chain.

**Purpose:** Ends every freeze whose planned period is over, so the membership can be used again and its end date includes the days frozen. The Receptionist does nothing (BR-25).

**Related:** UC-18, UC-41 · Rules BR-25, BR-26, DBR-51, DBR-34, CBR-07

**Parameters:** None (the freeze allowance P-01 and length P-02 were checked when the freeze was saved).

Processing steps:

1. Find freezes still in progress (Actual End Date empty) whose Planned End Date is before today.
2. For each freeze, in its own transaction, read the membership subscription it belongs to.
3. If the subscription is still Frozen: set Actual End Date = Planned End Date; compute the days frozen = Planned End Date − Start Date + 1; add them to the subscription End Date; set the subscription Active and the customer Active.
4. If the subscription is no longer Frozen (for example the Receptionist ended the freeze early — BR-26), only close the freeze record with Actual End Date = Planned End Date.
5. Write the audit line ("JOB-01 resumed the package after freeze #n, + d days, new end date dd/mm/yyyy") in the same transaction.
6. Log the number of packages resumed.

Failure behavior:

| Scenario | System behavior |
|---|---|
| Database unreachable | Retries up to 3 times at 5-minute intervals, then logs a critical error and waits for the next daily run. The memberships stay Frozen until the next successful run; the days added are still computed from the planned period. |
| Error on one freeze | Rolls back that freeze only and logs a warning; the other freezes are still processed. The freeze is processed again in the next run. |
| No freeze to resume | The job completes immediately with no action — this is normal and expected. |

## 16.2 Expire Membership and PT Packages (JOB-02)

**Trigger:** Scheduled — daily, right after JOB-01.

**Purpose:** Marks packages whose last valid day has passed as Expired, so gym access and PT sessions follow the real validity, and closes the PT sessions left in an expired PT package.

**Related:** UC-19, UC-36, UC-41 · Rules CBR-03, CBR-55, GBR-03, DBR-34, DBR-37

**Parameters:** None.

Processing steps:

1. Find Membership, PTSingle and PTCouple subscriptions that are Active and whose End Date is before today (the End Date is the last valid day — GBR-03).
2. For each subscription, in its own transaction, lock it and re-check that it is still Active and past its End Date.
3. Membership: set the subscription Expired.
4. PT package: set the subscription Expired and set every session of it that is still Scheduled to Cancelled (CancelType = PackageExpired), with no refund and no term extension (CBR-55).
5. If the customer now holds no Active or Frozen membership and no valid day pass, and the profile is not Inactive, set the customer Expired (CBR-03). A PT package never keeps a customer Active (CBR-58).
6. Write the audit lines in the same transaction.
7. Log the number of packages expired per type.

Failure behavior:

| Scenario | System behavior |
|---|---|
| Database unreachable | Retries up to 3 times at 5-minute intervals, then logs a critical error and waits for the next daily run. |
| Error on one subscription | Rolls back that subscription only; the others are still processed. |
| Subscription changed by another process | The re-check finds it no longer Active; it is skipped. |
| Nothing to expire | The job completes immediately with no action — this is normal and expected. |

## 16.3 Expire Unused Day Passes (JOB-03)

**Trigger:** Scheduled — daily, right after JOB-02.

**Purpose:** Expires day passes whose day has ended without a check-in. A used day pass is already Used (BR-29).

**Related:** UC-19, UC-41 · Rules BR-32, CBR-34, CBR-36, DBR-34

**Parameters:** None.

Processing steps:

1. Find DayPass subscriptions that are still Active and whose valid day is before today.
2. For each pass, in its own transaction, lock it and re-check that it is still Active and has no check-in.
3. Set the pass Expired.
4. If the customer now holds no Active or Frozen membership and no other valid day pass, and the profile is not Inactive, set the customer Expired.
5. Write the audit line in the same transaction and log the number of passes expired.

Failure behavior:

| Scenario | System behavior |
|---|---|
| Database unreachable | Retries up to 3 times at 5-minute intervals, then logs a critical error and waits for the next daily run. |
| Error on one pass | Rolls back that pass only; the others are still processed. |
| Pass used meanwhile | The re-check finds it Used; it is skipped. |
| Nothing to expire | The job completes immediately with no action — this is normal and expected. |

## 16.4 Auto-Close PT Sessions (JOB-04)

**Trigger:** Scheduled — daily, right after JOB-03.

**Purpose:** Closes every PT session whose date has passed and that is still Scheduled, so the session quota and the PT commission are final. The PT never marks attendance (CBR-42).

**Related:** UC-32, UC-36 · Rules CBR-42, CBR-56, CBR-124, CBR-63, CBR-64, CBR-67, CBR-68, CBR-80, CBR-117, DBR-37, DBR-38

**Parameters:** None.

Processing steps:

1. Find sessions still Scheduled whose session date is before today, however many days late.
2. For each session, in its own transaction, lock it, re-check that it is still Scheduled, and evaluate in order, stopping at the first match:
   a. The session date is declared closed all day, or the session lies outside that date's opening hours → Status = Cancelled, CancelType = GymClosed; no commission (CBR-56, CBR-124).
   b. Nobody in the session can use the gym on that date (CBR-117; couple package: neither member) and at least one of them is frozen on that date → Status = Missed, MissedReason = MembershipFrozen.
   c. Nobody in the session can use the gym on that date → Status = Missed, MissedReason = NoMembership.
   d. A check-in exists on that date for the customer (couple package: for either member) → Status = CheckedIn.
   e. Otherwise → Status = Missed, MissedReason = NoCheckIn.
3. Set IsAutoClosed = 1 and AttendanceMarkedAt = now. The time slot is not released and CommissionAmount is not recalculated; a Missed session earns it like a CheckedIn one (CBR-80).
4. Write the audit line in the same transaction as the session change.
5. Log the number of sessions per outcome.

Failure behavior:

| Scenario | System behavior |
|---|---|
| Database unreachable | Retries up to 3 times at 5-minute intervals. If it still fails, logs a critical error and waits for the next daily run. Sessions remain Scheduled until the next successful run. |
| Error while processing one session | Rolls back that session only and logs a warning. Other sessions are still processed. The session stays Scheduled and is processed again in the next run. |
| Session already changed by another process | The re-check finds it no longer Scheduled; the session is skipped. |
| No sessions to close | The job completes immediately with no action — this is normal and expected. |

## 16.5 Cancel Overdue Online Orders (JOB-05)

**Trigger:** Scheduled — every minute.

**Purpose:** Cancels online orders, and counter single-session orders, that were not paid by their payment deadline, so the held slot and the voucher use are released (CBR-70, CBR-54).

**Related:** UC-20, UC-22, UC-37, UC-PAY · Rules CBR-70, CBR-54, CBR-40, BR-44, DBR-36, DBR-43

**Parameters:** P-09 (payment deadline, copied into Orders.PaymentDueAt when the order is created — GBR-04).

Processing steps:

1. Find online orders, and counter single-session orders (CBR-54), that are still Pending and whose PaymentDueAt is before now. Other counter orders have no deadline and are never cancelled by this job.
2. For each order, in its own transaction, lock the customer and then the order, and re-check that the order is still Pending, still overdue and has no Success transaction (the IPN or the Receptionist may have completed it in between).
3. Set the order Cancelled with CancelType = PaymentTimeout.
4. Set its Pending transactions Expired, return its Held slot to Free (DBR-36) and return one use of its voucher, if any (BR-44). If the voucher use cannot be returned, the whole cancellation is rolled back.
5. Write the audit line and, if the customer's account is Active, send a bell notification, in the same transaction.

Failure behavior:

| Scenario | System behavior |
|---|---|
| Database unreachable | No retry within the run; the next run (1 minute later) processes the same orders. The error is logged. |
| Error on one order | Rolls back that order only; the others are still processed. |
| IPN paid the order meanwhile | The re-check finds a Success transaction or a Paid order; the order is skipped. |
| A late payment arrives after cancellation | Handled by API-02 as an invalid payment: the transaction is flagged for refund (CBR-72, BF-06). |
| No overdue order | The job completes immediately with no action — this is normal and expected. |

## 16.6 Expire Timed-Out Payment Transactions (JOB-06)

**Trigger:** Scheduled — every minute.

**Purpose:** Closes payment attempts whose 5-minute VNPay link has expired, so the customer can start a new attempt on the same order (CBR-69).

**Related:** UC-22, UC-37, UC-PAY · Rules CBR-69, CBR-70, DBR-41, DBR-42

**Parameters:** None (the 5-minute link validity is fixed — GBR-04).

Processing steps:

1. Find transactions that are still Pending and whose ExpiresAt is before now.
2. For each transaction, in its own transaction, set Status = Expired only if it is still Pending (an IPN or JOB-05 may have handled it first).
3. Write the audit line in the same transaction.
4. The order is not changed: it stays Pending until it is paid or JOB-05 cancels it.

Failure behavior:

| Scenario | System behavior |
|---|---|
| Database unreachable | No retry within the run; the next run (1 minute later) processes the same transactions. The error is logged. |
| Error on one transaction | Rolls back that transaction only; the others are still processed. |
| Transaction already completed | The conditional update changes nothing; the transaction is skipped. |
| No transaction to expire | The job completes immediately with no action — this is normal and expected. |

## 16.7 Remind Managers of Leave Requests Starting Tomorrow (JOB-07)

**Trigger:** Scheduled — every minute.

**Purpose:** Reminds the Managers once about Pending leave requests whose leave starts tomorrow. It never approves or rejects a request (CBR-85).

**Related:** UC-14, UC-15 · Rules CBR-85, DBR-44

**Parameters:** None.

Processing steps:

1. Find Pending leave requests whose FromDate is tomorrow and whose ReminderSentAt is empty.
2. In one transaction, set ReminderSentAt = now on each request that is still Pending and not yet reminded; a request reminded by an earlier run is skipped.
3. If at least one request was marked, send one system notification "Đơn sắp đến ngày nghỉ" to every Manager account: "Có N đơn nghỉ từ ngày mai đang chờ xử lý. Vui lòng kiểm tra và bố trí nhân sự."
4. Requests stay Pending; the Manager may still approve them after the leave has started (BR-86).

Failure behavior:

| Scenario | System behavior |
|---|---|
| Database unreachable | The job does not retry within the run; the next run (1 minute later) processes the same requests. Logs a critical error. |
| Notification cannot be written | The transaction is rolled back, ReminderSentAt stays empty and the next run tries again. |
| No requests due | The job completes immediately with no action — this is normal and expected. |

## 16.8 Remind Customers Without Gym Access (JOB-08)

**Trigger:** Scheduled — daily, right after JOB-04 (last job of the daily chain).

**Purpose:** Reminds a customer the day before a PT session that they cannot use the gym on that day, so they can buy a membership or day pass (or check their freeze) before JOB-04 closes the session as Missed.

**Related:** UC-36, UC-50 · Rules CBR-56, CBR-61, CBR-64, CBR-117, CBR-124

**Parameters:** None.

Processing steps:

1. Find PT sessions still Scheduled whose date is tomorrow, excluding sessions on a date declared closed all day or outside that date's opening hours (the PT must reschedule those — CBR-56, CBR-124).
2. For each session, take the holder and, for a couple package, the partner; keep the people who cannot use the gym tomorrow (CBR-117) and whose account is Active.
3. Group by person: one reminder per person per day, naming the earliest session of that day (time and PT).
4. Skip a person who already has a system notification titled "Nhắc buổi PT ngày dd/MM/yyyy" for that date (the job never sends the same reminder twice).
5. Send one system notification (SentByEmployeeId empty, Audience = Individual). The advice depends on the reason: no package → buy a membership package or a day pass; frozen → contact the Receptionist about the freeze period. Check and insert run in one transaction per person.

Failure behavior:

| Scenario | System behavior |
|---|---|
| Database unreachable | Retries up to 3 times at 5-minute intervals, then logs a critical error and waits for the next daily run. |
| Error for one person | Rolls back that person only; other people are still reminded. |
| Nobody to remind | The job completes immediately with no action — this is normal and expected. |

# I. Section II.17 External API Inventory — Thay toàn bộ

## 17.1 VNPay Payment Request (API-01)

| Field | Value |
|---|---|
| URL | GET redirect to the VNPay payment URL from deployment configuration (vnpay.pay-url; sandbox https://sandbox.vnpayment.vn/paymentv2/vpcpay.html), API version 2.1.0. |
| Purpose | Creates a signed payment request for a Pending online order and redirects the customer to the gateway (UC-22, UC-37, UC-PAY). |
| Request parameters | vnp_Version = 2.1.0, vnp_Command = pay, vnp_TmnCode, vnp_Amount = Transactions.Amount × 100, vnp_CurrCode = VND, vnp_TxnRef, vnp_OrderInfo (ASCII, Vietnamese marks removed), vnp_OrderType = other, vnp_Locale = vn, vnp_ReturnUrl, vnp_IpAddr, vnp_CreateDate and vnp_ExpireDate (yyyyMMddHHmmss, GMT+7), vnp_SecureHash. |
| Transaction reference | vnp_TxnRef = order code without hyphens + "A" + attempt number (for example DH20260001A2). It is stored in Transactions.GatewayReference when the transaction is created and is how API-02 finds the transaction. |
| Validity | vnp_ExpireDate = Transactions.ExpiresAt = the earlier of creation time + 5 minutes and Orders.PaymentDueAt. An expired link is closed by JOB-06; the order stays Pending (CBR-69). |
| Pre-checks | The order is Pending and before its deadline (MSG146), has no Pending transaction (MSG150), the amount is above 0 (MSG151), and the customer profile is not Frozen or Inactive (CBR-120, MSG144). |
| Return URL | GET /payment/vnpay-return (vnpay.return-url, public). The page verifies the signature (MSG154), then shows the transaction status read from the database. If the IPN has not arrived yet it shows "Đang chờ xác nhận thanh toán" (MSG167, MSG168) and never changes any order, transaction or slot. |
| Failure behavior | If the payment URL cannot be built, no transaction is created and MSG152 is shown; the customer can try again while the order is before its deadline. |
| Caller | GMS (outbound, browser redirect). |
| Auth | Terminal code (vnp_TmnCode) + vnp_SecureHash = HMAC-SHA512 (key = vnpay.hash-secret, stored in deployment configuration) over the non-empty vnp_* parameters sorted by name, joined as key=URL-encoded value with "&", as lowercase hex. |

## 17.2 VNPay IPN Webhook (API-02)

| Field | Value |
|---|---|
| URL | GET (POST also accepted): /api/v1/commerce/payment/webhook/vnpay — registered as the IPN URL on the VNPay merchant portal. It is excluded from the sign-in filter and needs no session or CSRF token (NFR-05). |
| Purpose | Receives the official payment result from VNPay and records it as a transaction (UC-PAY). This is the only place an online order is marked as paid. |
| Valid success | vnp_ResponseCode and vnp_TransactionStatus both "00", order still Pending, amount matches: Transactions.Status = Success, GatewayReference kept, CompletedAt = now; Orders.Status = Paid, PaidAt = now; the subscription is created and a held slot becomes Booked with its session Scheduled (CBR-53, BR-80). All of this happens in one database transaction. |
| Invalid payment | Order already Cancelled or amount does not match: the transaction is set to Success with NeedsRefund = 1; an order that is still Pending becomes Cancelled with CancelType = InvalidPayment, its voucher use is returned (BR-44) and its held slot returns to Free (DBR-36); no subscription is created and the transaction appears in the Manager's refund list (CBR-72, BR-47, BF-06). |
| Failed payment | vnp_ResponseCode or vnp_TransactionStatus is not "00": the transaction becomes Failed with FailureReason = the VNPay code and CompletedAt = now; the order stays Pending and its slot stays Held, so the customer can pay again before the deadline (CBR-69); the customer receives MSG166. |
| Repeated call | A second call for a transaction already completed changes nothing and returns RspCode 02. |
| Response to VNPay | JSON {"RspCode","Message"}: 00 Confirm Success (processed, including success with refund); 01 Order not found (no transaction with this vnp_TxnRef); 02 Order already confirmed; 04 Invalid amount (recorded per CBR-72); 97 Invalid signature (nothing recorded); 99 Unknown error. |
| Failure behavior | A database error rolls back the whole callback and returns 99; VNPay calls the IPN again under its own retry policy, and the repeated call is processed normally. |
| Caller | VNPay payment gateway (server-to-server). |
| Auth | HMAC-SHA512 signature validation (vnp_SecureHash, key = vnpay.hash-secret); the endpoint is public but every request must carry a valid signature, otherwise it is rejected with RspCode 97. |

## 17.3 Email Delivery (API-03)

| Field | Value |
|---|---|
| URL | SMTP smtp.gmail.com:587 with STARTTLS required (TLS 1.2 or 1.3); host and port from deployment configuration (mail.smtp.host, mail.smtp.port); client library Jakarta Mail (Eclipse Angus Mail). |
| Purpose | Sends: 6-digit verification codes for sign-up (SignUp, UC-05), profile claim (Claim, UC-Claim), password recovery (Recovery, UC-03), email change (EmailChange — code to the new email) and phone change (PhoneChange — code to the current email) (UC-04, CBR-110); the temporary password and customer code of an account created at the counter (UC-16, UC-20, CBR-31); and a notice to the old email when the login email changes (CBR-29). |
| Message format | Sender display name "Super Gym" (mail.from-name), subject prefix "[Super Gym]", body sent as plain text plus HTML. Subjects: "[Super Gym] Mã xác thực …"; "[Super Gym] Tài khoản và mật khẩu tạm của bạn". |
| Validity | Codes expire after P-15 (default 15 minutes) and can be used once (DBR-03). A temporary password has no expiry; the customer may change it in Account Settings (CBR-31). |
| Timeout | Connection, read and write timeouts are 10 seconds each; a timeout counts as a failed send. |
| Failure behavior | Verification code or temporary password: if sending fails nothing is saved (no code; for a counter account neither the profile nor the account) and the user sees MSG04 "Hệ thống đang bận. Vui lòng thử lại sau ít phút." Old-email notice (CBR-29): the email change is still kept and a warning is logged. |
| Development mode | When no app password is configured, the email is printed to the server console instead of being sent. |
| Caller | GMS (outbound). The system never sends SMS (GBR-15). |
| Auth | SMTP account (mail.username) + app password (mail.app-password), stored in deployment configuration. |

## 17.4 Weather Forecast (API-04)

| Field | Value |
|---|---|
| URL | GET https://api.open-meteo.com/v1/forecast?latitude=…&longitude=…&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,is_day,wind_speed_10m&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max&timezone=Asia/Ho_Chi_Minh&forecast_days=1 |
| Purpose | Shows today's weather at the gym on the PT Dashboard (UC-32, BR-101). |
| Location | From deployment configuration (weather.city, weather.latitude, weather.longitude; default Hà Nội 21.0285, 105.8542). |
| Cache | A successful result is kept in memory for 30 minutes and shared by all PTs; it is never reused after midnight. |
| Timeout and retry | A call that fails, returns a non-200 status or takes more than 8 seconds is not repeated for 5 minutes. |
| Failure behavior | Meanwhile the card shows the last successful reading of the same day marked as not up to date, or is hidden if there is none. The rest of the dashboard always loads (NFR-02). |
| Card style | Rain from a 50% rain chance; hot from 35 °C (feels like 39 °C); extreme heat from 38 °C (feels like 42 °C); strong wind from 35 km/h. |
| Caller | GMS (outbound), when a PT opens the dashboard. |
| Auth | None (public API, no key). |

## 17.5 Exercise Data (API-05)

| Field | Value |
|---|---|
| URL | GET https://wger.de/api/v2/exerciseinfo/?format=json&language__code=en&limit=100&offset=0, then follows the "next" link page by page (at most 30 pages of 100 exercises); timeout 20 seconds per request. |
| Purpose | Fills the local Exercises table used by the Exercise Library (UC-48) for customers and PTs. |
| Data kept | English name and plain-text description (HTML removed), category, main muscles, equipment and the main https image; category, muscle and equipment ids are wger codes, not foreign keys (DBR-71). |
| Refresh | When /customer/exercises or /pt/exercises is opened and the last copy is older than 1 day; there is no scheduled job. Only one refresh runs at a time; the old rows are deleted and the new ones inserted in one database transaction, so a failure leaves the previous copy untouched (BR-92). |
| Failure behavior | If wger cannot be reached, the page keeps showing the previous copy with a "not updated" notice and the next attempt is made after 5 minutes. If no copy exists yet, the page shows MSG306 with a reload action. |
| Images | An exercise without a wger image shows a public-domain illustration (free-exercise-db, stored in the application), chosen by exercise name or muscle group, labelled as an illustration and not repeated on the same page (BR-92). |
| Caller | GMS (outbound), triggered by opening the Exercise Library. |
| Auth | None (public read-only API, no key). Data licence CC BY-SA 4.0 — the source is credited on the page. |

# Z. Việc còn mở — KHÔNG copy vào SRS (dành cho nhóm)

## Z1. Code chưa khớp SRS (SRS giữ nguyên, code cần làm hoặc sửa)

| # | Chỗ | Code hiện tại (develop) | SRS |
|---|---|---|---|
| 1 | JOB-02 | `ExpireMembershipPackagesJob.run()` chỉ có TODO, chưa làm gì | 16.2 |
| 2 | JOB-03 | `ExpireUnusedDayPassesJob.run()` chỉ có TODO, chưa làm gì | 16.3 |
| 3 | JOB-04 | `AutoCloseSessionService.evaluate()` chưa có nhánh GymClosed (ngày đóng cửa, ngoài giờ mở cửa) | 16.4 bước 2a, CBR-56, CBR-124 |
| 4 | Bảo lưu | `FreezeService.applyFreeze()` bước B6 đổi buổi PT trong kỳ thành Missed ngay lúc bảo lưu và trả slot về Free | CBR-62, CBR-68, DBR-38: buổi giữ Scheduled, JOB-04 đóng sau |
| 5 | Màn #16 Operating Hours (UC-11) | Chưa có màn; không chỗ nào ghi vào bảng OperatingHourExceptions | UC-11, CBR-124 |
| 6 | Check-in ngày đóng cửa | `CheckInService` không kiểm tra ngày đóng cửa hoặc giờ mở cửa | GBR-11 |
| 7 | VNPay QueryDR | `VnPayClient.queryTransaction()` và `PaymentCallbackService.handleQueryResult()` có sẵn nhưng không job nào gọi | Không có trong SRS (không thêm API mới) |
| 8 | Bán buổi PT lẻ tại quầy | `CounterSaleService.java:562` từ chối và trả MSG135 "Chưa có màn bán buổi PT lẻ tại quầy" | UC-20, CBR-54, CBR-49, CBR-60; BF-05 A3. MSG135 rút khỏi SRS (E10) |
| 9 | JOB-05 với đơn buổi lẻ tại quầy | `CancelOverdueOnlineOrdersJob` chỉ hủy đơn online | 16.5, CBR-54, Entity Order (E9) |

## Z2. Đã chốt: bán buổi PT lẻ tại quầy — hướng (b)

Nhóm chọn **giữ SRS**: lễ tân vẫn bán buổi PT lẻ trong ngày tại quầy (UC-20, CBR-49, CBR-54, CBR-60, CBR-32). Đây là **chức năng chưa làm** (Z1 dòng 8, 9).

Để SRS không tự mâu thuẫn, file này đã sửa sẵn:
- BF-05 thêm nhánh **A3** (bán tại quầy), sửa Trigger, Pre-condition, E1, E2.
- Entity Order (E9): đơn buổi lẻ tại quầy có hạn thanh toán P-09.
- JOB-05 (bảng 5.3 và mục 16.5) hủy cả đơn buổi lẻ tại quầy quá hạn.
- MSG135 rút khỏi SRS (E10), vì message này nói chức năng không có.

## Z3. Sơ đồ

Phần chữ của BF-01 đến BF-11 ở mục D đã đánh số bước khớp với số task sẽ vẽ trên sơ đồ BPMN.
