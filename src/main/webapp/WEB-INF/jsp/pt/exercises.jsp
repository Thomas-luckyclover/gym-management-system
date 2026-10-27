<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="gms" uri="/WEB-INF/gms-functions.tld" %>
<%--
================================================================================
 exercises.jsp (PT) — MÀN THƯ VIỆN BÀI TẬP (/pt/exercises). PT luôn xem được, dùng khi soạn giáo án.

 AI GỬI DỮ LIỆU SANG?  ExerciseLibraryServlet.doGet (cùng Servlet với màn khách).
   library / libraryError / libraryUrl ("/pt/exercises")   như màn khách
   pageTitle, pageHelp, today, activeNav="exercises"         cho pt/_header.jspf và layout-pt.jspf

 Nội dung chung nằm ở common/_exercise-library.jspf.
================================================================================ --%>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<!DOCTYPE html>
<html lang="vi">
    <head>
        <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
        <title>Thư viện bài tập · Super Gym</title>
        <link rel="stylesheet" href="${ctx}/css/pages/pt/header.css?v=20261025city">
        <link rel="stylesheet" href="${ctx}/css/pages/shared/exercise-library.css?v=20261027c">
    </head>
    <body class="dock theme-pt exercise-page">
        <%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>
        <div class="app">
            <%@ include file="/WEB-INF/jsp/common/layout-pt.jspf" %>
            <div class="main">
                <%@ include file="/WEB-INF/jsp/pt/_header.jspf" %>
                <%@ include file="/WEB-INF/jsp/common/_exercise-library.jspf" %>
            </div>
        </div>
    </body>
</html>
