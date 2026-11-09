package com.gms.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * CẢ MÀN BÁN GÓI PT = 4 khối + khung "Đơn đang giữ chỗ". Mỗi khối 1 DTO riêng, đúng 1 vùng trên màn:
 *
 *   ┌──────────────────────────────┬──────────────────────────┐
 *   │ choice  "Gói & PT"           │ buyer   "Khách mua gói"  │
 *   │ plan    "Lịch tập"           │ choice  tóm tắt Đơn bán  │
 *   │                              │ heldOrder đồng hồ + thu tiền (?order=)
 *   └──────────────────────────────┴──────────────────────────┘
 *
 * Service tạo: PtSaleService.prepare (xem) / hold (giữ lịch, đọc lại để kiểm).
 * Servlet: PtSaleServlet.showPage → request.setAttribute("sale", view) → JSP đọc sale.choice.xxx, sale.buyer.xxx …
 */
public class PtSaleView {

    // ← khối "Gói & PT" → _pt-sale-step1.jspf + tóm tắt Đơn bán
    private final PtSaleChoiceBlock choice = new PtSaleChoiceBlock();

    // ← khối "Khách mua gói" → _pt-sale-customer-box.jspf
    private final PtSaleBuyerBlock buyer = new PtSaleBuyerBlock();

    // ← khối "Lịch tập" (+ lưới đang giữ) → _pt-sale-plan.jspf, _pt-sale-held.jspf
    private final PtSalePlanBlock plan = new PtSalePlanBlock();

    // ← khối "Đơn đang giữ" (chỉ trang ?order=) → đồng hồ + form thu tiền
    private final PtSaleHeldBlock heldOrder = new PtSaleHeldBlock();

    // ← loadPendingHolds → khung "Đơn đang giữ chỗ" đầu màn (_pt-sale-holds.jspf)
    private List<PtSalePendingHold> pendingHolds = new ArrayList<>();


    /** Đang ở trang ?order= (đã giữ chỗ) → JSP ẩn bước 1–2, hiện đồng hồ + Thu tiền. */
    // GETTER TỰ TÍNH — không có field/setter "held"; = heldOrder.orderId khác null; JSP: receptionist/pt-sales.jsp:115 …
    public boolean isHeld() {
        return heldOrder.getOrderId() != null;
    }

    /** Đủ để Giữ lịch: chưa giữ, có ≥ 1 buổi, lịch không lỗi (PtSaleService.firstProblem dùng). */
    // GETTER TỰ TÍNH — không có field/setter "ready"; = chưa giữ chỗ, có buổi và lịch không lỗi; JSP: không dùng (chỉ Java/test)
    public boolean isReady() {
        return !isHeld() && plan.getSessionCount() > 0 && plan.getPlanError() == null;
    }


    public PtSaleChoiceBlock getChoice() { return choice; }
    public PtSaleBuyerBlock getBuyer() { return buyer; }
    public PtSalePlanBlock getPlan() { return plan; }
    public PtSaleHeldBlock getHeldOrder() { return heldOrder; }

    public List<PtSalePendingHold> getPendingHolds() { return pendingHolds; }
    public void setPendingHolds(List<PtSalePendingHold> pendingHolds) { this.pendingHolds = pendingHolds; }
}



/*
 * ============================================================================================================
 * PHỤ LỤC — BẢN ĐỒ DTO MÀN BÁN GÓI PT: FIELD NÀO · HÀM NÀO TẠO · ĐI QUA ĐÂU · HIỆN Ở ĐÂU
 * ============================================================================================================
 *
 * A. CÂY GỌI HÀM — ai tạo PtSaleView
 *
 *   (1) XEM TRANG CHỌN   GET /receptionist/pt-sales            (không ?order=)
 *       showPage → prepare → releaseExpired → findReceptionist → newView → build(lock = false) → loadPendingHolds
 *                                                                build = loadHolder → loadPackage → [loadPartner] → loadPt → loadPlan
 *                                                                loadPlan(false) = B1–B2 → B3 → buildPlan → B5 → planJson
 *   (2) TRANG ĐANG GIỮ   GET /receptionist/pt-sales?order=<id>
 *       showPage → prepare → releaseExpired → findReceptionist → newView → loadHeld          (KHÔNG build, KHÔNG pendingHolds)
 *   (3) GIỮ LỊCH         POST action=hold
 *       hold → releaseExpired → [transaction] requireReceptionist → newView → lockPt → build(lock = true) → firstProblem …
 *                                            loadPlan(true) = B1–B2 → B6 → checkRequested → B8 → planBlock.days
 *       (view này KHÔNG đưa ra JSP — chỉ để kiểm rồi ghi DB. Lỗi → Servlet gọi lại (1) để vẽ trang.)
 *
 *   JSP đọc tất cả qua biến "sale" = PtSaleView (showPage: request.setAttribute("sale", view)).
 *
 * ------------------------------------------------------------------------------------------------------------
 * B. PtSaleView (sale) — gốc, chứa 4 khối + danh sách đơn đang giữ
 * ------------------------------------------------------------------------------------------------------------
 *   choice        PtSaleChoiceBlock    new sẵn trong PtSaleView      → mục C
 *   buyer         PtSaleBuyerBlock     new sẵn                       → mục D
 *   plan          PtSalePlanBlock      new sẵn                       → mục E
 *   heldOrder     PtSaleHeldBlock      new sẵn                       → mục F
 *   pendingHolds  List<PtSalePendingHold>   prepare → loadPendingHolds ← DAO findActiveHoldOrders       → mục G
 *                                      CHỈ (1). (2) return trước nên luôn rỗng.        → _pt-sale-holds.jspf
 *   held          (getter isHeld, KHÔNG phải field) = heldOrder.orderId != null       → JSP sale.held: chọn nhánh trang
 *   ready         (getter isReady)  = !held && plan.sessionCount > 0 && plan.planError == null
 *
 * ------------------------------------------------------------------------------------------------------------
 * C. PtSaleChoiceBlock (sale.choice) — khối "Gói & PT" + tóm tắt Đơn bán
 * ------------------------------------------------------------------------------------------------------------
 *   field            (1) xem / (3) giữ lịch                         (2) đang giữ                  hiện ở
 *   packageId        newView ← ?package=                             loadHeld ← đơn                radio gói tích sẵn, ô ẩn form Giữ lịch
 *   ptId             newView ← ?pt=                                  loadHeld ← khung giữ          radio PT tích sẵn, ô ẩn
 *   startDate        newView ← ?start= (trống = ngày mai)            prepare xóa nếu URL thiếu start
 *                                                                    → loadHeld = ngày khung đầu   ô "Ngày bắt đầu kỳ", ô ẩn start
 *   minStart         newView = hôm nay                               (giữ từ newView)              min ô ngày
 *   maxStart         newView = hôm nay + START_DAYS_AHEAD (30)       (giữ từ newView)              max ô ngày
 *   packages         build → loadPackage ← DAO findPtPackages        —                             các ô gói (List<PtSaleOption>, mục H)
 *   pts              build → loadPt ← DAO findWorkingPts             —                             các nút PT (rỗng khi chưa có gói hợp lệ → ẩn)
 *   choiceError      loadPackage (gói ngừng bán) · loadPt (PT sai, ngày ngoài khoảng)   —           chữ đỏ khối Gói & PT; firstProblem
 *   couple           loadPackage = PackageType == 'PTCouple'         —                             c:if hiện ô "Người tập cùng", customerReady
 *   packageName      loadPackage ← DAO findPackage                   loadHeld ← findPackage        Đơn bán "Gói"
 *   price            loadPackage ← giá gói hiện tại                  loadHeld ← đơn (snapshot)     "Phải thu", sessionValue
 *   months           loadPackage                                     loadHeld                      "· N tháng", tính endDate
 *   ptName           loadPt ← DAO findPt                             loadHeld ← findPt             Đơn bán "PT", hộp nhắc PT chưa công bố
 *   commissionRate   loadPt                                          —                             không in (hoa hồng)
 *   endDate          loadPt = calculateEndDateByMonths(start, months)   loadHeld (cùng công thức)  "Kỳ", done2 (JSP), kỳ của loadPlan
 *
 * ------------------------------------------------------------------------------------------------------------
 * D. PtSaleBuyerBlock (sale.buyer) — khối "Khách mua gói"
 * ------------------------------------------------------------------------------------------------------------
 *   customerId       newView ← ?customer=                            loadHeld ← đơn                ô ẩn customer, link Đổi khách
 *   partnerId        newView ← ?partner=                             —                             ô ẩn partner
 *   searchQuery      newView ← ?q= (chỉ khi gửi form tìm, tắt JS)    —                             chữ trong ô tìm
 *   searchResults    build → loadHolder ← DAO searchCustomers (+ whyCannotBuy từng dòng)            danh sách kết quả dự phòng
 *   holder           build → loadHolder ← DAO findCustomer (+ whyCannotBuy)   loadHeld ← findCustomer (KHÔNG chạy whyCannotBuy)
 *                                                                                                  thẻ "Người đứng tên", customerReady
 *   partner          build → loadPartner ← findCustomer (+ whyCannotBuy)      loadHeld (nếu đơn có người tập cùng)
 *                                                                                                  thẻ "Người tập cùng"
 *   holderError      loadHolder: không tìm thấy khách / tìm không ra ai      —                     chữ đỏ dưới ô tìm; firstProblem
 *   partnerError     loadPartner: chưa chọn / trùng người đứng tên           —                     dòng dưới ô tìm người tập cùng; firstProblem
 *
 * ------------------------------------------------------------------------------------------------------------
 * E. PtSalePlanBlock (sale.plan) — khối "Lịch tập" + lưới đang giữ
 * ------------------------------------------------------------------------------------------------------------
 *   weekTimes        newView ← ?t1..t5                               loadHeld ← giờ khung giữ theo thứ   loadPlan B3/B6 đọc
 *   unpublishedWeekdays   loadPlan B2 ← unpublishedWeekdays(slots)   —                             getter unpublishedText → hộp nhắc vàng
 *   planJson         (1) loadPlan B5d = JsonUtil.toJson(PtSalePlan)   —   ((3): luôn null)         <script id="sale-plan-data"> (mục J)
 *   planError        (3) loadPlan B7a / B7b (checkRequested ném lỗi)   —                           firstProblem; JSP khi planJson rỗng
 *   days             (3) loadPlan B8 ← checkRequested → checkDay     loadHeld: mỗi khung = 1 PtSaleDay "book"   hold() ghi DB · _pt-sale-held.jspf
 *   sessionCount     (3) loadPlan B8 = days.size()                   loadHeld = days.size()        "N buổi tập", MSG307, ô Buổi
 *   sessionValue     (3) loadPlan B8 = sessionValue(price, n)        loadHeld (cùng công thức)     ô Buổi "x đ/buổi"
 *   noteCount        (getter) đếm days không có buổi / có warning    —                             _pt-sale-held.jspf ghi chú dưới lưới
 *   ※ (1) xem: days / sessionCount / sessionValue = rỗng / 0 → số buổi trên màn do JS tự đếm từ planJson.
 *
 * ------------------------------------------------------------------------------------------------------------
 * F. PtSaleHeldBlock (sale.heldOrder) — CHỈ trang (2), các trang khác null / 0
 * ------------------------------------------------------------------------------------------------------------
 *   orderId          loadHeld ← tham số orderId (?order=)   → isHeld() = true · ô ẩn "order" form Thu tiền / Hủy giữ
 *   orderCode        loadHeld ← DAO findHeldOrder          → mã đơn cột phải, hộp xác nhận hủy
 *   holdSeconds      loadHeld = held.expiresAt − now (≥ 0) → data-countdown-seconds, common.js bindCountdowns
 *
 * ------------------------------------------------------------------------------------------------------------
 * G. PtSalePendingHold (phần tử sale.pendingHolds) — tạo bằng constructor trong loadPendingHolds
 * ------------------------------------------------------------------------------------------------------------
 *   orderId · orderCode       ← DAO findActiveHoldOrders                    → link "Tiếp tục thu tiền" ?order=, mã đơn
 *   secondsLeft               = MIN(HoldExpiresAt) − now (≥ 0)              → "còn mm:ss"
 *
 * ------------------------------------------------------------------------------------------------------------
 * H. PtSaleOption (phần tử choice.packages / choice.pts) — tạo bằng constructor, field final, KHÔNG có setter
 * ------------------------------------------------------------------------------------------------------------
 *                    gói (loadPackage)                              PT (loadPt)
 *   value            PackageId                                      EmployeeId                → value radio (?package= / ?pt=)
 *   label            tên gói                                        họ tên PT                 → chữ đậm
 *   note             money(price) + " · N tháng · " + loại          initials(fullName) "ĐS"   → dòng nhỏ / avatar
 *   (bản máy bạn có thêm field thứ 4 cho PT ← ptDetail(row): "chuyên môn · N năm kinh nghiệm")
 *   Ô nào được tích: JSP so o.value eq sale.choice.packageId / ptId.
 *
 * ------------------------------------------------------------------------------------------------------------
 * I. PtSaleCustomer (buyer.holder / partner / searchResults[] · JSON gợi ý) — DAO tạo, Service chỉ điền blockReason
 * ------------------------------------------------------------------------------------------------------------
 *   customerId · personId · fullName · customerCode · phone · dateOfBirth · membershipEnd · frozen
 *                    ← PtSaleDAO.searchCustomers / findCustomer (constructor, final)
 *   blockReason      ← whyCannotBuy (4 luật: chính lễ tân · đủ tuổi P-05 · có gói thành viên hôm nay · không bảo lưu)
 *                      gọi ở: search (AJAX) · loadHolder · loadPartner.   loadHeld KHÔNG gọi → trang đang giữ luôn null.
 *   Hiện ở: JSON gợi ý (PtSaleServlet.writeSuggestions: id, name, code, phone, ok, note) · _sale-customer.jspf (biến cust).
 *
 * ------------------------------------------------------------------------------------------------------------
 * J. PtSalePlan (CHỈ để đổi thành planJson ở (1)) — loadPlan B4 buildPlan tạo, B5 điền thêm, B5d toJson
 * ------------------------------------------------------------------------------------------------------------
 *   start · end · price · minutes   buildPlan                  → JS: price (giá/buổi ô Đơn bán); minutes, start, end JS không đọc
 *   times            buildPlan ← candidates (= published)      → nút giờ mỗi dòng Lịch mẫu
 *   days[]           buildPlan: mỗi ngày T2–T6 trong kỳ         → JS vẽ lịch, đếm buổi
 *     .date .weekday .label                                    → ô ngày, gom tuần, "Tối đa N buổi"
 *     .options[]     mỗi giờ của ngày → checkDay
 *        .time .end            giờ bắt đầu / kết thúc          → "07:00 – 08:00"
 *        .ok                   checkDay.isSession()            → xếp được không
 *        .reason .warning      whySkip / warningFor            → "Còn x/y buổi · PT có khách", ô vàng
 *        .fresh                checkDay action == "create"     → "PT chưa mở", ô xác nhận PT (needConfirm)
 *   unpublished      B5 ← unpublishedWeekdays                  → nhãn "PT chưa công bố" trên dòng thứ
 *   published        B5 ← formatTimes(published)               → giờ ngoài danh sách = "PT chưa mở"
 *   open · close     B5 ← giờ mở cửa / đóng cửa − thời lượng   → min / max ô "+ Thêm giờ"
 *   pattern[5]       B5a ← weekTimes (chỉ giờ dùng được)       → nút tích sẵn Lịch mẫu
 *   custom[5]        B5a ← weekdayExtra (giờ tự thêm hợp lệ)   → thêm nút giờ đúng thứ đó
 *   requested[]      B5b ← parseSessions(input.sessions)       → JS khôi phục buổi sửa riêng
 *   notice           B5c ← giờ tự nhập sai                     → <p> đỏ đầu khung Lịch tập
 *   ⚠ PtSalePlan.days[] (mọi ngày × mọi giờ) KHÁC PtSalePlanBlock.days (các buổi đã chốt, mục E).
 *
 * ------------------------------------------------------------------------------------------------------------
 * K. PtSaleDay (phần tử plan.days) — 1 ngày + 1 giờ = 1 buổi (mỗi ngày tối đa 1 buổi)
 * ------------------------------------------------------------------------------------------------------------
 *   Tạo ở 2 nơi:
 *     checkDay (constructor) ← checkRequested (hold, (3)) · ← buildPlan (xem, (1): đổi ngay thành PtSalePlan.Option rồi bỏ)
 *     loadHeld (constructor) — khung đang giữ, action = "book", reason / warning = null
 *   date · dayLabel · startTime · endTime                      → ô lưới, ghi chú; INSERT khung mới (create)
 *   action           checkDay: "book" (có khung Free đúng giờ) | "create" (PT chưa mở) | "skip" (không xếp được)
 *                    → hold: book = holdFreeSlot · create = insertHeldSlot; countNewSlots đếm create
 *   slotId           checkDay: TimeSlotId khung Free (chỉ book)  → holdFreeSlot UPDATE khung này
 *   reason           whySkip (chỉ skip)                          → ô xám + ghi chú
 *   warning          warningFor (phòng nghỉ / PT có đơn nghỉ)    → ô vàng + ghi chú
 *   session          (getter isSession) = action != "skip"
 * ============================================================================================================
 */