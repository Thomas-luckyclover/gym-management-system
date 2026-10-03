import datetime as dt, harness, seedgen as G, seeddata  # noqa
schema = open('../1_Init_Schema.sql').read()
con = harness.connect(); harness.run(con, schema, 's'); harness.run(con, G.render(now=dt.datetime(2026,10,3,10)), 'seed')
Q = {
 'PT: số buổi sinh = GeneratedSessionCount': """SELECT s.SubscriptionId, s.GeneratedSessionCount, COUNT(se.SessionId) FROM Subscriptions s JOIN Sessions se ON se.SubscriptionId=s.SubscriptionId
     WHERE s.SubscriptionType IN ('PTSingle','PTCouple') GROUP BY s.SubscriptionId, s.GeneratedSessionCount HAVING COUNT(se.SessionId) <> s.GeneratedSessionCount""",
 'Buổi gói PT rơi vào T7/CN mà không phải do dời (BR-35)': """SELECT se.SessionId FROM Sessions se JOIN Subscriptions s ON s.SubscriptionId=se.SubscriptionId JOIN TimeSlots t ON t.TimeSlotId=se.TimeSlotId
     WHERE s.SubscriptionType IN ('PTSingle','PTCouple') AND strftime('%w', t.SlotDate) IN ('0','6')
     AND se.SessionId NOT IN (SELECT SessionId FROM SessionMoveLogs)""",
 'Slot Scheduled nhưng không Booked': "SELECT se.SessionId FROM Sessions se JOIN TimeSlots t ON t.TimeSlotId=se.TimeSlotId WHERE se.Status='Scheduled' AND t.Status<>'Booked'",
 'Slot Booked không có buổi nào': "SELECT t.TimeSlotId FROM TimeSlots t WHERE t.Status='Booked' AND NOT EXISTS (SELECT 1 FROM Sessions se WHERE se.TimeSlotId=t.TimeSlotId)",
 'Slot Held mà đơn không Pending': "SELECT t.TimeSlotId FROM TimeSlots t JOIN Orders o ON o.OrderId=t.HeldByOrderId WHERE t.Status='Held' AND o.Status<>'Pending'",
 'Đơn Paid không có quyền dùng': "SELECT o.OrderId FROM Orders o WHERE o.Status='Paid' AND NOT EXISTS (SELECT 1 FROM Subscriptions s WHERE s.OrderId=o.OrderId)",
 'Đơn Paid tổng giao dịch Success ≠ số phải trả': "SELECT o.OrderId FROM Orders o JOIN Transactions t ON t.OrderId=o.OrderId AND t.Status='Success' WHERE o.Status='Paid' AND t.Amount <> o.UnitPriceSnapshot-o.DiscountAmount",
 'Check-in không có gói phủ ngày': "SELECT c.CheckInId FROM CheckIns c JOIN Subscriptions s ON s.SubscriptionId=c.SubscriptionId WHERE date(c.CheckedInAt) < s.StartDate OR date(c.CheckedInAt) > s.EndDate",
 'Check-in bằng gói PT (CBR-58)': "SELECT c.CheckInId FROM CheckIns c JOIN Subscriptions s ON s.SubscriptionId=c.SubscriptionId WHERE s.SubscriptionType NOT IN ('Membership','DayPass')",
 'Gói ngày > 1 lượt (CBR-36)': "SELECT SubscriptionId FROM CheckIns WHERE SubscriptionId IN (SELECT SubscriptionId FROM Subscriptions WHERE SubscriptionType='DayPass') GROUP BY SubscriptionId HAVING COUNT(*)>1",
 'Lễ tân tự bán cho mình (CBR-115)': "SELECT o.OrderId FROM Orders o JOIN Employees e ON e.EmployeeId=o.CreatedByEmployeeId JOIN Customers c ON c.CustomerId=o.HolderCustomerId WHERE e.PersonId=c.PersonId",
 'PT tự dạy mình (CBR-115)': "SELECT se.SessionId FROM Sessions se JOIN Employees e ON e.EmployeeId=se.PtEmployeeId JOIN Subscriptions s ON s.SubscriptionId=se.SubscriptionId JOIN Customers c ON c.CustomerId=s.CustomerId WHERE e.PersonId=c.PersonId",
 'Ca/đơn/check-in vào ngày nghỉ': "SELECT ShiftId FROM Shifts WHERE WorkDate IN (SELECT ApplyDate FROM OperatingHourExceptions WHERE IsClosedAllDay=1)",
 'Voucher UsedCount lệch': "SELECT v.VoucherId FROM Vouchers v WHERE v.UsedCount <> (SELECT COUNT(*) FROM Orders o WHERE o.VoucherId=v.VoucherId AND o.Status<>'Cancelled')",
 'Person nhả SĐT nhưng còn vai trò': """SELECT p.PersonId FROM Persons p WHERE p.IsReleased=1 AND (EXISTS (SELECT 1 FROM Customers c WHERE c.PersonId=p.PersonId AND c.Status<>'Inactive')
     OR EXISTS (SELECT 1 FROM Employees e WHERE e.PersonId=p.PersonId AND e.Status='Working'))""",
 'Person không còn vai trò nhưng chưa nhả': """SELECT p.PersonId FROM Persons p WHERE p.IsReleased=0 AND NOT EXISTS (SELECT 1 FROM Customers c WHERE c.PersonId=p.PersonId AND c.Status<>'Inactive')
     AND NOT EXISTS (SELECT 1 FROM Employees e WHERE e.PersonId=p.PersonId AND e.Status='Working')""",
 'TK Active mà không còn vai trò (CBR-86)': """SELECT a.AccountId FROM Accounts a WHERE a.Status='Active' AND NOT EXISTS (SELECT 1 FROM Customers c WHERE c.PersonId=a.PersonId AND c.Status<>'Inactive')
     AND NOT EXISTS (SELECT 1 FROM Employees e WHERE e.PersonId=a.PersonId AND e.Status='Working')""",
 'Hồ sơ có tài khoản nhưng Person không email (DBR-23)': "SELECT a.AccountId FROM Accounts a JOIN Persons p ON p.PersonId=a.PersonId WHERE p.Email IS NULL",
 'Dependent có SĐT riêng (DBR-49)': "SELECT c.CustomerId FROM Customers c JOIN Persons p ON p.PersonId=c.PersonId WHERE c.CustomerType='Dependent' AND p.Phone IS NOT NULL",
 'Self không SĐT (DBR-22)': "SELECT c.CustomerId FROM Customers c JOIN Persons p ON p.PersonId=c.PersonId WHERE c.CustomerType='Self' AND p.Phone IS NULL",
 'Chồng giờ slot cùng PT (DBR-33)': """SELECT a.TimeSlotId, b.TimeSlotId FROM TimeSlots a JOIN TimeSlots b ON a.PtEmployeeId=b.PtEmployeeId AND a.SlotDate=b.SlotDate AND a.TimeSlotId<b.TimeSlotId
     AND a.StartTime < b.EndTime AND b.StartTime < a.EndTime""",
}
bad = 0
for name, q in Q.items():
    rows = con.execute(q).fetchall()
    print(('OK  ' if not rows else 'LỖI'), name, rows[:5] if rows else '')
    bad += bool(rows)
print('lỗi:', bad)
