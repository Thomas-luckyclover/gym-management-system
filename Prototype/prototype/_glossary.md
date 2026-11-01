# Bảng từ chuẩn dùng chung (nguồn: SRS §4–§5, GBR, 2_Seed_Data.sql)

Giá trị lưu bằng tiếng Anh, **hiển thị tiếng Việt** (GBR-01). Màu badge (GBR-28): xanh = đang dùng/thành công · vàng = chờ/giữ chỗ · đỏ = từ chối/huỷ/lỗi · xám = hết hạn/ngừng.

## Trạng thái
| Đối tượng | Giá trị → chữ hiển thị |
|---|---|
| Customer | New → Mới tạo · Active → Đang hoạt động · Frozen → Đang bảo lưu · Expired → Hết hạn · Inactive → Đã xoá |
| Subscription (Membership / DayPass / PTSingle / PTCouple / SingleSession) | Active → Đang hiệu lực · Frozen → Đang bảo lưu · Used → Đã dùng · Expired → Hết hạn · Completed → Hoàn thành · Cancelled → Đã huỷ |
| Order | Pending → Chờ thanh toán · Paid → Đã thanh toán · Cancelled → Đã huỷ |
| Transaction | Pending → Đang xử lý · Success → Thành công · Failed → Thất bại · Expired → Hết hạn |
| Session | Scheduled → Đã xếp · Completed → Hoàn thành · Missed → Vắng · Cancelled → Đã huỷ |
| TimeSlot | Free → Còn trống · Held → Đang giữ chỗ · Booked → Đã có khách · Withdrawn → Đã gỡ |
| Shift | NotOpened → Chưa mở · Open → Đang mở · Closed → Đã chốt |
| Leave request | Pending → Chờ duyệt · Approved → Đã duyệt · Rejected → Từ chối |
| Maintenance request | Open → Chờ duyệt · InProgress → Đang xử lý · Rejected → Từ chối · Closed → Đã đóng |
| Facility | Available → Sẵn sàng · UnderMaintenance → Đang bảo trì · Retired → Ngừng dùng (không hiện) |
| Deletion request | Pending → Chờ duyệt · Approved → Đã duyệt · Rejected → Từ chối |
| Voucher | chạy / hết hạn / hết lượt / chưa bắt đầu / đã tắt (suy từ ngày, UsedCount, IsActive) |
| Account | Active · Locked → Bị khoá · Inactive |
| Blog | Active → Hiển thị · Inactive → Ẩn |

## Phân loại
- **Loại gói**: Membership → Gói tập · DayPass → Gói Ngày · PTSingle → Gói PT · PTCouple → Gói PT Couple · SingleSession → Buổi PT lẻ.
- **Phương thức thanh toán**: Cash → Tiền mặt · Transfer → Chuyển khoản · OnlineGateway → Cổng thanh toán. Kênh đơn: Counter → Tại quầy · Online → Trực tuyến.
- **Loại nghỉ phép**: Annual → Nghỉ phép năm · Sick → Nghỉ ốm · Unpaid → Nghỉ không lương. **Ca nghỉ**: Morning → Ca sáng · Afternoon → Ca chiều · FullDay → Cả ngày.
- **Ca làm việc**: Morning → Ca sáng · Afternoon → Ca chiều.
- **Huỷ buổi (CancelType)**: ByPt → PT huỷ · ByCustomer → Khách huỷ · GymClosed → Phòng nghỉ · PackageExpired → Gói hết hạn.
- **Lý do Missed**: NoCheckIn → Không check-in · NoMembership → Chưa có gói tập · MembershipFrozen → Gói đang bảo lưu · MarkedAbsent → PT đánh vắng.
- **Huỷ đơn (Orders.CancelType)**: PaymentTimeout · ReplacedByNewOrder · InvalidPayment · CancelledByReceptionist.
- **Hạng mục cơ sở vật chất**: Cardio · FreeWeights → Tạ tự do · Machines → Máy tập · Amenities → Tiện ích. **Ưu tiên bảo trì**: Urgent → Khẩn · Normal → Thường · Low → Thấp.
- **Vai trò**: Admin · Manager → Quản lý · PT → Huấn luyện viên · Receptionist → Lễ tân · Customer → Hội viên.

## Định dạng
- Mã khách `KH-nnnn` (KH-0241) · mã nhân viên `NV-nnnn` (NV-0042) · mã đơn `DH-yyyy-nnnn` (DH-2026-1191). Không còn `SG-`, `HD-`, `DH-20xx`.
- Ngày `dd/mm/yyyy`, giờ `HH:mm` 24h, múi giờ UTC+7. Ngày cuối của gói/bảo lưu là ngày cuối cùng còn dùng được (GBR-03).
- Tiền: đồng nguyên, dấu chấm phân nghìn, đuôi `đ`, canh phải; tổng ghi "gồm VAT 8%" (GBR-02, 29).
- SĐT di động 10 số bắt đầu `0` + đầu số hợp lệ (P-16). Email hợp lệ, không trùng. Mật khẩu 8–64 ký tự, có chữ và số.
- Lý do từ chối/huỷ/xoá: 5–500 ký tự (GBR-09). Danh sách 10 dòng/trang (10/20/50).

## Tham số mặc định (PARAMS)
| Mã | Giá trị | Mã | Giá trị |
|---|---|---|---|
| P-01 Số lần bảo lưu tối đa/gói | 1 lần | P-09 Hạn thanh toán online = giữ chỗ | 20 phút |
| P-02 Thời gian bảo lưu tối đa/lần | 1 tháng | P-10 VAT (đã gồm trong giá) | 8% |
| P-03 Phí bảo lưu | Miễn phí | P-11 Hoa hồng PT mới | 70% |
| P-04 Thời hạn gói tập | 1 ngày, 1, 3, 9, 12 tháng | P-12 Hạn nộp đơn nghỉ | 15:00 hôm trước |
| P-05 Tuổi tối thiểu dùng dịch vụ PT | 10 tuổi | P-13 / P-14 PT / lễ tân tối thiểu mỗi ca | 2 / 1 |
| P-06 Độ dài buổi PT | 90 phút | P-17 / P-18 Sai tối đa / khoá tạm | 5 lần / 5 phút |
| P-07 Cửa sổ xử lý sau buổi | 24 giờ | P-19 Giờ mở cửa mặc định | 05:30 – 22:00 |
| P-20 Hạn duyệt đơn nghỉ | 20:00 hôm trước | P-21 Thời hạn gói PT | 1, 2, 3 tháng |

Swap buổi (P-08) đã rút khỏi phạm vi: không còn nút "Đổi lịch" nào ở phía khách.

## Bảng giá chuẩn (đúng `database/2_Seed_Data.sql` nhánh develop; VAT 8% đã gồm trong giá)
| Gói | Giá | Ghi chú |
|---|---|---|
| Gói Ngày | 120.000đ | 1 ngày, vào một lượt |
| Gói 1 / 3 / 9 / 12 Tháng | 799.000đ / 2.190.000đ / 5.490.000đ / 6.990.000đ | Đang bán. Không bán gói 2, 6, 24, 36 tháng |
| Gói 6 Tháng (cũ) | 3.990.000đ | Ngừng bán, chỉ còn trong đơn / gói cũ của khách |
| Gói PT 1 / 2 / 3 Tháng | 8.800.000đ / 16.500.000đ / 23.900.000đ | 22 / 43 / 65 buổi dự kiến |
| Gói PT Couple 1 / 2 Tháng | 13.200.000đ / 24.900.000đ | 22 / 43 buổi, chung quota |
| Buổi PT lẻ | 450.000đ | 90 phút, không kỳ hạn; chỉ bán ở quầy (#37) hoặc đặt online (#52) |
| Voucher | GYMVUI10 (-10%, đơn từ 500.000đ) · NGAY15K (-15.000đ, đơn từ 100.000đ) | Chỉ áp Gói tập / Gói Ngày. Mã mẫu lỗi: HETLUOT25 (hết lượt), HE2026 (hết hạn), TET2027X (chưa bắt đầu) |
| Hoa hồng PT (70%) | PT 280.000đ · Couple 420.000đ · Buổi lẻ 315.000đ / buổi | PT 1 Tháng: 8.800.000 / 22 = 400.000 × 70%; Couple 1 Tháng: 13.200.000 / 22 = 600.000 × 70%; lẻ: 450.000 × 70% |

## Bộ dữ liệu mẫu chung (mốc "hôm nay" = Chủ nhật 20/09/2026)
- Hội viên đăng nhập: **Nguyễn Minh An · KH-0241** — Gói 6 Tháng (cũ) đến 14/11/2026, Gói PT 3 Tháng với PT Hoàng Long.
- Lễ tân: **Nguyễn Thị Hoa · NV-0042**, ca chiều 20/09 đang mở. PT: **Hoàng Long · NV-0118**.
- Khách tại quầy: KH-1001 Trần Bảo Ngọc · KH-1002 Lê Minh Tuấn · KH-1003 Phạm Minh Châu (đang bảo lưu) · KH-1004 Đỗ Văn Khang (hết hạn) · KH-1005 Vũ Hoài Nam + KH-1011 Đỗ Lan Chi (Gói PT Couple 1 Tháng, mua 15:15 hôm nay) · KH-1009 Trịnh Quốc Bảo.
- PT: Hoàng Long NV-0118 · Phạm Thu Trang NV-0121 · Đỗ Minh Khoa NV-0127 · Vũ Hải Yến NV-0133. Lễ tân: Nguyễn Thị Hoa NV-0042, Bùi Khánh Vân NV-0057. Quản lý: Nguyễn Văn An NV-0010. Admin: Trần Minh Quân NV-0001.
- KH-0241: Gói 6 Tháng (cũ) 15/05–14/11/2026; Gói PT 3 Tháng 19/08–18/11/2026 (PT Hoàng Long); đơn DH-2026-1191 (Gói 12 Tháng 6.990.000đ) đang Chờ thanh toán.
- Ca chiều 20/09 (14:00–22:00): đơn DH-2026-1192…1199 (xem Manager ORDERS / lt/ca-lam-viec).
