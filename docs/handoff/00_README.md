# docs/handoff: hồ sơ giao việc cho AI viết lại code theo v25

## Cách dùng (copy prompt này cho AI)

```
Đọc agents.md (Phần 14 code theo đợt, Phần 15 quyết định v25), NamingConvention.md mục 2b (enum),
codeImplementationBackend.md Mục 1 (comment khung), rồi đọc docs/handoff/<file module>.md.
Lập bảng kế hoạch theo đợt cho module này, chờ tôi gõ "tiếp".
```

## Danh sách module

| File | Module | UC | Mức độ |
|---|---|---|---|
| `01_auth-login-role.md` | Đăng nhập, chọn vai trò, đăng ký, quên MK | 01, 02, 03, 05 | ✅ Đã code. Còn rút comment về dạng khung |
| `02_account-settings.md` | Cài đặt tài khoản | 04 | ✅ Đã sửa lỗ hổng. Còn audit `ActingRole`, quyền sửa trường theo vai trò |

Chỉ giữ hồ sơ cho màn **đã có code**. Màn chưa code sẽ viết hồ sơ khi bắt đầu làm module đó, theo đúng khung 2 file trên.

## Quy ước chung cho mọi module
- Enum đúng bảng ở `NamingConvention.md` 2b. Tham số luôn đọc qua `SystemConfigService` (P-01…P-21).
- Nhân viên thao tác trên hồ sơ khách → gọi `SelfServiceGuard` trước khi ghi (CBR-115).
- Thao tác nhạy cảm → `AuditLogService` kèm `ActingRole` (CBR-112).
- Test bằng tài khoản và case ở `docs/database/SEED_CASES.md`.
