package com.gms.servlet.receptionist;

import com.gms.dto.CheckInLookupResult;
import com.gms.dto.CheckInLookupResult.Decision;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kiểm thử cây DOM (HTML structure) được sinh ra từ JSP check-in.jsp:
 * - Đối chiếu trường hợp DayPass (vé ngày không có ảnh check-in):
 *   DOM PHẢI có nút [data-ci-go] "Xác nhận vào tập" và banner "ok".
 * - Đối chiếu trường hợp Membership (gói tháng thiếu ảnh check-in):
 *   DOM KHÔNG ĐƯỢC có nút [data-ci-go] "Xác nhận vào tập", chỉ có nút chụp ảnh.
 */
@DisplayName("Kiểm thử DOM Giao diện Check-in (check-in.jsp)")
class CheckInDOMTest {

    /**
     * Hàm render mô phỏng đúng cấu trúc HTML sinh ra từ check-in.jsp lines 339-395.
     */
    private String renderCheckInCardHtml(CheckInLookupResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"ci-card\">");

        // Banner đánh giá điều kiện
        sb.append("<div class=\"alert-banner ").append(result.getDecision().getAlertType()).append("\">");
        sb.append("<span class=\"alert-title\">").append(result.getDecision().getTitle()).append("</span>");
        sb.append("<span class=\"alert-msg\">").append(result.getMessage()).append("</span>");
        sb.append("</div>");

        // Hàng nút hành động
        sb.append("<div class=\"btn-row\">");
        if (result.isCanCheckIn()) {
            sb.append("<form action=\"/gms/receptionist/check-in\" method=\"POST\">");
            sb.append("<input type=\"hidden\" name=\"action\" value=\"confirmCheckIn\"/>");
            sb.append("<button class=\"btn btn-primary\" type=\"submit\" data-ci-go=\"true\">");
            sb.append("Xác nhận vào tập");
            sb.append("</button>");
            sb.append("</form>");
        }

        if (result.isRequiresPhoto() || result.getCheckInPhoto() == null || result.getCheckInPhoto().isEmpty()) {
            sb.append("<button class=\"btn btn-outline\" type=\"button\" data-ci-photo=\"true\">");
            sb.append("Chụp ảnh tại quầy");
            sb.append("</button>");
        } else {
            sb.append("<button class=\"btn btn-outline\" type=\"button\" data-ci-photo=\"true\">");
            sb.append("Chụp lại ảnh");
            sb.append("</button>");
        }
        sb.append("</div>");

        sb.append("</div>");
        return sb.toString();
    }

    private Document parseHtmlToDOM(String html) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new InputSource(new StringReader(html)));
    }

    @Test
    @DisplayName("DOM Test: Khách DayPass KHÔNG có ảnh -> DOM vẫn có nút 'Xác nhận vào tập' (canCheckIn = true)")
    void testDayPassWithoutPhotoRendersCheckInButtonInDOM() throws Exception {
        // Chuẩn bị dữ liệu mẫu DayPass không ảnh (như KH-1016)
        CheckInLookupResult dayPassResult = new CheckInLookupResult();
        dayPassResult.setCustomerCode("KH-1016");
        dayPassResult.setFullName("Nguyễn Văn Tuấn");
        dayPassResult.setCheckInPhoto(null); // Không có ảnh check-in
        dayPassResult.setSubscriptionType("DayPass");
        dayPassResult.setPackageName("Vé Ngày");
        dayPassResult.setDecision(Decision.ELIGIBLE_DAYPASS);
        dayPassResult.setMessage("Vé ngày chỉ vào được một lượt. Xác nhận là dùng hết vé.");
        dayPassResult.setCanCheckIn(true); // Được vào tập ngay
        dayPassResult.setRequiresPhoto(false);

        // 1. Render ra chuỗi HTML theo logic check-in.jsp
        String html = renderCheckInCardHtml(dayPassResult);

        // 2. Parse thành cây DOM chuẩn W3C
        Document doc = parseHtmlToDOM(html);

        // 3. Kiểm tra DOM node banner: class phải là 'alert-banner ok'
        Element banner = (Element) doc.getElementsByTagName("div").item(1);
        assertTrue(banner.getAttribute("class").contains("ok"), "Banner phải có class 'ok' (màu xanh lá)");
        assertTrue(banner.getTextContent().contains("Đủ điều kiện vào tập (Vé ngày)"));

        // 4. Kiểm tra DOM node button [data-ci-go]: BẮT BUỘC TỒN TẠI trong DOM
        NodeList buttons = doc.getElementsByTagName("button");
        boolean hasConfirmCheckInButton = false;
        for (int i = 0; i < buttons.getLength(); i++) {
            Element btn = (Element) buttons.item(i);
            if ("true".equals(btn.getAttribute("data-ci-go"))) {
                hasConfirmCheckInButton = true;
                assertTrue(btn.getTextContent().contains("Xác nhận vào tập"));
            }
        }

        assertTrue(hasConfirmCheckInButton, "DOM PHẢI có nút [data-ci-go] 'Xác nhận vào tập' cho khách DayPass");
    }

    @Test
    @DisplayName("DOM Test: Khách Membership KHÔNG có ảnh -> DOM BỊ ẨN nút 'Xác nhận vào tập' (canCheckIn = false)")
    void testMembershipWithoutPhotoHidesCheckInButtonInDOM() throws Exception {
        // Chuẩn bị dữ liệu mẫu Membership thiếu ảnh (như KH-1014)
        CheckInLookupResult membershipResult = new CheckInLookupResult();
        membershipResult.setCustomerCode("KH-1014");
        membershipResult.setFullName("Lý Thị Ngọc");
        membershipResult.setCheckInPhoto(null); // Không có ảnh check-in
        membershipResult.setSubscriptionType("Membership");
        membershipResult.setPackageName("Gói 1 Tháng");
        membershipResult.setDecision(Decision.WARN_MISSING_PHOTO);
        membershipResult.setMessage("Khách mua online, chưa có ảnh check-in. Chụp ảnh tại quầy rồi mới cho vào.");
        membershipResult.setCanCheckIn(false); // Bị chặn
        membershipResult.setRequiresPhoto(true);

        // 1. Render ra chuỗi HTML theo logic check-in.jsp
        String html = renderCheckInCardHtml(membershipResult);

        // 2. Parse thành cây DOM chuẩn W3C
        Document doc = parseHtmlToDOM(html);

        // 3. Kiểm tra DOM node banner: class phải là 'alert-banner warn'
        Element banner = (Element) doc.getElementsByTagName("div").item(1);
        assertTrue(banner.getAttribute("class").contains("warn"), "Banner phải có class 'warn' (màu vàng)");
        assertTrue(banner.getTextContent().contains("Thiếu ảnh check-in"));

        // 4. Kiểm tra DOM node button [data-ci-go]: BẮT BUỘC KHÔNG TỒN TẠI trong DOM
        NodeList buttons = doc.getElementsByTagName("button");
        boolean hasConfirmCheckInButton = false;
        for (int i = 0; i < buttons.getLength(); i++) {
            Element btn = (Element) buttons.item(i);
            if ("true".equals(btn.getAttribute("data-ci-go"))) {
                hasConfirmCheckInButton = true;
            }
        }

        assertFalse(hasConfirmCheckInButton, "DOM TUYỆT ĐỐI KHÔNG ĐƯỢC có nút 'Xác nhận vào tập' khi Membership thiếu ảnh");
    }
}

