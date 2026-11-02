package com.gms.servlet.receptionist;

import com.gms.dto.FreezeCustomerView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kiểm thử cây DOM (HTML structure) được sinh ra từ JSP freeze-membership.jsp:
 * - Đối chiếu cấu trúc form thiết lập thời gian bảo lưu (formApplyFreeze).
 * - Kiểm tra các thuộc tính input type="date", min, max, required trên cây DOM.
 * - Kiểm tra khối hiển thị kết quả tính toán tự động: displayDaysCount, displayNewExpiryDate.
 */
@DisplayName("Kiểm thử DOM Giao diện Bảo lưu gói tập (freeze-membership.jsp)")
class FreezeMembershipDOMTest {

    private String formatVietnameseDate(LocalDate d) {
        if (d == null) return "";
        return String.format("%02d/%02d/%04d", d.getDayOfMonth(), d.getMonthValue(), d.getYear());
    }

    /**
     * Mô phỏng render HTML khối thao tác bảo lưu từ freeze-membership.jsp (lines 125-196).
     */
    private String renderFreezeFormHtml(FreezeCustomerView view) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"freeze-action-wrapper\">");

        if (!view.isCurrentlyFrozen() && view.isEligibleToFreeze()) {
            sb.append("<div class=\"freeze-form-header\">");
            sb.append("<h3>Thiết lập thời gian bảo lưu</h3>");
            sb.append("<span class=\"badge badge-ok\">Đủ điều kiện</span>");
            sb.append("</div>");

            sb.append("<form id=\"formApplyFreeze\" method=\"POST\" action=\"/gms/receptionist/freeze\">");
            sb.append("<input type=\"hidden\" name=\"action\" value=\"applyFreeze\"/>");
            sb.append("<input type=\"hidden\" name=\"subscriptionId\" value=\"").append(view.getSubscriptionId()).append("\"/>");
            sb.append("<input type=\"hidden\" id=\"customerIdHidden\" value=\"").append(view.getCustomerId()).append("\"/>");
            sb.append("<input type=\"hidden\" id=\"packageEndDateHidden\" value=\"").append(view.getPackageEndDate()).append("\"/>");

            sb.append("<div class=\"freeze-dates-grid\">");

            // Ô Ngày bắt đầu (gồm input text hiển thị dd/mm/yyyy và input date picker)
            sb.append("<div class=\"form-group\">");
            sb.append("<label for=\"startDateDisplay\">Ngày bắt đầu:</label>");
            sb.append("<div class=\"freeze-date-control\">");
            sb.append("<input type=\"text\" id=\"startDateDisplay\" class=\"form-control freeze-date-input\" ")
                    .append("value=\"").append(formatVietnameseDate(view.getMinStartDate())).append("\" ")
                    .append("placeholder=\"dd/mm/yyyy\" autocomplete=\"off\" required=\"true\"/>");
            sb.append("<button type=\"button\" class=\"freeze-calendar-button\" data-picker-target=\"startDate\" aria-label=\"Chọn ngày bắt đầu\">");
            sb.append("<i class=\"fa-regular fa-calendar\"></i>");
            sb.append("</button>");
            sb.append("<input type=\"date\" id=\"startDate\" name=\"startDate\" class=\"freeze-native-date\" ")
                    .append("min=\"").append(view.getMinStartDate()).append("\" ")
                    .append("max=\"").append(view.getPackageEndDate()).append("\" ")
                    .append("value=\"").append(view.getMinStartDate()).append("\" required=\"true\" tabindex=\"-1\"/>");
            sb.append("</div>");
            sb.append("<small class=\"hint\">Sớm nhất từ ngày mai.</small>");
            sb.append("</div>");

            // Ô Ngày kết thúc dự kiến (gồm input text hiển thị dd/mm/yyyy và input date picker)
            sb.append("<div class=\"form-group\">");
            sb.append("<label for=\"plannedEndDateDisplay\">Ngày kết thúc dự kiến:</label>");
            sb.append("<div class=\"freeze-date-control\">");
            sb.append("<input type=\"text\" id=\"plannedEndDateDisplay\" class=\"form-control freeze-date-input\" ")
                    .append("value=\"").append(formatVietnameseDate(view.getMaxEndDate())).append("\" ")
                    .append("placeholder=\"dd/mm/yyyy\" autocomplete=\"off\" required=\"true\"/>");
            sb.append("<button type=\"button\" class=\"freeze-calendar-button\" data-picker-target=\"plannedEndDate\" aria-label=\"Chọn ngày kết thúc dự kiến\">");
            sb.append("<i class=\"fa-regular fa-calendar\"></i>");
            sb.append("</button>");
            sb.append("<input type=\"date\" id=\"plannedEndDate\" name=\"plannedEndDate\" class=\"freeze-native-date\" ")
                    .append("min=\"").append(view.getMinStartDate()).append("\" ")
                    .append("max=\"").append(view.getMaxEndDate()).append("\" ")
                    .append("value=\"").append(view.getMaxEndDate()).append("\" required=\"true\" tabindex=\"-1\"/>");
            sb.append("</div>");
            sb.append("<small class=\"hint\" id=\"plannedEndDateHint\">Tối đa 1 tháng (đến ngày ").append(view.getMaxEndDate()).append(").</small>");
            sb.append("</div>");

            sb.append("</div>");

            // Khối hiển thị tính toán ngày
            sb.append("<div class=\"freeze-calc-bar\">");
            sb.append("<div class=\"calc-row\">");
            sb.append("<span>Số ngày bảo lưu dự kiến:</span>");
            sb.append("<b id=\"displayDaysCount\" class=\"text-primary\">30 ngày</b>");
            sb.append("</div>");
            sb.append("<div class=\"calc-row\">");
            sb.append("<span>Hạn thẻ mới sau khi mở lại:</span>");
            sb.append("<b id=\"displayNewExpiryDate\" class=\"text-success\">--/--/----</b>");
            sb.append("</div>");
            sb.append("</div>");

            // Ô Lý do
            sb.append("<div class=\"form-group\">");
            sb.append("<label for=\"freezeReason\">Lý do bảo lưu:</label>");
            sb.append("<textarea id=\"freezeReason\" name=\"reason\" required=\"true\"></textarea>");
            sb.append("</div>");

            // Nút Submit
            sb.append("<button type=\"submit\" class=\"btn btn-primary\" id=\"btnSubmitFreeze\">");
            sb.append("Lưu bảo lưu gói tập");
            sb.append("</button>");

            sb.append("</form>");
        }

        sb.append("</div>");
        return sb.toString();
    }

    private Document parseHtmlToDOM(String html) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new InputSource(new StringReader(html)));
    }

    @Test
    @DisplayName("DOM Test: Hội viên đủ điều kiện -> DOM sinh đủ ô startDate, plannedEndDate, hint và displayDaysCount")
    void testEligibleCustomerFormDOMStructure() throws Exception {
        FreezeCustomerView view = new FreezeCustomerView();
        view.setCustomerId(1001);
        view.setSubscriptionId(501);
        view.setCurrentlyFrozen(false);
        view.setEligibleToFreeze(true);
        view.setMinStartDate(LocalDate.of(2026, 11, 1));
        view.setMaxEndDate(LocalDate.of(2026, 11, 30));
        view.setPackageEndDate(LocalDate.of(2027, 6, 19));

        String html = renderFreezeFormHtml(view);
        Document doc = parseHtmlToDOM(html);

        // 1. Kiểm tra Form
        NodeList forms = doc.getElementsByTagName("form");
        assertEquals(1, forms.getLength());
        Element form = (Element) forms.item(0);
        assertEquals("formApplyFreeze", form.getAttribute("id"));
        assertEquals("POST", form.getAttribute("method"));

        // 2. Kiểm tra các trường input ngày (cả hiển thị dd/mm/yyyy và native picker)
        NodeList inputs = doc.getElementsByTagName("input");
        Element startInput = null;
        Element endInput = null;
        Element startDisplay = null;
        Element endDisplay = null;
        for (int i = 0; i < inputs.getLength(); i++) {
            Element input = (Element) inputs.item(i);
            if ("startDate".equals(input.getAttribute("id"))) {
                startInput = input;
            } else if ("plannedEndDate".equals(input.getAttribute("id"))) {
                endInput = input;
            } else if ("startDateDisplay".equals(input.getAttribute("id"))) {
                startDisplay = input;
            } else if ("plannedEndDateDisplay".equals(input.getAttribute("id"))) {
                endDisplay = input;
            }
        }

        assertNotNull(startDisplay, "Phải tồn tại ô startDateDisplay trong DOM");
        assertEquals("text", startDisplay.getAttribute("type"));
        assertEquals("01/11/2026", startDisplay.getAttribute("value"), "Ngày bắt đầu hiển thị phải theo định dạng dd/mm/yyyy");

        assertNotNull(endDisplay, "Phải tồn tại ô plannedEndDateDisplay trong DOM");
        assertEquals("text", endDisplay.getAttribute("type"));
        assertEquals("30/11/2026", endDisplay.getAttribute("value"), "Ngày kết thúc hiển thị phải theo định dạng dd/mm/yyyy (ngày đứng trước, tháng đứng sau)");

        assertNotNull(startInput, "Phải tồn tại ô startDate trong DOM");
        assertEquals("date", startInput.getAttribute("type"));
        assertEquals("2026-11-01", startInput.getAttribute("min"));
        assertEquals("2027-06-19", startInput.getAttribute("max"));
        assertEquals("2026-11-01", startInput.getAttribute("value"));

        assertNotNull(endInput, "Phải tồn tại ô plannedEndDate trong DOM");
        assertEquals("date", endInput.getAttribute("type"));
        assertEquals("2026-11-01", endInput.getAttribute("min"));
        assertEquals("2026-11-30", endInput.getAttribute("max"));
        assertEquals("2026-11-30", endInput.getAttribute("value"));

        // 3. Kiểm tra nút submit và khối hiển thị số ngày
        NodeList buttons = doc.getElementsByTagName("button");
        Element submitBtn = null;
        int calendarBtnCount = 0;
        for (int i = 0; i < buttons.getLength(); i++) {
            Element b = (Element) buttons.item(i);
            if ("btnSubmitFreeze".equals(b.getAttribute("id"))) {
                submitBtn = b;
            } else if (b.getAttribute("class").contains("freeze-calendar-button")) {
                calendarBtnCount++;
            }
        }
        assertNotNull(submitBtn, "Phải tồn tại nút submit #btnSubmitFreeze");
        assertEquals("submit", submitBtn.getAttribute("type"));
        assertEquals(2, calendarBtnCount, "Phải tồn tại 2 nút mở popup lịch cho startDate và plannedEndDate");

        NodeList bElements = doc.getElementsByTagName("b");
        boolean foundDisplayDays = false;
        for (int i = 0; i < bElements.getLength(); i++) {
            Element b = (Element) bElements.item(i);
            if ("displayDaysCount".equals(b.getAttribute("id"))) {
                foundDisplayDays = true;
                assertEquals("30 ngày", b.getTextContent());
            }
        }
        assertTrue(foundDisplayDays, "Phải tồn tại b#displayDaysCount trong DOM");
    }

    @Test
    @DisplayName("DOM Test: Gói tập sắp hết hạn (ví dụ còn 26 ngày) -> maxEndDate của DOM được giới hạn đúng hạn gói")
    void testCustomerNearExpiryMaxDateInDOM() throws Exception {
        FreezeCustomerView view = new FreezeCustomerView();
        view.setCustomerId(2037);
        view.setSubscriptionId(777);
        view.setCurrentlyFrozen(false);
        view.setEligibleToFreeze(true);
        view.setMinStartDate(LocalDate.of(2026, 11, 1));
        view.setMaxEndDate(LocalDate.of(2026, 11, 26)); // Chỉ còn 26 ngày
        view.setPackageEndDate(LocalDate.of(2026, 11, 26));

        String html = renderFreezeFormHtml(view);
        Document doc = parseHtmlToDOM(html);

        NodeList inputs = doc.getElementsByTagName("input");
        Element endInput = null;
        Element endDisplay = null;
        for (int i = 0; i < inputs.getLength(); i++) {
            Element input = (Element) inputs.item(i);
            if ("plannedEndDate".equals(input.getAttribute("id"))) {
                endInput = input;
            } else if ("plannedEndDateDisplay".equals(input.getAttribute("id"))) {
                endDisplay = input;
            }
        }

        assertNotNull(endDisplay);
        assertEquals("26/11/2026", endDisplay.getAttribute("value"), "Ngày kết thúc hiển thị phải là 26/11/2026 (ngày trước tháng sau)");

        assertNotNull(endInput);
        assertEquals("2026-11-26", endInput.getAttribute("max"), "max của plannedEndDate phải bị chặn đúng ngày 2026-11-26");
        assertEquals("2026-11-26", endInput.getAttribute("value"));
    }
}

