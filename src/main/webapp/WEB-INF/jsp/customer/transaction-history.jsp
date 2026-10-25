<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="gms" uri="/WEB-INF/gms-functions.tld" %>

<!DOCTYPE html>
<html lang="vi">
    <head>
        <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
        <title>Lịch sử giao dịch · Super Gym</title>

        <link rel="stylesheet"
              href="${pageContext.request.contextPath}/css/pages/customer/dashboard.css?v=7">

        <style>
            .history-panel {
                padding: 24px;
                background: #fff;
                border: 1px solid #ddd;
                border-radius: 16px;
            }

            .history-filter,
            .history-pagination {
                display: flex;
                flex-wrap: wrap;
                align-items: center;
                gap: 12px;
                margin: 16px 0;
            }

            .history-table-wrap {
                overflow-x: auto;
            }

            .history-table {
                width: 100%;
                border-collapse: collapse;
            }

            .history-table th,
            .history-table td {
                padding: 14px 12px;
                border-bottom: 1px solid #e5e5e5;
                text-align: left;
                vertical-align: top;
            }

            .history-table small {
                display: block;
                margin-top: 6px;
                color: #666;
                overflow-wrap: anywhere;
            }

            .history-actions a {
                display: block;
                margin-bottom: 8px;
            }

            .history-error {
                color: #b42318;
            }
        </style>
    </head>

    <body class="dock ${themeClass}">
        <div class="app">
            <%@ include file="/WEB-INF/jsp/common/layout-customer.jspf" %>

            <div class="main">
                <header class="topbar">
                    <h1 class="tb-title">Lịch sử giao dịch</h1>
                </header>

                <main class="content" id="main-content">
                    <section class="history-panel">
                        <p>
                            Theo dõi từng lần thanh toán và gói đã được cấp.
                            Bạn không cần xác nhận thanh toán tại đây.
                        </p>

                        <c:url var="historyUrl" value="/customer/transactions"/>

                        <form class="history-filter"
                              method="get"
                              action="${historyUrl}">

                            <label for="status">Trạng thái</label>

                            <select name="status" id="status">
                                <option value="">Tất cả</option>

                                <option value="Pending"
                                    ${selectedStatus eq 'Pending' ? 'selected' : ''}>
                                    Đang chờ kết quả
                                </option>

                                <option value="Success"
                                    ${selectedStatus eq 'Success' ? 'selected' : ''}>
                                    Thành công
                                </option>

                                <option value="Failed"
                                    ${selectedStatus eq 'Failed' ? 'selected' : ''}>
                                    Thất bại
                                </option>

                                <option value="Expired"
                                    ${selectedStatus eq 'Expired' ? 'selected' : ''}>
                                    Hết hạn lần thanh toán
                                </option>
                            </select>

                            <button type="submit" class="btn btn-outline">
                                Lọc
                            </button>
                        </form>

                        <c:choose>
                            <c:when test="${not empty historyError}">
                                <p class="history-error" role="alert">
                                    <c:out value="${historyError}"/>
                                </p>
                            </c:when>

                            <c:otherwise>
                                <c:choose>
                                    <c:when test="${empty historyItems}">
                                        <p>Không có giao dịch ở trang này.</p>
                                    </c:when>

                                    <c:otherwise>
                                        <div class="history-table-wrap">
                                            <table class="history-table">
                                                <thead>
                                                    <tr>
                                                        <th>Đơn / gói</th>
                                                        <th>Số tiền</th>
                                                        <th>Phương thức</th>
                                                        <th>Thời gian</th>
                                                        <th>Trạng thái</th>
                                                        <th>Hoàn tiền</th>
                                                        <th>Chi tiết</th>
                                                    </tr>
                                                </thead>

                                                <tbody>
                                                    <c:forEach var="item"
                                                               items="${historyItems}">
                                                        <tr>
                                                            <td>
                                                                <strong>
                                                                    <c:out value="${item.orderCode}"/>
                                                                </strong>

                                                                <div>
                                                                    <c:out value="${item.packageName}"/>
                                                                </div>

                                                                <small>
                                                                    Mã tham chiếu:
                                                                    <c:out value="${item.gatewayReference}"
                                                                           default="—"/>
                                                                </small>
                                                            </td>

                                                            <td>
                                                                <c:out value="${gms:formatMoney(item.amount)}"/>
                                                            </td>

                                                            <td>
                                                                <c:out value="${item.paymentMethodLabel}"/>
                                                            </td>

                                                            <td>
                                                                Tạo:
                                                                <c:out value="${item.createdAtText}"/>

                                                                <small>
                                                                    GMS xử lý:
                                                                    <c:out value="${item.completedAtText}"/>
                                                                </small>
                                                            </td>

                                                            <td>
                                                                <c:out value="${item.statusLabel}"/>

                                                                <c:if test="${item.status eq 'Expired'}">
                                                                    <small>
                                                                        Hết hạn không đồng nghĩa
                                                                        đã xác nhận tiền chưa về.
                                                                    </small>
                                                                </c:if>
                                                            </td>

                                                            <td>
                                                                <c:out value="${item.refundLabel}"/>
                                                            </td>

                                                            <td class="history-actions">
                                                                <c:if test="${item.onlineGateway}">
                                                                    <c:url var="resultUrl"
                                                                           value="/customer/payment-result">
                                                                        <c:param name="transaction"
                                                                                 value="${item.transactionId}"/>
                                                                    </c:url>

                                                                    <a href="${resultUrl}">
                                                                        Xem kết quả
                                                                    </a>
                                                                </c:if>

                                                                <c:if test="${item.canViewPackage}">
                                                                    <c:url var="packageUrl"
                                                                           value="/customer/bookings">
                                                                        <c:param name="id"
                                                                                 value="${item.subscriptionId}"/>
                                                                    </c:url>

                                                                    <a href="${packageUrl}">
                                                                        Xem gói
                                                                    </a>
                                                                </c:if>
                                                            </td>
                                                        </tr>
                                                    </c:forEach>
                                                </tbody>
                                            </table>
                                        </div>
                                    </c:otherwise>
                                </c:choose>

                                <nav class="history-pagination"
                                     aria-label="Phân trang lịch sử giao dịch">

                                    <c:if test="${currentPage > 1}">
                                        <c:url var="previousUrl"
                                               value="/customer/transactions">
                                            <c:param name="page"
                                                     value="${currentPage - 1}"/>
                                            <c:param name="status"
                                                     value="${selectedStatus}"/>
                                        </c:url>

                                        <a href="${previousUrl}">Trang trước</a>
                                    </c:if>

                                    <span>
                                        Trang <c:out value="${currentPage}"/>
                                    </span>

                                    <c:if test="${hasNext}">
                                        <c:url var="nextUrl"
                                               value="/customer/transactions">
                                            <c:param name="page"
                                                     value="${currentPage + 1}"/>
                                            <c:param name="status"
                                                     value="${selectedStatus}"/>
                                        </c:url>

                                        <a href="${nextUrl}">Trang sau</a>
                                    </c:if>
                                </nav>
                            </c:otherwise>
                        </c:choose>
                    </section>
                </main>
            </div>
        </div>
    </body>
</html>