# 01: Đăng nhập · chọn vai trò · đăng ký · quên mật khẩu (UC-01, 02, 03, 05)

**Trạng thái:** ✅ đã code theo v25 (commit `fix(auth)` trên nhánh `fix/auth-v25`). **Việc còn lại:** rút comment trong các file dưới đây về dạng khung (`codeImplementationBackend.md` Mục 1).

## File
| File | Ghi chú |
|---|---|
| `service/AuthService.java` | `login()`, `fetchPersonProfile()` (mọi vai trò còn hiệu lực), `findActiveRoles()` |
| `servlet/auth/LoginServlet.java` | ≥ 2 vai trò → chưa đặt ROLE, chuyển `/select-role` |
| `servlet/auth/SelectRoleServlet.java` + `jsp/auth/select-role.jsp` | Mới |
| `filter/AuthFilter.java` | Chưa chọn vai trò → chỉ vào được `/select-role`, `/logout` |
| `service/RegistrationService.java`, `dao/PersonDAO.java` | Tra SĐT mọi Person còn hoạt động |
| `service/SelfServiceGuard.java` | Mới, dùng cho CBR-115 |

## Luồng đăng nhập
1. B1: kiểm tra trống (MSG39, MSG09)
2. B2: tìm tài khoản theo email hoặc SĐT (CBR-113)
3. B3: TK `Inactive` / `Locked` → MSG37 (CBR-96)
4. B4: đang khóa tạm → MSG35; hết khóa → reset bộ đếm (BR-01, BR-02)
5. B5: chưa có mật khẩu → MSG38 (CBR-31)
6. B6: lấy vai trò còn hiệu lực: Employee `Working`, Customer khác `Inactive`; không còn vai trò nào → MSG37 (CBR-86)
7. B7: sai mật khẩu. Nếu TK có vai trò nhân viên → MSG34, không đếm; nếu chỉ là khách → đếm, đủ P-17 thì khóa P-18 (BR-01)
8. B8: đúng → reset bộ đếm, `LastLoginAt`, đổi session id; 1 vai trò → vào trang chủ vai trò, ≥ 2 vai trò → `/select-role`

## Luồng đăng ký (CBR-19→28, CBR-114)
SĐT khớp Person còn hoạt động:
- Đã có TK → MSG51 (kể cả SĐT nhân viên, không lộ là của ai).
- Không có hồ sơ Self còn hiệu lực → MSG53, mời ra quầy.
- Có email mà khách nhập khác → MSG52.
- Có email và khách nhập khớp → gửi mã `Claim`, gắn TK vào hồ sơ cũ.
- Hồ sơ không có email → MSG53.

SĐT không khớp Person nào → đăng ký mới (Person + Customer `Self`/`New` + Account) sau khi đúng mã `SignUp`.

## Case test (SEED_CASES §Đăng nhập, §Đăng ký, §Quên MK)
`trang.nguyen@gms.vn`, `phuong.hoang@gms.vn`, `bao.dang@gmail.com`, `hoa.mai@gms.vn`, `tu.phan@gms.vn`, `kim.vo@gmail.com`, `khanh.trinh@gmail.com`, SĐT `0912000005` khi đăng ký.
