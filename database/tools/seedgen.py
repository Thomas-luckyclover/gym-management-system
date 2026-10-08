# -*- coding: utf-8 -*-
"""Sinh 2_Seed_Data.sql cho GMS v26.

- Lịch sử cố định quanh MỐC = Thứ Bảy 03/10/2026 (tháng 6 → tháng 11/2026).
- Dữ liệu "sống" (mã xác thực, khóa tạm, đơn online đang chờ, ca đang mở, gói ngày hôm nay)
  tính theo giờ chạy script (@Now / @Today) nên chạy lại lúc nào cũng test được.
- Mọi ràng buộc nghiệp vụ được assert ngay trong lúc sinh.
"""
import random
from datetime import date, datetime, time, timedelta
from dateutil.relativedelta import relativedelta

SEED = date(2026, 10, 3)            # mốc dữ liệu (Thứ Bảy)
FUTURE_FROM = date(2026, 10, 5)     # buổi PT từ ngày này trở đi = Scheduled
HASH = '$2a$10$o22fNyHpwvbuAj8q.LKCI.KMakVydfKblTjnBh7rgiUTrt55ZnXmu'  # Gms@2026
VAT = 10
SESSION_MIN = 90
rng = random.Random(2026)


# ============================================================ giá trị "sống"
class Live:
    """Giá trị tính theo giờ chạy: base 'now' (phút lệch) hoặc 'today' (ngày lệch, phút trong ngày)."""
    def __init__(self, base, days=0, minutes=0, kind='dt'):
        self.base, self.days, self.minutes, self.kind = base, days, minutes, kind

    def eval(self, now):
        if self.base == 'now':
            v = now + timedelta(days=self.days, minutes=self.minutes)
        else:
            v = datetime.combine(now.date(), time(0)) + timedelta(days=self.days, minutes=self.minutes)
        return v.date() if self.kind == 'date' else v

    def sql(self):
        if self.base == 'now':
            e = '@Now'
            if self.days: e = f'DATEADD(DAY, {self.days}, {e})'
            if self.minutes: e = f'DATEADD(MINUTE, {self.minutes}, {e})'
            return f'CAST({e} AS DATE)' if self.kind == 'date' else e
        if self.kind == 'date':
            return f'DATEADD(DAY, {self.days}, @Today)' if self.days else '@Today'
        e = 'CAST(@Today AS DATETIME2(0))'
        if self.days: e = f'DATEADD(DAY, {self.days}, {e})'
        if self.minutes: e = f'DATEADD(MINUTE, {self.minutes}, {e})'
        return e


def now_(m=0): return Live('now', minutes=m)
def today_dt(day=0, hh=0, mm=0): return Live('today', day, hh * 60 + mm)
def today_d(day=0): return Live('today', day, kind='date')


# ============================================================ kho dữ liệu
TABLES = ['Persons', 'Employees', 'Customers', 'Accounts', 'VerificationCodes', 'Packages', 'Vouchers',
          'Shifts', 'Orders', 'Transactions', 'Subscriptions', 'FreezeRecords', 'CheckIns',
          'CustomerDeletionRequests', 'TimeSlots', 'Sessions', 'SessionMoveLogs', 'LeaveRequests',
          'Facilities', 'MaintenanceRequests', 'StaffAttendances', 'PrivateNotes', 'TrainingPlans',
          'Feedbacks', 'BlogPosts', 'Notifications', 'NotificationRecipients', 'SystemConfigs',
          'OperatingHourExceptions', 'AuditLogs']
DB = {t: [] for t in TABLES}
NEXT = {t: 1 for t in TABLES}


def add(table, row, note=None, rid=None):
    pk = PK[table]
    if rid is None:
        rid = NEXT[table]
    NEXT[table] = max(NEXT[table], rid + 1)
    row = {pk: rid, **row}
    row['_note'] = note
    DB[table].append(row)
    return rid


PK = {'Persons': 'PersonId', 'Employees': 'EmployeeId', 'Customers': 'CustomerId', 'Accounts': 'AccountId',
      'VerificationCodes': 'VerificationCodeId', 'Packages': 'PackageId', 'Vouchers': 'VoucherId',
      'Shifts': 'ShiftId', 'Orders': 'OrderId', 'Transactions': 'TransactionId',
      'Subscriptions': 'SubscriptionId', 'FreezeRecords': 'FreezeRecordId', 'CheckIns': 'CheckInId',
      'CustomerDeletionRequests': 'CustomerDeletionRequestId', 'TimeSlots': 'TimeSlotId',
      'Sessions': 'SessionId', 'SessionMoveLogs': 'SessionMoveLogId', 'LeaveRequests': 'LeaveRequestId',
      'Facilities': 'FacilityId', 'MaintenanceRequests': 'MaintenanceRequestId',
      'StaffAttendances': 'StaffAttendanceId', 'PrivateNotes': 'PrivateNoteId',
      'TrainingPlans': 'TrainingPlanId', 'Feedbacks': 'FeedbackId', 'BlogPosts': 'BlogPostId',
      'Notifications': 'NotificationId', 'NotificationRecipients': 'NotificationRecipientId',
      'SystemConfigs': 'SystemConfigId', 'OperatingHourExceptions': 'OperatingHourExceptionId',
      'AuditLogs': 'AuditLogId'}


def get(table, rid):
    for r in DB[table]:
        if r[PK[table]] == rid:
            return r
    raise KeyError((table, rid))


def dt_(y, m, d, hh=0, mm=0):
    return datetime(y, m, d, hh, mm)


def d_(y, m, d):
    return date(y, m, d)


# ============================================================ tham số + ngày đặc biệt
CLOSED = {}   # date -> declared_at (ngày gym nghỉ cả ngày)


def is_open_day(d):
    return d not in CLOSED


# ============================================================ nhân sự
def person(pid, name, dob, phone, email, created, avatar=None, released=0, note=None):
    return add('Persons', dict(FullName=name, DateOfBirth=dob, Phone=phone, Email=email, Avatar=avatar,
                               IsReleased=released, CreatedAt=created), note, pid)


def employee(eid, pid, code, pos, hire, rate=None, spec=None, yrs=None, status='Working', deleted=None,
             reason=None, note=None):
    return add('Employees', dict(PersonId=pid, EmployeeCode=code, Position=pos, HireDate=hire,
                                 CommissionRate=rate, Specialty=spec, YearsOfExperience=yrs, Status=status,
                                 DeletedAt=deleted, ResignationReason=reason), note, eid)


def customer(cid, pid, code, joined, ctype='Self', photo=None, guardian=None, status=None, deleted=None,
             note=None):
    g = guardian or (None, None, None, None)
    return add('Customers', dict(PersonId=pid, CustomerCode=code, CheckInPhoto=photo, CustomerType=ctype,
                                 GuardianName=g[0], GuardianRelationship=g[1], GuardianPhone=g[2],
                                 GuardianPhone2=g[3], Status=status or 'New', JoinedAt=joined,
                                 DeletedAt=deleted), note, cid)


def account(aid, pid, created, pw=HASH, status='Active', failed=0, locked=None, last=None, note=None):
    return add('Accounts', dict(PersonId=pid, PasswordHash=pw, Status=status, FailedLoginCount=failed,
                                LockedUntil=locked, LastLoginAt=last, CreatedAt=created), note, aid)


def emp_person(eid):
    return get('Employees', eid)['PersonId']


def cust_person(cid):
    return get('Customers', cid)['PersonId']


def account_of_person(pid):
    for a in DB['Accounts']:
        if a['PersonId'] == pid:
            return a['AccountId']
    return None


# ============================================================ ca làm & lễ tân trực
SHIFT_IDX = {}
LIVE_SHIFT_MORNING = None


def duty_receptionist(d, shift_type):
    wd = d.weekday()
    if wd == 6:
        return 9 if shift_type == 'Morning' else 4      # CN: Phương sáng, Lâm chiều
    if wd == 5:
        return 3 if shift_type == 'Morning' else 11     # T7: Tâm sáng, Hoa chiều
    return 3 if shift_type == 'Morning' else 4          # T2–T6: Tâm sáng, Lâm chiều


def shift_for(when):
    if isinstance(when, Live):
        return LIVE_SHIFT_MORNING
    d = when.date()
    assert is_open_day(d), f'không có ca vào ngày nghỉ {d}'
    st = 'Morning' if when.time() < time(13, 0) else 'Afternoon'
    key = (d, st)
    if key not in SHIFT_IDX:
        rec = duty_receptionist(d, st)
        open_at = datetime.combine(d, time(5, 30) if st == 'Morning' else time(13, 0))
        close_at = datetime.combine(d, time(13, 0) if st == 'Morning' else time(22, 0))
        SHIFT_IDX[key] = add('Shifts', dict(WorkDate=d, ShiftType=st, OpenedByEmployeeId=rec,
                                            ClosedByEmployeeId=rec, Status='Closed', OpenedAt=open_at,
                                            ClosedAt=close_at, ExpectedCash=0, CountedCash=0,
                                            VarianceReason=None))
    return SHIFT_IDX[key]


def shift_receptionist(shift_id):
    return get('Shifts', shift_id)['OpenedByEmployeeId']


# ============================================================ đơn / giao dịch / quyền dùng
ORDER_SEQ = [0]


def vat_of(payable):
    return (payable * VAT * 2 + (100 + VAT)) // (2 * (100 + VAT))   # làm tròn nửa lên


def order(holder, pkg_id, created, channel, status='Paid', paid=None, partner=None, voucher=None,
          cancel=None, method='Cash', fail_first=False, note=None, created_by=None, gateway=None,
          refund=None, pay_delay=2):
    """Lập đơn + giao dịch. cancel = (type, at, reason). refund = 'open' | ('done', at, emp, note)."""
    pkg = get('Packages', pkg_id)
    price = pkg['Price']
    disc = 0
    if voucher:
        v = get('Vouchers', voucher)
        if not isinstance(created, Live):
            assert v['StartDate'] <= created.date() <= v['EndDate'], f'CBR-73 voucher {v["VoucherCode"]} ngoài hạn {created}'
        assert pkg['PackageType'] in (('Membership', 'DayPass') if v['ApplicableScope'] == 'MembershipAndDayPass'
                                      else ('Membership',)), 'CBR-76'
        assert price >= v['MinOrderValue'], 'CBR-73 min order'
        disc = price * v['DiscountValue'] // 100 if v['DiscountType'] == 'Percent' else v['DiscountValue']
        disc = min(disc, price)
    payable = price - disc
    ORDER_SEQ[0] += 1
    code = f'DH-2026-{ORDER_SEQ[0]:04d}'
    shift_id = emp = due = None
    if channel == 'Counter':
        shift_id = shift_for(created)
        emp = created_by or shift_receptionist(shift_id)
        if not isinstance(created, Live):
            assert emp_person(emp) != cust_person(holder), 'CBR-115: lễ tân tự bán cho mình'
    else:
        due = created + timedelta(minutes=20) if not isinstance(created, Live) else Live('now', minutes=created.minutes + 20)
    if status in ('Paid',) and paid is None:
        paid = created + timedelta(minutes=pay_delay) if not isinstance(created, Live) else Live(created.base, created.days, created.minutes + pay_delay)
    paid_at = paid if status == 'Paid' or (cancel and cancel[0] == 'CancelledByReceptionist' and paid) else None
    oid = add('Orders', dict(
        OrderCode=code, HolderCustomerId=holder, PartnerCustomerId=partner, PackageId=pkg_id, VoucherId=voucher,
        CreatedByEmployeeId=emp, ShiftId=shift_id, Channel=channel, PackageNameSnapshot=pkg['PackageName'],
        PackageDescriptionSnapshot=pkg['Description'], PackageBenefitsSnapshot=pkg['Benefits'],
        UnitPriceSnapshot=price, DiscountAmount=disc, VatRateSnapshot=VAT, VatAmount=vat_of(payable),
        Status=status, PaymentDueAt=due, CreatedAt=created, PaidAt=paid_at,
        CancelType=cancel[0] if cancel else None, CancelledAt=cancel[1] if cancel else None,
        CancelReason=cancel[2] if cancel else None), note)
    o = get('Orders', oid)
    o['_payable'] = payable
    # ----- giao dịch
    if channel == 'Counter':
        if paid_at is not None:
            tx(oid, payable, method, 'Success', paid_at, received=emp,
               ref=gateway or (f'BANK-{paid_at:%Y%m%d}-{oid:03d}' if method == 'Transfer' and not isinstance(paid_at, Live) else None))
    else:
        t0 = created + timedelta(minutes=1) if not isinstance(created, Live) else Live('now', minutes=created.minutes + 1)
        if fail_first:
            tx(oid, payable, 'OnlineGateway', 'Failed', t0, ref=f'VNP-{oid:05d}-1', fail='Thẻ bị từ chối')
            t0 = t0 + timedelta(minutes=2)
        if status == 'Paid':
            tx(oid, payable, 'OnlineGateway', 'Success', t0, ref=f'VNP-{oid:05d}-2')
        elif status == 'Pending':
            tx(oid, payable, 'OnlineGateway', 'Pending', t0, ref=f'VNP-{oid:05d}-2')
        elif cancel and cancel[0] == 'PaymentTimeout':
            tx(oid, payable, 'OnlineGateway', 'Expired', t0, ref=f'VNP-{oid:05d}-2')
        elif cancel and cancel[0] == 'InvalidPayment':
            tx(oid, payable, 'OnlineGateway', 'Success', t0 + timedelta(minutes=25), ref=f'VNP-{oid:05d}-2',
               refund=refund or 'open', expires=t0 + timedelta(minutes=5))
    return oid


def tx(oid, amount, method, status, created, received=None, ref=None, fail=None, refund=None, expires=None):
    online = method == 'OnlineGateway'
    if online and expires is None:
        expires = created + timedelta(minutes=5) if not isinstance(created, Live) else Live('now', minutes=created.minutes + 5)
    completed = None
    if status in ('Success', 'Failed'):
        completed = created + timedelta(minutes=1) if (online and not isinstance(created, Live)) else created
    r = dict(OrderId=oid, Amount=amount, PaymentMethod=method, GatewayReference=ref,
             ReceivedByEmployeeId=received if not online else None, Status=status, FailureReason=fail,
             NeedsRefund=1 if refund else 0, RefundedAt=None, RefundedByEmployeeId=None, RefundNote=None,
             CreatedAt=created, ExpiresAt=expires if online else None, CompletedAt=completed)
    if isinstance(refund, tuple):
        r.update(RefundedAt=refund[1], RefundedByEmployeeId=refund[2], RefundNote=refund[3])
    return add('Transactions', r)


def end_of(start, pkg):
    if pkg['DurationUnit'] == 'Day':
        return start
    return start + relativedelta(months=pkg['DurationValue'])        # GBR-03: ngày cuối tính cả


def subscription(oid, start, status=None, pt=None, fixed=None, end=None, note=None):
    o = get('Orders', oid)
    pkg = get('Packages', o['PackageId'])
    t = pkg['PackageType']
    end = end or (start if t == 'SingleSession' else end_of(start, pkg))
    r = dict(OrderId=oid, CustomerId=o['HolderCustomerId'], SubscriptionType=t, PtEmployeeId=pt,
             StartDate=start, EndDate=end, FixedStartTime=fixed,
             GeneratedSessionCount=1 if t == 'SingleSession' else None, SwapsUsed=0,
             MaxSwapsSnapshot=3 if t in ('PTSingle', 'PTCouple') else None, FreezeUsed=0,
             MaxFreezesSnapshot=1 if t == 'Membership' else None, Status=status or 'Active')
    return add('Subscriptions', r, note)


def subs_of(cid, types=('Membership',)):
    return [s for s in DB['Subscriptions'] if s['CustomerId'] == cid and s['SubscriptionType'] in types
            and s['Status'] != 'Cancelled']


FREEZES = {}  # sub_id -> (start, end_effective)


def membership_valid(cid, d, allow_daypass=False):
    """Có gói tập (không bảo lưu) phủ ngày d không — CBR-58/59/64."""
    for s in subs_of(cid, ('Membership',)):
        if isinstance(s['StartDate'], Live):
            continue
        if s['StartDate'] <= d <= s['EndDate']:
            fz = FREEZES.get(s['SubscriptionId'])
            if fz and fz[0] <= d <= fz[1]:
                continue
            return s['SubscriptionId']
    if allow_daypass:
        for s in subs_of(cid, ('DayPass',)):
            if not isinstance(s['StartDate'], Live) and s['StartDate'] == d:
                return s['SubscriptionId']
    return None


# ============================================================ check-in
CHECKIN_KEYS = set()


def checkin(cid, when, photo=True, sub=None):
    d = when.date()
    assert is_open_day(d)
    sid = sub or membership_valid(cid, d, allow_daypass=True)
    assert sid, f'BR-27: khách {cid} không có gói hợp lệ ngày {d}'
    key = (cid, when)
    if key in CHECKIN_KEYS:
        return
    CHECKIN_KEYS.add(key)
    st = 'Morning' if when.time() < time(13, 0) else 'Afternoon'
    shift_for(when)
    s = get('Subscriptions', sid)
    add('CheckIns', dict(SubscriptionId=sid, VerifiedByEmployeeId=duty_receptionist(d, st), CheckedInAt=when,
                         PhotoVerified=0 if s['SubscriptionType'] == 'DayPass' else 1))


def checked_in_on(cid, d):
    return any(c == cid and w.date() == d for c, w in CHECKIN_KEYS if not isinstance(w, Live))


# ============================================================ lịch PT (slot không chồng giờ — DBR-33)
BUSY = {}  # (pt, date) -> list[(start_min, end_min)]


def _mins(t):
    return t.hour * 60 + t.minute


def slot(pt, d, start, status='Free', held_order=None, hold_exp=None, created=None, check=True):
    """Tạo slot 90' (P-06). d có thể là Live (slot "sống")."""
    st = time(*start) if isinstance(start, tuple) else start
    end = (datetime.combine(date(2000, 1, 1), st) + timedelta(minutes=SESSION_MIN)).time()
    if check and not isinstance(d, Live):
        key = (pt, d)
        s0, e0 = _mins(st), _mins(st) + SESSION_MIN
        for a, b in BUSY.get(key, []):
            assert not (s0 < b and a < e0), f'DBR-33 chồng giờ PT {pt} {d} {st}'
        assert time(5, 30) <= st and e0 <= 22 * 60, 'GBR-11 giờ mở cửa'
        BUSY.setdefault(key, []).append((s0, e0))
    if created is None:
        created = datetime.combine(d - timedelta(days=10), time(21, 0)) if not isinstance(d, Live) else now_(-60 * 24 * 3)
    return add('TimeSlots', dict(PtEmployeeId=pt, SlotDate=d, StartTime=st, EndTime=end, Status=status,
                                 HeldByOrderId=held_order, HoldExpiresAt=hold_exp, CreatedAt=created))


def free_at(pt, d, start):
    s0 = _mins(time(*start))
    return all(not (s0 < b and a < s0 + SESSION_MIN) for a, b in BUSY.get((pt, d), []))


def pt_rate(eid):
    return get('Employees', eid)['CommissionRate']


def session(sub_id, slot_id, pt, status, value, **kw):
    r = dict(SubscriptionId=sub_id, TimeSlotId=slot_id, PtEmployeeId=pt, Status=status, SessionValue=value,
             CommissionAmount=round(value * pt_rate(pt) / 100), AttendanceMarkedAt=None, IsAutoClosed=0,
             MissedReason=None, CancelType=None, CancelReason=None, CancelledAt=None,
             CancelledByAccountId=None, RefundAgreed=0)
    r.update(kw)
    if status in ('CheckedIn', 'Missed'):
        day = get('TimeSlots', slot_id)['SlotDate']
        r['AttendanceMarkedAt'] = datetime.combine(day + timedelta(days=1), time(0, 5))
        r['IsAutoClosed'] = 1
    if status == 'Missed' and not any(s['TimeSlotId'] == slot_id and s['Status'] in ('Scheduled', 'CheckedIn')
                                      for s in DB['Sessions']):
        get('TimeSlots', slot_id)['Status'] = 'Free'
    return add('Sessions', r)


def pt_package(oid, pt, start, fixed, events=None, freeze=None, holder_checkin_rate=0.85, note=None):
    """Sinh gói PT theo kỳ hạn (BR-40): mỗi T2–T6 trong kỳ, bỏ ngày nghỉ khai báo TRƯỚC khi bán (CBR-56).
    events: {date: ('cancel', type, reason) | ('missed', reason) | ('move', movetype, new_date, new_time, new_pt, reason)}"""
    events = events or {}
    o = get('Orders', oid)
    holder, partner = o['HolderCustomerId'], o['PartnerCustomerId']
    sold = o['PaidAt']
    for c in [holder] + ([partner] if partner else []):
        assert membership_valid(c, sold.date()), 'CBR-59/BR-37: phải có gói tập lúc bán'
        assert relativedelta(sold.date(), get('Persons', cust_person(c))['DateOfBirth']).years >= 10, 'CBR-13/14'
        assert cust_person(c) != emp_person(pt), 'CBR-115'
    sid = subscription(oid, start, pt=pt, fixed=time(*fixed), note=note)
    sub = get('Subscriptions', sid)
    end = sub['EndDate']
    dates = []
    d = start
    while d <= end:
        if d.weekday() < 5 and not (d in CLOSED and CLOSED[d] < sold):
            dates.append(d)
        d += timedelta(days=1)
    sub['GeneratedSessionCount'] = len(dates)
    value = round(o['_payable'] / len(dates))
    pt_acc = account_of_person(emp_person(pt))
    holder_acc = account_of_person(cust_person(holder))
    swaps = 0
    for d in dates:
        ev = events.get(d)
        at = datetime.combine(d, time(*fixed))
        end_at = at + timedelta(minutes=SESSION_MIN)
        cur_pt = pt
        if ev and ev[0] == 'move':
            _, mtype, nd, nt, npt, reason = ev
            old_slot = slot(pt, d, fixed, 'Free')
            npt = npt or pt
            new_slot = slot(npt, nd, nt, 'Booked' if nd >= FUTURE_FROM else 'Booked')
            at = datetime.combine(nd, time(*nt))
            end_at = at + timedelta(minutes=SESSION_MIN)
            cur_pt = npt
            if mtype in ('CustomerSwap', 'PtOnBehalfSwap'):
                swaps += 1
            requester = holder_acc if mtype == 'CustomerSwap' else account_of_person(emp_person(pt))
            sess_id = _session_status(sid, new_slot, cur_pt, value, nd, at, end_at, holder, partner, freeze, holder_checkin_rate)
            add('SessionMoveLogs', dict(SessionId=sess_id, MoveType=mtype, OldTimeSlotId=old_slot,
                                        NewTimeSlotId=new_slot, OldPtEmployeeId=pt, NewPtEmployeeId=npt,
                                        RequestedByAccountId=requester, Reason=reason,
                                        MovedAt=datetime.combine(min(d, nd) - timedelta(days=2), time(20, 15))))
            continue
        if d in CLOSED and CLOSED[d] >= sold:
            sl = slot(pt, d, fixed, 'Free')
            session(sid, sl, pt, 'Cancelled', value, CancelType='GymClosed',
                    CancelReason='Phòng tập nghỉ cả ngày theo lịch Manager khai báo sau khi bán gói',
                    CancelledAt=CLOSED[d])
            continue
        if ev and ev[0] == 'cancel':
            _, ctype, reason = ev
            sl = slot(pt, d, fixed, 'Free')
            by = pt_acc if ctype == 'ByPt' else holder_acc
            if ctype == 'PackageExpired':
                session(sid, sl, pt, 'Cancelled', value, CancelType='PackageExpired', CancelReason=reason,
                        CancelledAt=datetime.combine(end + timedelta(days=1), time(0, 5)))
            else:
                session(sid, sl, pt, 'Cancelled', value, CancelType=ctype, CancelReason=reason,
                        CancelledAt=at - timedelta(hours=20), CancelledByAccountId=by,
                        RefundAgreed=1 if ctype == 'ByPt' else 0)
            continue
        if ev and ev[0] == 'missed':
            sl = slot(pt, d, fixed, 'Booked')
            session(sid, sl, pt, 'Missed', value, MissedReason=ev[1],
                    IsAutoClosed=1)
            continue
        sl_status = 'Booked'
        if freeze and freeze[0] <= d <= freeze[1]:
            sl_status = 'Free'
        sl = slot(pt, d, fixed, sl_status)
        _session_status(sid, sl, pt, value, d, at, end_at, holder, partner, freeze, holder_checkin_rate)
    sub['SwapsUsed'] = swaps
    sub['Status'] = 'Expired' if end < SEED else 'Active'
    return sid


def _session_status(sid, sl, pt, value, d, at, end_at, holder, partner, freeze, rate):
    people = [holder] + ([partner] if partner else [])
    if freeze and freeze[0] <= d <= freeze[1]:
        others_ok = partner and membership_valid(partner, d)
        if not others_ok:
            return session(sid, sl, pt, 'Missed', value, MissedReason='MembershipFrozen', IsAutoClosed=0,
                           AttendanceMarkedAt=None)
    if d >= FUTURE_FROM:
        return session(sid, sl, pt, 'Scheduled', value)
    valid = [c for c in people if membership_valid(c, d)]
    if not valid:
        return session(sid, sl, pt, 'Missed', value, MissedReason='NoMembership', IsAutoClosed=1)
    if rng.random() > rate:
        return session(sid, sl, pt, 'Missed', value, MissedReason='NoCheckIn', IsAutoClosed=1)
    for c in valid[:1] if rng.random() < 0.5 else valid:
        checkin(c, at - timedelta(minutes=20))
    if rng.random() < 0.6:
        return session(sid, sl, pt, 'CheckedIn', value, AttendanceMarkedAt=end_at - timedelta(minutes=5))
    return session(sid, sl, pt, 'CheckedIn', value, IsAutoClosed=1)


def single_session(cid, pt, d, start, channel, created, status=None, note=None):
    """Buổi lẻ (CBR-53/54): online thì đặt trong tuần (CBR-39), tại quầy thì đúng hôm nay."""
    assert membership_valid(cid, d, allow_daypass=True), 'CBR-60'
    oid = order(cid, PKG['single'], created, channel, note=note)
    sid = subscription(oid, d, pt=pt)
    o = get('Orders', oid)
    sl = slot(pt, d, start, 'Booked', created=created - timedelta(days=3))
    at = datetime.combine(d, time(*start))
    if d >= FUTURE_FROM:
        session(sid, sl, pt, 'Scheduled', o['_payable'])
    elif status == 'Missed':
        session(sid, sl, pt, 'Missed', o['_payable'], MissedReason='NoCheckIn', IsAutoClosed=1)
        get('Subscriptions', sid)['Status'] = 'Expired'
    else:
        checkin(cid, at - timedelta(minutes=15))
        session(sid, sl, pt, 'CheckedIn', o['_payable'], AttendanceMarkedAt=at + timedelta(minutes=85))
        get('Subscriptions', sid)['Status'] = 'Used'
    return sid


# ============================================================ in SQL
def lit(v, now=None):
    if v is None:
        return 'NULL'
    if isinstance(v, Live):
        if now is None:
            return v.sql()
        v = v.eval(now)
    if isinstance(v, bool):
        return '1' if v else '0'
    if isinstance(v, int):
        return str(v)
    if isinstance(v, datetime):
        return f"'{v:%Y-%m-%d %H:%M:%S}'"
    if isinstance(v, date):
        return f"'{v:%Y-%m-%d}'"
    if isinstance(v, time):
        return f"'{v:%H:%M:%S}'"
    s = str(v).replace("'", "''")
    return f"N'{s}'" if any(ord(ch) > 127 for ch in s) or '\n' in s else f"'{s}'"


SECTION = {}


def render(now=None, header=''):
    out = [header]
    if now is None:
        out += ['USE GMS;', 'GO', '', 'SET ANSI_NULLS ON;', 'SET QUOTED_IDENTIFIER ON;  -- bắt buộc khi ghi vào bảng có filtered index', 'SET NOCOUNT ON;', 'SET XACT_ABORT ON;', 'BEGIN TRANSACTION;', '',
                '-- Giờ chạy script: mọi dữ liệu "sống" tính theo 2 biến này',
                'DECLARE @Now   DATETIME2(0) = CAST(SYSDATETIME() AS DATETIME2(0));',
                'DECLARE @Today DATE         = CAST(@Now AS DATE);', '']
    for t in TABLES:
        rows = DB[t]
        if not rows:
            continue
        cols = [c for c in rows[0].keys() if not c.startswith('_')]
        out.append('/* ' + '=' * 76)
        out.append(f'   {t} — {len(rows)} dòng. {SECTION.get(t, "")}'.rstrip())
        out.append('   ' + '=' * 76 + ' */')
        out.append(f'SET IDENTITY_INSERT {t} ON;')
        for i in range(0, len(rows), 400):
            chunk = rows[i:i + 400]
            out.append(f'INSERT INTO {t} ({", ".join(cols)}) VALUES')
            for j, r in enumerate(chunk):
                vals = ', '.join(lit(r[c], now) for c in cols)
                sep = ',' if j < len(chunk) - 1 else ';'
                note = f' -- {r["_note"]}' if r.get('_note') else ''
                out.append(f' ({vals}){sep}{note}')
        out.append(f'SET IDENTITY_INSERT {t} OFF;')
        out.append('')
    if now is None:
        out += ['COMMIT TRANSACTION;', 'GO', '', "PRINT N'>>> Nạp seed GMS v26 thành công.';",
                'SELECT t.name AS TableName, SUM(p.rows) AS [Rows] FROM sys.tables t',
                'JOIN sys.partitions p ON p.object_id = t.object_id AND p.index_id IN (0, 1)',
                'GROUP BY t.name ORDER BY t.name;', 'GO', '']
    return '\n'.join(out)


PKG = {}
