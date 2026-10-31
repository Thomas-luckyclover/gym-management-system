# SRS v27 — bản cập nhật khớp code develop

- `SRS-G5-v27-update.docx` — file giao: (1) bảng theo dõi, (2) các bảng FULL để copy vào SRS, (3) mâu thuẫn trong Use Case, (4) code cần sửa.
- `content/` — nội dung từng bảng (Python, sửa ở đây rồi build lại).
- `findings.md` — nhật ký bằng chứng đọc từ code.
- Build lại:
  ```bash
  python3 docs/srs-v27/build/build_json.py && python3 docs/srs-v27/build/check.py && node docs/srs-v27/build/make_docx.js docs/srs-v27/SRS-G5-v27-update.docx
  ```
  `check.py` báo ID trùng, tham chiếu tới ID không tồn tại hoặc đã Withdrawn, và mã luật lọt vào câu thông báo.
