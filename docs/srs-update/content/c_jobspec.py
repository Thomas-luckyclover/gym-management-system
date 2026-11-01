# Rewritten parts of section II.13 (Background Job Inventory) — same layout as the SRS.
FAIL_COLS = ["Scenario", "System behavior"]
JOBSPEC = [
 {"title": "13.0 Common behavior for all jobs — replace the first paragraph",
  "paras": ["All times use the Asia/Ho_Chi_Minh time zone (UTC+7). Daily jobs run in sequence at 00:05: JOB-01 → JOB-02 → JOB-03 → JOB-04 → JOB-08. JOB-01 runs before JOB-02 because the frozen days must be added before expiry is evaluated; JOB-08 runs last so that it reads the gym access left after the expiry jobs. JOB-05, JOB-06 and JOB-07 each run every minute."],
  "keep": "Keep the remaining paragraphs of 13.0 unchanged (state-based selection, one transaction per record, no overlap, audit with actor = System, 3 retries at 5-minute intervals for daily jobs)."},
 {"title": "13.4 Auto-Close PT Sessions (JOB-04) — replace the whole section",
  "trigger": "Scheduled — daily, right after JOB-03",
  "purpose": "Closes every PT session whose date has passed and that is still Scheduled, so the session quota and the PT commission are final. The PT never marks attendance (CBR-42).",
  "related": "UC-31, UC-32, UC-36 · Rules CBR-42, CBR-56, CBR-63, CBR-64, CBR-67, CBR-68, CBR-80, CBR-117, DBR-37, DBR-38",
  "params": "None.",
  "steps": [
   "Find sessions still Scheduled whose session date is before today, however many days late.",
   "For each session, lock it, re-check that it is still Scheduled, and evaluate in order, stopping at the first match:",
   "    a. The session date is declared closed all day → Status = Cancelled, CancelType = GymClosed; no commission (CBR-56).",
   "    b. Nobody in the session can use the gym on that date (CBR-117; couple package: neither member) and at least one of them is frozen on that date → Status = Missed, MissedReason = MembershipFrozen.",
   "    c. Nobody in the session can use the gym on that date → Status = Missed, MissedReason = NoMembership.",
   "    d. A check-in exists on that date for the customer (couple package: for either member) → Status = CheckedIn.",
   "    e. Otherwise → Status = Missed, MissedReason = NoCheckIn.",
   "Set IsAutoClosed = 1 and AttendanceMarkedAt = now. The time slot is not released and CommissionAmount is not recalculated; a Missed session earns it like a CheckedIn one (CBR-80).",
   "Write the audit line in the same transaction as the session change.",
   "Log the number of sessions per outcome."],
  "fail": [
   ["Database unreachable", "Retries up to 3 times at 5-minute intervals. If it still fails, logs a critical error and waits for the next daily run. Sessions remain Scheduled until the next successful run."],
   ["Error while processing one session", "Rolls back that session only and logs a warning. Other sessions are still processed. The session stays Scheduled and is processed again in the next run."],
   ["Session already changed by another process", "The re-check finds it no longer Scheduled; the session is skipped."],
   ["No sessions to close", "Job completes immediately with no action — this is normal and expected."]]},
 {"title": "13.7 Remind Managers of Leave Requests Starting Tomorrow (JOB-07) — replace the whole section",
  "trigger": "Scheduled — every minute",
  "purpose": "Reminds the Managers once about Pending leave requests whose leave starts tomorrow. It never approves or rejects a request (CBR-85).",
  "related": "UC-14, UC-15 · Rules CBR-85, DBR-44",
  "params": "None.",
  "steps": [
   "Find Pending leave requests whose FromDate is tomorrow and whose ReminderSentAt is empty.",
   "In one transaction, set ReminderSentAt = now on each request that is still Pending and not yet reminded; a request reminded by an earlier run is skipped.",
   "If at least one request was marked, send one system notification \"Đơn sắp đến ngày nghỉ\" to every Manager account: \"Có N đơn nghỉ từ ngày mai đang chờ xử lý. Vui lòng kiểm tra và bố trí nhân sự.\"",
   "Requests stay Pending; the Manager may still approve them after the leave has started (BR-86)."],
  "fail": [
   ["Database unreachable", "The job does not retry within the run; the next run (1 minute later) processes the same requests. Logs a critical error."],
   ["Notification cannot be written", "The transaction is rolled back, ReminderSentAt stays empty and the next run tries again."],
   ["No requests due", "Job completes immediately with no action — this is normal and expected."]]},
 {"title": "13.8 Remind Customers Without Gym Access (JOB-08) — new section",
  "trigger": "Scheduled — daily, right after JOB-04 (last job of the daily chain)",
  "purpose": "Reminds a customer the day before a PT session that they cannot use the gym on that day, so they can buy a membership or day pass (or check their freeze) before JOB-04 closes the session as Missed.",
  "related": "UC-36, UC-50 · Rules CBR-56, CBR-61, CBR-64, CBR-117",
  "params": "None.",
  "steps": [
   "Find PT sessions still Scheduled whose date is tomorrow, excluding dates declared closed all day (the PT must reschedule those — CBR-56).",
   "For each session, take the holder and, for a couple package, the partner; keep the people who cannot use the gym tomorrow (CBR-117) and whose account is Active.",
   "Group by person: one reminder per person per day, naming the earliest session of that day (time and PT).",
   "Skip a person who already has a system notification titled \"Nhắc buổi PT ngày dd/MM/yyyy\" for that date (the job never sends the same reminder twice).",
   "Send one system notification (SentByEmployeeId empty, Audience = Individual). The advice depends on the reason: no package → buy a membership package or a day pass; frozen → contact the Receptionist about the freeze period. Check and insert run in one transaction per person."],
  "fail": [
   ["Database unreachable", "Retries up to 3 times at 5-minute intervals, then logs a critical error and waits for the next daily run."],
   ["Error for one person", "Rolls back that person only; other people are still reminded."],
   ["Nobody to remind", "Job completes immediately with no action — this is normal and expected."]]},
]

# Section II passages that now contradict the rewritten tables (prototypes kept; only mark or edit the text)
S2_COLS = ["Section (II)", "Current text", "Mark / change to"]
S2 = [
 ["1.3 Forgot Password", "\"An account created at the counter that has no password yet uses the same three steps to set its first password.\"", "Delete the sentence: counter accounts receive a temporary password by email (CBR-31)."],
 ["1.4.1 Profile Tab", "Avatar: max 2 MB", "Avatar: max 5 MB, 200 × 200 – 2000 × 2000 px, centre crop 256 × 256 (DBR-64); add a Remove avatar action."],
 ["1.4.2 Password Tab", "\"đang lỗi màn\"", "Delete the note. New password 8–64 with letters and digits, different from the current one (MSG48), other sessions signed out (CBR-93)."],
 ["5.2.3 Leave Request Approval", "\"A request still Pending after the approval cut-off time is rejected automatically…\"", "Delete the bullet (CBR-85). Add bulk approval (BR-85) and late approval (BR-86)."],
 ["7.2.2 PT Package Sales", "\"After payment, each session can be swapped individually up to the swap limit copied at sale (P-08, default 3…)\"", "Delete the bullet: customers do not swap sessions; the PT reschedules (BR-64)."],
 ["8.1.2 Facility List / 8.1.3 Add / Edit Facility Item", "Field \"Quantity\" (read-only / required ≥ 1)", "Quantity is computed from equipment items (BR-104); 8.1.3 is merged into the master–detail screen #43."],
 ["9.2.1 My Bookings", "Status filter \"All, Scheduled, Completed, Missed, Cancelled\"; cancel action", "Screen redesigned as a view-only package summary (BR-98); session states are Scheduled, CheckedIn, Missed, Cancelled; no cancel."],
 ["9.2.2 My PT Sessions", "Status filter with \"Completed\"; number of swaps still allowed", "Week / month timetable, view only (BR-99); no swap counter."],
 ["9.2.4 Swap Session", "Whole section", "Mark the section Withdrawn (UC-38 withdrawn); keep the heading for traceability."],
 ["9.3.2 Add Time Slot", "Separate screen", "Merged into the weekly PT schedule #54 (BR-94, BR-100)."],
 ["9.3.3 Session Attendance", "Absent checkbox, attendance window, on-behalf swap (\"at most three\"), auto-close to Completed", "Mark the section Withdrawn: attendance is closed by JOB-04 (CBR-42); session handling is on the PT schedule (BR-64 – BR-66)."],
 ["12.1.1 Operational & Revenue Report", "Commission counted for sessions \"Completed, Missed …\"", "Replace Completed with CheckedIn."],
 ["13.0, 13.4, 13.7, 13.8 Background jobs", "JOB-04 with P-07 and Completed; JOB-07 auto-reject with P-20; no JOB-08", "Replace with the texts in section 2 \"II.13 Background Job Inventory\" of this file."],
]
