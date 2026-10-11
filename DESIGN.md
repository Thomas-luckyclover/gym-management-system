# DESIGN.md — Hệ giao diện khu nội bộ Super Gym (10/2026)

Áp dụng cho mọi trang sau đăng nhập (`body.dock` + `theme-*`). Trang Guest, Auth và trang lỗi giữ thiết kế cũ.

## Nguyên tắc
- **Tác vụ trước, trang trí sau.** Hiệu ứng chỉ để báo trạng thái hoặc tạo điểm nhấn; dữ liệu luôn nằm trên card trắng, chữ tối, tương phản AA.
- **Một hệ, hai nhóm màu:**
  - **Vận hành** (Quản lý, Quản trị, Lễ tân): xanh đậm navy, ba sắc lệch nhẹ (`theme-manager`, `theme-admin`, `theme-staff`).
  - **Thời tiết** (PT, Hội viên): màu chủ đạo đổi theo `weather-<kiểu>` trên `<body>` (nắng, nóng, nhiều mây, mưa, giông, tuyết, gió, đêm). Các trang không có dữ liệu thời tiết dùng màu "trời quang".
- **Font:** Montserrat (tiêu đề, số liệu) + Inter (nội dung). Không thêm font khác.
- **Bo góc:** control 2px, card 4px. Không dùng bo tròn lớn.
- **Không Tailwind/Bootstrap.** Mọi giá trị lấy từ `tokens.css` (mục 9b).

## Khung trang (common.css mục 11)
- **Sidebar dock nền đen chữ trắng:** ẩn sau mép trái, tay nắm mũi tên ở giữa chiều cao màn hình; rê chuột thì trượt ra. Quầng sáng tròn chạy theo con trỏ. Mục đang chọn tô màu chủ đạo.
- **Topbar:** dính trên cùng, nền kính. `tb-title` dùng Montserrat 800.
- Layout: `layout-<vai-trò>.jspf`. Cấu trúc: `.app > #dock(.sidebar + #dockHandle) + .main(.topbar + .content)`. **Không chép lại CSS/JS dock vào file trang.**

## Thành phần đặc trưng
| Thành phần | Dùng ở | Ghi chú |
|---|---|---|
| Bảng điểm `.scoreboard` | Manager dashboard | Nền đen `--color-board`, ô đầu `is-lead` tô màu chủ đạo, số Montserrat 800 |
| Biểu đồ cột `.bar` | Manager dashboard | Cột mọc lên khi tải; cột kỳ gần nhất tô màu chủ đạo; `tabindex` + `data-tooltip` |
| Làn chạy `.lane` | Bảng doanh thu theo gói | Mặt đường màu chủ đạo, vạch đích ca-rô, chấm chạy tới `--pct` |
| Khối chào `.wx-hero` | PT/Customer dashboard | Lời chào + 4 người chạy doodle trên đường chạy (rê chuột → chạy nhanh) + thẻ thời tiết **khối màu** (hiệu ứng riêng bên trong, icon/nhiệt độ nổi 3D khi nghiêng) |
| Cảnh thời tiết `.weather-scene` | PT/Customer dashboard | Nền màu đậm theo thời tiết + cảnh riêng: nắng (mặt trời tia xoay), nóng (mặt trời đỏ, hơi nóng), mây (dải mây), mưa (kính mưa hoàng hôn: đèn nhòe, thành phố, giọt đọng/trượt), giông (mây đen, chớp), tuyết (núi tuyết), gió (đồi cỏ, lá bay), đêm (trăng, cực quang, sao băng). Hạt né con trỏ, parallax 3 tầng |
| Mái bạt `.pt-awning` | PT/Customer dashboard | Sọc màu thời tiết; cuộn xuống thì cuốn, cuộn lên thì thả |

## Hiệu ứng dùng lại (common.js mục "Hiệu ứng giao diện")
| Thuộc tính | Tác dụng |
|---|---|
| `data-count` | Số chạy từ 0 khi tải trang (giữ định dạng `1.248`, `184,6 tr`) |
| `data-flip` + `setFlipValue(el, text)` | Số lật kiểu bảng điểm khi đổi giá trị |
| `data-tilt` | Nghiêng 3D ≤ 4° theo con trỏ + vệt sáng (không dùng trên phần tử đã có `::after`) |
| `data-reveal` | Hiện dần theo thứ tự khi tải trang |
| `data-tooltip="..."` | Chú thích khi hover/focus bàn phím |
| `.skeleton` | Khung xương đang tải |

Mọi hiệu ứng tự tắt khi người dùng bật "Reduce motion".

## Trạng thái bắt buộc mỗi khối
Đang tải (skeleton), rỗng (câu nói rõ không có gì), lỗi (thông báo thân thiện, không biến thành "rỗng"), chưa hoàn thiện (khóa nút + lý do). Badge trạng thái theo GBR-28.
