<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="gms" uri="/WEB-INF/gms-functions.tld" %>

<!DOCTYPE html>
<html lang="vi">
    <head>
        <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
        <title>Mua gói · Super Gym</title>
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/base.css?v=4">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/awning.css?v=2">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/weather.css?v=2">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/customer/dashboard.css?v=4">
        <%-- Oswald: font condensed cho chữ tiêu đề banner và giá trên thẻ gói (chỉ dùng ở màn này) --%>
        <link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Oswald:wght@600;700&display=swap">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/customer/package-selection.css?v=3">
        <script src="${pageContext.request.contextPath}/js/pages/customer/package-selection.js?v=2" defer></script>
    </head>

    <body class="dock ${themeClass}">
        <%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>

        <div class="app">
            <%@ include file="/WEB-INF/jsp/common/layout-customer.jspf" %>

            <div class="main">

                <%-- ===== Thanh trên cùng (dashboard: thông tin khách, do Servlet lấy từ CustomerDashboardService) ===== --%>
                <header class="topbar">
                    <div class="tb-left">
                        <h1 class="tb-title">Mua gói</h1>
                        <p class="tb-subtitle">Chọn gói tập hoặc vé ngày, giá đã gồm VAT.</p>
                    </div>

                    <c:if test="${not empty dashboard}">
                        <div class="top-right">
                            <a class="tb-user" href="${pageContext.request.contextPath}/account" title="Cài đặt tài khoản">
                                <c:choose>
                                    <c:when test="${not empty dashboard.avatar}">
                                        <img class="avatar" src="${pageContext.request.contextPath}${dashboard.avatar}" alt="Ảnh đại diện">
                                    </c:when>
                                    <c:otherwise>
                                        <span class="avatar"><c:out value="${dashboard.initials}" /></span>
                                    </c:otherwise>
                                </c:choose>
                                <span class="tb-user-text">
                                    <b><c:out value="${dashboard.fullName}" /></b>
                                    <span><c:out value="${dashboard.customerCode}" /></span>
                                </span>
                            </a>
                        </div>
                    </c:if>
                </header>

                <main class="content package-selection">

                    <%-- ===== Thông báo lỗi (dashboardError, selectionError: chuỗi do Servlet đặt khi Service thất bại) ===== --%>
                    <c:if test="${not empty dashboardError}">
                        <div class="alert-banner danger" role="alert">
                            <i class="fa-solid fa-circle-exclamation"></i>
                            <span><c:out value="${dashboardError}" /></span>
                        </div>
                    </c:if>
                    <c:if test="${not empty selectionError}">
                        <div class="alert-banner danger" role="alert">
                            <i class="fa-solid fa-circle-exclamation"></i>
                            <span><c:out value="${selectionError}" /></span>
                        </div>
                    </c:if>

                    <c:if test="${not empty packageSelection}">
                        <%-- Form lọc rỗng: mọi ô lọc (thanh trên, cột trái) khai báo form="packageFilterForm" để không lồng form
                             trong form (các thẻ gói bên dưới có form POST "Mua ngay"). Form dùng GET nên bộ lọc nằm trên URL.
                             package-selection.js gửi form khi đổi chip lọc hoặc ô sắp xếp (không dùng data-auto-submit của common.js vì
                             hàm đó tìm form bằng closest("form"), mà ô sắp xếp nằm ngoài form). common.js xử lý nút Xóa bộ lọc (data-clear-filters). --%>
                        <form id="packageFilterForm" method="get" action="${pageContext.request.contextPath}/customer/packages" role="search"></form>

                        <%-- ===== Khu 1: tìm kiếm + sắp xếp ===== --%>
                        <div class="package-toolbar">
                            <div class="package-toolbar__search">
                                <label class="visually-hidden" for="packageKeyword">Tìm gói theo tên</label>
                                <i class="fa-solid fa-magnifying-glass" aria-hidden="true"></i>
                                <input id="packageKeyword" name="keyword" type="search" maxlength="100" form="packageFilterForm"
                                       placeholder="Tên gói, ví dụ: 3 tháng, vé ngày, PT 1-1"
                                       data-placeholder-short="Tìm tên gói"
                                       value="<c:out value='${packageSelection.keyword}' />">
                            </div>

                            <div class="package-toolbar__sort">
                                <label for="packageSort">Sắp xếp</label>
                                <select id="packageSort" name="sort" form="packageFilterForm">
                                    <option value="default" ${packageSelection.sortKey == 'default' ? 'selected' : ''}>Mặc định</option>
                                    <option value="saving" ${packageSelection.sortKey == 'saving' ? 'selected' : ''}>Tiết kiệm hơn</option>
                                    <option value="price-asc" ${packageSelection.sortKey == 'price-asc' ? 'selected' : ''}>Giá thấp đến cao</option>
                                    <option value="price-desc" ${packageSelection.sortKey == 'price-desc' ? 'selected' : ''}>Giá cao đến thấp</option>
                                </select>
                            </div>

                            <button class="btn btn-primary btn-sm" type="submit" form="packageFilterForm">Tìm</button>
                            <button class="btn btn-outline btn-sm package-toolbar__toggle" type="button"
                                    data-filter-toggle aria-expanded="false" aria-controls="packageFilters">
                                <i class="fa-solid fa-sliders" aria-hidden="true"></i> Bộ lọc
                            </button>
                        </div>

                        <div class="package-body">

                            <%-- ===== Khu 2: cột bộ lọc (số liệu do Service query từ các gói đang bán) ===== --%>
                            <aside class="package-filters" id="packageFilters" aria-label="Bộ lọc gói">
                                <div class="package-filters__head">
                                    <b>Bộ lọc</b>
                                    <button class="package-filters__clear" type="button" data-clear-filters>Xóa tất cả</button>
                                </div>

                                <fieldset class="filter-group">
                                    <legend>Loại gói</legend>
                                    <label class="filter-chip">
                                        <input type="radio" name="type" value="all" form="packageFilterForm" ${packageSelection.typeFilter == 'all' ? 'checked' : ''}>
                                        <span>Tất cả <small>${packageSelection.allCount}</small></span>
                                    </label>
                                    <label class="filter-chip">
                                        <input type="radio" name="type" value="gym" form="packageFilterForm" ${packageSelection.typeFilter == 'gym' ? 'checked' : ''}>
                                        <span>Gói tập <small>${packageSelection.gymCount}</small></span>
                                    </label>
                                    <label class="filter-chip">
                                        <input type="radio" name="type" value="day-pass" form="packageFilterForm" ${packageSelection.typeFilter == 'day-pass' ? 'checked' : ''}>
                                        <span>Vé ngày <small>${packageSelection.dayPassCount}</small></span>
                                    </label>
                                    <label class="filter-chip">
                                        <input type="radio" name="type" value="pt" form="packageFilterForm" ${packageSelection.typeFilter == 'pt' ? 'checked' : ''}>
                                        <span>Gói PT <small>${packageSelection.ptCount}</small></span>
                                    </label>
                                </fieldset>

                                <%-- Khoảng giá: hai thanh kéo (không gửi lên) đồng bộ với hai ô số (gửi lên) nhờ package-selection.js
                                     (hàm bindPriceRange). Không có JS thì vẫn nhập số được. --%>
                                <fieldset class="filter-group" id="priceFilter">
                                    <legend>Khoảng giá</legend>
                                    <div class="price-range" data-price-range>
                                        <span class="price-range__fill" data-range-fill></span>
                                        <input class="price-range__slider" type="range" data-range-min aria-label="Giá thấp nhất"
                                               min="${packageSelection.minBound}" max="${packageSelection.maxBound}" step="10000" value="${packageSelection.minPrice}">
                                        <input class="price-range__slider" type="range" data-range-max aria-label="Giá cao nhất"
                                               min="${packageSelection.minBound}" max="${packageSelection.maxBound}" step="10000" value="${packageSelection.maxPrice}">
                                    </div>
                                    <div class="price-inputs">
                                        <span class="price-field">
                                            <input type="number" name="minPrice" form="packageFilterForm" inputmode="numeric" aria-label="Giá từ"
                                                   min="${packageSelection.minBound}" max="${packageSelection.maxBound}" step="10000" value="${packageSelection.minPrice}" data-price-min>
                                        </span>
                                        <span aria-hidden="true">–</span>
                                        <span class="price-field">
                                            <input type="number" name="maxPrice" form="packageFilterForm" inputmode="numeric" aria-label="Giá đến"
                                                   min="${packageSelection.minBound}" max="${packageSelection.maxBound}" step="10000" value="${packageSelection.maxPrice}" data-price-max>
                                        </span>
                                    </div>
                                    <button class="btn btn-outline btn-sm btn-block" type="submit" form="packageFilterForm">Áp dụng</button>
                                </fieldset>

                                <fieldset class="filter-group">
                                    <legend>Thời hạn</legend>
                                    <c:forEach var="durationOption" items="${packageSelection.durationOptions}">
                                        <label class="filter-chip">
                                            <input type="checkbox" name="duration" value="${durationOption.key}" form="packageFilterForm"
                                                   ${durationOption.selected ? 'checked' : ''}>
                                            <span><c:out value="${durationOption.label}" /> <small>${durationOption.count}</small></span>
                                        </label>
                                    </c:forEach>
                                </fieldset>

                                <fieldset class="filter-group">
                                    <legend>Hình thức mua</legend>
                                    <label class="filter-chip">
                                        <input type="checkbox" name="buy" value="online" form="packageFilterForm" ${packageSelection.buyOnline ? 'checked' : ''}>
                                        <span>Thanh toán trực tuyến <small>${packageSelection.onlineCount}</small></span>
                                    </label>
                                    <label class="filter-chip">
                                        <input type="checkbox" name="buy" value="counter" form="packageFilterForm" ${packageSelection.buyCounterOnly ? 'checked' : ''}>
                                        <span>Chỉ mua tại quầy <small>${packageSelection.counterOnlyCount}</small></span>
                                    </label>
                                </fieldset>
                            </aside>

                            <%-- ===== Khu 3: kết quả ===== --%>
                            <section class="package-results" aria-label="Danh sách gói">

                                <c:if test="${not empty packageSelection.chips}">
                                    <div class="package-chips">
                                        <c:forEach var="chip" items="${packageSelection.chips}">
                                            <button class="package-chip-active" type="button"
                                                    data-chip-field="${chip.field}" data-chip-value="<c:out value='${chip.key}' />">
                                                <c:out value="${chip.label}" /> <i class="fa-solid fa-xmark" aria-hidden="true"></i>
                                                <span class="visually-hidden">Bỏ bộ lọc</span>
                                            </button>
                                        </c:forEach>
                                    </div>
                                </c:if>

                                <c:choose>

                                    <%-- Chưa có gói nào đang bán (khác với lọc không ra kết quả) --%>
                                    <c:when test="${packageSelection.totalActiveCount == 0}">
                                        <div class="empty-state package-empty">
                                            <h2>Hiện chưa có gói nào đang bán</h2>
                                            <p>Vui lòng quay lại sau hoặc liên hệ quầy lễ tân để được tư vấn.</p>
                                        </div>
                                    </c:when>

                                    <%-- Có gói nhưng bộ lọc không khớp gói nào --%>
                                    <c:when test="${empty packageSelection.options}">
                                        <div class="empty-state package-empty">
                                            <h2>Không tìm thấy gói phù hợp</h2>
                                            <p>Thử từ khóa khác hoặc bỏ bớt bộ lọc.</p>
                                            <button class="btn btn-outline btn-sm" type="button" data-clear-filters>Xóa tất cả bộ lọc</button>
                                        </div>
                                    </c:when>

                                    <c:otherwise>
                                        <p class="package-count" aria-live="polite">
                                            Tìm thấy ${packageSelection.filteredCount} gói<c:if test="${packageSelection.totalPages > 1}"> · trang ${packageSelection.page}/${packageSelection.totalPages}</c:if>
                                            </p>

                                        <%-- Lưới thẻ gói (packageSelection.options: List<PackageOptionView>) --%>
                                        <div class="package-grid" id="packageGrid">
                                            <c:forEach var="option" items="${packageSelection.options}">
                                                <c:set var="pkg" value="${option.card.pkg}" />

                                                <%-- Thẻ gói kiểu banner: banner tiêu đề lớn (có dải chéo nhãn ở góc nếu có) → chân thẻ có giá + nút.
                                                     Giá gốc gạch ngang và số tiền tiết kiệm do Service tính sẵn (option.originalPrice, option.savedAmount);
                                                     JSP chỉ in, không làm phép tính. --%>
                                                <article class="package-card" id="packageCard-${pkg.packageId}">

                                                    <div class="package-card__banner">
                                                        <c:if test="${not empty option.imagePath}">
                                                            <img class="package-card__bg" src="${pageContext.request.contextPath}${option.imagePath}" alt="" loading="lazy">
                                                        </c:if>
                                                        <%-- Dải chéo ở góc trên phải banner (chỉ khi có nhãn): "Tiết kiệm hơn" vàng, "Mới ra mắt" đỏ --%>
                                                        <c:if test="${not empty option.card.badge}">
                                                            <span class="package-card__sash ${option.card.badge == 'Tiết kiệm hơn' ? 'is-saving' : 'is-new'}"><c:out value="${option.card.badge}" /></span>
                                                        </c:if>
                                                        <span class="package-card__brand">SUPER GYM</span>
                                                        <c:choose>
                                                            <c:when test="${option.purchasableOnline}">
                                                                <h2 class="package-card__headline"><c:out value="${fn:toUpperCase(option.durationLabel)}" /><br>TẬP</h2>
                                                                </c:when>
                                                                <c:otherwise>
                                                                <h2 class="package-card__headline"><c:out value="${fn:toUpperCase(pkg.packageName)}" /></h2>
                                                            </c:otherwise>
                                                        </c:choose>
                                                        <span class="package-card__kind"><c:out value="${fn:toUpperCase(option.typeLabel)}" /></span>
                                                    </div>

                                                    <div class="package-card__foot">
                                                        <div class="package-card__info">
                                                            <h3 class="package-card__name"><c:out value="${pkg.packageName}" /></h3>
                                                            <c:if test="${not empty option.originalPrice}">
                                                                <s class="package-card__was">${gms:formatMoney(option.originalPrice)}</s>
                                                                </c:if>
                                                            <div class="package-card__price">
                                                                ${gms:formatMoney(pkg.price)}
                                                                <small>/ <c:out value="${option.durationLabel}" /></small>
                                                            </div>
                                                            <c:if test="${not empty option.savedAmount}">
                                                                <div class="package-card__deal">
                                                                    <b>-${option.card.savingPercent}%</b>
                                                                    Tiết kiệm ${gms:formatMoney(option.savedAmount)}
                                                                </div>
                                                            </c:if>
                                                        </div>

                                                        <%-- Nút theo loại gói: mua được online → Mua ngay (nút vàng) + Xem chi tiết (viền);
                                                             chỉ mua tại quầy → chỉ có Xem chi tiết (không có nút đặt chỗ vì hệ thống không hỗ trợ).
                                                             Tooltip là phần thêm cho máy có chuột; cách mua đầy đủ nằm trong popup chi tiết.
                                                             package-selection.js (hàm bindDetailButtons) mở popup theo data-dialog-target. --%>
                                                        <div class="package-card__actions${option.purchasableOnline ? '' : ' package-card__actions--single'}">
                                                            <c:choose>
                                                                <c:when test="${option.purchasableOnline}">
                                                                    <form method="post" action="${pageContext.request.contextPath}/customer/checkout">
                                                                        <input type="hidden" name="packageId" value="${pkg.packageId}">
                                                                        <button class="package-card__buy" type="submit">Mua ngay</button>
                                                                    </form>
                                                                    <button class="package-card__detail" type="button"
                                                                            data-dialog-target="packageDialog-${pkg.packageId}">
                                                                        Xem chi tiết
                                                                    </button>
                                                                </c:when>
                                                                <c:otherwise>
                                                                    <div class="package-tip">
                                                                        <button class="package-card__detail is-solid" type="button"
                                                                                data-dialog-target="packageDialog-${pkg.packageId}"
                                                                                aria-describedby="packageTip-${pkg.packageId}">
                                                                            Xem chi tiết
                                                                        </button>
                                                                        <span class="package-tip__bubble" id="packageTip-${pkg.packageId}" role="tooltip">
                                                                            <c:out value="${option.purchaseHint}" />
                                                                        </span>
                                                                    </div>
                                                                </c:otherwise>
                                                            </c:choose>
                                                        </div>
                                                    </div>
                                                </article>

                                                <%-- ===== Popup chi tiết gói (dialog native: có sẵn phím Escape và trả focus về nút đã mở) =====
                                                     Dữ liệu đã nằm sẵn trong option nên không gọi thêm request. --%>
                                                <dialog class="package-dialog" id="packageDialog-${pkg.packageId}"
                                                        aria-labelledby="packageDialogTitle-${pkg.packageId}">
                                                    <div class="package-dialog__panel">
                                                        <header class="package-dialog__header">
                                                            <div>
                                                                <span class="package-card__type"><c:out value="${option.typeLabel}" /></span>
                                                                <h2 id="packageDialogTitle-${pkg.packageId}"><c:out value="${pkg.packageName}" /></h2>
                                                            </div>
                                                            <button class="package-dialog__close" type="button" data-dialog-close aria-label="Đóng">
                                                                <i class="fa-solid fa-xmark"></i>
                                                            </button>
                                                        </header>

                                                        <div class="package-dialog__body">
                                                            <c:if test="${not empty option.imagePath}">
                                                                <img class="package-dialog__image" src="${pageContext.request.contextPath}${option.imagePath}" alt="">
                                                            </c:if>

                                                            <c:if test="${not empty option.originalPrice}">
                                                                <s class="package-card__was">${gms:formatMoney(option.originalPrice)}</s>
                                                                </c:if>
                                                            <div class="package-card__price">
                                                                ${gms:formatMoney(pkg.price)}
                                                                <small>/ <c:out value="${option.durationLabel}" /></small>
                                                            </div>
                                                            <c:if test="${not empty option.card.monthlyPrice}">
                                                                <div class="package-card__sub">≈ ${gms:formatMoney(option.card.monthlyPrice)} mỗi tháng</div>
                                                            </c:if>
                                                            <c:if test="${not empty option.savedAmount}">
                                                                <div class="package-card__deal">
                                                                    <b>-${option.card.savingPercent}%</b>
                                                                    Tiết kiệm ${gms:formatMoney(option.savedAmount)} so với mua lẻ từng tháng
                                                                </div>
                                                            </c:if>
                                                            <c:if test="${not empty pkg.estimatedSessionCount}">
                                                                <p class="package-dialog__note">Khoảng ${pkg.estimatedSessionCount} buổi trong thời hạn gói.</p>
                                                            </c:if>

                                                            <c:if test="${not empty pkg.description}">
                                                                <p><c:out value="${pkg.description}" /></p>
                                                            </c:if>

                                                            <c:if test="${not empty pkg.benefitLines}">
                                                                <h3>Quyền lợi</h3>
                                                                <ul class="package-dialog__benefits">
                                                                    <c:forEach var="benefitLine" items="${pkg.benefitLines}">
                                                                        <li><i class="fa-solid fa-check" aria-hidden="true"></i> <c:out value="${benefitLine}" /></li>
                                                                        </c:forEach>
                                                                </ul>
                                                            </c:if>

                                                            <section class="package-dialog__howto">
                                                                <h3>Cách mua</h3>
                                                                <p><c:out value="${option.purchaseHint}" /></p>
                                                            </section>
                                                        </div>

                                                        <c:if test="${option.purchasableOnline}">
                                                            <footer class="package-dialog__footer">
                                                                <form method="post" action="${pageContext.request.contextPath}/customer/checkout">
                                                                    <input type="hidden" name="packageId" value="${pkg.packageId}">
                                                                    <button class="btn btn-primary" type="submit">
                                                                        Thanh toán ngay <i class="fa-solid fa-arrow-right"></i>
                                                                    </button>
                                                                </form>
                                                            </footer>
                                                        </c:if>
                                                    </div>
                                                </dialog>
                                            </c:forEach>
                                        </div>

                                        <%-- Phân trang (chỉ hiện khi có từ 2 trang; link giữ nguyên mọi bộ lọc đang chọn) --%>
                                        <c:if test="${packageSelection.totalPages > 1}">
                                            <nav class="package-pager" aria-label="Phân trang gói">
                                                <c:url var="pagerBase" value="/customer/packages">
                                                    <c:param name="keyword" value="${packageSelection.keyword}" />
                                                    <c:param name="type" value="${packageSelection.typeFilter}" />
                                                    <c:param name="sort" value="${packageSelection.sortKey}" />
                                                    <c:param name="minPrice" value="${packageSelection.minPrice}" />
                                                    <c:param name="maxPrice" value="${packageSelection.maxPrice}" />
                                                    <c:forEach var="durationOption" items="${packageSelection.durationOptions}">
                                                        <c:if test="${durationOption.selected}">
                                                            <c:param name="duration" value="${durationOption.key}" />
                                                        </c:if>
                                                    </c:forEach>
                                                    <c:if test="${packageSelection.buyOnline}"><c:param name="buy" value="online" /></c:if>
                                                    <c:if test="${packageSelection.buyCounterOnly}"><c:param name="buy" value="counter" /></c:if>
                                                </c:url>

                                                <c:choose>
                                                    <c:when test="${packageSelection.page > 1}">
                                                        <a class="package-pager__item" href="${pagerBase}&amp;page=${packageSelection.page - 1}" aria-label="Trang trước">
                                                            <i class="fa-solid fa-chevron-left" aria-hidden="true"></i>
                                                        </a>
                                                    </c:when>
                                                    <c:otherwise>
                                                        <span class="package-pager__item is-disabled" aria-hidden="true"><i class="fa-solid fa-chevron-left"></i></span>
                                                        </c:otherwise>
                                                    </c:choose>

                                                <c:forEach var="pageNumber" begin="1" end="${packageSelection.totalPages}">
                                                    <c:choose>
                                                        <c:when test="${pageNumber == packageSelection.page}">
                                                            <span class="package-pager__item is-current" aria-current="page">${pageNumber}</span>
                                                        </c:when>
                                                        <c:otherwise>
                                                            <a class="package-pager__item" href="${pagerBase}&amp;page=${pageNumber}">${pageNumber}</a>
                                                        </c:otherwise>
                                                    </c:choose>
                                                </c:forEach>

                                                <c:choose>
                                                    <c:when test="${packageSelection.page < packageSelection.totalPages}">
                                                        <a class="package-pager__item" href="${pagerBase}&amp;page=${packageSelection.page + 1}" aria-label="Trang sau">
                                                            <i class="fa-solid fa-chevron-right" aria-hidden="true"></i>
                                                        </a>
                                                    </c:when>
                                                    <c:otherwise>
                                                        <span class="package-pager__item is-disabled" aria-hidden="true"><i class="fa-solid fa-chevron-right"></i></span>
                                                        </c:otherwise>
                                                    </c:choose>
                                            </nav>
                                        </c:if>
                                    </c:otherwise>
                                </c:choose>
                            </section>
                        </div>
                    </c:if>
                </main>
            </div>
        </div>
    </body>
</html>
