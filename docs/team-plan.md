# Kế hoạch nhóm — 5 người (G5)

Chia theo **module dọc** (mỗi người làm trọn Entity → DAO → Service → Servlet → JSP → CSS → JS của module mình),
không chia theo tầng. Lý do: ít phụ thuộc chéo, ít đụng file của nhau, mỗi người tự demo được phần mình.
Bảng màn hình chi tiết: `docs/project-structure.md` mục 3.

## 1. Phân công gợi ý

| TV | Vai trò | Module | Màn SRS | Job | Khối lượng |
|---|---|---|---|---|---|
| **TV1 — Minh (Leader)** | Nền tảng + review | Nền tảng dùng chung (util, filter, tokens, common.js), Xác thực & tài khoản, Mua gói online + VNPay + Email, Lịch sử giao dịch | 1, 2, 3, 4, 35, 36, 40, 41 | JOB-05, JOB-06 | 8 màn + hạ tầng + tích hợp |
| TV2 | Dev | Trang công khai, Quản trị hệ thống, Blog, Thông báo, Góp ý, Báo cáo Admin | 5, 6, 7, 8–13, 61–65, 67 | — | 15 màn, phần lớn CRUD |
| TV3 | Dev | Cấu hình & danh mục (tham số, giờ mở cửa, gói, voucher), Nhân sự & nghỉ phép, Báo cáo Manager | 14–27, 66 | JOB-07 | 15 màn |
| TV4 | Dev | Hồ sơ khách, Check-in, Bảo lưu, Xóa hội viên, Bán gói tại quầy, Ca làm việc, Cơ sở vật chất | 28–34, 37, 39, 42–47 | JOB-01, 02, 03 | 15 màn |
| TV5 | Dev | Lịch tập & PT: dashboard, đặt buổi, đổi lịch, slot, điểm danh, khách của PT, giáo án, Bán gói PT | 38, 48–60 | JOB-04 | 14 màn, logic khó nhất |

Phụ thuộc cần làm TRƯỚC (tuần 1, Leader hoặc người được giao):
1. `database/01_schema.sql` + `02_seed.sql` chốt và push — mọi người chạy cùng 1 schema.
2. `SystemConfigService` (đọc P-01…P-20) và `AuditLogService.log(...)` — ai cũng gọi.
3. `LoginServlet` + layout JSP dùng chung (header/sidebar/footer từng vai trò trong `WEB-INF/jsp/common/`) — để mọi người đăng nhập vào khu của mình mà test.
4. `PackageService` (TV3) cần xong sớm vì TV1 (mua online), TV4 (bán quầy), TV5 (bán gói PT) đều đọc danh mục gói.

## 2. File dùng chung — chỉ Leader sửa

`pom.xml`, `agents.md`, `css/tokens.css`, `css/common.css`, `js/common.js`, `util/*`, `filter/*`, `constant/*`, `dto/*`,
`WEB-INF/web.xml`, `WEB-INF/jsp/common/*`, `database/01_schema.sql`.
Cần thêm/sửa → nhắn Leader (hoặc mở PR riêng chỉ sửa đúng file đó, ghi rõ lý do). Tránh 5 người cùng sửa 1 file gây conflict.

## 3. Quy trình Git

```
main      ← chỉ merge khi demo được, luôn build pass
develop   ← nhánh tích hợp hằng ngày
feature/tv3-voucher, feature/tv5-book-session …  ← mỗi màn/nhóm màn 1 nhánh
```

1. `git checkout develop && git pull` → `git checkout -b feature/tvX-ten-man`
2. Code xong: `mvn clean package` phải pass → commit theo agents.md Phần 10 (`feat: …`, `fix: …`)
3. Push, mở Pull Request vào `develop`, Leader review (checklist bên dưới) rồi merge.
4. Mỗi sáng `git pull origin develop` vào nhánh đang làm để bớt conflict.

## 4. Checklist review (Definition of Done)

- [ ] Đúng package/thư mục theo `docs/project-structure.md`; đúng tên theo `NamingConvention.md`
- [ ] Servlet chỉ điều phối; SQL chỉ ở DAO (PreparedStatement); nghiệp vụ + validate ở Service
- [ ] Service trả `ServiceResult`, bắt `SQLException`, thông báo lấy từ `Messages`
- [ ] Tham số nghiệp vụ đọc từ `SystemConfigService`, không hard-code (VD 20 phút, 5 lần, 15:00)
- [ ] Thao tác nhạy cảm có ghi AuditLog (GBR-07); thao tác tiêu cực có lý do ≥ 5 ký tự (GBR-09)
- [ ] JSP dùng `<c:out>` cho mọi dữ liệu người dùng nhập; include `head.jspf` + `flash-toast.jspf`
- [ ] CSS chỉ dùng `var(--…)` từ tokens.css; JS dùng hàm có sẵn trong common.js
- [ ] Danh sách: 10/20/50 dòng, lọc về trang 1, có trạng thái rỗng, STT tính theo trang
- [ ] Đã tự test cả nhánh lỗi (bỏ trống, sai định dạng, trùng dữ liệu, sai quyền)
