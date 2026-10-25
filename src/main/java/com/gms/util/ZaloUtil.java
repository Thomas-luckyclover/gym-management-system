package com.gms.util;

/**
 * Đổi số điện thoại thành link mở chat Zalo: https://zalo.me/<số>.
 *
 * Dùng ở:
 *   - Khách xem PT:  MyPtSessionService, MyBookingService (nút "Nhắn Zalo PT")
 *   - PT xem khách:  PtAssignedPackageView.getCustomerZaloUrl / getPartnerZaloUrl (PT Dashboard)
 */
public final class ZaloUtil {

    private ZaloUtil() {
    }

    /**
     * HÀM CHÍNH — SĐT → link Zalo. Không có số → null (JSP thấy null thì không vẽ nút).
     *
     * Ví dụ:  "0912 345 678"  → https://zalo.me/0912345678
     *         "+84912345678"  → https://zalo.me/0912345678   (đầu 84 đổi về 0)
     */
    public static String toUrl(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }

        // B1: chỉ giữ chữ số (bỏ dấu cách, dấu +, dấu chấm).
        String digits = phone.replaceAll("\\D", "");

        // B2: mã nước 84 + 9 số → đổi về số 0 đầu.
        if (digits.startsWith("84") && digits.length() == 11) {
            digits = "0" + digits.substring(2);
        }

        return digits.isEmpty() ? null : "https://zalo.me/" + digits;
    }
}
