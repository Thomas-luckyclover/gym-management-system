package com.gms.dto;

import com.gms.entity.VerificationCode;

/**
 * Kết quả kiểm tra 1 link đặt mật khẩu /set-password?token=... (CBR-24, CBR-31).
 *
 * Ai tạo: VerificationCodeService.inspectSetupLink.
 * Ai dùng: SetPasswordServlet đọc getStatus() để chọn: hiện form / chuyển sang Quên mật khẩu / về đăng nhập / báo lỗi.
 *
 * @author Le Cong Minh
 */
public class SetupLinkCheck {

    /**
     * Trạng thái của link. Service kiểm tra theo thứ tự: không tìm thấy → đã dùng → hết hạn → hợp lệ.
     */
    public enum Status {
        /** Link còn dùng được: tìm thấy, chưa dùng, còn trong 15 phút → hiện form đặt mật khẩu. */
        VALID,
        /** Không có token, hoặc token không khớp dòng nào trong database (link bịa) → chỉ báo chung. */
        NOT_FOUND,
        /** Khách đã đặt mật khẩu bằng link này rồi (cột UsedAt đã có giờ) → mời đăng nhập. */
        USED,
        /** Chưa dùng nhưng đã quá 15 phút → chuyển sang Quên mật khẩu, điền sẵn email của link. */
        EXPIRED
    }

    private final Status status;
    private final VerificationCode link;

    /**
     * Tạo kết quả kiểm tra link.
     *
     * @param status trạng thái link (VALID / NOT_FOUND / USED / EXPIRED).
     * @param link   dòng link tìm được trong bảng VerificationCodes; truyền null khi NOT_FOUND (không có dòng nào).
     */
    public SetupLinkCheck(Status status, VerificationCode link) {
        this.status = status;
        this.link = link;
    }

    /**
     * @return trạng thái link, VD EXPIRED.
     */
    public Status getStatus() {
        return status;
    }

    /**
     * @return dòng link trong database — lấy getEmail() (email khách) và getAccountId() (tài khoản nào);
     *         trả null khi trạng thái là NOT_FOUND.
     */
    public VerificationCode getLink() {
        return link;
    }

    /**
     * @return true nếu link còn dùng được (trạng thái VALID); false cho 3 trạng thái còn lại.
     */
    public boolean isValid() {
        return status == Status.VALID;
    }
}
