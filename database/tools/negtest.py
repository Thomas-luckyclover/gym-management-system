import datetime as dt, harness, seedgen as G, seeddata  # noqa
schema = open('../1_Init_Schema.sql').read()
con = harness.connect(); harness.run(con, schema, 's'); harness.run(con, G.render(now=dt.datetime(2026,10,3,10)), 'seed')
BAD = {
 'phone format': "INSERT INTO Persons (PersonId,FullName,DateOfBirth,Phone,IsReleased,CreatedAt) VALUES (9001,'Ab','2000-01-01','12345',0,'2026-01-01')",
 'dup active phone': "INSERT INTO Persons (PersonId,FullName,DateOfBirth,Phone,IsReleased,CreatedAt) VALUES (9002,'Ab','2000-01-01','0901234501',0,'2026-01-01')",
 'released phone reuse OK': "INSERT INTO Persons (PersonId,FullName,DateOfBirth,Phone,IsReleased,CreatedAt) VALUES (9003,'Ab','2000-01-01','0901234512',0,'2026-01-01')",
 '2nd active customer same person': "INSERT INTO Customers (CustomerId,PersonId,CustomerCode,CustomerType,Status,JoinedAt) VALUES (9001,1,'KH-9001','Self','New','2026-01-01')",
 'new customer on person with inactive one OK': "INSERT INTO Customers (CustomerId,PersonId,CustomerCode,CustomerType,Status,JoinedAt) VALUES (9002,27,'KH-9002','Self','New','2026-01-01')",
 'dependent without guardian': "INSERT INTO Customers (CustomerId,PersonId,CustomerCode,CustomerType,Status,JoinedAt) VALUES (9003,9003,'KH-9003','Dependent','New','2026-01-01')",
 'missed no reason': "UPDATE Sessions SET Status='Missed' WHERE SessionId=(SELECT MIN(SessionId) FROM Sessions WHERE Status='Scheduled')",
 'cancel no type': "UPDATE Sessions SET Status='Cancelled', CancelledAt='2026-10-01' WHERE SessionId=(SELECT MIN(SessionId) FROM Sessions WHERE Status='Scheduled')",
 'online no due': "UPDATE Orders SET PaymentDueAt=NULL WHERE Channel='Online' AND OrderId=(SELECT MIN(OrderId) FROM Orders WHERE Channel='Online')",
 'receptionist cancel no reason': "UPDATE Orders SET Status='Cancelled', CancelType='CancelledByReceptionist', CancelledAt='2026-10-01', CancelReason=NULL WHERE OrderId=(SELECT MIN(OrderId) FROM Orders WHERE Channel='Counter' AND Status='Paid')",
 'membership duration day': "INSERT INTO Packages (PackageId,PackageName,PackageType,DurationUnit,DurationValue,Price,IsActive,CreatedAt) VALUES (99,'Gói lỗi','Membership','Day',30,1000,1,'2026-01-01')",
 'membership null unit': "INSERT INTO Packages (PackageId,PackageName,PackageType,Price,IsActive,CreatedAt) VALUES (98,'Gói lỗi 2','Membership',1000,1,'2026-01-01')",
 'dup active pkg name': "INSERT INTO Packages (PackageId,PackageName,PackageType,DurationUnit,DurationValue,Price,IsActive,CreatedAt) VALUES (97,'Gói 1 Tháng','Membership','Month',1,1000,1,'2026-01-01')",
 'second success tx': "INSERT INTO Transactions (TransactionId,OrderId,Amount,PaymentMethod,Status,ReceivedByEmployeeId,NeedsRefund,CreatedAt,CompletedAt) SELECT 9999,OrderId,1000,'Cash','Success',3,0,'2026-01-01','2026-01-01' FROM Transactions WHERE Status='Success' AND PaymentMethod='Cash' LIMIT 1",
 'shift variance no reason': "UPDATE Shifts SET CountedCash=ExpectedCash+1, VarianceReason=NULL WHERE ShiftId=(SELECT MIN(ShiftId) FROM Shifts WHERE Status='Closed' AND VarianceReason IS NULL)",
 'verification code letters': "INSERT INTO VerificationCodes (VerificationCodeId,Email,Purpose,Code,SentAt,ExpiresAt) VALUES (999,'a@b.co','SignUp','12AB56','2026-01-01','2026-01-02')",
 'frozen daypass': "UPDATE Subscriptions SET Status='Frozen' WHERE SubscriptionType='DayPass' AND SubscriptionId=(SELECT MIN(SubscriptionId) FROM Subscriptions WHERE SubscriptionType='DayPass')",
 'move transfer same pt': "UPDATE SessionMoveLogs SET NewPtEmployeeId=OldPtEmployeeId WHERE MoveType='PtTransfer'",
}
for name, sql in BAD.items():
    try:
        con.execute('SAVEPOINT x'); cur = con.execute(sql); n = cur.rowcount; con.execute('ROLLBACK TO x')
        print(('PASS (allowed)' if 'OK' in name else 'FAIL (should be rejected)') + f' rows={n}', name)
    except Exception as e:
        con.execute('ROLLBACK TO x')
        print(('FAIL (should be allowed)' if 'OK' in name else 'PASS (rejected)'), name, '-', str(e)[:60])
