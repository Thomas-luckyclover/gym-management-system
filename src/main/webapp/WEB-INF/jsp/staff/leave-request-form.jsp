<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="gms" uri="/WEB-INF/gms-functions.tld" %>

<!DOCTYPE html>
<html lang="vi">
<head>
  <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
  <title>Nộp đơn nghỉ phép · Super Gym</title>
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/manager/leave-requests.css">
</head>

<body class="dock">
  <%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>

  <div class="app">
    <%-- TODO: layout sidebar theo role (PT hoặc Receptionist) --%>

    <div class="main">
      <main class="content" id="main-content" tabindex="-1">
        <%-- TODO: form nộp đơn nghỉ phép --%>
      </main>
    </div>
  </div>
</body>
</html>
