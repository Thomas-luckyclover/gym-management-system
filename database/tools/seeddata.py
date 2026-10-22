# -*- coding: utf-8 -*-
"""Dữ liệu seed GMS v26 — chạy: python3 seeddata.py  → in ra 2_Seed_Data.sql"""
import sys
from seedgen import *
import seedgen as G

# ============================================================ 1. NGƯỜI
P = person
# --- Khách cũ (giữ nguyên email / SĐT để docs Phase 1 còn dùng được)
P(1, 'Nguyễn Văn An', d_(1995, 3, 12), '0901234501', 'an.nguyen95@gmail.com', dt_(2026, 7, 6, 9, 10), note='khách chính: gia hạn trước, bảo lưu cũ, giám hộ 2 bé')
P(2, 'Trần Thị Bích', d_(1998, 7, 24), '0901234502', 'bich.tran@gmail.com', dt_(2026, 9, 3, 20, 5), note='gói hết hạn đúng ngày mốc, đơn cần hoàn tiền')
P(3, 'Lê Minh Quân', d_(1992, 11, 5), '0901234503', 'quan.le92@gmail.com', dt_(2026, 7, 1, 18, 40), note='Expired, gói PT đã hết')
P(4, 'Phạm Thị Hồng', d_(1990, 1, 30), '0901234504', 'hong.pham@yahoo.com', dt_(2026, 7, 29, 10, 15), note='Frozen, buổi PT bị Missed do bảo lưu')
P(5, 'Hoàng Văn Dũng', d_(2000, 5, 18), '0901234505', None, dt_(2026, 10, 1, 7, 45), note='khách vãng lai, gói ngày đã dùng hôm nay')
P(6, 'Đỗ Thị Mai', d_(1997, 9, 9), '0901234506', 'mai.do97@gmail.com', dt_(2026, 9, 4, 8, 30), note='gói PT 2 tháng: swap, hủy, chuyển PT')
P(7, 'Vũ Quốc Hưng', d_(1994, 2, 14), '0901234507', 'hung.vu@gmail.com', dt_(2026, 8, 20, 17, 20), note='gói couple — người đứng tên')
P(8, 'Bùi Thị Thu Hà', d_(1996, 12, 1), '0901234508', 'ha.bui96@gmail.com', dt_(2026, 8, 20, 17, 25), note='gói couple — người đi kèm, gói tập đã hết')
P(9, 'Ngô Gia Bảo', d_(2017, 4, 22), None, None, dt_(2026, 9, 3, 15, 0), note='Dependent 9 tuổi')
P(10, 'Đặng Thị Lan', d_(1985, 8, 8), '0901234510', 'lan.dang85@gmail.com', dt_(2026, 9, 3, 15, 5), note='giám hộ của Gia Bảo, hồ sơ chưa có tài khoản')
P(11, 'Michael Johnson', d_(1989, 6, 17), '0901234511', 'michael.johnson@outlook.com', dt_(2026, 7, 15, 11, 0), note='khách nước ngoài, gói 12 tháng giá cũ')
P(12, 'Trương Mỹ Linh', d_(1993, 10, 11), '0901234512', 'linh.truong93@gmail.com', dt_(2026, 3, 1, 9, 0), released=1, note='đã xóa hồ sơ → nhả SĐT + email')
P(13, 'Trịnh Văn Khánh', d_(1991, 4, 3), '0901234513', 'khanh.trinh@gmail.com', dt_(2026, 9, 21, 9, 50), note='TK Lễ tân tạo, chưa có mật khẩu; đang chờ duyệt xóa')
P(14, 'Lý Thị Ngọc', d_(1999, 1, 25), '0901234514', 'ngoc.ly99@gmail.com', dt_(2026, 9, 20, 21, 10), note='đơn online quá hạn + đơn online đang chờ')
P(15, 'Phan Văn Toàn', d_(2001, 7, 7), '0901234515', 'toan.phan@gmail.com', dt_(2026, 9, 22, 19, 0), note='đơn tại quầy đang Pending trong ca đang mở')
P(16, 'Nguyễn Thị Hạnh', d_(1993, 3, 19), '0901234516', None, dt_(2026, 10, 1, 8, 5), note='vãng lai: gói ngày + buổi PT lẻ hôm nay')
# --- Nhân viên
P(17, 'Nguyễn Hải Đăng', d_(1988, 5, 20), '0912000001', 'dang.nguyen@gms.vn', dt_(2025, 1, 5, 8, 0), note='Admin đang hoạt động duy nhất (BR-07)')
P(18, 'Trần Quốc Việt', d_(1986, 9, 14), '0912000002', 'viet.tran@gms.vn', dt_(2025, 1, 5, 8, 0), note='Manager')
P(19, 'Lê Thị Thanh Tâm', d_(1997, 2, 2), '0912000003', 'tam.le@gms.vn', dt_(2025, 2, 10, 8, 0), note='Lễ tân ca sáng')
P(20, 'Phạm Văn Lâm', d_(1996, 11, 28), '0912000004', 'lam.pham@gms.vn', dt_(2025, 6, 1, 8, 0), note='Lễ tân ca chiều')
P(21, 'Đinh Công Sơn', d_(1994, 8, 16), '0912000005', 'son.dinh@gms.vn', dt_(2025, 3, 15, 8, 0), note='PT')
P(22, 'Nguyễn Thị Kiều Trang', d_(1995, 12, 9), '0912000006', 'trang.nguyen@gms.vn', dt_(2025, 4, 1, 8, 0), note='PT + KHÁCH: 1 tài khoản, chọn vai trò (CBR-113)')
P(23, 'David Miller', d_(1990, 3, 27), '0912000007', 'david.miller@gms.vn', dt_(2025, 9, 1, 8, 0), note='PT nước ngoài')
P(24, 'Hồ Văn Nam', d_(1992, 6, 30), '0912000008', 'nam.ho@gms.vn', dt_(2025, 5, 1, 8, 0), released=1, note='PT đã nghỉ, không còn vai trò nào → nhả SĐT/email')
P(25, 'Hoàng Thu Phương', d_(1999, 4, 4), '0912000009', 'phuong.hoang@gms.vn', dt_(2025, 11, 1, 8, 0), note='LỄ TÂN + KHÁCH (CBR-115)')
P(26, 'Đặng Quốc Bảo', d_(1991, 1, 9), '0912000010', 'bao.dang@gmail.com', dt_(2025, 2, 1, 8, 0), note='PT đã nghỉ nhưng VẪN là khách → TK chỉ còn vai trò Khách (CBR-86)')
P(27, 'Mai Thị Hoa', d_(1998, 8, 18), '0912000011', 'hoa.mai@gms.vn', dt_(2026, 1, 10, 8, 0), note='Lễ tân, hồ sơ khách đã xóa → TK chỉ còn vai trò Lễ tân')
P(28, 'Lý Hoàng Long', d_(1993, 5, 5), '0912000012', 'long.ly@gms.vn', dt_(2025, 3, 1, 8, 0), note='PT nghỉ rồi được tuyển lại (2 dòng Employees)')
P(30, 'Phan Thanh Tú', d_(1995, 10, 10), '0912000014', 'tu.phan@gms.vn', dt_(2026, 2, 1, 8, 0), note='PT bị Admin khóa tài khoản')
P(31, 'Bùi Đức Anh', d_(1984, 12, 24), '0912000015', 'anh.bui@gms.vn', dt_(2025, 1, 2, 8, 0), released=1, note='Admin cũ đã nghỉ')
P(32, 'Đỗ Văn Lực', d_(1987, 6, 12), '0901234532', None, dt_(2026, 9, 1, 7, 55), note='khách KHÔNG tài khoản có gói PT → PT đổi lịch hộ (BR-62)')
# --- Phase 1 (giữ nguyên ID 101–113)
P(101, 'Lê Hoàng Phúc', d_(1994, 4, 18), '0935000101', 'phuc.le@gmail.com', dt_(2026, 8, 1, 10, 0), note='Admin khóa → MSG37')
P(102, 'Võ Thị Kim', d_(1999, 10, 2), '0935000102', 'kim.vo@gmail.com', dt_(2026, 8, 5, 10, 0), avatar='/uploads/avatar/p-102.jpg', note='đang khóa tạm 5 phút → MSG35')
P(103, 'Nguyễn Gia Huy', d_(2018, 5, 10), None, None, dt_(2026, 9, 1, 13, 50), note='Dependent 8 tuổi (< P-05) có gói tập')
P(104, 'Nguyễn Minh Châu', d_(2012, 2, 14), None, None, dt_(2026, 9, 1, 13, 52), note='Dependent 14 tuổi, An là giám hộ thứ 2')
P(105, 'Đoàn Thị Yến', d_(1996, 6, 6), '0935000105', 'yen.doan@gmail.com', dt_(2026, 9, 12, 16, 0), note='hồ sơ quầy có email, chưa có TK → kích hoạt online (CBR-20)')
P(106, 'Kiều Bảo Ngọc', d_(2019, 8, 20), None, None, dt_(2026, 9, 10, 9, 0), note='Dependent, số giám hộ không thuộc ai')
P(107, 'Mai Thanh Tùng', d_(1990, 12, 12), '0935000107', 'tung.mai@gmail.com', dt_(2026, 5, 1, 10, 0), released=1, note='hồ sơ đã xóa → SĐT/email đã nhả')
P(108, 'Lương Thu Thảo', d_(1997, 3, 21), '0935000108', 'thao.luong@gmail.com', dt_(2026, 8, 10, 10, 0), note='mã khôi phục còn hiệu lực; đã sai MK 4 lần')
P(109, 'Ngô Đức Mạnh', d_(1993, 9, 9), '0935000109', 'manh.ngo@gmail.com', dt_(2026, 8, 11, 10, 0), note='đã xin 4 mã trong 1 giờ')
P(110, 'Đinh Văn Khoa', d_(1995, 1, 15), '0935000110', 'khoa.dinh@gmail.com', dt_(2026, 8, 12, 10, 0), note='vừa xin mã < 60 giây')
P(111, 'Tạ Thanh Bình', d_(1998, 11, 30), '0935000111', 'binh.ta@gmail.com', dt_(2026, 8, 13, 10, 0), note='sai mã 5 lần → email bị khóa xác thực')
P(112, 'Cao Thị Quỳnh', d_(2000, 7, 7), '0935000112', 'quynh.cao@gmail.com', dt_(2026, 8, 14, 10, 0), note='mã khôi phục đã hết hạn')
P(113, 'Hồ Thị Nhung', d_(1994, 2, 2), '0935000113', 'nhung.ho@gmail.com', dt_(2026, 9, 20, 9, 0), note='TK Lễ tân vừa tạo, chưa đặt MK')

# ============================================================ 2. NHÂN VIÊN
E = employee
E(1, 17, 'NV-0001', 'Admin', d_(2025, 1, 5))
E(2, 18, 'NV-0002', 'Manager', d_(2025, 1, 5))
E(3, 19, 'NV-0003', 'Receptionist', d_(2025, 2, 10))
E(4, 20, 'NV-0004', 'Receptionist', d_(2025, 6, 1))
E(5, 21, 'NV-0005', 'PT', d_(2025, 3, 15), 70, 'Strength & Conditioning', 8)
E(6, 22, 'NV-0006', 'PT', d_(2025, 4, 1), 65, 'Giảm mỡ · Thể hình nữ', 6)
E(7, 23, 'NV-0007', 'PT', d_(2025, 9, 1), 60, 'Tăng cơ · Powerlifting', 9)
E(8, 24, 'NV-0008', 'PT', d_(2025, 5, 1), 70, 'Yoga & Phục hồi chấn thương', 5, 'Resigned', dt_(2026, 8, 31, 18, 0), 'Chuyển về quê làm việc, đã bàn giao khách')
E(9, 25, 'NV-0009', 'Receptionist', d_(2025, 11, 1))
E(10, 26, 'NV-0010', 'PT', d_(2025, 2, 1), 65, 'Boxing · Cardio', 4, 'Resigned', dt_(2026, 6, 30, 18, 0), 'Nghỉ để tự mở lớp, vẫn tập tại gym với tư cách khách')
E(11, 27, 'NV-0011', 'Receptionist', d_(2026, 1, 10))
E(12, 28, 'NV-0012', 'PT', d_(2025, 3, 1), 60, 'Calisthenics', 3, 'Resigned', dt_(2025, 12, 31, 18, 0), 'Đi du học 6 tháng')
E(13, 28, 'NV-0013', 'PT', d_(2026, 8, 15), 60, 'Calisthenics · Mobility', 4, note='tuyển lại trên CÙNG Person 28 (DBR-65)')
E(14, 30, 'NV-0014', 'PT', d_(2026, 2, 1), 60, 'Functional Training', 3)
E(15, 31, 'NV-0015', 'Admin', d_(2025, 1, 2), status='Resigned', deleted=dt_(2026, 5, 31, 18, 0), reason='Chuyển công tác sang chi nhánh khác')

# ============================================================ 3. GÓI, VOUCHER, NGÀY NGHỈ
BEN_MEM = 'Tập không giới hạn giờ mở cửa\nDùng đủ thiết bị khu cardio và khu tạ\nMiễn phí nước uống'


def pkg(pid, name, desc, ben, ptype, unit, val, est, price, active=1, key=None):
    add('Packages', dict(PackageName=name, Description=desc, Benefits=ben, PackageType=ptype, DurationUnit=unit,
                         DurationValue=val, EstimatedSessionCount=est, Price=price, IsActive=active,
                         CreatedAt=dt_(2025, 1, 10, 9, 0)), None, pid)
    if key:
        G.PKG[key] = pid


pkg(1, 'Gói Ngày', 'Vé vào tập một lượt trong ngày.', 'Vào tập 1 lượt trong ngày\nDùng đủ thiết bị khu chung', 'DayPass', 'Day', 1, None, 120000, key='day')
pkg(2, 'Gói 1 Tháng', 'Bắt đầu xây thói quen tập luyện.', BEN_MEM, 'Membership', 'Month', 1, None, 799000, key='m1')
pkg(3, 'Gói 3 Tháng', 'Đủ thời gian thấy chuyển biến thể lực.', BEN_MEM + '\nTặng 1 buổi đo InBody', 'Membership', 'Month', 3, None, 2190000, key='m3')
pkg(4, 'Gói 9 Tháng', 'Cam kết dài hạn, giá tốt.', BEN_MEM + '\nTặng 3 buổi đo InBody', 'Membership', 'Month', 9, None, 5490000, key='m9')
pkg(5, 'Gói 12 Tháng', 'Mức giá tốt nhất cho người tập lâu dài.', BEN_MEM + '\nTặng 4 buổi đo InBody', 'Membership', 'Month', 12, None, 6990000, key='m12')
pkg(6, 'Gói 12 Tháng', 'Bảng giá cũ — đã thay bằng gói 12 tháng mới.', BEN_MEM, 'Membership', 'Month', 12, None, 6490000, active=0, key='m12old')
pkg(7, 'Gói 6 Tháng', 'Đã ngừng bán (P-04 không còn 6 tháng).', BEN_MEM, 'Membership', 'Month', 6, None, 3990000, active=0, key='m6old')
pkg(8, 'Gói PT 1 Tháng', 'Gói PT theo kỳ hạn, tập T2–T6 một khung giờ cố định.', 'Huấn luyện viên riêng\nGiáo án và thực đơn cá nhân hóa', 'PTSingle', 'Month', 1, 22, 8800000, key='pt1')
pkg(9, 'Gói PT 2 Tháng', 'Gói PT theo kỳ hạn 2 tháng.', 'Huấn luyện viên riêng\nGiáo án và thực đơn cá nhân hóa\nĐo InBody mỗi 2 tuần', 'PTSingle', 'Month', 2, 43, 16500000, key='pt2')
pkg(10, 'Gói PT 3 Tháng', 'Gói PT theo kỳ hạn 3 tháng.', 'Huấn luyện viên riêng\nGiáo án và thực đơn cá nhân hóa\nĐo InBody mỗi 2 tuần', 'PTSingle', 'Month', 3, 65, 23900000, key='pt3')
pkg(11, 'Gói PT Couple 1 Tháng', 'Hai người tập chung một huấn luyện viên.', 'Chung hạn mức buổi cho 2 người\nGiáo án riêng cho từng người', 'PTCouple', 'Month', 1, 22, 13200000, key='c1')
pkg(12, 'Gói PT Couple 2 Tháng', 'Hai người tập chung 2 tháng.', 'Chung hạn mức buổi cho 2 người\nGiáo án riêng cho từng người', 'PTCouple', 'Month', 2, 43, 24900000, key='c2')
pkg(13, 'Buổi PT Lẻ', 'Một buổi tập cùng huấn luyện viên.', 'Không ràng buộc kỳ hạn', 'SingleSession', None, None, None, 450000, key='single')


def voucher(vid, code, typ, val, s, e, maxu, per, minv, scope, active=1, note=None):
    add('Vouchers', dict(VoucherCode=code, DiscountType=typ, DiscountValue=val, StartDate=s, EndDate=e,
                         MaxUsage=maxu, MaxUsagePerCustomer=per, UsedCount=0, MinOrderValue=minv,
                         ApplicableScope=scope, IsActive=active, CreatedAt=datetime.combine(s - timedelta(days=5), time(9, 0))), note, vid)


voucher(1, 'WELCOME10', 'Percent', 10, d_(2026, 9, 1), d_(2026, 12, 31), 100, 1, 500000, 'MembershipAndDayPass', note='đang chạy')
voucher(2, 'SUMMER200K', 'Amount', 200000, d_(2026, 6, 1), d_(2026, 8, 31), 50, 1, 2000000, 'MembershipOnly', note='đã hết hạn')
voucher(3, 'FULLBOOKED', 'Amount', 100000, d_(2026, 8, 15), d_(2026, 10, 31), 2, 1, 700000, 'MembershipOnly', note='đã dùng hết lượt')
voucher(4, 'OLDCAMPAIGN', 'Percent', 15, d_(2026, 1, 1), d_(2026, 3, 31), 200, None, 0, 'MembershipAndDayPass', active=0, note='đã tắt, đã dùng → chỉ tắt không xóa (CBR-98)')
voucher(5, 'TET2027', 'Percent', 20, d_(2027, 1, 20), d_(2027, 2, 28), 50, 1, 0, 'MembershipAndDayPass', note='chưa tới ngày bắt đầu, chưa ai dùng')
voucher(6, 'DAYPASS20K', 'Amount', 20000, d_(2026, 9, 1), d_(2026, 12, 31), 500, None, 100000, 'MembershipAndDayPass', note='dùng nhiều lần / khách (không giới hạn)')

# Ngày gym nghỉ: ngày → thời điểm Manager khai báo
G.CLOSED.update({d_(2026, 9, 2): dt_(2026, 8, 20, 10, 0),      # gói PT vẫn sinh buổi; đã qua ngày → GymClosed nếu chưa dời
                 d_(2026, 9, 18): dt_(2026, 9, 10, 9, 0),      # đã qua ngày, chưa dời → JOB-04 chốt GymClosed
                 d_(2026, 10, 20): dt_(2026, 8, 25, 9, 0),     # tương lai → Scheduled để PT chủ động dời
                 d_(2027, 2, 6): dt_(2026, 9, 30, 9, 0)})      # Tết

# ============================================================ 4. KHÁCH (hồ sơ)
C = customer
C(1, 1, 'KH-1001', dt_(2026, 7, 6, 9, 15), photo='/uploads/checkin/kh-1001.jpg')
C(2, 2, 'KH-1002', dt_(2026, 9, 3, 20, 10), photo='/uploads/checkin/kh-1002.jpg')
C(3, 3, 'KH-1003', dt_(2026, 7, 1, 18, 45), photo='/uploads/checkin/kh-1003.jpg')
C(4, 4, 'KH-1004', dt_(2026, 7, 29, 10, 20), photo='/uploads/checkin/kh-1004.jpg')
C(5, 5, 'KH-1005', dt_(2026, 10, 1, 7, 50), note='không ảnh: chỉ mua gói ngày (BR-30)')
C(6, 6, 'KH-1006', dt_(2026, 9, 4, 8, 35), photo='/uploads/checkin/kh-1006.jpg')
C(7, 7, 'KH-1007', dt_(2026, 8, 20, 17, 30), photo='/uploads/checkin/kh-1007.jpg')
C(8, 8, 'KH-1008', dt_(2026, 8, 20, 17, 35), photo='/uploads/checkin/kh-1008.jpg')
C(9, 9, 'KH-1009', dt_(2026, 9, 3, 15, 10), 'Dependent', '/uploads/checkin/kh-1009.jpg', ('Đặng Thị Lan', 'Mẹ', '0901234510', '0901234507'))
C(10, 10, 'KH-1010', dt_(2026, 9, 3, 15, 15))
C(11, 11, 'KH-1011', dt_(2026, 7, 15, 11, 5), photo='/uploads/checkin/kh-1011.jpg')
C(12, 12, 'KH-1012', dt_(2026, 3, 1, 9, 5), photo='/uploads/checkin/kh-1012.jpg', status='Inactive', deleted=dt_(2026, 9, 15, 16, 0))
C(13, 13, 'KH-1013', dt_(2026, 9, 21, 9, 55), photo='/uploads/checkin/kh-1013.jpg')
C(14, 14, 'KH-1014', dt_(2026, 9, 20, 21, 15))
C(15, 15, 'KH-1015', dt_(2026, 10, 1, 9, 0))
C(16, 16, 'KH-1016', dt_(2026, 10, 1, 8, 10))
C(17, 22, 'KH-1017', dt_(2026, 9, 10, 17, 50), photo='/uploads/checkin/kh-1017.jpg', note='PT Kiều Trang trong vai trò khách')
C(18, 25, 'KH-1018', dt_(2026, 9, 15, 7, 0), photo='/uploads/checkin/kh-1018.jpg', note='Lễ tân Phương trong vai trò khách')
C(19, 26, 'KH-1019', dt_(2026, 8, 1, 8, 0), photo='/uploads/checkin/kh-1019.jpg', note='PT cũ, nay chỉ là khách')
C(20, 27, 'KH-1020', dt_(2026, 7, 1, 8, 0), photo='/uploads/checkin/kh-1020.jpg', status='Inactive', deleted=dt_(2026, 9, 5, 15, 0), note='hồ sơ khách của Lễ tân Hoa đã xóa')
C(21, 32, 'KH-1021', dt_(2026, 9, 1, 8, 0), photo='/uploads/checkin/kh-1021.jpg')
C(101, 101, 'KH-1101', dt_(2026, 8, 1, 10, 5))
C(102, 102, 'KH-1102', dt_(2026, 8, 5, 10, 5))
C(103, 103, 'KH-1103', dt_(2026, 9, 1, 13, 55), 'Dependent', '/uploads/checkin/kh-1103.jpg', ('Nguyễn Văn An', 'Bố', '0901234501', None))
C(104, 104, 'KH-1104', dt_(2026, 9, 1, 13, 57), 'Dependent', None, ('Trần Thị Hoa', 'Mẹ', '0909111222', '0901234501'))
C(105, 105, 'KH-1105', dt_(2026, 9, 12, 16, 5), photo='/uploads/checkin/kh-1105.jpg')
C(106, 106, 'KH-1106', dt_(2026, 9, 10, 9, 5), 'Dependent', None, ('Kiều Văn Hải', 'Bố', '0935000999', None))
C(107, 107, 'KH-1107', dt_(2026, 5, 1, 10, 5), status='Inactive', deleted=dt_(2026, 9, 1, 9, 0))
for i in range(108, 114):
    C(i, i, f'KH-{1000 + i}', get('Persons', i)['CreatedAt'] + timedelta(minutes=5), photo='/uploads/checkin/kh-1113.jpg' if i == 113 else None)

# ============================================================ 5. TÀI KHOẢN
A = account
A(1, 1, dt_(2026, 7, 6, 9, 20), last=dt_(2026, 10, 2, 18, 30))
A(2, 2, dt_(2026, 9, 3, 20, 15), last=dt_(2026, 10, 3, 7, 10))
A(3, 3, dt_(2026, 7, 1, 18, 50), failed=2, last=dt_(2026, 9, 18, 19, 0), note='đang sai 2 lần, chưa khóa')
A(4, 4, dt_(2026, 7, 29, 10, 25), failed=5, locked=dt_(2026, 9, 23, 9, 5), last=dt_(2026, 9, 21, 8, 0), note='khóa tạm ĐÃ HẾT → đăng nhập đúng sẽ reset (BR-02)')
A(5, 6, dt_(2026, 9, 4, 8, 40), last=dt_(2026, 10, 2, 20, 0))
A(6, 7, dt_(2026, 8, 20, 17, 40), last=dt_(2026, 10, 1, 7, 30))
A(7, 8, dt_(2026, 8, 20, 17, 45), last=dt_(2026, 9, 30, 17, 45))
A(8, 11, dt_(2026, 7, 15, 11, 10), last=dt_(2026, 9, 29, 12, 0))
A(9, 13, dt_(2026, 9, 21, 10, 0), pw=None, note='Lễ tân tạo (CBR-24) → MSG38, đặt MK qua Quên mật khẩu')
A(10, 14, dt_(2026, 9, 20, 21, 20), last=dt_(2026, 10, 3, 8, 50))
A(11, 17, dt_(2025, 1, 5, 8, 5), last=dt_(2026, 10, 3, 7, 0))
A(12, 18, dt_(2025, 1, 5, 8, 5), last=dt_(2026, 10, 3, 7, 20))
A(13, 19, dt_(2025, 2, 10, 8, 5), failed=3, last=dt_(2026, 10, 3, 5, 35), note='nhân viên sai 3 lần, KHÔNG bao giờ tự khóa (BR-01)')
A(14, 20, dt_(2025, 6, 1, 8, 5), last=dt_(2026, 10, 2, 13, 40))
A(15, 21, dt_(2025, 3, 15, 8, 5), last=dt_(2026, 10, 3, 6, 5))
A(16, 22, dt_(2025, 4, 1, 8, 5), last=dt_(2026, 10, 3, 6, 10), note='PT + Khách → màn chọn vai trò')
A(17, 23, dt_(2025, 9, 1, 8, 5), last=dt_(2026, 10, 2, 16, 0))
A(18, 24, dt_(2025, 5, 1, 8, 5), status='Inactive', last=dt_(2026, 8, 31, 17, 50), note='nghỉ việc, hết vai trò → Inactive')
A(19, 25, dt_(2025, 11, 1, 8, 5), last=dt_(2026, 10, 3, 5, 25), note='Lễ tân + Khách → màn chọn vai trò')
A(20, 26, dt_(2025, 2, 1, 8, 5), last=dt_(2026, 10, 1, 18, 0), note='PT đã nghỉ, vẫn là khách → TK Active, chỉ vai trò Khách')
A(21, 27, dt_(2026, 1, 10, 8, 5), last=dt_(2026, 10, 3, 13, 0), note='hồ sơ khách đã xóa, vẫn là Lễ tân')
A(22, 28, dt_(2025, 3, 1, 8, 5), last=dt_(2026, 10, 2, 9, 0), note='1 TK xuyên 2 lần làm việc')
A(23, 30, dt_(2026, 2, 1, 8, 5), status='Locked', last=dt_(2026, 9, 24, 9, 0), note='Admin khóa → MSG37')
A(24, 31, dt_(2025, 1, 2, 8, 5), status='Inactive', last=dt_(2026, 5, 31, 17, 0))
A(101, 101, dt_(2026, 8, 1, 10, 10), status='Locked', last=dt_(2026, 9, 10, 20, 0), note='Admin khóa → MSG37')
A(102, 102, dt_(2026, 8, 5, 10, 10), failed=5, locked=now_(5), last=dt_(2026, 9, 20, 8, 0), note='khóa tạm 5 phút tính từ lúc chạy script')
A(107, 107, dt_(2026, 5, 1, 10, 10), status='Inactive', last=dt_(2026, 8, 30, 18, 0))
A(108, 108, dt_(2026, 8, 10, 10, 10), failed=4, last=dt_(2026, 9, 18, 8, 0), note='sai thêm 1 lần là bị khóa tạm')
for i in (109, 110, 111, 112):
    A(i, i, get('Persons', i)['CreatedAt'] + timedelta(minutes=10), last=dt_(2026, 9, 18, 8, 0))
A(113, 113, dt_(2026, 9, 20, 9, 10), pw=None, note='Lễ tân tạo, chưa đặt MK → MSG38')
A(25, 15, today_dt(0, 9, 0), pw=None, note='Lễ tân tạo khi bán gói 1 tháng (CBR-24)')

# ============================================================ 6. CA ĐANG MỞ HÔM NAY (sống)
G.LIVE_SHIFT_MORNING = add('Shifts', dict(WorkDate=today_d(), ShiftType='Morning', OpenedByEmployeeId=3,
                                          ClosedByEmployeeId=None, Status='Open', OpenedAt=today_dt(0, 5, 30),
                                          ClosedAt=None, ExpectedCash=None, CountedCash=None, VarianceReason=None),
                           'ca sáng HÔM NAY đang mở (còn 1 đơn Pending → chưa chốt được)')
add('Shifts', dict(WorkDate=today_d(), ShiftType='Afternoon', OpenedByEmployeeId=None, ClosedByEmployeeId=None,
                   Status='NotOpened', OpenedAt=None, ClosedAt=None, ExpectedCash=None, CountedCash=None,
                   VarianceReason=None), 'ca chiều hôm nay chưa mở')

# ============================================================ 7. MUA GÓI + LỊCH PT
# ---- An (1): gói 3 tháng, bảo lưu hủy sớm, gia hạn trước 9 tháng, buổi lẻ, đơn sống
o = order(1, PKG['m3'], dt_(2026, 7, 6, 9, 25), 'Counter')
s_an = subscription(o, d_(2026, 7, 6), note='bảo lưu 03/08 → hủy sớm 12/08: hạn +9 ngày (DBR-51)')
get('Subscriptions', s_an)['EndDate'] = d_(2026, 10, 6) + timedelta(days=9)
get('Subscriptions', s_an)['FreezeUsed'] = 1
G.FREEZES[s_an] = (d_(2026, 8, 3), d_(2026, 8, 11))
add('FreezeRecords', dict(SubscriptionId=s_an, HandledByEmployeeId=4, StartDate=d_(2026, 8, 3),
                          PlannedEndDate=d_(2026, 9, 2), ActualEndDate=d_(2026, 8, 12),
                          Reason='Khách bị chấn thương gối, đã có giấy khám', CreatedAt=dt_(2026, 8, 2, 16, 0)),
    'hủy bảo lưu sớm (BR-26)')
o = order(1, PKG['m9'], dt_(2026, 9, 20, 20, 10), 'Online', voucher=1)
subscription(o, d_(2026, 10, 16), note='gia hạn trước: bắt đầu ngay sau gói cũ (DBR-52)')
single_session(1, 5, d_(2026, 9, 26), (9, 0), 'Online', dt_(2026, 9, 24, 21, 0))
o_rep = order(1, PKG['single'], now_(-10), 'Online', status='Cancelled', cancel=('ReplacedByNewOrder', now_(-2), None),
              note='bị thay bằng đơn mới (CBR-41, CBR-71)')
slot(7, today_d(1), (15, 30), 'Free', check=False)
o_held = order(1, PKG['single'], now_(-2), 'Online', status='Pending', note='đang giữ slot ngày mai 14:00 (CBR-53)')
slot(7, today_d(1), (14, 0), 'Held', held_order=o_held, hold_exp=now_(18), check=False)

# ---- Bích (2): gói 1 tháng hết hạn đúng ngày mốc, đơn sai tiền chờ hoàn, buổi lẻ
o = order(2, PKG['m1'], dt_(2026, 9, 3, 20, 20), 'Online', voucher=1, fail_first=True)
subscription(o, d_(2026, 9, 3), note='hết hạn đúng 03/10/2026')
order(2, PKG['m3'], dt_(2026, 9, 19, 10, 0), 'Online', status='Cancelled',
      cancel=('InvalidPayment', dt_(2026, 9, 19, 10, 26), 'Tiền về sau khi đơn đã hết hạn thanh toán'),
      note='giao dịch Success nhưng đơn đã hủy → Manager hoàn tay (CBR-72, BR-47)')
single_session(2, 5, d_(2026, 9, 26), (10, 30), 'Online', dt_(2026, 9, 25, 19, 0))

# ---- Quân (3): gói tập hết trước gói PT → buổi NoMembership + buổi cuối PackageExpired
o = order(3, PKG['m1'], dt_(2026, 7, 1, 18, 55), 'Counter')
subscription(o, d_(2026, 7, 1))
o = order(3, PKG['pt1'], dt_(2026, 7, 3, 9, 0), 'Counter', method='Transfer')
pt_package(o, 5, d_(2026, 7, 6), (7, 0), events={d_(2026, 8, 6): ('cancel', 'PackageExpired', 'Gói PT hết hạn, buổi còn lại chưa diễn ra (CBR-55)'),
                                              d_(2026, 7, 15): ('missed', 'NoCheckIn')},
           note='gói PT đã hết; 03–05/08 Missed vì hết gói tập (CBR-64)')

# ---- Hồng (4): gói 6 tháng (đã ngừng bán) đang bảo lưu, gói PT 2 tháng với David
o = order(4, PKG['m6old'], dt_(2026, 7, 29, 10, 30), 'Counter', method='Transfer')
s_hong = subscription(o, d_(2026, 7, 29), status='Frozen', note='đang bảo lưu, hết lượt bảo lưu (BR-24)')
get('Subscriptions', s_hong)['FreezeUsed'] = 1
add('FreezeRecords', dict(SubscriptionId=s_hong, HandledByEmployeeId=3, StartDate=d_(2026, 9, 28),
                          PlannedEndDate=d_(2026, 10, 27), ActualEndDate=None,
                          Reason='Khách đi công tác nước ngoài 1 tháng', CreatedAt=dt_(2026, 9, 27, 10, 30)),
    'đang bảo lưu → buổi PT trong kỳ thành Missed (CBR-62)')
o = order(4, PKG['pt2'], dt_(2026, 9, 4, 10, 0), 'Counter', method='Transfer')
G.FREEZES[s_hong] = (d_(2026, 9, 28), d_(2026, 10, 27))
pt_package(o, 7, d_(2026, 9, 7), (17, 0), freeze=(d_(2026, 9, 28), d_(2026, 10, 27)))

# ---- Mai (6): gói tập 1 tháng (hết 04/10) + PT 2 tháng: swap, khách hủy, chuyển PT, vắng
o = order(6, PKG['m1'], dt_(2026, 9, 4, 8, 40), 'Counter', voucher=3)
subscription(o, d_(2026, 9, 4), note='hết 04/10 trước gói PT → buổi sau đó gắn nhãn "Không có gói tập" (CBR-66)')
o = order(6, PKG['pt2'], dt_(2026, 9, 4, 9, 0), 'Counter', method='Transfer')
pt_package(o, 5, d_(2026, 9, 7), (18, 0), events={
    d_(2026, 9, 11): ('move', 'CustomerSwap', d_(2026, 9, 12), (9, 0), None, None),
    d_(2026, 9, 16): ('missed', 'NoCheckIn'),
    d_(2026, 9, 22): ('cancel', 'ByCustomer', 'Khách bận đột xuất'),
    d_(2026, 9, 29): ('move', 'PtTransfer', d_(2026, 9, 29), (18, 0), 13, 'PT Sơn ốm, khách chọn PT Long'),
}, note='swap 1/6 (gói 2 tháng × 3); 1 buổi chuyển PT → hoa hồng cho PT mới (BR-68)')

# ---- Hưng (7) + Hà (8): gói couple 2 tháng với PT Trang
o = order(7, PKG['m3'], dt_(2026, 8, 20, 17, 40), 'Counter', voucher=3)
subscription(o, d_(2026, 8, 20))
o = order(8, PKG['m1'], dt_(2026, 8, 20, 17, 50), 'Counter')
subscription(o, d_(2026, 8, 20), note='hết 20/09 → Expired nhưng vẫn còn buổi couple (CBR-63, CBR-67)')
o = order(7, PKG['c2'], dt_(2026, 8, 21, 9, 0), 'Counter', partner=8, method='Transfer')
pt_package(o, 6, d_(2026, 8, 24), (19, 30), events={
    d_(2026, 9, 10): ('missed', 'NoCheckIn'),
    d_(2026, 9, 15): ('move', 'PtReschedule', d_(2026, 9, 16), (6, 0), None, 'PT bận họp chuyên môn'),
    d_(2026, 9, 21): ('cancel', 'ByPt', 'PT bận việc gia đình, khách đồng ý nhận lại tiền mặt'),
}, note='couple: chung hạn mức; 02/09 nghỉ lễ khai báo trước → không sinh buổi')

# ---- Gia Bảo (9, Dependent 9 tuổi)
o = order(9, PKG['m3'], dt_(2026, 9, 3, 15, 20), 'Counter')
subscription(o, d_(2026, 9, 3))
# ---- Michael (11): gói 12 tháng giá cũ, voucher mùa hè
o = order(11, PKG['m12old'], dt_(2026, 7, 15, 11, 5), 'Online', voucher=2)
subscription(o, d_(2026, 7, 15))
# ---- Linh (12): đã xóa
o = order(12, PKG['m1'], dt_(2026, 3, 1, 9, 10), 'Counter', voucher=4)
subscription(o, d_(2026, 3, 1), status='Expired')
# ---- Khánh (13)
o = order(13, PKG['m1'], dt_(2026, 9, 21, 10, 0), 'Counter')
subscription(o, d_(2026, 9, 21))
# ---- Ngọc (14): đơn online quá hạn (voucher được trả lượt — BR-44) + đơn online đang chờ (sống)
order(14, PKG['m1'], dt_(2026, 9, 20, 21, 25), 'Online', status='Cancelled', voucher=1,
      cancel=('PaymentTimeout', dt_(2026, 9, 20, 21, 45), 'Quá hạn thanh toán 20 phút'))
order(14, PKG['m1'], now_(-3), 'Online', status='Pending', note='đơn online còn hạn ~17 phút')
# ---- Kiều Trang (khách 17): tập với PT David — không được tự dạy mình (CBR-115)
o = order(17, PKG['m3'], dt_(2026, 9, 10, 18, 0), 'Counter')
subscription(o, d_(2026, 9, 10))
o = order(17, PKG['pt1'], dt_(2026, 9, 11, 11, 0), 'Counter', method='Transfer')
pt_package(o, 7, d_(2026, 9, 14), (12, 0), note='18/09 nghỉ khai báo TRƯỚC khi bán → không sinh buổi')
# ---- Phương (18): lễ tân mua gói — phải do lễ tân khác bán (CBR-115)
o = order(18, PKG['m1'], dt_(2026, 9, 15, 7, 10), 'Counter')
subscription(o, d_(2026, 9, 15))
# ---- Bảo (19): PT cũ nay là khách
o = order(19, PKG['m3'], dt_(2026, 8, 1, 8, 10), 'Counter')
subscription(o, d_(2026, 8, 1))
# ---- Hoa (20): hồ sơ khách đã xóa
o = order(20, PKG['m1'], dt_(2026, 7, 1, 8, 10), 'Counter')
subscription(o, d_(2026, 7, 1), status='Expired')
# ---- Lực (21): không tài khoản, PT đổi lịch hộ
o = order(21, PKG['m3'], dt_(2026, 9, 1, 8, 5), 'Counter')
subscription(o, d_(2026, 9, 1))
o = order(21, PKG['pt1'], dt_(2026, 9, 11, 7, 30), 'Counter')
pt_package(o, 5, d_(2026, 9, 14), (6, 30), events={
    d_(2026, 9, 24): ('move', 'PtOnBehalfSwap', d_(2026, 9, 25), (20, 0), None, 'Khách gọi điện nhờ PT dời buổi'),
})
# ---- Gia Huy (103, 8 tuổi)
o = order(103, PKG['m3'], dt_(2026, 9, 1, 14, 0), 'Counter', method='Transfer')
subscription(o, d_(2026, 9, 1))

# ---- HÔM NAY (sống): gói ngày, buổi lẻ, đơn Pending tại quầy, đơn bị Lễ tân hủy
o = order(5, PKG['day'], today_dt(0, 7, 50), 'Counter', voucher=6)
s5 = subscription(o, today_d(), status='Used', note='đã check-in → hết hiệu lực (BR-29)')
add('CheckIns', dict(SubscriptionId=s5, VerifiedByEmployeeId=3, CheckedInAt=today_dt(0, 7, 55), PhotoVerified=0))
o = order(16, PKG['day'], today_dt(0, 8, 10), 'Counter')
s16 = subscription(o, today_d(), note='gói ngày chưa check-in')
o = order(16, PKG['single'], today_dt(0, 8, 15), 'Counter', note='bán buổi lẻ hôm nay tại quầy (CBR-54)')
s16b = subscription(o, today_d(), pt=7)
sl = slot(7, today_d(), (20, 0), 'Booked', check=False)
session(s16b, sl, 7, 'Scheduled', 450000)
order(15, PKG['m1'], today_dt(0, 9, 0), 'Counter', status='Pending', note='chưa thu tiền → chặn chốt ca (CBR-78)')
o = order(10, PKG['day'], today_dt(0, 6, 40), 'Counter', status='Cancelled',
          paid=today_dt(0, 6, 42),
          cancel=('CancelledByReceptionist', today_dt(0, 6, 55), 'Khách đổi ý, đã hoàn tiền mặt tại quầy'),
          note='Paid → Cancelled trong cùng ca (CBR-11)')
subscription(o, today_d(), status='Cancelled')

# ============================================================ 8. ~40 KHÁCH THƯỜNG (dữ liệu dày cho danh sách / báo cáo)
FAM = ['Nguyễn', 'Trần', 'Lê', 'Phạm', 'Hoàng', 'Huỳnh', 'Phan', 'Vũ', 'Võ', 'Đặng', 'Bùi', 'Đỗ', 'Hồ', 'Ngô', 'Dương', 'Lý']
MID = ['Văn', 'Thị', 'Minh', 'Thanh', 'Quốc', 'Ngọc', 'Hữu', 'Thu', 'Đức', 'Hoài', 'Gia', 'Xuân']
GIV = ['Anh', 'Bình', 'Châu', 'Dũng', 'Giang', 'Hải', 'Hiếu', 'Hương', 'Khoa', 'Linh', 'Long', 'My', 'Nam', 'Nga',
       'Phong', 'Quang', 'Sơn', 'Tâm', 'Thảo', 'Trung', 'Tuấn', 'Uyên', 'Vy', 'Yến', 'Hùng', 'Lan', 'Phúc', 'Thắng']
ASCII = str.maketrans('àáảãạăằắẳẵặâầấẩẫậèéẻẽẹêềếểễệìíỉĩịòóỏõọôồốổỗộơờớởỡợùúủũụưừứửữựỳýỷỹỵđ',
                      'aaaaaaaaaaaaaaaaaeeeeeeeeeeeiiiiiooooooooooooooooouuuuuuuuuuuyyyyyd')
PREFIX = ['090', '091', '093', '097', '098', '032', '035', '070', '077', '081', '085', '086', '096']
used_names = set()
BULK = []
for i in range(40):
    pid = 201 + i
    while True:
        fam, mid, giv = rng.choice(FAM), rng.choice(MID), rng.choice(GIV)
        if (fam, giv) not in used_names:
            used_names.add((fam, giv))
            break
    name = f'{fam} {mid} {giv}'
    has_email = i % 6 != 5
    email = f'{giv.lower().translate(ASCII)}.{fam.lower().translate(ASCII)}{pid}@gmail.com' if has_email else None
    phone = f'{PREFIX[i % len(PREFIX)]}{7000000 + pid * 37:07d}'
    joined = datetime(2026, 6, 1, 8, 0) + timedelta(days=rng.randint(0, 112), minutes=rng.randint(0, 720))
    while joined.date() in CLOSED or joined.date() >= d_(2026, 10, 3):
        joined -= timedelta(days=1)
    dob = d_(rng.randint(1975, 2006), rng.randint(1, 12), rng.randint(1, 28))
    P(pid, name, dob, phone, email, joined)
    C(pid, pid, f'KH-{2000 + i + 1}', joined + timedelta(minutes=5), photo=f'/uploads/checkin/kh-{2001 + i}.jpg')
    if has_email:
        A(pid, pid, joined + timedelta(minutes=8), last=joined + timedelta(days=rng.randint(1, 40)))
    BULK.append(pid)


def counter_time(d):
    t = datetime.combine(d, time(rng.randint(6, 20), rng.choice([0, 10, 20, 30, 40, 50])))
    return t


for idx, cid in enumerate(BULK):
    joined = get('Customers', cid)['JoinedAt']
    kind = idx % 10
    if kind == 9:  # chỉ mua gói ngày vài lần
        for k in range(rng.randint(1, 3)):
            d = joined.date() + timedelta(days=k * rng.randint(5, 15))
            if d >= d_(2026, 10, 3) or d in CLOSED:
                continue
            o = order(cid, PKG['day'], counter_time(d), 'Counter', voucher=6 if (rng.random() < 0.4 and d >= d_(2026, 9, 1)) else None)
            s = subscription(o, d, status='Used')
            checkin(cid, get('Orders', o)['CreatedAt'] + timedelta(minutes=3), sub=s)
        continue
    key = rng.choices(['m1', 'm3', 'm9', 'm12'], [50, 30, 10, 10])[0]
    channel = 'Online' if (get('Customers', cid) and account_of_person(cid) and rng.random() < 0.4) else 'Counter'
    t = joined + timedelta(minutes=10)
    v = 1 if (rng.random() < 0.3 and t.date() >= d_(2026, 9, 1)) else None
    o = order(cid, PKG[key], t, channel, voucher=v if channel == 'Online' else None, fail_first=rng.random() < 0.1)
    s = subscription(o, get('Orders', o)['PaidAt'].date())
    sub = get('Subscriptions', s)
    # gia hạn nối tiếp (DBR-52) cho một số khách đã hết hoặc sắp hết
    if sub['EndDate'] < d_(2026, 10, 1) and rng.random() < 0.5:
        rd = sub['EndDate'] - timedelta(days=rng.randint(0, 5))
        while rd in CLOSED:
            rd -= timedelta(days=1)
        if rd < d_(2026, 10, 3):
            o2 = order(cid, PKG[rng.choice(['m1', 'm3'])], counter_time(rd), 'Counter')
            subscription(o2, sub['EndDate'] + timedelta(days=1), note='gia hạn nối tiếp (DBR-52)')

# ---- thêm gói PT cho khách thường (lịch PT kín hơn)
extra_pt = [(13, (7, 30), d_(2026, 9, 21), 'pt1'), (6, (6, 0), d_(2026, 9, 21), 'pt1'), (5, (15, 0), d_(2026, 9, 28), 'pt2'),
            (14, (10, 0), d_(2026, 9, 14), 'pt1'), (7, (19, 0), d_(2026, 10, 5), 'pt1')]
picked = []
for pt, fixed, start, key in extra_pt:
    sale = start - timedelta(days=3)
    while sale in CLOSED:
        sale -= timedelta(days=1)
    for cid in BULK:
        if cid in picked or not membership_valid(cid, sale.date() if isinstance(sale, datetime) else sale):
            continue
        age = relativedelta(sale, get('Persons', cid)['DateOfBirth']).years
        if age < 10:
            continue
        picked.append(cid)
        o = order(cid, PKG[key], datetime.combine(sale, time(10, 30)), 'Counter', method='Transfer')
        pt_package(o, pt, start, fixed, note='khách thường mua gói PT')
        break

# ---- khách vãng lai mua gói ngày rải đều tháng 9 (báo cáo doanh thu có số liệu mỗi ngày)
WALKIN = [c for i, c in enumerate(BULK) if i % 10 == 9]
day = d_(2026, 9, 1)
while day <= d_(2026, 10, 2):
    if day not in CLOSED:
        for k in range(rng.randint(1, 3)):
            cid = rng.choice(WALKIN)
            if any(s['StartDate'] == day for s in subs_of(cid, ('DayPass',))):
                continue
            o = order(cid, PKG['day'], datetime.combine(day, time(rng.randint(6, 20), rng.choice([5, 25, 45]))), 'Counter',
                      voucher=6 if rng.random() < 0.3 else None, method=rng.choice(['Cash', 'Cash', 'Transfer']))
            s_ = subscription(o, day, status='Used')
            checkin(cid, get('Orders', o)['CreatedAt'] + timedelta(minutes=4), sub=s_)
    day += timedelta(days=1)

# ---- check-in hằng ngày: tháng 8 thưa, tháng 9 dày
REGULARS = BULK + [1, 2, 6, 7, 8, 9, 11, 13, 17, 18, 19, 21, 103]
day = d_(2026, 8, 1)
while day <= d_(2026, 10, 2):
    if day not in CLOSED:
        p = 0.18 if day.month == 8 else 0.38
        for cid in REGULARS:
            if membership_valid(cid, day) and rng.random() < p:
                checkin(cid, datetime.combine(day, time(rng.randint(6, 20), rng.choice([0, 15, 30, 45]))))
        # khách vào 2 lượt trong ngày (BR-34)
        if day.day % 7 == 0 and membership_valid(1, day):
            checkin(1, datetime.combine(day, time(6, 10)))
            checkin(1, datetime.combine(day, time(18, 40)))
    day += timedelta(days=1)

# ---- buổi lẻ tháng 9 của khách thường (online đặt trong tuần, có Missed)
pts = [5, 6, 7, 13]
LEAVE_BLOCK = {(5, d_(2026, 9, 19))}
cands = [(7, 0), (8, 30), (10, 0), (14, 0), (15, 30), (16, 0)]
made = 0
for cid in BULK:
    if made >= 18:
        break
    for _ in range(3):
        d = d_(2026, 9, rng.randint(5, 30))
        pt = rng.choice(pts)
        st = rng.choice(cands)
        if d in CLOSED or (pt, d) in LEAVE_BLOCK or not membership_valid(cid, d) or not free_at(pt, d, st):
            continue
        if pt == 13 and d < d_(2026, 8, 15):
            continue
        created = datetime.combine(d - timedelta(days=rng.randint(0, d.weekday() if d.weekday() < 5 else 1)), time(21, 0))
        if account_of_person(cust_person(cid)):
            single_session(cid, pt, d, st, 'Online', created, status='Missed' if made % 6 == 5 else None)
        else:
            single_session(cid, pt, d, st, 'Counter', datetime.combine(d, time(st[0] - 1 if st[0] > 6 else 6, 0)) if d not in CLOSED else None)
        made += 1
        break

# ============================================================ 9. LỊCH TRỐNG PT CÔNG BỐ (dày, có cuối tuần)
PUBLISH = {5: [(8, 0), (9, 30), (11, 0), (15, 0), (20, 0)],
           6: [(8, 0), (9, 30), (14, 0), (16, 0), (17, 30)],
           7: [(7, 0), (8, 30), (10, 0)],
           13: [(6, 0), (7, 30), (9, 0), (16, 0), (17, 30), (19, 0)],
           14: [(10, 0), (15, 0)]}
day = d_(2026, 10, 5)
while day <= d_(2026, 10, 31):
    for pt, times in PUBLISH.items():
        for st in times:
            if day in CLOSED:
                continue
            if day == d_(2026, 10, 11) and not (time(7, 0) <= time(*st) and G._mins(time(*st)) + 90 <= 18 * 60):
                continue  # ngày giờ đặc biệt 07:00–18:00
            if free_at(pt, day, st):
                slot(pt, day, st, 'Free', created=dt_(2026, 10, 1, 21, 0))
    day += timedelta(days=1)
# slot trống tháng 9 không ai đặt (lịch sử) + 1 slot PT gỡ (Withdrawn)
day = d_(2026, 9, 1)
while day <= d_(2026, 10, 2):
    if day not in CLOSED and day.weekday() in (0, 2, 4, 5):
        for pt in (5, 6, 13):
            st = (9, 30) if pt != 13 else (16, 0)
            if (pt, day) in LEAVE_BLOCK:
                continue
            if free_at(pt, day, st):
                slot(pt, day, st, 'Free')
    day += timedelta(days=1)
slot(5, d_(2026, 10, 9), (13, 0), 'Withdrawn', created=dt_(2026, 10, 1, 21, 0))
slot(8, d_(2026, 8, 20), (17, 0), 'Free', created=dt_(2026, 8, 1, 8, 0))   # PT Nam đã nghỉ — slot lịch sử

# ============================================================ 10. ĐỦ 2 CA MỖI NGÀY MỞ CỬA THÁNG 9 + TIỀN KỲ VỌNG
day = d_(2026, 9, 1)
while day <= d_(2026, 10, 2):
    if day not in CLOSED:
        shift_for(datetime.combine(day, time(8, 0)))
        shift_for(datetime.combine(day, time(15, 0)))
    day += timedelta(days=1)
for s in DB['Shifts']:
    if s['Status'] != 'Closed':
        continue
    cash = 0
    for o in DB['Orders']:
        if o['ShiftId'] == s['ShiftId'] and o['Status'] != 'Cancelled':
            for t in DB['Transactions']:
                if t['OrderId'] == o['OrderId'] and t['Status'] == 'Success' and t['PaymentMethod'] == 'Cash':
                    cash += t['Amount']
    s['ExpectedCash'] = s['CountedCash'] = cash
VAR_CASES = [(50000, 'Thừa 50.000đ chưa rõ nguồn, đã lập biên bản bàn giao'),
             (-20000, 'Thiếu 20.000đ do thối nhầm cho khách, lễ tân tự bù')]
sept_cash = [s for s in DB['Shifts'] if s['Status'] == 'Closed' and not isinstance(s['WorkDate'], Live)
             and s['WorkDate'] >= d_(2026, 9, 15) and s['ExpectedCash'] >= 100000]
for s, (diff, reason) in zip(sept_cash[::7][:2], VAR_CASES):
    s['CountedCash'] = s['ExpectedCash'] + diff
    s['VarianceReason'] = reason
    s['_note'] = 'chốt ca lệch tiền (BR-46)'

# voucher: số lượt = đơn chưa hủy đang dùng (BR-43, BR-44)
for v in DB['Vouchers']:
    v['UsedCount'] = sum(1 for o in DB['Orders'] if o['VoucherId'] == v['VoucherId'] and o['Status'] != 'Cancelled')
assert get('Vouchers', 3)['UsedCount'] == get('Vouchers', 3)['MaxUsage'], 'FULLBOOKED phải hết lượt'

# ============================================================ 11. TRẠNG THÁI KHÁCH theo ngày mốc (DBR-34, DBR-47)
LIVE_STATUS = {5: 'Active', 16: 'Active', 15: 'New', 10: 'New'}
for c in DB['Customers']:
    cid = c['CustomerId']
    if c['Status'] == 'Inactive':
        continue
    if cid in LIVE_STATUS:
        c['Status'] = LIVE_STATUS[cid]
        continue
    mem = [s for s in subs_of(cid, ('Membership', 'DayPass')) if not isinstance(s['StartDate'], Live)]
    if not mem:
        c['Status'] = 'New'
        continue
    frozen = any(s['Status'] == 'Frozen' for s in mem)
    cover = any(s['StartDate'] <= SEED <= s['EndDate'] for s in mem if s['SubscriptionType'] == 'Membership')
    c['Status'] = 'Frozen' if frozen else ('Active' if cover else 'Expired')
for s in DB['Subscriptions']:
    if isinstance(s['EndDate'], Live) or s['Status'] in ('Frozen', 'Cancelled', 'Used'):
        continue
    if s['SubscriptionType'] in ('Membership', 'PTSingle', 'PTCouple', 'DayPass', 'SingleSession') and s['EndDate'] < SEED:
        s['Status'] = 'Expired'

# ============================================================ 12. XÓA HỒ SƠ, MÃ XÁC THỰC
add('CustomerDeletionRequests', dict(CustomerId=13, RequestedByEmployeeId=3, ReviewedByEmployeeId=None,
                                     Reason='Khách yêu cầu xóa toàn bộ dữ liệu cá nhân', Status='Pending',
                                     RejectReason=None, RequestedAt=dt_(2026, 9, 29, 15, 0), ReviewedAt=None),
    'còn gói đang chạy → chờ Manager (CBR-16)')
add('CustomerDeletionRequests', dict(CustomerId=12, RequestedByEmployeeId=4, ReviewedByEmployeeId=2,
                                     Reason='Khách chuyển vào Nam, không tập nữa', Status='Approved', RejectReason=None,
                                     RequestedAt=dt_(2026, 9, 14, 10, 0), ReviewedAt=dt_(2026, 9, 15, 16, 0)))
add('CustomerDeletionRequests', dict(CustomerId=1, RequestedByEmployeeId=3, ReviewedByEmployeeId=2,
                                     Reason='Khách nhờ xóa hộ qua điện thoại', Status='Rejected',
                                     RejectReason='Chưa xác minh được danh tính người yêu cầu, mời khách tới quầy',
                                     RequestedAt=dt_(2026, 9, 10, 9, 0), ReviewedAt=dt_(2026, 9, 10, 14, 20)))
add('CustomerDeletionRequests', dict(CustomerId=20, RequestedByEmployeeId=3, ReviewedByEmployeeId=2,
                                     Reason='Lễ tân Hoa xin xóa hồ sơ khách của mình', Status='Approved', RejectReason=None,
                                     RequestedAt=dt_(2026, 9, 4, 10, 0), ReviewedAt=dt_(2026, 9, 5, 15, 0)),
    'xóa hồ sơ khách nhưng vẫn là nhân viên (CBR-86)')


def vcode(acc, email, purpose, code, sent, exp, failed=0, locked=None, used=None, note=None):
    add('VerificationCodes', dict(AccountId=acc, Email=email, Purpose=purpose, Code=code, SentAt=sent, ExpiresAt=exp,
                                  FailedAttempts=failed, LockedUntil=locked, UsedAt=used), note)


vcode(10, 'ngoc.ly99@gmail.com', 'Recovery', '481920', dt_(2026, 9, 21, 21, 15), dt_(2026, 9, 21, 21, 30), used=dt_(2026, 9, 21, 21, 18))
vcode(None, 'toan.phan@gmail.com', 'SignUp', '730154', dt_(2026, 9, 22, 18, 50), dt_(2026, 9, 22, 19, 5), failed=1, note='đăng ký bỏ dở, mã hết hạn')
vcode(3, 'quan.le92@gmail.com', 'Recovery', '915003', dt_(2026, 9, 23, 8, 20), dt_(2026, 9, 23, 8, 35), failed=5, locked=dt_(2026, 9, 23, 8, 40))
vcode(108, 'thao.luong@gmail.com', 'Recovery', '246810', now_(-2), now_(13), note='nhập 246810 là đúng')
for k, m in enumerate((-50, -40, -30, -5)):
    vcode(109, 'manh.ngo@gmail.com', 'Recovery', f'10000{k + 1}', now_(m), now_(m + 15), note='4 mã trong 1 giờ → lần 5 bị khóa (CBR-94)' if k == 3 else None)
vcode(110, 'khoa.dinh@gmail.com', 'Recovery', '555111', Live('now', minutes=0), now_(15), note='vừa gửi → phải chờ 60 giây')
vcode(111, 'binh.ta@gmail.com', 'Recovery', '777222', now_(-3), now_(12), failed=5, locked=now_(5), note='sai 5 lần → khóa 5 phút')
vcode(112, 'quynh.cao@gmail.com', 'Recovery', '999000', now_(-30), now_(-15), note='đã hết hạn → MSG44')
vcode(1, 'an.nguyen.moi@gmail.com', 'EmailChange', '135790', now_(-1), now_(14), note='An đổi email, mã 135790')
vcode(15, 'son.dinh@gms.vn', 'PhoneChange', '864200', now_(-1), now_(14), note='PT Sơn đổi SĐT: mã gửi về email HIỆN TẠI (CBR-110)')
vcode(None, 'khach.moi@gmail.com', 'SignUp', '112233', now_(-1), now_(14), note='đăng ký mới, mã 112233')
vcode(None, 'yen.doan@gmail.com', 'Claim', '445566', now_(-1), now_(14), note='kích hoạt hồ sơ Yến, mã 445566')

# ============================================================ 13. NHÂN SỰ, THIẾT BỊ
def LR(**kw):
    """Map mẫu đơn một ngày sang schema FromDate/ToDate/Days hiện tại."""
    leave_date = kw.pop('LeaveDate')
    kw['FromDate'] = leave_date
    kw['ToDate'] = leave_date
    kw['Days'] = 1.0 if kw['ShiftType'] == 'FullDay' else 0.5
    kw['AssignedToEmployeeId'] = None
    return add('LeaveRequests', kw)
LR(EmployeeId=5, ReviewedByEmployeeId=2, LeaveType='Annual', LeaveDate=d_(2026, 9, 19), ShiftType='Morning', Reason='Về quê giỗ ông',
   Status='Approved', RejectReason=None, SubmittedAt=dt_(2026, 9, 17, 10, 0), ReviewedAt=dt_(2026, 9, 17, 15, 30), ReminderSentAt=None)
LR(EmployeeId=7, ReviewedByEmployeeId=2, LeaveType='Unpaid', LeaveDate=d_(2026, 9, 19), ShiftType='Afternoon', Reason='Có việc cá nhân',
   Status='Rejected', RejectReason='Ca chiều chỉ còn 2 PT, duyệt sẽ thiếu người (CBR-88)', SubmittedAt=dt_(2026, 9, 18, 9, 0),
   ReviewedAt=dt_(2026, 9, 18, 17, 0), ReminderSentAt=None)
LR(EmployeeId=4, ReviewedByEmployeeId=None, LeaveType='Annual', LeaveDate=d_(2026, 9, 16), ShiftType='Morning', Reason='Đưa con đi khám',
   Status='Rejected', RejectReason='Quá hạn duyệt', SubmittedAt=dt_(2026, 9, 15, 14, 0), ReviewedAt=dt_(2026, 9, 15, 20, 0),
   ReminderSentAt=dt_(2026, 9, 15, 19, 0))
LR(EmployeeId=3, ReviewedByEmployeeId=2, LeaveType='Annual', LeaveDate=d_(2026, 10, 14), ShiftType='Afternoon', Reason='Đi đám cưới bạn',
   Status='Approved', RejectReason=None, SubmittedAt=dt_(2026, 10, 1, 9, 30), ReviewedAt=dt_(2026, 10, 1, 11, 0), ReminderSentAt=None)
LR(EmployeeId=6, ReviewedByEmployeeId=None, LeaveType='Sick', LeaveDate=d_(2026, 10, 10), ShiftType='FullDay', Reason='Lịch tái khám định kỳ',
   Status='Pending', RejectReason=None, SubmittedAt=dt_(2026, 10, 2, 8, 0), ReviewedAt=None, ReminderSentAt=None)
LR(EmployeeId=13, ReviewedByEmployeeId=None, LeaveType='Annual', LeaveDate=d_(2026, 10, 12), ShiftType='Morning', Reason='Thi chứng chỉ huấn luyện',
   Status='Pending', RejectReason=None, SubmittedAt=dt_(2026, 10, 2, 14, 0), ReviewedAt=None, ReminderSentAt=None)
LR(EmployeeId=9, ReviewedByEmployeeId=2, LeaveType='Sick', LeaveDate=d_(2026, 9, 24), ShiftType='FullDay', Reason='Sốt cao, có giấy khám',
   Status='Approved', RejectReason=None, SubmittedAt=dt_(2026, 9, 23, 11, 0), ReviewedAt=dt_(2026, 9, 23, 16, 0), ReminderSentAt=None)

FAC = [('Máy chạy bộ Technogym', 'Cardio', 'Khu cardio tầng 1', 8, 'Available'),
       ('Xe đạp tập', 'Cardio', 'Khu cardio tầng 1', 6, 'Available'),
       ('Giàn tạ đa năng', 'Machines', 'Khu tạ tầng 2', 3, 'Available'),
       ('Bộ tạ đơn 2–40kg', 'FreeWeights', 'Khu tạ tầng 2', 20, 'Available'),
       ('Máy ép ngực', 'Machines', 'Khu tạ tầng 2', 2, 'UnderMaintenance'),
       ('Ghế đẩy vai', 'Machines', 'Khu tạ tầng 2', 2, 'Available'),
       ('Tủ khóa cá nhân', 'Amenities', 'Phòng thay đồ', 60, 'Available'),
       ('Máy leo cầu thang cũ', 'Cardio', 'Kho', 1, 'Retired'),
       ('Máy kéo cáp', 'Machines', 'Khu tạ tầng 2', 2, 'Available'),
       ('Thảm yoga', 'Amenities', 'Phòng tập nhóm', 30, 'Available')]
for f in FAC:
    add('Facilities', dict(FacilityName=f[0], Category=f[1], Location=f[2], Quantity=f[3], Status=f[4]))


def mreq(fid, rep, desc, status, pri='Normal', tag=0, rev=None, rev_at=None, setum=None, rej=None, closer=None,
         closed=None, accept=None, rep_at=None, note=None):
    n = NEXT['MaintenanceRequests']
    add('MaintenanceRequests', dict(RequestCode=f'BT-{n:04d}', FacilityId=fid, ReportedByEmployeeId=rep,
                                    ReviewedByEmployeeId=rev, ClosedByEmployeeId=closer, Priority=pri,
                                    WarningTagPlaced=tag, Description=desc, Status=status, SetUnderMaintenance=setum,
                                    RejectReason=rej, AcceptanceNote=accept, ReportedAt=rep_at, ReviewedAt=rev_at,
                                    ClosedAt=closed), note)


mreq(5, 3, 'Máy ép ngực kêu to khi đẩy, nghi hỏng bạc đạn', 'InProgress', 'Urgent', 1, 2, dt_(2026, 9, 20, 11, 0), 1, rep_at=dt_(2026, 9, 20, 9, 0), note='thiết bị chuyển Under maintenance (CBR-101)')
mreq(1, 4, 'Máy chạy số 3 bị giật khi tăng tốc', 'Closed', 'Normal', 1, 2, dt_(2026, 9, 10, 10, 0), 1, closer=3, closed=dt_(2026, 9, 14, 16, 0), accept='Đã thay dây curoa, chạy thử 30 phút ổn định', rep_at=dt_(2026, 9, 10, 8, 0))
mreq(4, 3, 'Xin mua thêm tạ đơn 45kg cho khu tạ', 'Rejected', 'Low', 0, 2, dt_(2026, 9, 12, 17, 30), rej='Ngoài phạm vi bảo trì, chuyển sang kế hoạch mua sắm quý sau', rep_at=dt_(2026, 9, 12, 14, 0))
mreq(2, 3, 'Xe đạp số 2 kẹt bàn đạp, không xoay được', 'Open', 'Normal', 1, rep_at=dt_(2026, 10, 2, 8, 30))
mreq(7, 4, 'Tủ khóa số 12 hỏng chốt, không khóa được', 'Open', 'Low', 0, rep_at=dt_(2026, 10, 1, 20, 0))
mreq(9, 9, 'Dây cáp máy kéo bị sờn ở đầu nối', 'InProgress', 'Normal', 1, 2, dt_(2026, 9, 30, 9, 0), 0, rep_at=dt_(2026, 9, 29, 19, 0), note='duyệt nhưng KHÔNG chuyển Under maintenance (CBR-101 bỏ tick)')
mreq(10, 11, 'Thảm yoga số 4 bị rách góc', 'Closed', 'Low', 0, 2, dt_(2026, 9, 6, 9, 0), 0, closer=11, closed=dt_(2026, 9, 8, 15, 0), accept='Đã thay thảm mới', rep_at=dt_(2026, 9, 5, 18, 0))

for i, (e, d, st, ci, co, stt, note) in enumerate([
    (3, d_(2026, 10, 2), 'Morning', dt_(2026, 10, 2, 5, 25), dt_(2026, 10, 2, 13, 5), 'Present', None),
    (4, d_(2026, 10, 2), 'Afternoon', dt_(2026, 10, 2, 13, 15), dt_(2026, 10, 2, 22, 5), 'Late', 'Đi muộn 15 phút do tắc đường'),
    (5, d_(2026, 10, 2), 'Afternoon', dt_(2026, 10, 2, 13, 0), dt_(2026, 10, 2, 21, 30), 'Present', None),
    (6, d_(2026, 10, 2), 'Afternoon', dt_(2026, 10, 2, 12, 55), dt_(2026, 10, 2, 21, 35), 'Present', None),
    (5, d_(2026, 9, 19), 'Morning', None, None, 'OnLeave', 'Nghỉ phép đã được duyệt'),
    (9, d_(2026, 9, 24), 'Morning', None, None, 'OnLeave', 'Nghỉ ốm đã được duyệt')]):
    add('StaffAttendances', dict(EmployeeId=e, WorkDate=d, ShiftType=st, CheckInAt=ci,
                                 CheckInPhoto=f'/uploads/attendance/nv-{e:04d}-{d:%m%d}.jpg' if ci else None,
                                 CheckOutAt=co, Status=stt, Note=note))

# ============================================================ 14. PT ↔ KHÁCH
NOTE = lambda pt, c, txt, up, dl=None, note=None: add('PrivateNotes', dict(PtEmployeeId=pt, CustomerId=c, Content=txt,
                                                                          CreatedAt=up - timedelta(days=3), UpdatedAt=up, DeletedAt=dl), note)
NOTE(5, 6, 'Ghi chú cũ: chưa đo InBody.', dt_(2026, 9, 7, 20, 0), dt_(2026, 9, 8, 9, 0), 'đã xóa → viết được bản mới (BR-59)')
NOTE(5, 6, 'Khách đau vai phải, tránh bài đẩy vai qua đầu. Ưu tiên tập chân và lưng.', dt_(2026, 9, 8, 20, 0))
NOTE(6, 7, 'Khách tiến bộ nhanh, có thể tăng tạ 5kg mỗi 2 tuần.', dt_(2026, 9, 15, 21, 30))
NOTE(6, 8, 'Ghi chú cũ đã lỗi thời, viết lại sau.', dt_(2026, 8, 25, 21, 0), dt_(2026, 9, 1, 9, 0))
NOTE(7, 17, 'Khách là PT của phòng, tập nặng được, chú ý cổ tay trái.', dt_(2026, 9, 16, 13, 30), note='David ghi chú về Kiều Trang — Trang (PT) không thấy (CBR-92)')
NOTE(7, 4, 'Khách đang bảo lưu, khi quay lại giảm tạ 20% tuần đầu.', dt_(2026, 9, 27, 18, 0))
NOTE(5, 21, 'Khách không dùng smartphone, liên hệ qua số điện thoại.', dt_(2026, 9, 14, 8, 0))

PLAN = lambda pt, c, typ, title, content, pub=None, dl=None, note=None, pub_title=None, pub_content=None: add('TrainingPlans', dict(
    PtEmployeeId=pt, CustomerId=c, PlanType=typ, Title=title, Content=content,
    PublishedTitle=(pub_title or title) if pub else None, PublishedContent=(pub_content or content) if pub else None,
    PublishedAt=pub, CreatedAt=dt_(2026, 9, 7, 20, 0), UpdatedAt=dt_(2026, 9, 20, 21, 0), DeletedAt=dl), note)
PLAN(5, 6, 'Workout', 'Giáo án 8 tuần — tăng cơ nền (bản sửa)', 'Tuần 1–2: full body 3 buổi.\nTuần 3–8: đẩy/kéo/chân, thêm 1 buổi cardio nhẹ.',
     dt_(2026, 9, 8, 21, 0), pub_title='Giáo án 8 tuần — tăng cơ nền', pub_content='Tuần 1–2: full body 3 buổi.\nTuần 3–8: đẩy/kéo/chân.',
     note='nháp mới hơn bản đã xuất bản → khách vẫn thấy bản cũ (BR-60)')
PLAN(5, 6, 'Meal', 'Thực đơn thâm hụt nhẹ', 'Bữa sáng: yến mạch + trứng.\nBữa trưa: ức gà + cơm gạo lứt.', note='chưa xuất bản → khách chưa thấy')
PLAN(6, 7, 'Workout', 'Giáo án couple — 8 tuần', 'Tập theo cặp, luân phiên đẩy và kéo.', dt_(2026, 8, 26, 20, 0))
PLAN(6, 8, 'Workout', 'Giáo án cũ', 'Nội dung cũ, đã thay bằng bản mới.', dt_(2026, 8, 20, 20, 0), dt_(2026, 8, 26, 19, 0), note='đã xóa → khách mất ngay (BR-61)')
PLAN(6, 8, 'Workout', 'Giáo án couple — người đi kèm', 'Giảm khối lượng 20% so với người đứng tên.', dt_(2026, 8, 27, 20, 0))
PLAN(7, 4, 'Meal', 'Thực đơn tăng cơ', 'Ăn 5 bữa/ngày, mỗi bữa 25–30g đạm.', dt_(2026, 9, 10, 20, 0))
PLAN(7, 17, 'Workout', 'Giáo án powerlifting 4 tuần', 'Squat – Bench – Deadlift, 4 buổi/tuần.', dt_(2026, 9, 15, 20, 0))

# ============================================================ 15. FEEDBACK, BLOG, THÔNG BÁO
FB = [(1, 'Phòng tắm hết nước nóng vào buổi tối, mong phòng gym kiểm tra.', dt_(2026, 10, 2, 21, 10), None),
      (2, 'Nhân viên lễ tân rất nhiệt tình, cảm ơn phòng gym.', dt_(2026, 9, 21, 19, 0), dt_(2026, 9, 22, 9, 0)),
      (6, 'Đề xuất mở thêm lớp yoga buổi sáng.', dt_(2026, 9, 20, 7, 30), dt_(2026, 9, 20, 15, 0)),
      (11, 'The air conditioning in the weights area is too cold in the morning.', dt_(2026, 9, 19, 8, 15), None),
      (7, 'Máy chạy số 3 chạy giật, đã báo lễ tân nhưng ghi nhận lại cho chắc.', dt_(2026, 9, 9, 20, 0), dt_(2026, 9, 10, 8, 30)),
      (17, 'Khu tạ tự do hơi chật vào giờ cao điểm 18h–20h.', dt_(2026, 9, 28, 20, 30), None)]
for cid in BULK[:8]:
    FB.append((cid, rng.choice(['Mong phòng gym mở thêm máy chạy bộ.', 'Tủ khóa hay bị kẹt, nhờ kiểm tra giúp.',
                                'Nhạc trong phòng hơi to vào buổi tối.', 'Huấn luyện viên rất tận tâm, rất hài lòng.',
                                'Mong có thêm khu giãn cơ riêng.']), dt_(2026, 9, rng.randint(1, 30), 20, 0), None if rng.random() < 0.5 else dt_(2026, 10, 1, 9, 0)))
for c, txt, at, rd in FB:
    add('Feedbacks', dict(CustomerId=c, Content=txt, SubmittedAt=at, IsRead=1 if rd else 0, ReadAt=rd, ReadByEmployeeId=2 if rd else None,
                         RatedPtEmployeeId=None, Rating=None, RatingMonth=None, UpdatedAt=None))

# DBR-66/67: PT 5 đủ ba khách, PT 6 chưa đủ; mỗi khách có buổi tháng 09.
RATINGS = [
    (1, 5, 1, 'PT hướng dẫn chưa rõ, tôi mong được hỗ trợ kỹ hơn.', 0, 0),
    (2, 5, 2, 'Tôi muốn PT giải thích bài tập kỹ hơn trước khi tập.', 10, 1),
    (6, 5, 5, 'Tôi sửa đánh giá: PT đã hỗ trợ tốt hơn buổi trước.', 20, 0),
    (7, 6, 4, 'PT hướng dẫn dễ hiểu và quan tâm tới người tập.', 30, 0),
    (201, 6, 5, 'PT hỗ trợ tốt và điều chỉnh bài tập phù hợp.', 40, 0),
]
for customer, pt, rating, content, minute, read in RATINGS:
    add('Feedbacks', dict(CustomerId=customer, Content=content, SubmittedAt=dt_(2026, 9, 30, 10, minute),
                         IsRead=read, ReadAt=dt_(2026, 9, 30, 11) if read else None,
                         ReadByEmployeeId=2 if read else None, RatedPtEmployeeId=pt,
                         Rating=rating, RatingMonth=d_(2026, 9, 1),
                         UpdatedAt=dt_(2026, 9, 30, 12) if customer == 6 else None))

LONG = ''.join(['<p>Mỗi buổi tập nên bắt đầu bằng 5–10 phút làm nóng toàn thân, sau đó là các động tác khởi động riêng cho nhóm cơ sắp tập. Tăng dần biên độ và tốc độ, không kéo giãn tĩnh quá lâu trước khi nâng tạ nặng.</p>'] * 20)
BLOGS = [
    ('Ba lỗi squat phổ biến', 'Training', 'Squat sai tư thế dễ gây đau gối và lưng dưới.', '<p>Lỗi 1: gối đổ vào trong. Lỗi 2: gót chân nhấc khỏi sàn. Lỗi 3: lưng cong khi xuống thấp.</p>', '/images/blog/squat.jpg', 'Active', dt_(2026, 9, 1, 10, 0)),
    ('Protein bao nhiêu là đủ?', 'Nutrition', 'Người tập tạ cần bao nhiêu gram protein mỗi ngày.', '<p>Khuyến nghị 1.6–2.2g/kg cân nặng mỗi ngày.</p>', '/images/blog/protein.jpg', 'Active', dt_(2026, 9, 8, 10, 0)),
    ('Lịch nghỉ lễ Quốc khánh', 'News', 'Phòng gym nghỉ ngày 02/09.', '<p>Phòng gym nghỉ cả ngày 02/09, mở lại bình thường từ 03/09.</p>', '/images/blog/news.jpg', 'Active', dt_(2026, 8, 25, 9, 0)),
    ('Khuyến mãi tháng 6 (đã kết thúc)', 'News', 'Chương trình đã kết thúc.', '<p>Nội dung khuyến mãi cũ.</p>', '/images/blog/promo.jpg', 'Inactive', dt_(2026, 6, 1, 9, 0)),
    ('5 bài khởi động giúp giảm chấn thương trước buổi tập nặng', 'Training', 'Bỏ qua khởi động là nguyên nhân phổ biến gây chấn thương vai và gối.', '<h2>Vì sao phải khởi động</h2>' + LONG, '/images/blog/khoi-dong.jpg', 'Active', dt_(2026, 9, 12, 8, 0)),
    ('Ăn gì trước và sau buổi tập để tối ưu phục hồi cơ bắp', 'Nutrition', 'Thời điểm và loại thực phẩm nạp vào quanh buổi tập ảnh hưởng trực tiếp đến hiệu suất.', '<h2>Trước buổi tập</h2><p>Ăn bữa có tinh bột và đạm vừa phải trước 60–90 phút.</p><h2>Sau buổi tập</h2><ul><li><strong>Đạm:</strong> 20–40g</li><li><strong>Tinh bột:</strong> bù năng lượng</li></ul>', '/images/blog/an-gi.jpg', 'Active', dt_(2026, 9, 8, 8, 0)),
    ('Super Gym khai trương khu giãn cơ & phục hồi', 'News', 'Khu foam roller và ghế massage chính thức mở cửa từ tuần này.', '<p>Khu giãn cơ mở cửa từ 06:00 đến 22:00, miễn phí cho hội viên.</p>', '/images/blog/gian-co.jpg', 'Active', dt_(2026, 9, 2, 8, 0)),
    ('Ngủ đủ giấc quan trọng thế nào với người tập gym?', 'Nutrition', 'Tập nặng nhưng ngủ ít có thể khiến mọi nỗ lực gần như không hiệu quả.', '<p>Người tập nên ngủ 7–9 tiếng mỗi đêm.</p>', '/images/blog/ngu-du-giac.jpg', 'Active', dt_(2026, 8, 2, 8, 0)),
    ('Hướng dẫn chọn gói tập phù hợp với mục tiêu của bạn', 'Training', 'Gói 1 tháng hay gói 12 tháng — nên bắt đầu từ đâu.', '<p>Người mới nên thử gói 1 tháng để tạo thói quen.</p>', '/images/blog/chon-goi.jpg', 'Active', dt_(2026, 7, 27, 8, 0)),
    ('Cardio hay tạ nặng trước? Thứ tự tối ưu cho mục tiêu giảm mỡ', 'Training', 'Thứ tự bài tập ảnh hưởng đến hiệu suất từng phần.', '<p>Ưu tiên bài tạ khi còn sung sức.</p>', '/images/blog/cardio.jpg', 'Active', dt_(2026, 7, 20, 8, 0)),
    ('Creatine có thực sự cần thiết?', 'Nutrition', 'Creatine có đáng để bổ sung vào chế độ tập của bạn?', '<p>Creatine monohydrate 3–5g mỗi ngày an toàn với người khỏe mạnh.</p>', '/images/blog/creatine.jpg', 'Active', dt_(2026, 7, 14, 8, 0)),
    ('Super Gym ra mắt ứng dụng đặt lịch PT mới', 'News', 'Xem khung giờ rảnh của PT, đặt buổi lẻ ngay trên điện thoại.', '<p>Ứng dụng hỗ trợ đặt buổi lẻ, xem lịch tập.</p>', '/images/blog/app-pt.jpg', 'Active', dt_(2026, 7, 5, 8, 0)),
    ('Tập lưng đúng cách: 4 bài kéo cho dân văn phòng', 'Training', None, '<p>Lat pulldown, seated row, face pull và band pull-apart giúp cân bằng nhóm cơ lưng trên.</p>', '/images/blog/tap-lung.jpg', 'Active', dt_(2026, 6, 28, 8, 0)),
    ('Nhịn ăn gián đoạn có phù hợp với người tập gym?', 'Nutrition', 'IF giúp một số người kiểm soát calo dễ hơn.', '<p>Nếu mục tiêu là tăng cơ, hãy đảm bảo đủ đạm.</p>', '/images/blog/nhin-an.jpg', 'Active', dt_(2026, 6, 20, 8, 0)),
    ('Super Gym mở rộng giờ hoạt động cuối tuần', 'News', 'Phòng tập mở cửa sớm hơn vào Thứ 7 và Chủ Nhật.', '<p>Giờ mở cửa cuối tuần áp dụng theo lịch mới.</p>', '/images/blog/gio-tap.jpg', 'Active', dt_(2026, 6, 12, 8, 0)),
    ('Bài nháp: lịch tập Tết 2027', 'News', 'Đang soạn.', '<p>Nội dung đang soạn.</p>', None, 'Inactive', None),
]
for t, cat, ex, ct, cover, st, pub in BLOGS:
    created = (pub or dt_(2026, 10, 1, 9, 0)) - timedelta(days=1)
    add('BlogPosts', dict(AuthorEmployeeId=2, Title=t, Category=cat, Excerpt=ex, Content=ct, CoverImage=cover, Status=st,
                          PublishedAt=pub, CreatedAt=created, UpdatedAt=created))


def accounts_for(aud):
    out = []
    for a in DB['Accounts']:
        if a['Status'] != 'Active':
            continue
        pid = a['PersonId']
        emps = [e for e in DB['Employees'] if e['PersonId'] == pid and e['Status'] == 'Working']
        cus = [c for c in DB['Customers'] if c['PersonId'] == pid and c['Status'] != 'Inactive']
        pos = {e['Position'] for e in emps}
        if aud == 'AllCustomers' and cus: out.append(a['AccountId'])
        elif aud == 'ActiveMembers' and any(c['Status'] in ('Active', 'Frozen') for c in cus): out.append(a['AccountId'])
        elif aud == 'PTs' and 'PT' in pos: out.append(a['AccountId'])
        elif aud == 'Receptionists' and 'Receptionist' in pos: out.append(a['AccountId'])
        elif aud == 'AllStaff' and emps: out.append(a['AccountId'])
    return out


def notify(by, title, content, aud, sent=None, sched=None, to=None, read_ratio=0.5, note=None):
    nid = add('Notifications', dict(SentByEmployeeId=by, Title=title, Content=content, Audience=aud, ScheduledAt=sched,
                                    SentAt=sent, CreatedAt=(sent or sched) - timedelta(minutes=30)), note)
    if sent:
        for acc in (to or accounts_for(aud)):
            add('NotificationRecipients', dict(NotificationId=nid, AccountId=acc,
                                               ReadAt=sent + timedelta(hours=3) if rng.random() < read_ratio else None))


notify(2, 'Lịch nghỉ ngày 20/10', 'Phòng tập nghỉ cả ngày 20/10/2026 để bảo trì điện toàn tòa nhà.', 'AllCustomers', sent=dt_(2026, 8, 25, 9, 5))
notify(2, 'Họp nhân sự cuối tháng', 'Toàn bộ PT và lễ tân họp lúc 21:30 ngày 30/09.', 'AllStaff', sent=dt_(2026, 9, 22, 10, 0))
notify(2, 'Cập nhật mẫu giáo án', 'PT dùng mẫu giáo án mới từ tuần sau, xem file trong nhóm.', 'PTs', sent=dt_(2026, 10, 1, 10, 0))
notify(2, 'Quy trình đối soát ca', 'Lễ tân nhớ ghi lý do khi tiền đếm lệch với hệ thống.', 'Receptionists', sent=dt_(2026, 9, 30, 9, 0))
notify(2, 'Ưu đãi gia hạn tháng 10', 'Hội viên gia hạn trong tháng 10 nhận thêm 1 buổi đo InBody.', 'ActiveMembers', sched=dt_(2026, 10, 10, 8, 0), note='hẹn giờ, chưa gửi')
notify(None, 'Buổi tập bị hủy do phòng tập nghỉ', 'Buổi PT ngày 18/09 bị hủy vì phòng tập nghỉ cả ngày, không trừ buổi (CBR-57).', 'Individual',
       sent=dt_(2026, 9, 10, 9, 1), to=[4, 5, 6, 7, 15, 16, 17])
notify(None, 'Buổi tập đã chuyển PT', 'Buổi PT ngày 29/09 lúc 18:00 chuyển sang PT Lý Hoàng Long (CBR-46).', 'Individual',
       sent=dt_(2026, 9, 27, 20, 16), to=[5, 22])
notify(None, 'Đơn nghỉ bị từ chối', 'Đơn nghỉ ngày 16/09 bị từ chối do quá hạn duyệt (CBR-85).', 'Individual', sent=dt_(2026, 9, 15, 20, 0), to=[14])
notify(None, 'Buổi tập đã được dời', 'Buổi PT ngày 15/09 dời sang 06:00 ngày 16/09 (BR-64).', 'Individual', sent=dt_(2026, 9, 13, 20, 16), to=[6, 7])

CFG = [('P-01', '1', 'Số lần bảo lưu tối đa mỗi gói tập (lần)'),
       ('P-02', '1', 'Thời gian bảo lưu tối đa mỗi lần (tháng)'),
       ('P-03', '0', 'Phí bảo lưu (VND), 0 = miễn phí'),
       ('P-04', '1D,1M,3M,9M,12M', 'Thời hạn gói tập: D = ngày, M = tháng'),
       ('P-05', '10', 'Tuổi tối thiểu dùng dịch vụ PT'),
       ('P-06', '90', 'Độ dài một buổi PT (phút)'),
       ('P-08', '3', 'Số lượt đổi buổi mỗi tháng của gói PT (hạn mức gói = P-08 × số tháng)'),
       ('P-09', '20', 'Hạn thanh toán đơn online = thời gian giữ slot (phút)'),
       ('P-10', '10', 'Thuế VAT (%) đã nằm trong giá bán'),
       ('P-11', '70', 'Tỷ lệ hoa hồng mặc định cho PT mới (%)'),
       ('P-12', '15:00', 'Giờ chốt nộp đơn nghỉ (ngày hôm trước)'),
       ('P-13', '2', 'Số PT tối thiểu còn lại mỗi ca'),
       ('P-14', '1', 'Số lễ tân tối thiểu còn lại mỗi ca'),
       ('P-15', '15', 'Hiệu lực mã xác thực / mã khôi phục (phút)'),
       ('P-16', '032,033,034,035,036,037,038,039,052,056,058,059,070,076,077,078,079,081,082,083,084,085,086,088,089,090,091,092,093,094,096,097,098,099', 'Đầu số di động hợp lệ (cấu hình triển khai, Manager không sửa)'),
       ('P-17', '5', 'Số lần sai trước khi khóa tạm (đăng nhập và mã xác thực)'),
       ('P-18', '5', 'Thời gian khóa tạm (phút)'),
       ('P-19', '05:30-22:00', 'Giờ mở cửa mặc định hằng ngày'),
       ('P-20', '20:00', 'Giờ chốt duyệt đơn nghỉ (ngày hôm trước)'),
       ('P-21', '1M,2M,3M', 'Thời hạn gói PT (tháng) — DBR-19'),
       ('P-22', '3', "Minimum customers to show a PT's average rating (DBR-69)")]
for k, v, d in CFG:
    add('SystemConfigs', dict(ConfigKey=k, ConfigValue=v, Description=d,
                             UpdatedAt=dt_(2026, 10, 6, 9) if k == 'P-22' else dt_(2026, 9, 1, 9),
                             UpdatedByEmployeeId=2), rid=int(k[2:]))

OHE = [(d_(2026, 9, 2), None, None, 1, 'Nghỉ lễ Quốc khánh'),
       (d_(2026, 9, 18), None, None, 1, 'Sự cố điện, nghỉ cả ngày'),
       (d_(2026, 10, 11), time(7, 0), time(18, 0), 0, 'Chủ nhật bảo trì hệ thống nước, mở muộn đóng sớm'),
       (d_(2026, 10, 20), None, None, 1, 'Bảo trì điện toàn tòa nhà'),
       (d_(2027, 2, 6), None, None, 1, 'Nghỉ Tết Nguyên đán')]
for d, o_, c_, closed, note in OHE:
    add('OperatingHourExceptions', dict(ApplyDate=d, OpenTime=o_, CloseTime=c_, IsClosedAllDay=closed, Note=note, SetByEmployeeId=2,
                                        CreatedAt=CLOSED.get(d, dt_(2026, 9, 28, 9, 0))))

AUD = [(12, 'Manager', 'Đổi giá gói dịch vụ', 'Packages', 2, 'Price', '749000', '799000', 'Điều chỉnh giá theo bảng giá quý IV', dt_(2026, 9, 1, 9, 30)),
       (12, 'Manager', 'Ngừng bán gói', 'Packages', 7, 'IsActive', '1', '0', 'P-04 bỏ thời hạn 6 tháng', dt_(2026, 9, 1, 9, 35)),
       (13, 'Receptionist', 'Đổi số điện thoại khách tại quầy', 'Persons', 3, 'Phone', '0901234599', '0901234503', 'Khách đổi số, đã đối chiếu ảnh check-in', dt_(2026, 9, 5, 10, 15)),
       (12, 'Manager', 'Từ chối đơn nghỉ phép', 'LeaveRequests', 2, 'Status', 'Pending', 'Rejected', 'Ca chiều chỉ còn 2 PT, duyệt sẽ thiếu người', dt_(2026, 9, 18, 17, 0)),
       (None, None, 'Tự từ chối đơn nghỉ quá hạn', 'LeaveRequests', 3, 'Status', 'Pending', 'Rejected', 'Quá hạn duyệt 20:00 ngày hôm trước', dt_(2026, 9, 15, 20, 0)),
       (12, 'Manager', 'Duyệt xóa hồ sơ khách', 'Customers', 12, 'Status', 'Active', 'Inactive', 'Khách chuyển vào Nam, không tập nữa', dt_(2026, 9, 15, 16, 0)),
       (None, None, 'Hủy đơn quá hạn thanh toán', 'Orders', None, 'Status', 'Pending', 'Cancelled', 'Quá hạn thanh toán 20 phút', dt_(2026, 9, 20, 21, 45)),
       (12, 'Manager', 'Cho nhân viên nghỉ việc', 'Employees', 8, 'Status', 'Working', 'Resigned', 'Nhân viên nghỉ việc từ 31/08/2026', dt_(2026, 8, 31, 18, 0)),
       (None, None, 'Vô hiệu hóa tài khoản vì không còn vai trò', 'Accounts', 18, 'Status', 'Active', 'Inactive', 'CBR-86', dt_(2026, 8, 31, 18, 0)),
       (13, 'Receptionist', 'Bảo lưu gói tập', 'Subscriptions', None, 'Status', 'Active', 'Frozen', 'Khách đi công tác nước ngoài 1 tháng', dt_(2026, 9, 27, 10, 30)),
       (11, 'Admin', 'Khóa tài khoản', 'Accounts', 23, 'Status', 'Active', 'Locked', 'PT vi phạm nội quy, tạm khóa chờ xử lý', dt_(2026, 9, 25, 9, 0)),
       (11, 'Admin', 'Khóa tài khoản', 'Accounts', 101, 'Status', 'Active', 'Locked', 'Khách dùng chung tài khoản cho người khác', dt_(2026, 9, 10, 20, 5)),
       (11, 'Admin', 'Mở khóa tạm sớm', 'Accounts', 3, 'LockedUntil', '2026-09-18 19:05', None, 'Khách gọi tổng đài xác minh danh tính', dt_(2026, 9, 18, 19, 1)),
       (16, 'Customer', 'Đổi mật khẩu', 'Accounts', 16, 'PasswordHash', None, None, None, dt_(2026, 9, 12, 21, 0)),
       (19, 'Customer', 'Đổi email', 'Persons', 25, 'Email', 'phuong.hoang99@gmail.com', 'phuong.hoang@gms.vn', None, dt_(2026, 9, 16, 7, 0)),
       (12, 'Manager', 'Đổi họ tên nhân viên', 'Persons', 28, 'FullName', 'Lý Hoàng Lâm', 'Lý Hoàng Long', 'Sai chính tả khi nhập hồ sơ', dt_(2026, 8, 16, 9, 0)),
       (12, 'Manager', 'Đánh dấu đã hoàn tiền', 'Transactions', None, 'RefundedAt', None, '2026-07-20', 'Đã chuyển khoản hoàn cho khách', dt_(2026, 7, 20, 10, 0)),
       (12, 'Manager', 'Đổi tham số hệ thống', 'SystemConfigs', 2, 'ConfigValue', '30', '1', 'P-02 đổi đơn vị sang tháng', dt_(2026, 9, 1, 9, 0))]
for acc, role, action, ent, eid, field, old, new, reason, at in AUD:
    add('AuditLogs', dict(AccountId=acc, ActingRole=role, Action=action, EntityName=ent, EntityId=eid, FieldChanged=field,
                          OldValue=old, NewValue=new, Reason=reason, CreatedAt=at))

# một giao dịch đã được Manager đánh dấu hoàn (BR-47): đơn online sai số tiền tháng 7
o = order(19, PKG['m1'], dt_(2026, 7, 18, 9, 0), 'Online', status='Cancelled',
          cancel=('InvalidPayment', dt_(2026, 7, 18, 9, 21), 'Số tiền chuyển không khớp số phải trả'),
          refund=('done', dt_(2026, 7, 20, 10, 0), 2, 'Đã chuyển khoản hoàn cho khách'), note='đã hoàn xong')

# ============================================================ 16. SECTION NOTES
SECTION.update({
    'Persons': 'ID 1–32 dàn nhân vật chính; 101–113 test xác thực; 201–240 khách thường.',
    'Accounts': 'Mật khẩu chung Gms@2026 (trừ TK chưa đặt MK).',
    'Shifts': 'Đủ 2 ca mỗi ngày mở cửa 01/09–02/10 + ca hôm nay (sống).',
    'TimeSlots': 'Lịch PT: slot đã đặt + slot trống công bố dày 05/10–31/10 (có cuối tuần).',
    'Sessions': 'Trước 05/10 đã có kết quả; từ 05/10 Scheduled.',
})

if __name__ == '__main__':
    HEADER = open('seed_header.sql', encoding='utf-8').read() if len(sys.argv) < 2 else ''
    sys.stdout.write(render(header=HEADER))
