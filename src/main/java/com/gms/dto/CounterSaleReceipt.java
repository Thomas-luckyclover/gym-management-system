package com.gms.dto;

/**
 * BIÊN LAI KẾT QUẢ BÁN GÓI TẠI QUẦY (/receptionist/sales?receipt=…), SRS #37.
 *
 * Ai tạo: CounterSaleService.checkout sau khi transaction tạo đơn và cấp gói thành công.
 * Ai dùng: counter-sale-receipt.jsp hiển thị phiếu thu và hỗ trợ in nhiệt (window.print).
 * Chỉ đọc: tạo bằng constructor, không có setter.
 */
public class CounterSaleReceipt {

    // DAO: Orders.OrderId tự sinh từ database.
    // Ý nghĩa: khóa chính định danh đơn hàng vừa thanh toán.
    // Cách dùng: gắn vào URL ?receipt=... và đối soát kỹ thuật.
    private final int orderId;

    // DAO: Orders.OrderCode sinh từ OrderDAO.generateNextOrderCode, dạng DH-2026-0001.
    // Ý nghĩa: mã đơn hàng nghiệp vụ hiển thị cho khách và kế toán.
    // Cách dùng: in to ở đầu biên lai thu tiền.
    private final String orderCode;

    // DAO: Persons.FullName của khách hàng thụ hưởng.
    // Ý nghĩa: họ tên hội viên được cấp thẻ tập.
    // Cách dùng: in ở dòng "Hội viên" trên biên lai.
    private final String customerName;

    // DAO: Customers.CustomerCode, dạng KH-0102.
    // Ý nghĩa: mã định danh khách hàng trong phòng gym.
    // Cách dùng: in kèm số điện thoại trên biên lai.
    private final String customerCode;

    // DAO: Persons.Phone hoặc Customers.GuardianPhone đã định dạng.
    // Ý nghĩa: số điện thoại liên hệ của hội viên hoặc người giám hộ.
    // Cách dùng: in ở dòng thông tin khách trên biên lai.
    private final String phoneText;

    // DAO: Packages.PackageName snapshot lúc bán.
    // Ý nghĩa: tên gói dịch vụ khách đã mua.
    // Cách dùng: in đậm ở mục "Gói đăng ký" trên biên lai.
    private final String packageName;

    // Service: thời hạn viết sẵn, ví dụ "1 tháng", "1 ngày".
    // Ý nghĩa: thời lượng sử dụng của gói.
    // Cách dùng: in kèm tên gói trong ngoặc đơn trên biên lai.
    private final String durationText;

    // Service: Subscriptions.StartDate định dạng dd/MM/yyyy qua DateTimeUtil.formatDate.
    // Ý nghĩa: ngày bắt đầu có hiệu lực vào phòng tập.
    // Cách dùng: in ở phần "Hiệu lực gói: [Từ ngày] → [Đến ngày]".
    private final String startDateText;

    // Service: Subscriptions.EndDate định dạng dd/MM/yyyy qua DateTimeUtil.formatDate.
    // Ý nghĩa: ngày cuối cùng hội viên được quyền vào phòng tập.
    // Cách dùng: in ở phần "Hiệu lực gói: [Từ ngày] → [Đến ngày]".
    private final String endDateText;

    // DAO: Orders.UnitPriceSnapshot trừ DiscountAmount, đã gồm VAT.
    // Ý nghĩa: tổng số tiền thực tế lễ tân đã thu từ khách tại quầy.
    // Cách dùng: in to, màu cam ở dòng "TỔNG ĐÃ THU" qua gms:formatMoney.
    private final long finalAmount;

    // DAO: Transactions.PaymentMethod, giá trị "Cash" hoặc "Transfer".
    // Ý nghĩa: mã phương thức thanh toán lưu trong cơ sở dữ liệu.
    // Cách dùng: dùng kiểm tra logic hoặc phân loại nội bộ.
    private final String paymentMethod;

    // Service: nhãn tiếng Việt tương ứng, ví dụ "Tiền mặt" hoặc "Chuyển khoản (POS / QR)".
    // Ý nghĩa: tên phương thức thanh toán thân thiện với người dùng.
    // Cách dùng: in ở dòng "Hình thức" trên biên lai.
    private final String paymentMethodText;

    // Service: thời điểm ghi nhận giao dịch định dạng dd/MM/yyyy HH:mm qua DateTimeUtil.formatDateTime.
    // Ý nghĩa: ngày giờ chính xác lễ tân bấm xác nhận thu tiền.
    // Cách dùng: in ở dòng "Thời gian thu" trên biên lai.
    private final String paidAtText;

    // Session: tên hiển thị của tài khoản lễ tân đang trực ca.
    // Ý nghĩa: nhân viên chịu trách nhiệm thu khoản tiền này.
    // Cách dùng: in ở dòng "Lễ tân thu tiền" trên biên lai.
    private final String receptionistName;

    // DAO: Orders.DiscountAmount (đồng), 0 nếu không áp dụng voucher.
    // Ý nghĩa: số tiền được miễn giảm từ voucher ưu đãi.
    // Cách dùng: in ở dòng "Giảm giá" trên biên lai nếu > 0.
    private final long discountAmount;

    // DAO: Vouchers.VoucherCode áp dụng (null nếu không có voucher).
    // Ý nghĩa: mã ưu đãi khách hàng đã sử dụng.
    // Cách dùng: in kèm dòng giảm giá trên biên lai.
    private final String voucherCode;

    public CounterSaleReceipt(int orderId, String orderCode, String customerName, String customerCode,
            String phoneText, String packageName, String durationText, String startDateText,
            String endDateText, long finalAmount, String paymentMethod, String paymentMethodText,
            String paidAtText, String receptionistName) {
        this(orderId, orderCode, customerName, customerCode, phoneText, packageName, durationText,
                startDateText, endDateText, finalAmount, paymentMethod, paymentMethodText, paidAtText,
                receptionistName, 0L, null);
    }

    public CounterSaleReceipt(int orderId, String orderCode, String customerName, String customerCode,
            String phoneText, String packageName, String durationText, String startDateText,
            String endDateText, long finalAmount, String paymentMethod, String paymentMethodText,
            String paidAtText, String receptionistName, long discountAmount, String voucherCode) {
        this.orderId = orderId;
        this.orderCode = orderCode;
        this.customerName = customerName;
        this.customerCode = customerCode;
        this.phoneText = phoneText;
        this.packageName = packageName;
        this.durationText = durationText;
        this.startDateText = startDateText;
        this.endDateText = endDateText;
        this.finalAmount = finalAmount;
        this.paymentMethod = paymentMethod;
        this.paymentMethodText = paymentMethodText;
        this.paidAtText = paidAtText;
        this.receptionistName = receptionistName;
        this.discountAmount = discountAmount;
        this.voucherCode = voucherCode;
    }

    // Mã đơn tự tăng trong database, dùng trong URL hoặc đối soát kỹ thuật.
    public int getOrderId() {
        return orderId;
    }

    // Mã đơn dạng DH-2026-nnnn, in nổi bật ở đầu biên lai POS.
    public String getOrderCode() {
        return orderCode;
    }

    // Họ tên hội viên thụ hưởng, in ở dòng thông tin khách trên biên lai POS.
    public String getCustomerName() {
        return customerName;
    }

    // Mã hội viên dạng KH-nnnn, in kèm số điện thoại trên biên lai POS.
    public String getCustomerCode() {
        return customerCode;
    }

    // Số điện thoại hội viên, in ở dòng thông tin liên hệ trên biên lai POS.
    public String getPhoneText() {
        return phoneText;
    }

    // Tên gói dịch vụ đã mua, in ở mục chi tiết dịch vụ trên biên lai POS.
    public String getPackageName() {
        return packageName;
    }

    // Thời hạn sử dụng viết sẵn, in kèm tên gói trên biên lai POS.
    public String getDurationText() {
        return durationText;
    }

    // Ngày bắt đầu hiệu lực dd/MM/yyyy, in trong mục khoảng thời gian hiệu lực trên biên lai POS.
    public String getStartDateText() {
        return startDateText;
    }

    // Ngày hết hạn hiệu lực dd/MM/yyyy, in trong mục khoảng thời gian hiệu lực trên biên lai POS.
    public String getEndDateText() {
        return endDateText;
    }

    // Tổng số tiền đã thu (đồng), in đậm ở dòng "TỔNG ĐÃ THU" trên biên lai POS.
    public long getFinalAmount() {
        return finalAmount;
    }

    // Mã phương thức thanh toán ("Cash"/"Transfer"), dùng để kiểm tra logic.
    public String getPaymentMethod() {
        return paymentMethod;
    }

    // Tên phương thức thanh toán tiếng Việt, in ở dòng "Phương thức" trên biên lai POS.
    public String getPaymentMethodText() {
        return paymentMethodText;
    }

    // Thời điểm thu tiền dd/MM/yyyy HH:mm, in ở dòng thời gian trên biên lai POS.
    public String getPaidAtText() {
        return paidAtText;
    }

    // Tên lễ tân thu tiền, in ở dòng nhân viên phụ trách trên biên lai POS.
    public String getReceptionistName() {
        return receptionistName;
    }

    // Số tiền giảm giá được trừ, 0 nếu không có voucher.
    public long getDiscountAmount() {
        return discountAmount;
    }

    // Mã voucher áp dụng, null nếu không có voucher.
    public String getVoucherCode() {
        return voucherCode;
    }
}
