<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="gms" uri="/WEB-INF/gms-functions.tld" %>
<%--
================================================================================
 counter-sale-customers.jsp — BÁN GÓI TẠI QUẦY, BƯỚC 2: CHỌN KHÁCH (/receptionist/sales?package=…), SRS #37

 AI GỬI DỮ LIỆU SANG?  CounterSaleServlet.showCustomers
 BIẾN     selectedCard (CounterPackageCard, gói đã chọn ở bước 1)
 TÌM KHÁCH  AJAX: counter-sale.js gọi GET /receptionist/sales/customers?q=… (CounterSaleServlet.writeCustomers) rồi tự vẽ bảng
 QUY TẮC  Gói tập và vé ngày bán cho mọi hồ sơ chưa xóa (Mới, Đang hoạt động, Bảo lưu, Hết hạn): nút "Chọn" luôn bấm được.
          Hồ sơ chưa có tài khoản vẫn mua được, chỉ gắn nhãn để lễ tân biết.
 BỐ CỤC   trái: ô tìm + bảng khách + nút Đổi gói / Tạo hồ sơ · phải: "Tóm tắt đơn"
================================================================================ --%>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<c:set var="base" value="${ctx}/receptionist/sales" />
<!DOCTYPE html>
<html lang="vi">
<head>
    <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
    <title>Chọn khách · Super Gym Lễ tân</title>
    <link rel="stylesheet" href="${ctx}/css/pages/receptionist/counter-sale.css?v=20261031_quickmodal">
    <script src="${ctx}/js/pages/receptionist/counter-sale.js?v=20261031_quickmodal" defer></script>
</head>
<body class="dock theme-staff counter-sale-page">
<%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>
<div class="app">
    <%@ include file="/WEB-INF/jsp/common/layout-receptionist.jspf" %>
    <div class="main">
        <header class="topbar">
            <div class="tb-left">
                <h1 class="tb-title">Chọn khách</h1>
            </div>
            <div class="top-right">
                <a class="tb-user" href="${ctx}/account" title="Cài đặt tài khoản">
                    <span class="avatar">LT</span>
                    <span class="tb-user-text"><b><c:out value="${sessionScope.displayName}" default="Lễ tân" /></b><small>Lễ tân quầy</small></span>
                </a>
            </div>
        </header>

        <main class="content counter-sale" id="main-content" tabindex="-1">
            <div class="counter-layout">

                <%-- ===== Cột trái: ô tìm khách (AJAX) + bảng kết quả ===== --%>
                <section class="counter-block">
                    <%-- #customerSearch: counter-sale.js hàm handleCustomerInput() đọc chữ gõ rồi gọi loadCustomers().
                         data-package-id gắn gói đã chọn vào nút "Chọn" của từng dòng. Không có form, không có nút Tìm. --%>
                    <div class="counter-search" role="search">
                        <label class="visually-hidden" for="customerSearch">Tìm khách</label>
                        <i class="fa-solid fa-magnifying-glass" aria-hidden="true"></i>
                        <input id="customerSearch" type="search" data-package-id="${selectedCard.packageId}"
                               placeholder="Tên, số điện thoại hoặc mã KH (KH-0102)" autocomplete="off" spellcheck="false" autofocus>
                    </div>

                    <%-- #customerState: chữ hướng dẫn / đang tìm / không thấy / lỗi. counter-sale.js hàm showCustomerState() ghi vào đây. --%>
                    <p class="counter-empty" id="customerState" aria-live="polite">Gõ tên, số điện thoại hoặc mã KH để tìm khách.
                        <span class="counter-hint">Tên không cần dấu: "nguyen van an", "0901", "KH-0102" đều được.</span></p>

                    <%-- #customerTableWrap hiện khi có kết quả; #customerTableBody do counter-sale.js hàm renderCustomerRows() đổ dòng vào --%>
                    <div class="counter-table-wrap" id="customerTableWrap" hidden>
                        <table class="counter-table no-gms-table">
                            <thead>
                                <tr>
                                    <th scope="col">Khách hàng</th>
                                    <th scope="col">Trạng thái</th>
                                    <th scope="col">Gói tập</th>
                                    <th scope="col"><span class="visually-hidden">Chọn</span></th>
                                </tr>
                            </thead>
                            <tbody id="customerTableBody"></tbody>
                        </table>
                    </div>

                    <%-- Hàng nút dưới bảng: Đổi gói (trái) · Tạo hồ sơ mới (phải, mở popup tạo nhanh) --%>
                    <div class="counter-actions">
                        <a class="btn btn-ghost" href="${base}">← Đổi gói</a>
                        <button type="button" class="btn btn-outline" id="btnOpenQuickCustomerModal">+ Tạo hồ sơ</button>
                    </div>
                </section>

                <%-- ===== Cột phải: Tóm tắt đơn (selectedCard). Dòng Giảm giá thêm ở bước Voucher. ===== --%>
                <aside class="counter-summary" aria-label="Tóm tắt đơn">
                    <p class="counter-summary-title">Tóm tắt đơn</p>
                    <dl class="counter-summary-list">
                        <div><dt>Khách</dt><dd class="counter-muted">Chưa chọn</dd></div>
                        <div><dt>Gói</dt><dd><c:out value="${selectedCard.packageName}" /></dd></div>
                        <div><dt>Giá gói</dt><dd><c:out value="${gms:formatMoney(selectedCard.price)}" /></dd></div>
                        <div><dt>Trước VAT</dt><dd><c:out value="${gms:formatMoney(selectedCard.preTaxAmount)}" /></dd></div>
                        <div><dt>VAT <c:out value="${selectedCard.vatRate}" />% (đã gồm)</dt><dd><c:out value="${gms:formatMoney(selectedCard.vatAmount)}" /></dd></div>
                        <div class="is-total"><dt>Phải thu</dt><dd><c:out value="${gms:formatMoney(selectedCard.price)}" /></dd></div>
                    </dl>
                </aside>
            </div>
        </main>
    </div>
</div>

<%-- ===== MODAL TẠO NHANH HỒ SƠ KHÁCH HÀNG ===== --%>
<div class="quick-modal-backdrop" id="quickCustomerBackdrop" hidden>
    <div class="quick-modal" role="dialog" aria-modal="true" aria-labelledby="quickModalTitle">
        <header class="quick-modal-header">
            <h2 class="quick-modal-title" id="quickModalTitle">
                <i class="fa-solid fa-user-plus" aria-hidden="true"></i> Tạo hồ sơ khách hàng
            </h2>
            <button type="button" class="quick-modal-close" id="btnCloseQuickModal" aria-label="Đóng popup">×</button>
        </header>

        <form id="formQuickCustomer" novalidate>
            <input type="hidden" name="csrfToken" id="quickCsrfToken" value="${csrfToken}">
            <input type="hidden" name="packageId" id="quickPackageId" value="${selectedCard.packageId}">

            <div class="quick-modal-body">
                <div class="quick-form-alert" id="quickModalAlert" hidden></div>

                <div class="quick-form-group">
                    <label class="quick-label">Loại hồ sơ <span class="quick-req">*</span></label>
                    <div class="quick-radio-group">
                        <label class="quick-radio-label">
                            <input type="radio" name="customerType" value="Self" checked>
                            <span>Tự sở hữu (Độc lập)</span>
                        </label>
                        <label class="quick-radio-label">
                            <input type="radio" name="customerType" value="Dependent">
                            <span>Phụ thuộc (Trẻ em/Người thân)</span>
                        </label>
                    </div>
                    <p class="quick-hint">Loại hồ sơ không thể thay đổi sau khi tạo (BR-18).</p>
                </div>

                <div class="quick-form-group">
                    <label class="quick-label" for="qcFullName">Họ và tên <span class="quick-req">*</span></label>
                    <input type="text" id="qcFullName" name="fullName" class="quick-input" placeholder="Ví dụ: Nguyễn Văn An" maxlength="50" required>
                    <span class="quick-field-error" id="err_fullName" hidden></span>
                </div>

                <div class="quick-form-row">
                    <div class="quick-form-group quick-col-6" id="wrapPhone">
                        <label class="quick-label" for="qcPhone">Số điện thoại <span class="quick-req">*</span></label>
                        <input type="tel" id="qcPhone" name="phone" class="quick-input" placeholder="0901234567" maxlength="10" required>
                        <span class="quick-field-error" id="err_phone" hidden></span>
                    </div>

                    <div class="quick-form-group quick-col-6">
                        <label class="quick-label" for="qcDob">Ngày sinh <span class="quick-req">*</span></label>
                        <input type="date" id="qcDob" name="dateOfBirth" class="quick-input" min="1924-01-01" required>
                        <span class="quick-field-error" id="err_dateOfBirth" hidden></span>
                    </div>
                </div>

                <%-- Nhóm thông tin người giám hộ (Chỉ hiện khi chọn Dependent) --%>
                <div class="quick-guardian-box" id="wrapGuardian" hidden>
                    <p class="quick-guardian-title"><i class="fa-solid fa-shield-halved" aria-hidden="true"></i> Thông tin Người giám hộ</p>
                    <div class="quick-form-group">
                        <label class="quick-label" for="qcGuardianName">Tên người giám hộ <span class="quick-req">*</span></label>
                        <input type="text" id="qcGuardianName" name="guardianName" class="quick-input" placeholder="Ví dụ: Trần Thị Bình" maxlength="50">
                        <span class="quick-field-error" id="err_guardianName" hidden></span>
                    </div>
                    <div class="quick-form-row">
                        <div class="quick-form-group quick-col-6">
                            <label class="quick-label" for="qcGuardianRel">Mối quan hệ <span class="quick-req">*</span></label>
                            <input type="text" id="qcGuardianRel" name="guardianRelationship" class="quick-input" placeholder="Bố, Mẹ, Anh..." maxlength="30">
                            <span class="quick-field-error" id="err_guardianRelationship" hidden></span>
                        </div>
                        <div class="quick-form-group quick-col-6">
                            <label class="quick-label" for="qcGuardianPhone">SĐT người giám hộ <span class="quick-req">*</span></label>
                            <input type="tel" id="qcGuardianPhone" name="guardianPhone" class="quick-input" placeholder="0912345678" maxlength="10">
                            <span class="quick-field-error" id="err_guardianPhone" hidden></span>
                        </div>
                    </div>
                </div>
            </div>

            <footer class="quick-modal-footer">
                <button type="button" class="btn btn-ghost" id="btnCancelQuickModal">Hủy</button>
                <button type="submit" class="btn btn-primary" id="btnSubmitQuickCustomer">
                    <span class="btn-text">Tạo & Chọn mua gói</span>
                    <i class="fa-solid fa-arrow-right" aria-hidden="true"></i>
                </button>
            </footer>
        </form>
    </div>
</div>
</body>
</html>