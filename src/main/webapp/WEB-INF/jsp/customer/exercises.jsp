<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="gms" uri="/WEB-INF/gms-functions.tld" %>
<%--
================================================================================
 exercises.jsp (khách) — MÀN THƯ VIỆN BÀI TẬP (/customer/exercises). Đặc quyền hội viên.

 AI GỬI DỮ LIỆU SANG?  ExerciseLibraryServlet.doGet gọi ExerciseLibraryService.getPage MỘT lần.
   library       ExerciseLibraryView: màn khóa (chưa có gói còn hiệu lực) hoặc bộ lọc + lưới bài + phân trang
   libraryError  câu lỗi khi thư viện trống mà wger lỗi, hoặc DB lỗi
   libraryUrl    "/customer/exercises" — gốc của mọi link lọc
   (thẻ tài khoản góc phải: common/topbar-user.jspf, đọc session)

 Nội dung chung với màn PT nằm ở common/_exercise-library.jspf; file này chỉ có khung trang khách.
================================================================================ --%>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<!DOCTYPE html>
<html lang="vi">
<head>
    <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
    <title>Thư viện bài tập · Super Gym</title>
    <link rel="stylesheet" href="${ctx}/css/pages/shared/exercise-library.css?v=20261030jump">
    <link rel="stylesheet" href="${ctx}/css/pages/auth/notification-bell.css">
    <script src="${ctx}/js/pages/auth/notification-bell.js" defer></script>
</head>
<body class="dock theme-pt exercise-page">
<%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>
<div class="app">
    <%@ include file="/WEB-INF/jsp/common/layout-customer.jspf" %>
    <div class="main">
        <header class="topbar">
            <div class="tb-left">
                <h1 class="tb-title">Thư viện bài tập</h1>
                <span class="pt-info">
                    <button type="button" class="pt-info-trigger" aria-label="Hướng dẫn Thư viện bài tập" aria-describedby="exercise-help"><span aria-hidden="true">i</span></button>
                    <span id="exercise-help" class="pt-info-popover" role="tooltip">
                        <strong>Đặc quyền hội viên:</strong> mở khi bạn có gói tập hoặc gói PT còn hiệu lực (kể cả đang bảo lưu).<br>
                        <strong>Tìm bài:</strong> chọn nhóm cơ, dụng cụ hoặc gõ tên tiếng Anh (ví dụ squat, press).<br>
                        Hỏi PT trước khi tập bài mới.<br>
                        <%-- Ghi nguồn bắt buộc của wger (CC BY-SA 4.0) --%>
                        <strong>Nguồn:</strong> dữ liệu và hình từ wger.de (CC BY-SA 4.0), cập nhật mỗi ngày · ảnh nhóm cơ từ free-exercise-db (public domain).
                    </span>
                </span>
            </div>
            <div class="top-right">
                <%@ include file="/WEB-INF/jsp/common/notification-bell.jspf" %>
                <%@ include file="/WEB-INF/jsp/common/topbar-user.jspf" %>
            </div>
        </header>

        <%@ include file="/WEB-INF/jsp/common/_exercise-library.jspf" %>
    </div>
</div>
</body>
</html>
