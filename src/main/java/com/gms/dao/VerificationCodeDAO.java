package com.gms.dao;

import com.gms.entity.VerificationCode;
import com.gms.util.DBContext;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;

/**
 * DAO cho bảng VerificationCodes — coi bảng này như "cuốn sổ ghi mã OTP".
 * Mỗi lần gửi mã cho ai, ta ghi 1 dòng: gửi cho email nào, mã gì, gửi lúc nào, hết hạn lúc nào,
 * đã gõ sai mấy lần, có bị khóa không, đã dùng chưa.
 *
 * File này có 3 nhóm hàm:
 * 1. Ghi dòng mới  : insert
 * 2. Hỏi sổ        : findLatestByEmailAndPurpose, findByTokenHash, findLockedUntil, countCodesSentSince
 * 3. Sửa dòng cũ   : increaseFailedAttempts, lockCode, markUsed
 *
 * DAO chỉ đọc/ghi sổ, KHÔNG quyết định gì (mã đúng hay sai, còn hạn không, có phải khóa không).
 * Những quyết định đó nằm ở VerificationCodeService.
 *
 * @author Le Cong Minh
 */
public class VerificationCodeDAO {

    // ===== 1. Ghi dòng mới =====

    /**
     * Ghi 1 dòng mới vào sổ, ngay sau khi hệ thống gửi mã cho khách.
     *
     * Dùng khi: khách bấm "Gửi mã" (đăng ký, quên mật khẩu, đổi email...), hoặc Lễ tân tạo link đặt mật khẩu.
     * Ví dụ: gửi mã 246810 cho thao@gmail.com lúc 10:00
     *        → sổ có thêm dòng: thao@gmail.com | Recovery | 246810 | gửi 10:00 | hết hạn 10:15
     *
     * Không cần truyền Id, số lần sai, giờ khóa, giờ dùng — DB tự điền (Id tự tăng, sai = 0, còn lại để trống).
     *
     * @param verificationCode object chứa email, mục đích, mã (hoặc token), giờ gửi, giờ hết hạn
     * @throws SQLException lỗi kết nối hoặc lỗi câu SQL
     */
    public void insert(VerificationCode verificationCode) throws SQLException {
        // B1: câu SQL ghi 7 cột, mỗi dấu ? là 1 chỗ trống sẽ điền giá trị ở B3
        String sql = "INSERT INTO VerificationCodes (AccountId, Email, Purpose, Code, TokenHash, SentAt, ExpiresAt) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";

        // B2: mở kết nối tới DB (tự đóng khi ra khỏi try)
        try (Connection conn = DBContext.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            // B3: điền dấu ? số 1 — AccountId. Khách đang đăng ký thì chưa có tài khoản → ghi NULL
            if (verificationCode.getAccountId() == null) {
                stmt.setNull(1, Types.INTEGER);
            } else {
                stmt.setInt(1, verificationCode.getAccountId());
            }

            // B4: điền dấu ? số 2 → 7 (giờ phải đổi LocalDateTime → Timestamp vì JDBC chỉ nhận Timestamp)
            stmt.setString(2, verificationCode.getEmail());
            stmt.setString(3, verificationCode.getPurpose().getDbValue());
            stmt.setString(4, verificationCode.getCode());
            stmt.setString(5, verificationCode.getTokenHash());
            stmt.setTimestamp(6, Timestamp.valueOf(verificationCode.getSentAt()));
            stmt.setTimestamp(7, Timestamp.valueOf(verificationCode.getExpiresAt()));

            // B5: chạy câu INSERT → sổ có thêm 1 dòng
            stmt.executeUpdate();
        }
    }

    // ===== 2. Hỏi sổ =====

    /**
     * Lấy ra dòng mã MỚI NHẤT đã gửi cho 1 email, theo 1 mục đích.
     * Chỉ mã mới nhất mới được dùng — mã cũ hơn coi như bỏ.
     *
     * Dùng khi (Service gọi ở 2 chỗ):
     * - Khách gõ mã → lấy dòng này ra, so mã khách gõ với cột Code, xem ExpiresAt còn hạn không.
     * - Khách bấm "Gửi lại mã" → lấy SentAt ra, xem đã đủ 60 giây kể từ lần gửi trước chưa.
     * Ví dụ: thao@gmail.com có 3 mã Recovery gửi lúc 9:00, 9:30, 10:00 → trả về dòng 10:00.
     *
     * @param email email nhận mã
     * @param purpose mục đích: SIGN_UP, CLAIM, RECOVERY, EMAIL_CHANGE
     * @return dòng mới nhất (đủ 11 cột), hoặc null nếu email này chưa từng được gửi mã cho mục đích này
     * @throws SQLException lỗi kết nối hoặc lỗi câu SQL
     */
    public VerificationCode findLatestByEmailAndPurpose(String email, VerificationCode.Purpose purpose) throws SQLException {
        // B1: lọc theo email + mục đích, xếp giờ gửi mới nhất lên đầu, lấy đúng 1 dòng đầu (TOP 1)
        String sql = "SELECT TOP 1 VerificationCodeId, AccountId, Email, Purpose, Code, TokenHash, "
                + "SentAt, ExpiresAt, FailedAttempts, LockedUntil, UsedAt "
                + "FROM VerificationCodes "
                + "WHERE Email = ? AND Purpose = ? "
                + "ORDER BY SentAt DESC";

        // B2: mở kết nối
        try (Connection conn = DBContext.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            // B3: điền 2 dấu ? — email và mục đích
            stmt.setString(1, email);
            stmt.setString(2, purpose.getDbValue());

            // B4: chạy câu SELECT. Có dòng → chép thành object VerificationCode; không có → null
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToVerificationCode(rs);
                }
                return null;
            }
        }
    }

    /**
     * Tìm dòng link đặt mật khẩu khớp với link khách vừa bấm.
     *
     * Dùng khi: khách được Lễ tân tạo tài khoản tại quầy, về nhà mở email bấm link /set-password?token=...
     * DB không lưu token gốc mà lưu "mã băm" của nó, nên Service băm token trên link trước rồi mới gọi hàm này.
     * Hàm chỉ tìm, KHÔNG kiểm tra hết hạn / đã dùng — Service tự kiểm tra để báo đúng lỗi cho khách.
     *
     * @param tokenHash mã băm của token = SecureCodeUtil.sha256Hex(token trên link)
     * @return dòng tìm được, hoặc null nếu link sai / không tồn tại
     * @throws SQLException lỗi kết nối hoặc lỗi câu SQL
     */
    public VerificationCode findByTokenHash(String tokenHash) throws SQLException {
        // B1: tìm dòng có TokenHash trùng khớp (mỗi TokenHash là duy nhất trong bảng)
        String sql = "SELECT VerificationCodeId, AccountId, Email, Purpose, Code, TokenHash, "
                + "SentAt, ExpiresAt, FailedAttempts, LockedUntil, UsedAt "
                + "FROM VerificationCodes "
                + "WHERE TokenHash = ?";

        // B2: mở kết nối
        try (Connection conn = DBContext.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            // B3: điền dấu ? — mã băm
            stmt.setString(1, tokenHash);

            // B4: chạy câu SELECT. Có dòng → chép thành object; không có → null
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToVerificationCode(rs);
                }
                return null;
            }
        }
    }

    /**
     * Hỏi: email này có đang bị khóa xác thực không? Nếu có thì khóa tới mấy giờ?
     * (CBR-94: gõ sai mã 5 lần, hoặc xin mã lần thứ 5 trong 1 giờ → khóa 5 phút.)
     *
     * Dùng khi: TRƯỚC khi cho khách gõ mã hoặc xin mã mới — đang bị khóa thì chặn luôn (MSG61).
     * Ví dụ: bây giờ 10:07, sổ có dòng khóa tới 10:10 → trả về 10:10 (còn khóa 3 phút).
     *        bây giờ 10:12, khóa tới 10:10 đã qua → trả về null (hết khóa).
     *
     * @param email email cần kiểm tra
     * @param now giờ hiện tại, lấy bằng DateTimeUtil.now()
     * @return giờ hết khóa, hoặc null nếu email không bị khóa
     * @throws SQLException lỗi kết nối hoặc lỗi câu SQL
     */
    public LocalDateTime findLockedUntil(String email, LocalDateTime now) throws SQLException {
        // B1: trong các dòng của email này, lấy giờ khóa xa nhất mà vẫn còn sau "bây giờ"
        String sql = "SELECT MAX(LockedUntil) AS LockedUntil "
                + "FROM VerificationCodes "
                + "WHERE Email = ? AND LockedUntil > ?";

        // B2: mở kết nối
        try (Connection conn = DBContext.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            // B3: điền 2 dấu ? — email và giờ hiện tại
            stmt.setString(1, email);
            stmt.setTimestamp(2, Timestamp.valueOf(now));

            // B4: câu MAX luôn trả đúng 1 dòng; không có khóa nào thì ô đó trống → trả null
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                return toLocalDateTime(rs.getTimestamp("LockedUntil"));
            }
        }
    }

    /**
     * Đếm: kể từ 1 mốc giờ đến giờ, email này đã xin mã bao nhiêu lần?
     * (CBR-94: xin lần thứ 5 trong vòng 1 giờ → khóa 5 phút.)
     *
     * Dùng khi: khách bấm "Gửi mã" — Service truyền mốc = 1 tiếng trước, nếu đã đủ 4 lần thì lần này bị khóa.
     * Ví dụ: bây giờ 10:20, mốc 9:20; email đã xin lúc 10:00, 10:05, 10:10, 10:15 → trả về 4.
     * Chỉ đếm mã 6 số (Code có giá trị), không đếm link đặt mật khẩu vì link do Lễ tân tạo, không phải khách xin.
     *
     * @param email email nhận mã
     * @param since mốc bắt đầu đếm, VD 1 tiếng trước
     * @return số lần đã gửi mã kể từ mốc đó
     * @throws SQLException lỗi kết nối hoặc lỗi câu SQL
     */
    public int countCodesSentSince(String email, LocalDateTime since) throws SQLException {
        // B1: đếm các dòng của email này có giờ gửi từ mốc since trở về sau
        String sql = "SELECT COUNT(*) AS SentCount "
                + "FROM VerificationCodes "
                + "WHERE Email = ? AND Code IS NOT NULL AND SentAt >= ?";

        // B2: mở kết nối
        try (Connection conn = DBContext.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            // B3: điền 2 dấu ? — email và mốc giờ
            stmt.setString(1, email);
            stmt.setTimestamp(2, Timestamp.valueOf(since));

            // B4: câu COUNT luôn trả đúng 1 dòng chứa con số → đọc ra
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                return rs.getInt("SentCount");
            }
        }
    }

    // ===== 3. Sửa dòng cũ =====

    /**
     * Cộng thêm 1 vào số lần gõ sai của 1 mã.
     *
     * Dùng khi: khách gõ mã sai. Ví dụ: dòng 120 đang sai 1 lần → sau khi gọi thành 2 lần.
     * Service tự tính: nếu đây là lần sai thứ 5 thì gọi thêm lockCode để khóa.
     *
     * @param verificationCodeId Id của dòng mã (lấy từ findLatestByEmailAndPurpose)
     * @throws SQLException lỗi kết nối hoặc lỗi câu SQL
     */
    public void increaseFailedAttempts(int verificationCodeId) throws SQLException {
        // B1: tăng cột FailedAttempts thêm 1 cho đúng dòng có Id này
        String sql = "UPDATE VerificationCodes "
                + "SET FailedAttempts = FailedAttempts + 1 "
                + "WHERE VerificationCodeId = ?";

        // B2: mở kết nối, điền Id, chạy câu UPDATE
        try (Connection conn = DBContext.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, verificationCodeId);
            stmt.executeUpdate();
        }
    }

    /**
     * Ghi giờ hết khóa vào 1 dòng mã → email đó bị khóa xác thực tới giờ này.
     *
     * Dùng khi: khách gõ sai lần thứ 5, hoặc xin mã lần thứ 5 trong 1 giờ.
     * Ví dụ: bây giờ 10:05, khóa 5 phút → ghi LockedUntil = 10:10.
     * Sau đó findLockedUntil sẽ trả về 10:10 cho tới khi qua giờ đó.
     *
     * @param verificationCodeId Id của dòng mã cần ghi khóa
     * @param lockedUntil giờ hết khóa = giờ hiện tại + số phút khóa (P-18)
     * @throws SQLException lỗi kết nối hoặc lỗi câu SQL
     */
    public void lockCode(int verificationCodeId, LocalDateTime lockedUntil) throws SQLException {
        // B1: ghi giờ hết khóa vào đúng dòng có Id này
        String sql = "UPDATE VerificationCodes "
                + "SET LockedUntil = ? "
                + "WHERE VerificationCodeId = ?";

        // B2: mở kết nối, điền 2 dấu ?, chạy câu UPDATE
        try (Connection conn = DBContext.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setTimestamp(1, Timestamp.valueOf(lockedUntil));
            stmt.setInt(2, verificationCodeId);
            stmt.executeUpdate();
        }
    }

    /**
     * Đánh dấu 1 mã (hoặc link) là "đã dùng" → không dùng lại được nữa (DBR-03: mỗi mã dùng 1 lần).
     *
     * Dùng khi: khách gõ ĐÚNG mã, hoặc đặt mật khẩu thành công qua link.
     * Ví dụ: khách gõ đúng lúc 10:03 → ghi UsedAt = 10:03. Lần sau Service thấy UsedAt có giá trị → từ chối.
     *
     * @param verificationCodeId Id của dòng mã
     * @param usedAt giờ dùng, lấy bằng DateTimeUtil.now()
     * @throws SQLException lỗi kết nối hoặc lỗi câu SQL
     */
    public void markUsed(int verificationCodeId, LocalDateTime usedAt) throws SQLException {
        // B1: ghi giờ dùng vào đúng dòng có Id này
        String sql = "UPDATE VerificationCodes "
                + "SET UsedAt = ? "
                + "WHERE VerificationCodeId = ?";

        // B2: mở kết nối, điền 2 dấu ?, chạy câu UPDATE
        try (Connection conn = DBContext.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setTimestamp(1, Timestamp.valueOf(usedAt));
            stmt.setInt(2, verificationCodeId);
            stmt.executeUpdate();
        }
    }

    // ===== 4. Hàm phụ: chép 1 dòng trong DB thành object Java =====

    /**
     * Đọc 11 ô của dòng mà ResultSet đang đứng, chép từng ô vào 1 object VerificationCode.
     * Được 2 hàm findLatestByEmailAndPurpose và findByTokenHash gọi, để khỏi viết lặp 11 dòng.
     */
    private VerificationCode mapResultSetToVerificationCode(ResultSet rs) throws SQLException {
        // B1: Id là khóa chính, không đổi được → phải truyền vào lúc tạo object
        VerificationCode verificationCode = new VerificationCode(rs.getInt("VerificationCodeId"));

        // B2: AccountId có thể trống (khách đang đăng ký). getInt gặp ô trống trả 0,
        // nên phải hỏi wasNull() — ô thật sự có số thì mới gán
        int accountId = rs.getInt("AccountId");
        if (!rs.wasNull()) {
            verificationCode.setAccountId(accountId);
        }

        // B3: chép các ô chữ và số
        verificationCode.setEmail(rs.getString("Email"));
        verificationCode.setPurpose(VerificationCode.Purpose.fromDbValue(rs.getString("Purpose")));
        verificationCode.setCode(rs.getString("Code"));
        verificationCode.setTokenHash(rs.getString("TokenHash"));
        verificationCode.setFailedAttempts(rs.getInt("FailedAttempts"));

        // B4: chép các ô giờ (đổi Timestamp của JDBC → LocalDateTime của dự án)
        verificationCode.setSentAt(toLocalDateTime(rs.getTimestamp("SentAt")));
        verificationCode.setExpiresAt(toLocalDateTime(rs.getTimestamp("ExpiresAt")));
        verificationCode.setLockedUntil(toLocalDateTime(rs.getTimestamp("LockedUntil")));
        verificationCode.setUsedAt(toLocalDateTime(rs.getTimestamp("UsedAt")));
        return verificationCode;
    }

    /**
     * Đổi kiểu giờ Timestamp (JDBC trả về) sang LocalDateTime (dự án dùng).
     * Ô giờ trong DB có thể trống (VD UsedAt khi mã chưa dùng) → trả null, tránh lỗi khi gọi toLocalDateTime() lên null.
     */
    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        if (timestamp == null) {
            return null;
        }
        return timestamp.toLocalDateTime();
    }
}