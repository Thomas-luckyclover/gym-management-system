"""Generate matching editable draw.io and SVG sequence diagrams from one source."""
from pathlib import Path
import html
import textwrap
import xml.etree.ElementTree as ET

OUT = Path(__file__).resolve().parent


class Sequence:
    def __init__(self, name, title, lanes):
        self.name, self.title, self.lanes = name, title, lanes
        self.width = 160 + 275 * (len(lanes) - 1) + 160
        self.x = {key: 160 + i * 275 for i, (key, _) in enumerate(lanes)}
        self.y = 175
        self.events = []
        self.frames = []

    def msg(self, a, b, text, reply=False):
        span = abs(self.x[a] - self.x[b])
        chars = max(24, int((span - 20) / 6.4)) if a != b else 53
        lines = textwrap.wrap(text, chars, break_long_words=False, break_on_hyphens=False)
        height = max(66, 28 + len(lines) * 16)
        self.y += height
        self.events.append(('msg', a, b, lines, reply, self.y))

    def note(self, text):
        lines = textwrap.wrap(text, int((self.width - 200) / 7))
        height = 24 + 17 * len(lines)
        self.y += 18
        self.events.append(('note', lines, self.y, height))
        self.y += height + 22

    def begin(self, kind, guard):
        self.y += 20
        start = self.y
        self.events.append(('guard', kind, guard, self.y))
        self.y += 36
        return start

    def branch(self, guard):
        self.y += 30
        self.events.append(('branch', guard, self.y))
        self.y += 32

    def end(self, start):
        self.y += 30
        self.frames.append((start, self.y))

    def save(self):
        height = self.y + 95
        mx = ET.Element('mxfile', host='app.diagrams.net')
        diagram = ET.SubElement(mx, 'diagram', name=self.title, id=self.name)
        model = ET.SubElement(diagram, 'mxGraphModel', grid='1', gridSize='10', page='1',
                              pageWidth=str(self.width), pageHeight=str(height), pageScale='1')
        root = ET.SubElement(model, 'root')
        ET.SubElement(root, 'mxCell', id='0')
        ET.SubElement(root, 'mxCell', id='1', parent='0')
        counter = 1

        def box(value, x, y, w, h, style):
            nonlocal counter
            counter += 1
            c = ET.SubElement(root, 'mxCell', id=f'c{counter}', value=value,
                              style=style, vertex='1', parent='1')
            ET.SubElement(c, 'mxGeometry', x=str(x), y=str(y), width=str(w), height=str(h), **{'as': 'geometry'})

        def edge(x1, y1, x2, y2, label='', dashed=False, arrow=True, points=None):
            nonlocal counter
            counter += 1
            style = ('html=1;fontSize=12;fontFamily=Arial;labelBackgroundColor=#ffffff;'
                     'verticalAlign=bottom;spacingBottom=5;strokeColor=#334155;'
                     + ('endArrow=open;endFill=0;' if arrow else 'endArrow=none;')
                     + ('dashed=1;dashPattern=5 4;' if dashed else ''))
            c = ET.SubElement(root, 'mxCell', id=f'c{counter}', value=label, edge='1', parent='1', style=style)
            g = ET.SubElement(c, 'mxGeometry', relative='1', **{'as': 'geometry'})
            ET.SubElement(g, 'mxPoint', x=str(x1), y=str(y1), **{'as': 'sourcePoint'})
            ET.SubElement(g, 'mxPoint', x=str(x2), y=str(y2), **{'as': 'targetPoint'})
            if points:
                arr = ET.SubElement(g, 'Array', **{'as': 'points'})
                for px, py in points:
                    ET.SubElement(arr, 'mxPoint', x=str(px), y=str(py))

        esc = html.escape
        svg = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{self.width}" height="{height}" viewBox="0 0 {self.width} {height}">',
               '<defs><marker id="arrow" markerWidth="10" markerHeight="8" refX="9" refY="4" orient="auto"><path d="M1,1 L9,4 L1,7" fill="none" stroke="#334155"/></marker></defs>',
               f'<rect width="{self.width}" height="{height}" fill="white"/>',
               '<g font-family="Arial, sans-serif" fill="#0f172a">']

        def svgtext(x, y, lines, anchor='middle', size=12, bold=False):
            style = 'font-weight="bold"' if bold else ''
            # White stroke keeps messages readable where they cross a lifeline.
            svg.append(f'<text x="{x}" y="{y}" text-anchor="{anchor}" font-size="{size}" {style} stroke="white" stroke-width="4" paint-order="stroke">')
            for i, line in enumerate(lines):
                svg.append(f'<tspan x="{x}" dy="{0 if i == 0 else 16}">{esc(line)}</tspan>')
            svg.append('</text>')

        box(self.title, 45, 16, self.width - 90, 38,
            'text;html=1;fontSize=22;fontStyle=1;align=left;strokeColor=none;fillColor=none;')
        svgtext(45, 42, [self.title], 'start', 22, True)
        for key, label in self.lanes:
            x = self.x[key]
            lines = label.split('\n')
            color = '#dcfce7' if key == 'view' else '#dbeafe'
            box('<br>'.join(map(esc, lines)), x - 120, 80, 240, 58,
                f'rounded=0;whiteSpace=wrap;html=1;fontSize=13;fontStyle=1;fillColor={color};strokeColor=#334155;')
            edge(x, 138, x, height - 35, dashed=True, arrow=False)
            svg.append(f'<rect x="{x-120}" y="80" width="240" height="58" fill="{color}" stroke="#334155"/>')
            svgtext(x, 105 if len(lines) > 1 else 114, lines, size=13, bold=True)
            svg.append(f'<path d="M{x},138 V{height-35}" stroke="#94a3b8" stroke-dasharray="5 5"/>')

        for start, end in self.frames:
            box('', 35, start, self.width - 70, end - start,
                'rounded=0;fillColor=none;strokeColor=#334155;html=1;')
            svg.append(f'<rect x="35" y="{start}" width="{self.width-70}" height="{end-start}" fill="none" stroke="#334155"/>')

        for ev in self.events:
            if ev[0] == 'msg':
                _, a, b, lines, reply, y = ev
                x1, x2 = self.x[a], self.x[b]
                label = '<br>'.join(map(esc, lines))
                if a == b:
                    edge(x1, y-22, x2, y, label, reply, points=[(x1+55,y-22),(x1+55,y)])
                    svg.append(f'<path d="M{x1},{y-22} h55 v22 h-55" fill="none" stroke="#334155" marker-end="url(#arrow)"/>')
                    svgtext(x1+65, y-22-16*(len(lines)-1), lines, 'start')
                else:
                    edge(x1, y, x2, y, label, reply)
                    dash = 'stroke-dasharray="5 4"' if reply else ''
                    svg.append(f'<path d="M{x1},{y} H{x2}" stroke="#334155" {dash} marker-end="url(#arrow)"/>')
                    svgtext((x1+x2)/2, y-10-16*(len(lines)-1), lines)
                # Short activation for each receiver; lifelines remain visible between messages.
                box('', x2-4, y, 8, 22, 'rounded=0;fillColor=#ffffff;strokeColor=#334155;')
                svg.append(f'<rect x="{x2-4}" y="{y}" width="8" height="22" fill="white" stroke="#334155"/>')
            elif ev[0] == 'note':
                _, lines, y, h = ev
                box('<br>'.join(map(esc, lines)), 80, y, self.width-160, h,
                    'rounded=0;whiteSpace=wrap;html=1;fontSize=12;align=left;spacing=10;fillColor=#fff7d6;strokeColor=#c9a227;')
                svg.append(f'<rect x="80" y="{y}" width="{self.width-160}" height="{h}" fill="#fff7d6" stroke="#c9a227"/>')
                svgtext(92, y+19, lines, 'start')
            elif ev[0] == 'guard':
                _, kind, guard, y = ev
                box(kind, 35, y, 65, 26, 'rounded=0;fillColor=#ffffff;fontStyle=1;html=1;')
                box(esc(guard), 110, y+3, self.width-180, 25, 'text;html=1;align=left;fontSize=12;')
                svgtext(45, y+20, [kind], 'start', bold=True)
                svgtext(110, y+20, [guard], 'start')
            elif ev[0] == 'branch':
                _, guard, y = ev
                edge(35, y, self.width-35, y, dashed=True, arrow=False)
                box(esc(guard), 110, y+5, self.width-180, 25, 'text;html=1;align=left;fontSize=12;')
                svg.append(f'<path d="M35,{y} H{self.width-35}" stroke="#334155" stroke-dasharray="6 5"/>')
                svgtext(110, y+23, [guard], 'start')
        svg.append('</g></svg>')
        ET.indent(mx)
        ET.ElementTree(mx).write(OUT / (self.name+'.drawio'), encoding='utf-8', xml_declaration=True)
        (OUT / (self.name+'.svg')).write_text('\n'.join(svg), encoding='utf-8')
        return self.name, self.width, height


def create_leave():
    s = Sequence('SD_Create_Leave_Request_v2', 'Create Leave Request — UC-14 / MBF-07', [
        ('actor','PT / Receptionist'), ('form','LeaveRequestFormServlet'),
        ('preview','LeaveRequestPreviewServlet'), ('svc','StaffLeaveService'),
        ('dao','LeaveRequestDAO'), ('notify','NotificationService'), ('view','staff/\nleave-request-form.jsp')])
    s.note('Simplified code-level sequence. GET/POST base URL: /pt/leave-requests or /receptionist/leave-requests. Authentication and CSRF are checked by the controller; accountId comes from the session.')
    s.msg('actor','form','GET /{role}/leave-requests')
    s.msg('form','svc','getStaffLeaveForm(accountId)')
    s.msg('svc','dao','countOnDuty(conn, date, shiftType, position, 0)')
    s.msg('dao','svc','Current staffing counts', True)
    s.msg('svc','dao','findByEmployeeId(employeeId, 10)')
    s.msg('dao','svc','List<LeaveRequest>', True)
    s.msg('svc','form','ServiceResult<StaffLeaveFormView>', True)
    s.msg('form','view','setAttribute("formView", data); forward("staff/leave-request-form")')
    s.msg('view','actor','HTML form and request history', True)
    f=s.begin('opt','[User changes dates, shift or leave type — preview only, no writes]')
    s.msg('actor','preview','GET /{role}/leave-requests/preview?date=...&toDate=...&shift=...&leaveType=... (form JS)')
    s.msg('preview','svc','previewSubmission(accountId, type, fromDate, toDate, shift)')
    s.msg('svc','svc','calculateSubmission(...): annual balance and Annual/Unpaid allocation')
    s.note('For PT only: findConflictingSessions(...) checks Scheduled sessions; preview includes conflicts and staffing. JS displays the plan and asks for unpaid consent when required.')
    s.msg('svc','preview','ServiceResult<LeaveSubmissionPreview>',True)
    s.msg('preview','actor','JSON: allocation, balance, conflicts and staffing',True)
    s.end(f)
    s.msg('actor','form','POST /{role}/leave-requests: leaveType, leaveDate, toDate, shiftType, reason, acceptUnpaid, expectedAnnualDays, expectedUnpaidDays, csrfToken')
    s.msg('form','svc','submitLeaveRequest(accountId, type, leaveDate, toDate, shift, reason, acceptUnpaid, expectedAnnualDays, expectedUnpaidDays)')
    s.msg('svc','svc','Validate input, staff role and submission cut-off P-12')
    s.note('Transaction: lockDecisionScope(conn), re-check Working position and cut-off. Check hasConflictingLeave(...), Scheduled PT conflicts and annual quota. Recalculate allocation and validate consent/expected values before saving.')
    f=s.begin('alt','[Validation fails / conflicting leave / PT sessions / missing or stale consent]')
    s.msg('svc','form','ServiceResult.fail(...) / failField(...); rollback if transaction started',True)
    s.msg('form','view','Reload form data; preserve input and field errors; forward JSP')
    s.msg('view','actor','Form with error / revised plan',True)
    s.branch('[All checks pass]')
    s.msg('svc','svc','createRequestParts(...): one request or Annual + Unpaid sharing LeaveGroupId')
    loop=s.begin('loop','[For each request part — one or two parts]')
    s.msg('svc','dao','insert(conn, part)')
    s.msg('dao','svc','int leaveRequestId',True)
    s.note('saveRequestParts(...) also writes an audit entry for each part using AuditLogService and the same connection.')
    s.end(loop)
    s.msg('svc','notify','notifyRoleInTransaction(conn, Role.MANAGER, title, content) — once for the submission')
    s.msg('svc','svc','connection.commit()')
    s.msg('svc','form','ServiceResult.ok(newLeaveRequestId)',True)
    s.msg('form','actor','Redirect to the role-specific leave page; success toast',True)
    s.end(f)
    s.note('Any SQL/runtime failure inside the transaction rolls back all parts, audit entries and notifications. Withdrawal and JOB-07 are separate sequences.')
    return s.save()


def process_leave():
    s=Sequence('SD_Process_Leave_Requests_v2','Process Leave Requests — UC-15 / MBF-07',[
        ('actor','Manager'),('list','LeaveRequestListServlet'),('review','LeaveRequestReviewServlet'),
        ('svc','LeaveRequestService'),('dao','LeaveRequestDAO'),('notify','NotificationService'),
        ('view','manager JSP views\nlist / review')])
    s.note('Simplified sequence: Pending list and single-request review. JSP views are leave-request-list.jsp and leave-request-review.jsp. Controller checks session and CSRF for POST.')
    s.msg('actor','list','GET /manager/leave-requests?status=Pending&page=1&pageSize=10')
    s.msg('list','svc','createManagerFilter(keyword, status, position, leaveType, fromDate, toDate)')
    s.msg('svc','list','LeaveRequestFilter',True)
    s.msg('list','svc','getRequestPage(filter, managerAccountId, 1, 10)')
    s.msg('svc','dao','countRequests(filter)')
    s.msg('dao','svc','int totalRequests (grouped rows)',True)
    s.msg('svc','svc','Clamp currentPage; calculate offset = (currentPage - 1) * pageSize')
    s.msg('svc','dao','findRequestPage(filter, 0, 10, managerEmployeeId)')
    s.msg('dao','svc','List<LeaveRequestRow>',True)
    s.note('Service converts rows to LeaveRequestItemView, loads groups/approval summaries and grouped review parts as needed; then wraps items in PageResult.')
    s.msg('svc','list','ServiceResult<PageResult<LeaveRequestItemView>>',True)
    s.msg('list','svc','getStatusCounts(filter)')
    s.msg('svc','dao','getStatusCounts(filter)')
    s.msg('dao','svc','Map<String, Integer>',True)
    s.msg('svc','list','ServiceResult<Map<String, Integer>> (includes All)',True)
    s.msg('list','view','Set requestPage/statusCounts; forward leave-request-list.jsp')
    s.msg('view','actor','List HTML',True)
    s.msg('actor','review','GET /manager/leave-requests/review?id=12')
    s.msg('review','svc','getReviewDetail(12, managerAccountId)')
    s.msg('svc','dao','findReviewDetail(12, managerEmployeeId)')
    s.msg('dao','svc','LeaveReviewDetailView',True)
    s.note('For Pending requests, call LeaveApprovalService.review(conn, request, workingPosition): annual quota, minimum staffing and Scheduled PT sessions. Attach checks, staffing rows, blocking message and group to the view.')
    s.msg('svc','review','ServiceResult<LeaveReviewDetailView>',True)
    s.msg('review','view','Set item; forward leave-request-review.jsp')
    s.msg('view','actor','Review detail page',True)
    s.msg('actor','review','POST /manager/leave-requests/review: action, leaveRequestId=12, lateApprovalReason/rejectReason, csrfToken')
    f=s.begin('alt','[action == approve]')
    s.msg('review','svc','approveRequest(managerAccountId, 12, lateApprovalReason)')
    s.note('Begin transaction; lockDecisionScope and validateDecision: Working Manager + existing Pending request. Late approval needs a 5–500 character reason. validateApproval delegates to LeaveApprovalService.review(...).')
    s.msg('svc','dao','approve(conn, 12, managerEmployeeId)')
    s.msg('dao','svc','boolean isApproved',True)
    s.note('If allowed and updated: auditLogService.logInTransaction(...). If a validation fails or update returns false: rollback and return a failure, not the success path below.')
    s.msg('svc','notify','notifyEmployeeInTransaction(conn, requesterEmployeeId, title, content)')
    s.msg('svc','svc','connection.commit()')
    s.msg('svc','review','ServiceResult.ok(null)',True)
    s.msg('review','actor','Success toast; redirect to validated returnUrl or leave list',True)
    s.branch('[action == reject]')
    s.msg('review','svc','rejectRequest(managerAccountId, 12, rejectReason)')
    s.note('Validate reason 5–500 characters and Working Manager. Begin transaction, lock and re-read Pending request; reject does not require approval quota/staffing checks.')
    s.msg('svc','dao','reject(conn, 12, managerEmployeeId, rejectReason)')
    s.msg('dao','svc','boolean isRejected',True)
    s.note('After successful update: write audit log in the same transaction. Validation/update failure returns a failure after rollback.')
    s.msg('svc','notify','notifyEmployeeInTransaction(conn, requesterEmployeeId, title, content)')
    s.msg('svc','svc','connection.commit()')
    s.msg('svc','review','ServiceResult.ok(null)',True)
    s.msg('review','actor','Success toast; redirect to validated returnUrl or leave list',True)
    s.end(f)
    s.note('POST failure: controller preserves reason/errors and reloads the review JSP. SQL/runtime transaction errors roll back. Bulk approval is described separately below; it is not one UPDATE for every selected ID.')
    f=s.begin('opt','[action == approveMany from list; 1–50 selected IDs]')
    s.msg('actor','review','POST: action=approveMany, leaveRequestIds[], csrfToken, returnUrl')
    s.msg('review','svc','approveRequests(managerAccountId, requestIds)')
    s.note('Validate IDs and remove duplicates. For each ID call approveRequest(managerAccountId, id): one transaction per request; collect approvedCount and failedRequests. Late approval without a reason fails for that request.')
    s.msg('svc','review','ServiceResult<LeaveBulkApprovalResult>',True)
    s.msg('review','actor','Store bulk result in session; redirect to list for one-time result display',True)
    s.end(f)
    return s.save()


def delete_member():
    s=Sequence('SD_Process_Member_Deletion_Requests_v2','Process Member Deletion Requests — UC-17 / MBF-09',[
        ('actor','Manager'),('ctl','MemberDeletionReviewServlet'),('svc','CustomerDeletionService'),
        ('dao','CustomerDeletionRequestDAO'),('customer','CustomerDAO'),
        ('notify','NotificationService'),('view','manager/\nmember-deletion-review.jsp')])
    s.note('Scope: Manager review of an existing Pending deletion request. Receptionist request/immediate deletion (UC-16) is a separate sequence. Signed-in POST requires a valid CSRF token.')
    s.msg('actor','ctl','GET /manager/member-deletions/review?id=7')
    s.msg('ctl','svc','getReviewDetail(accountId, 7)')
    s.msg('svc','dao','findReviewDetail(7, accountId)')
    s.msg('dao','svc','CustomerDeletionReviewView',True)
    s.msg('svc','customer','findActiveSubscriptionsByCustomer(conn, customerId)')
    s.msg('customer','svc','Active package summaries',True)
    s.msg('svc','customer','findScheduledSessionsByCustomer(conn, customerId)')
    s.msg('customer','svc','Scheduled session summaries',True)
    s.msg('svc','ctl','ServiceResult<CustomerDeletionReviewView>',True)
    s.msg('ctl','view','Set review and csrfToken; forward member-deletion-review.jsp')
    s.msg('view','actor','Review detail page',True)
    s.msg('actor','ctl','POST /manager/member-deletions/review: action, id=7, rejectReason, csrfToken')
    f=s.begin('alt','[action == approve]')
    s.msg('ctl','svc','approveDeletion(accountId, 7)')
    s.note('Check actor permission. Begin transaction; read request with lock and require Pending. Reject actions on the actor\'s own customer profile (CBR-115).')
    s.msg('svc','dao','findById(conn, 7, true)')
    s.msg('dao','svc','CustomerDeletionRequest',True)
    s.msg('svc','customer','findById(conn, customerId, true)')
    s.msg('customer','svc','Customer (must exist and not be Inactive)',True)
    s.msg('svc','dao','approve(conn, 7, managerEmployeeId)')
    s.msg('dao','svc','boolean approved',True)
    s.msg('svc','svc','deactivateCustomer(conn, customer)')
    s.msg('svc','customer','softDeleteCustomer(conn, customerId)')
    s.msg('customer','svc','boolean deleted',True)
    s.note('deactivateCustomer also calls PersonDAO.releasePersonContactIfNoActiveRoles(...) and AccountDAO.deactivateAccountIfNoActiveRoles(...), then finds the account ID. Keep contact/account active while another active role remains. Preserve orders, packages, check-ins and sessions.')
    s.note('Write AuditLogService.logInTransaction(...) using the same connection.')
    s.msg('svc','notify','notifyEmployeeInTransaction(conn, requestedByEmployeeId, title, content)')
    s.msg('svc','svc','connection.commit()')
    s.note('After successful commit only: SessionRegistryListener.invalidateSessionsOfAccount(customerAccountId, null), if an account exists.')
    s.msg('svc','ctl','ServiceResult.ok(MSG292_DELETION_APPROVED)',True)
    s.msg('ctl','actor','Redirect /manager/members?tab=pending-deletions; success toast',True)
    s.branch('[action == reject]')
    s.msg('ctl','svc','rejectDeletion(accountId, 7, rejectReason)')
    s.note('Validate reason 5–500 characters and actor permission. Begin transaction; lock/read request, require Pending and enforce own-profile guard.')
    s.msg('svc','dao','findById(conn, 7, true)')
    s.msg('dao','svc','CustomerDeletionRequest',True)
    s.msg('svc','dao','reject(conn, 7, managerEmployeeId, rejectReason)')
    s.msg('dao','svc','boolean rejected',True)
    s.note('Write audit log in the same transaction; read customer code for the notification. Customer profile and account remain unchanged.')
    s.msg('svc','notify','notifyEmployeeInTransaction(conn, requestedByEmployeeId, title, content)')
    s.msg('svc','svc','connection.commit()')
    s.msg('svc','ctl','ServiceResult.ok(MSG293_DELETION_REJECTED)',True)
    s.msg('ctl','actor','Redirect /manager/members?tab=pending-deletions; success toast',True)
    s.end(f)
    s.note('Failure branches do not continue into the success messages: rollback where a transaction is open; return ServiceResult.fail/failField. Controller preserves rejectReason and errors, then reloads the review page. No session invalidation on rejection or rollback.')
    return s.save()


if __name__ == '__main__':
    for result in [create_leave(), process_leave(), delete_member()]:
        print(*result)
