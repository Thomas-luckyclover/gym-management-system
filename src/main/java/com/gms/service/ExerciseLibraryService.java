package com.gms.service;

import com.gms.constant.Messages;
import com.gms.dao.ExerciseLibraryDAO;
import com.gms.dto.ExerciseItem;
import com.gms.dto.ExerciseLibraryView;
import com.gms.dto.ExerciseLibraryView.Access;
import com.gms.dto.ExerciseLibraryView.Option;
import com.gms.dto.ExerciseLibraryView.Period;
import com.gms.dto.ServiceResult;
import com.gms.entity.Package;
import com.gms.integration.WgerClient;
import com.gms.integration.WgerUnavailableException;
import com.gms.util.DBContext;
import com.gms.util.DateTimeUtil;
import com.gms.util.MoneyUtil;
import com.gms.util.SqlUtil;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SERVICE MÀN THƯ VIỆN BÀI TẬP (/customer/exercises, /pt/exercises) — 27/10/2026.
 *
 * NGHIỆP VỤ
 *   Thư viện là ĐẶC QUYỀN HỘI VIÊN. Khách xem được khi đang có gói tập hoặc gói PT còn hiệu lực
 *   (Packages.UnlocksExerciseLibrary = 1, kể cả đang bảo lưu). PT luôn xem được để tra bài khi soạn giáo án.
 *
 * DỮ LIỆU
 *   API wger.de → bảng Exercises (BỘ ĐỆM, không phải dữ liệu gốc). Quá 1 ngày thì tải lại TOÀN BỘ;
 *   API lỗi thì vẫn hiện bản cũ kèm giờ tải, 5 phút sau mới thử lại. Lọc, đếm, sắp xếp, phân trang đều làm ở SQL.
 *
 * AI GỌI SERVICE NÀY?   ExerciseLibraryServlet.doGet (1 Servlet cho cả 2 URL) → getPage, 1 lần mỗi lần mở trang.
 * SERVICE GỌI AI?       ExerciseLibraryDAO (bảng Exercises, Subscriptions, Packages) · WgerClient (API wger.de)
 *                       · ExerciseImagePool (kho 194 ảnh minh họa) · SqlUtil (mẫu LIKE).
 *
 * BỐ CỤC FILE — HÀM CHÍNH đứng trước, hàm phụ của nó nằm bên dưới:
 *   HÀM CHÍNH   getPage        mở trang / lọc / tìm / đổi trang        (PHẦN 1)
 *     B1 kiểm quyền       → decideAccess, accessOf, lockDateOf, lockMessage, packageLines
 *     B2 đồng bộ wger     → syncIfNeeded (PHẦN 2) → needsSync, canRetry
 *     B3 đọc bộ lọc URL   → cleanQuery, parseCategory, parseEquipment, parseSort, parseId
 *     B4 đếm + lấy trang  → totalPages, parsePage, orderByOf, fillLabels → joinLabels
 *     B5 dữ liệu bộ lọc   → categoryOptions, equipmentOptions, sortOptions
 *   Các hàm phụ ở PHẦN 3 đều static, không đọc DB → ExerciseLibraryServiceTest gọi thẳng được.
 *
 * DTO TRẢ VỀ: ExerciseLibraryView (biến JSP "library") — xem PHỤ LỤC ở cuối file.
 */
public class ExerciseLibraryService {
    // ================================================================================================================
    // HẰNG SỐ + BIẾN DÙNG CHUNG
    // ================================================================================================================

    // Ghi log server khi gọi wger lỗi (syncIfNeeded, nhánh catch).
    private static final Logger LOGGER = Logger.getLogger(ExerciseLibraryService.class.getName());

    // static final không ghi private = cùng package dùng được → file test đọc được các hằng này.
    static final int PAGE_SIZE = 12;            // 12 bài / trang. Đổi số bài mỗi trang → sửa đúng dòng này.
    static final int MAX_QUERY_LENGTH = 50;     // ô tìm tên tối đa 50 ký tự (dài hơn thì cắt).
    static final int CACHE_DAYS = 1;            // bảng Exercises cũ quá 1 ngày → tải lại từ wger.
    static final int RETRY_MINUTES = 5;         // wger lỗi → 5 phút sau mới thử lại (người dùng không bị treo chờ timeout).
    static final String SORT_NAME = "name";     // ?sort=name  → Tên A → Z (mặc định)
    static final String SORT_IMAGE = "image";   // ?sort=image → Có hình trước

    // ORDER BY cố định theo ô "Sắp xếp". ORDER BY không dùng dấu ? được nên phải ghép chuỗi vào SQL;
    // vì vậy chữ trên URL chỉ dùng để CHỌN 1 trong 2 hằng số này (whitelist, chống SQL injection).
    // ExerciseId ASC ở cuối: 2 bài trùng tên vẫn có thứ tự cố định → đổi trang không lặp / mất bài.
    private static final String ORDER_BY_NAME = "Name ASC, ExerciseId ASC";
    // CASE: bài CÓ ảnh = 0, KHÔNG ảnh = 1 → sắp tăng dần thì bài có ảnh lên trước.
    private static final String ORDER_BY_IMAGE = "CASE WHEN ImageUrl IS NULL THEN 1 ELSE 0 END, Name ASC, ExerciseId ASC";

    // 3 bảng đổi MÃ SỐ của wger → CHỮ tiếng Việt (DB chỉ lưu mã). Đồng thời là danh sách mã HỢP LỆ cho ?category= / ?equipment=.
    // LinkedHashMap giữ đúng thứ tự lúc put → chip / ô chọn hiện theo thứ tự này (HashMap sẽ đảo lộn).
    // 8 nhóm cơ cố định của wger (fixtures/categories.json), theo thứ tự hiện chip.
    static final Map<Integer, String> CATEGORY_LABELS = new LinkedHashMap<>();
    // 12 dụng cụ của wger (fixtures/equipment.json).
    static final Map<Integer, String> EQUIPMENT_LABELS = new LinkedHashMap<>();
    // 16 cơ của wger (fixtures/muscles.json). Mã lạ (wger thêm sau) → bỏ qua, không lỗi.
    static final Map<Integer, String> MUSCLE_LABELS = new LinkedHashMap<>();

    // Khối static: chạy ĐÚNG 1 LẦN khi class được nạp, điền dữ liệu cho 3 bảng trên; mọi request sau dùng lại.
    static {
        CATEGORY_LABELS.put(11, "Ngực");
        CATEGORY_LABELS.put(12, "Lưng");
        CATEGORY_LABELS.put(13, "Vai");
        CATEGORY_LABELS.put(8, "Tay");
        CATEGORY_LABELS.put(10, "Bụng");
        CATEGORY_LABELS.put(9, "Chân");
        CATEGORY_LABELS.put(14, "Bắp chân");
        CATEGORY_LABELS.put(15, "Cardio");

        EQUIPMENT_LABELS.put(7, "Không dụng cụ");
        EQUIPMENT_LABELS.put(3, "Tạ đơn");
        EQUIPMENT_LABELS.put(1, "Thanh đòn");
        EQUIPMENT_LABELS.put(2, "Thanh đòn chữ Z");
        EQUIPMENT_LABELS.put(10, "Tạ ấm");
        EQUIPMENT_LABELS.put(8, "Ghế tập");
        EQUIPMENT_LABELS.put(9, "Ghế dốc");
        EQUIPMENT_LABELS.put(12, "Máy cáp");
        EQUIPMENT_LABELS.put(6, "Xà đơn");
        EQUIPMENT_LABELS.put(11, "Dây kháng lực");
        EQUIPMENT_LABELS.put(5, "Bóng tập");
        EQUIPMENT_LABELS.put(4, "Thảm tập");

        MUSCLE_LABELS.put(1, "Tay trước");
        MUSCLE_LABELS.put(2, "Vai trước");
        MUSCLE_LABELS.put(3, "Cơ răng cưa");
        MUSCLE_LABELS.put(4, "Ngực");
        MUSCLE_LABELS.put(5, "Tay sau");
        MUSCLE_LABELS.put(6, "Bụng");
        MUSCLE_LABELS.put(7, "Bắp chân");
        MUSCLE_LABELS.put(8, "Mông");
        MUSCLE_LABELS.put(9, "Cầu vai");
        MUSCLE_LABELS.put(10, "Đùi trước");
        MUSCLE_LABELS.put(11, "Đùi sau");
        MUSCLE_LABELS.put(12, "Xô");
        MUSCLE_LABELS.put(13, "Cơ cánh tay");
        MUSCLE_LABELS.put(14, "Bụng chéo");
        MUSCLE_LABELS.put(15, "Bắp chân sâu");
        MUSCLE_LABELS.put(16, "Lưng dưới");
    }

    // ---- 2 biến DÙNG CHUNG CẢ APP cho phần đồng bộ (static) ----
    // static: cả app chỉ có 1 chìa + 1 mốc lỗi, dù có bao nhiêu object Service. Không static → mỗi
    //   new ExerciseLibraryService() có chìa riêng → 2 object không chặn được nhau → có thể cùng gọi wger, cùng ghi bảng.
    //   (Hiện chỉ ExerciseLibraryServlet tạo Service nên thực tế có 1 object; static là phòng khi sau này nơi khác tạo thêm.)

    // SYNC_LOCK = "chiếc chìa khóa" cho synchronized ở syncIfNeeded. Là object rỗng, không chứa dữ liệu.
    //   final: gán 1 lần, không đổi (đổi chìa giữa chừng thì 2 luồng cầm 2 chìa khác nhau → khóa vô tác dụng).
    //   private: bên ngoài không lấy chìa này để khóa linh tinh.
    private static final Object SYNC_LOCK = new Object();

    // lastFailureAt = lần gọi wger LỖI gần nhất. null = chưa lỗi / lần tải gần nhất thành công. Chỉ nằm trong RAM (restart → null).
    //   volatile: mọi luồng luôn đọc giá trị mới nhất ở bộ nhớ chính, không đọc bản sao cũ trong bộ đệm CPU
    //   → cần vì có chỗ đọc NGOÀI synchronized (getPage B2 setStale, syncIfNeeded lần kiểm đầu).
    private static volatile LocalDateTime lastFailureAt;

    // ---- Biến của object ----
    // DAO không giữ dữ liệu riêng (mỗi hàm tự mở/đóng connection) → tạo luôn tại chỗ.
    private final ExerciseLibraryDAO libraryDAO = new ExerciseLibraryDAO();
    // Client gọi API wger. KHÔNG tạo tại chỗ mà để constructor gán → thay được từ bên ngoài (test truyền client giả).
    private final WgerClient wgerClient;

    /** Constructor app thật dùng (Servlet gọi new ExerciseLibraryService()) → tạo WgerClient thật rồi chuyển sang constructor dưới. */
    public ExerciseLibraryService() {
        this(new WgerClient());
    }

    /**
     * Constructor nhận WgerClient từ ngoài (dependency injection qua constructor).
     * Không ghi public → chỉ cùng package (file test) gọi được; test truyền client giả để không gọi wger thật qua mạng.
     * (Hiện chưa có test nào dùng — chuẩn bị sẵn để test syncIfNeeded.)
     *
     * Dependency injection (DI), tạm dịch "tiêm phụ thuộc": một class không tự tạo thứ nó cần dùng, mà nhận thứ đó từ bên ngoài đưa vào.
     *   Phụ thuộc (dependency): thứ một class cần để làm việc — ExerciseLibraryService cần WgerClient để gọi wger.
     *   Tiêm (injection): đưa phụ thuộc đó vào từ bên ngoài, thường qua constructor.
     */
    ExerciseLibraryService(WgerClient wgerClient) {
        this.wgerClient = wgerClient;
    }



    /**
     * HÀM CHÍNH 1 — mở trang thư viện, lọc, tìm, đổi trang (mọi thao tác là GET cùng URL).
     *   Ai gọi: ExerciseLibraryServlet.doGet
     *   Trả về: ok(view) — màn khóa nếu khách chưa có gói mở khóa · lỗi khi thư viện trống / lỗi hệ thống
     */
    public ServiceResult<ExerciseLibraryView> getPage(
            int accountId,
            boolean isPt,
            String q,
            String category,
            String equipment,
            String sort,
            String page,
            LocalDateTime now) {
        try {
            ExerciseLibraryView view = new ExerciseLibraryView();
            view.setPt(isPt);                                    // JSP: PT thì bỏ nút "Xem các gói"

            // B1: khách phải có gói mở khóa thư viện (PT vào thẳng)
            if (!isPt) {
                // Các gói có đặc quyền của khách
                List<Period> periods = libraryDAO.findUnlockPeriods(accountId);
                // Hôm nay có gói nào dùng được không
                Period decided = decideAccess(periods, now.toLocalDate());
                // Xếp loại: mở / chưa tới ngày / hết hạn / chưa mua
                Access access = accessOf(decided, periods, now.toLocalDate());
                if (access != Access.OPEN) {
                    // Chưa mở → dựng màn khóa rồi dừng
                    view.setAccess(access);                                              // JSP: library.open = false → khối màn khóa
                    view.setLockDate(lockDateOf(access, periods, now.toLocalDate()));    // ngày hết hạn / ngày bắt đầu (ghép vào câu)
                    view.setLockMessage(lockMessage(access, view.getLockDate()));        // câu giải thích theo từng loại
                    view.setUnlockingPackages(packageLines(libraryDAO.findUnlockingPackages())); // "Gói 1 Tháng · 799.000đ"
                    return ServiceResult.ok(view);
                }
            }

            // B2: dữ liệu quá 1 ngày → tải lại từ wger (lỗi thì giữ bản cũ)
            LocalDateTime lastSynced = syncIfNeeded(now);
            if (lastSynced == null) {
                // Thư viện trống và wger lỗi → báo lỗi
                return ServiceResult.fail(Messages.MSG306_EXERCISE_LIBRARY_UNAVAILABLE);
            }
            view.setSyncedAtText(DateTimeUtil.formatDateTime(lastSynced));
            // Lần tải gần nhất lỗi và dữ liệu đã cũ → hiện cảnh báo
            view.setStale(lastFailureAt != null && needsSync(lastSynced, now));

            // B3: đọc bộ lọc trên URL (giá trị lạ → bỏ lọc)
            String query = cleanQuery(q);                         // ô tìm tên: bỏ khoảng trắng thừa, rỗng → null, > 50 ký tự → cắt
            Integer categoryId = parseCategory(category);         // chỉ nhận 8 mã nhóm cơ, lạ → null
            Integer equipmentId = parseEquipment(equipment);      // chỉ nhận 12 mã dụng cụ, lạ → null
            String sortKey = parseSort(sort);                     // chỉ "image" hoặc "name"
            String namePattern = query == null ? null : SqlUtil.toContainsPattern(query);
            view.setQuery(query);
            view.setCategoryId(categoryId);
            view.setEquipmentId(equipmentId);
            view.setSort(sortKey);

            // B4: đếm bài khớp lọc rồi lấy đúng 1 trang (12 bài)
            int total = libraryDAO.countFiltered(namePattern, categoryId, equipmentId);
            int totalPages = totalPages(total);
            // Trang sai → 1, quá trang cuối → trang cuối
            int pageNumber = Math.min(parsePage(page), totalPages);
            int offset = (pageNumber - 1) * PAGE_SIZE;
            List<ExerciseItem> items = libraryDAO.findPage(
                    namePattern,
                    categoryId,
                    equipmentId,
                    orderByOf(sortKey),
                    offset,
                    PAGE_SIZE);
            // Đổi mã → chữ tiếng Việt (nhóm cơ, dụng cụ)
            for (ExerciseItem item : items) {
                fillLabels(item);
            }
            // Bài không có ảnh → gán ảnh minh họa (không trùng trong trang)
            ExerciseImagePool.assign(items);
            view.setItems(items);                                 // lưới thẻ bài
            view.setTotal(total);                                 // "Tìm thấy 25 bài"
            view.setPage(pageNumber);                             // nút trang đang sáng
            view.setTotalPages(totalPages);                       // số nút trang
            view.setFirstIndex(total == 0 ? 0 : offset + 1);      // "Hiện 13–24 / 25": STT bài đầu trang (0 bài → 0)
            view.setLastIndex(offset + items.size());             //                    STT bài cuối trang

            // B5: dữ liệu cho bộ lọc: tổng số bài, chip nhóm cơ, dụng cụ, sắp xếp
            view.setLibraryTotal(libraryDAO.countAll());
            view.setCategories(categoryOptions(libraryDAO.countByCategory(), categoryId));
            view.setEquipments(equipmentOptions(equipmentId));
            view.setSorts(sortOptions(sortKey));
            return ServiceResult.ok(view);
        } catch (SQLException exception) {
            // Lỗi DB → báo hệ thống đang bận
            exception.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }



    // ================================================================================================================
    // PHẦN 2. ĐỒNG BỘ wger → bảng Exercises
    //   Ai gọi: getPage B2  ·  Hàm phụ: needsSync, canRetry (PHẦN 3) · WgerClient.fetchAll · DAO deleteAll / insertAll
    // ================================================================================================================

    /**
     * HÀM PHỤ (getPage B2) — bảng trống hoặc quá 1 ngày → gọi wger và ghi lại CẢ BẢNG trong 1 transaction.
     *   Kích hoạt bởi request (lazy), KHÔNG phải job nền: không ai mở trang thì không tải.
     *   RA       MAX(SyncedAt) sau khi xử lý (thành công → now); null = thư viện vẫn trống.
     *   BƯỚC     1. findLastSyncedAt: SELECT MAX(SyncedAt) FROM Exercises (bảng trống → null)
     *            2. Kiểm nhanh NGOÀI khóa: chưa cần tải HOẶC đang nghỉ 5' sau lỗi → trả luôn (đường thường gặp nhất)
     *            3. synchronized: chỉ 1 request vào; vào rồi ĐỌC LẠI + kiểm lại (người trước có thể đã tải xong / vừa lỗi)
     *               → 1000 người cùng mở lúc tới hạn chỉ gây ra 1 lần gọi wger (double-checked locking)
     *            4. wgerClient.fetchAll: tải hết bài TRƯỚC, chưa đụng DB (gọi wger lâu → không giữ khóa bảng lúc chờ)
     *            5. 1 transaction: deleteAll + insertAll (SyncedAt = now) → commit; lỗi giữa chừng → rollback, bản cũ còn nguyên
     *            6. Thành công → lastFailureAt = null, trả now
     *            7. Lỗi (wger hoặc DB) → ghi log WARNING, lastFailureAt = now, trả giờ BẢN CŨ (không ghi gì vào DB)
     *   LỖI LIÊN TỤC  cứ 5 phút, người mở trang kế tiếp thử lại 1 lần; giữa các lần thử luôn hiện bản tốt gần nhất + cảnh báo.
     */
    LocalDateTime syncIfNeeded(LocalDateTime now) throws SQLException {
        // B1: còn mới, hoặc vừa lỗi chưa quá 5 phút → không tải
        LocalDateTime lastSynced = libraryDAO.findLastSyncedAt();
        if (!needsSync(lastSynced, now) || !canRetry(lastFailureAt, now)) {
            return lastSynced;
        }

        // B2: nhiều người mở cùng lúc → chỉ 1 người tải, người sau chờ rồi dùng kết quả
        synchronized (SYNC_LOCK) {
            lastSynced = libraryDAO.findLastSyncedAt();
            if (!needsSync(lastSynced, now) || !canRetry(lastFailureAt, now)) {
                return lastSynced;
            }
            try {
                // B3: tải hết bài từ wger
                List<ExerciseItem> items = wgerClient.fetchAll();

                // B4: xóa bản cũ + ghi bản mới trong 1 transaction
                try (Connection connection = DBContext.getConnection()) {
                    connection.setAutoCommit(false);
                    try {
                        libraryDAO.deleteAll(connection);
                        libraryDAO.insertAll(connection, items, now);   // batch: gom hết rồi gửi 1 lượt
                        connection.commit();
                    } catch (SQLException exception) {
                        connection.rollback();                          // lỗi giữa chừng → bảng trở lại như cũ
                        throw exception;
                    }
                }

                // B5: thành công → xóa mốc lỗi
                lastFailureAt = null;
                return now;
            } catch (WgerUnavailableException | SQLException exception) {
                // Lỗi wger hoặc DB → ghi log, nhớ mốc lỗi, giữ bản cũ
                LOGGER.log(Level.WARNING, "Không đồng bộ được Thư viện bài tập từ wger.", exception);
                lastFailureAt = now;                                    // 5 phút tới không thử lại
                return lastSynced;                                      // giờ của bản cũ (bảng trống → null → MSG306)
            }
        }
    }



    // ================================================================================================================
    // PHẦN 3. HÀM PHỤ TÍNH TOÁN — static, KHÔNG đọc DB (ExerciseLibraryServiceTest gọi thẳng, truyền dữ liệu giả)
    // ================================================================================================================

    // ---------------------------------------------------- dùng ở B2 / syncIfNeeded ----------------------------------

    /**
     * HÀM PHỤ (syncIfNeeded, getPage B2 setStale) — cần tải lại chưa?
     * true khi chưa tải bao giờ (null) HOẶC bây giờ ≥ lúc tải + 1 ngày.
     */
    static boolean needsSync(LocalDateTime lastSynced, LocalDateTime now) {
        return lastSynced == null || !now.isBefore(lastSynced.plusDays(CACHE_DAYS));
    }

    /**
     * HÀM PHỤ (syncIfNeeded) — được thử gọi wger chưa?
     * true khi chưa lỗi lần nào (null) HOẶC lần lỗi gần nhất đã qua 5 phút.
     */
    static boolean canRetry(LocalDateTime lastFailure, LocalDateTime now) {
        return lastFailure == null || !now.isBefore(lastFailure.plusMinutes(RETRY_MINUTES));
    }

    // ---------------------------------------------------- dùng ở B1 (kiểm quyền khách) ------------------------------

    /**
     * HÀM PHỤ (getPage B1.2) — gói đang mở khóa HÔM NAY: Active hoặc Frozen VÀ StartDate ≤ hôm nay ≤ EndDate. Không có → null.
     *   Bảo lưu vẫn mở: khách tạm ngưng tập nhưng vẫn là hội viên, xem bài không tốn gì của phòng.
     *   Kiểm cả NGÀY chứ không chỉ Status: job đổi Active → Expired chạy 00:05, trước đó gói quá hạn vẫn ghi Active.
     */
    static Period decideAccess(List<Period> periods, LocalDate today) {
        for (Period period : periods) {
            boolean usable = "Active".equals(period.getStatus()) || "Frozen".equals(period.getStatus());
            boolean covers = !today.isBefore(period.getStartDate()) && !today.isAfter(period.getEndDate());
            if (usable && covers) {
                return period;
            }
        }
        return null;
    }

    /**
     * HÀM PHỤ (getPage B1.3) — xếp loại quyền, kiểm THEO THỨ TỰ:
     *   1. có gói mở khóa hôm nay → OPEN
     *   2. không có gói nào → NEVER (chưa mua bao giờ)
     *   3. có gói Active chưa tới ngày bắt đầu → NOT_STARTED (ưu tiên hơn EXPIRED: báo "sắp mở" có ích hơn)
     *   4. còn lại → EXPIRED (từng có, nay hết)
     */
    static Access accessOf(Period open, List<Period> periods, LocalDate today) {
        if (open != null) {
            return Access.OPEN;
        }
        if (periods.isEmpty()) {
            return Access.NEVER;
        }
        for (Period period : periods) {
            if ("Active".equals(period.getStatus()) && period.getStartDate().isAfter(today)) {
                return Access.NOT_STARTED;
            }
        }
        return Access.EXPIRED;
    }

    /**
     * HÀM PHỤ (getPage B1.4) — ngày để in trong câu khóa:
     *   NOT_STARTED → ngày bắt đầu SỚM NHẤT sắp tới · EXPIRED → ngày hết hạn MUỘN NHẤT · NEVER → null.
     */
    static LocalDate lockDateOf(Access access, List<Period> periods, LocalDate today) {
        LocalDate result = null;
        for (Period period : periods) {
            if (access == Access.NOT_STARTED && "Active".equals(period.getStatus()) && period.getStartDate().isAfter(today)) {
                boolean earlier = result == null || period.getStartDate().isBefore(result);
                if (earlier) {
                    result = period.getStartDate();
                }
            }
            if (access == Access.EXPIRED) {
                boolean later = result == null || period.getEndDate().isAfter(result);
                if (later) {
                    result = period.getEndDate();
                }
            }
        }
        return result;
    }

    /** HÀM PHỤ (getPage B1.4) — câu ở màn khóa, mỗi loại một câu để khách hiểu phải làm gì (đã ghép sẵn ngày). */
    static String lockMessage(Access access, LocalDate lockDate) {
        return switch (access) {
            case EXPIRED -> "Gói của bạn đã hết hạn ngày " + DateTimeUtil.formatDate(lockDate)
                    + ". Gia hạn gói tập hoặc gói PT để mở lại thư viện.";
            case NOT_STARTED -> "Gói của bạn bắt đầu từ ngày " + DateTimeUtil.formatDate(lockDate)
                    + ". Thư viện sẽ mở từ ngày đó.";
            case NEVER -> "Thư viện bài tập là đặc quyền của hội viên. Mua gói tập hoặc gói PT để mở khóa.";
            default -> null;
        };
    }

    /** HÀM PHỤ (getPage B1.4) — "Gói 1 Tháng · 799.000đ" cho từng gói ĐANG BÁN có đặc quyền (gói tập trước, rẻ trước — DAO sắp). */
    static List<String> packageLines(List<Package> packages) {
        List<String> lines = new ArrayList<>();
        for (Package pkg : packages) {
            lines.add(pkg.getPackageName() + " · " + MoneyUtil.formatMoney(pkg.getPrice()));
        }
        return lines;
    }

    // ---------------------------------------------------- dùng ở B3 (đọc bộ lọc URL) --------------------------------

    /** HÀM PHỤ (getPage B3) — ô tìm tên: bỏ khoảng trắng 2 đầu + gộp khoảng trắng giữa, rỗng → null, dài quá 50 ký tự → cắt. */
    static String cleanQuery(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim().replaceAll("\\s+", " ");
        return trimmed.length() > MAX_QUERY_LENGTH ? trimmed.substring(0, MAX_QUERY_LENGTH) : trimmed;
    }

    /** HÀM PHỤ (getPage B3) — ?category= chỉ nhận 8 mã nhóm cơ có trong CATEGORY_LABELS; chữ lạ / mã lạ (?category=999) → null (không lọc). */
    static Integer parseCategory(String raw) {
        Integer id = parseId(raw);
        return id != null && CATEGORY_LABELS.containsKey(id) ? id : null;
    }

    /** HÀM PHỤ (getPage B3) — ?equipment= chỉ nhận 12 mã dụng cụ có trong EQUIPMENT_LABELS; lạ → null. */
    static Integer parseEquipment(String raw) {
        Integer id = parseId(raw);
        return id != null && EQUIPMENT_LABELS.containsKey(id) ? id : null;
    }

    /** HÀM PHỤ (getPage B3) — ?sort= chỉ nhận "image"; mọi giá trị khác (kể cả chữ SQL như "DROP TABLE") → "name". */
    static String parseSort(String raw) {
        return SORT_IMAGE.equals(raw) ? SORT_IMAGE : SORT_NAME;
    }

    // ---------------------------------------------------- dùng ở B4 (đếm + lấy trang) -------------------------------

    /** HÀM PHỤ (getPage B4.5) — khóa sắp xếp → mệnh đề ORDER BY cố định (whitelist, chữ người dùng không bao giờ vào SQL). */
    static String orderByOf(String sortKey) {
        return SORT_IMAGE.equals(sortKey) ? ORDER_BY_IMAGE : ORDER_BY_NAME;
    }

    /** HÀM PHỤ (getPage B4.3) — ?page= không phải số hoặc < 1 → 1 (trang lớn quá được kẹp bằng Math.min ở getPage). */
    static int parsePage(String raw) {
        Integer page = parseId(raw);
        return page == null || page < 1 ? 1 : page;
    }

    /**
     * HÀM PHỤ (getPage B4.2) — số trang, 0 bài vẫn là 1 trang (trang trống).
     *   Công thức làm tròn LÊN bằng phép chia số nguyên: (tổng + cỡ_trang − 1) ÷ cỡ_trang.
     *   24 bài → 35/12 = 2 · 25 bài → 36/12 = 3 · 0 bài → max(1, 0) = 1.
     */
    static int totalPages(int total) {
        return Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    /**
     * HÀM PHỤ (getPage B4.6) — điền 3 nhãn tiếng Việt cho 1 bài (DB chỉ lưu mã):
     *   categoryLabel: 11 → "Ngực" (mã lạ → "Khác")
     *   equipmentText: [1, 8] → "Thanh đòn, Ghế tập" (rỗng → "Không dụng cụ")
     *   muscleText:    [4, 5] → "Ngực, Tay sau" (rỗng → "" → JSP ẩn dòng)
     *   Ảnh minh họa cho bài thiếu ảnh wger KHÔNG làm ở đây mà ở ExerciseImagePool.assign (cần cả trang để tránh trùng).
     */
    static void fillLabels(ExerciseItem item) {
        item.setCategoryLabel(CATEGORY_LABELS.getOrDefault(item.getCategoryId(), "Khác"));
        String equipment = joinLabels(item.getEquipmentIds(), EQUIPMENT_LABELS);
        item.setEquipmentText(equipment.isEmpty() ? "Không dụng cụ" : equipment);
        item.setMuscleText(joinLabels(item.getMuscleIds(), MUSCLE_LABELS));
    }

    /** HÀM PHỤ (fillLabels) — [1, 8] + bảng nhãn → "Thanh đòn, Ghế tập"; mã không có trong bảng → bỏ qua; nhãn trùng → in 1 lần. */
    static String joinLabels(List<Integer> ids, Map<Integer, String> labels) {
        List<String> names = new ArrayList<>();
        for (Integer id : ids) {
            String label = labels.get(id);
            if (label != null && !names.contains(label)) {
                names.add(label);
            }
        }
        return String.join(", ", names);
    }

    // ---------------------------------------------------- dùng ở B5 (dữ liệu bộ lọc) --------------------------------

    /**
     * HÀM PHỤ (getPage B5.2) — 8 chip nhóm cơ theo thứ tự cố định của CATEGORY_LABELS, kèm số bài;
     * nhóm không có bài vẫn hiện (0). Chip đang chọn → selected = true.
     */
    static List<Option> categoryOptions(Map<Integer, Integer> counts, Integer selected) {
        List<Option> options = new ArrayList<>();
        for (Map.Entry<Integer, String> entry : CATEGORY_LABELS.entrySet()) {
            int count = counts.getOrDefault(entry.getKey(), 0);
            boolean isSelected = entry.getKey().equals(selected);
            options.add(new Option(
                    String.valueOf(entry.getKey()),
                    entry.getValue(),
                    count,
                    isSelected));
        }
        return options;
    }

    /** HÀM PHỤ (getPage B5.3) — 12 dụng cụ cho ô chọn (count = -1: không in số bài). */
    static List<Option> equipmentOptions(Integer selected) {
        List<Option> options = new ArrayList<>();
        for (Map.Entry<Integer, String> entry : EQUIPMENT_LABELS.entrySet()) {
            boolean isSelected = entry.getKey().equals(selected);
            options.add(new Option(
                    String.valueOf(entry.getKey()),
                    entry.getValue(),
                    -1,
                    isSelected));
        }
        return options;
    }

    /** HÀM PHỤ (getPage B5.4) — 2 cách sắp xếp cho ô chọn. */
    static List<Option> sortOptions(String selected) {
        List<Option> options = new ArrayList<>();
        options.add(new Option(
                SORT_NAME,
                "Tên A → Z",
                -1,
                SORT_NAME.equals(selected)));
        options.add(new Option(
                SORT_IMAGE,
                "Có hình trước",
                -1,
                SORT_IMAGE.equals(selected)));
        return options;
    }

    // ---------------------------------------------------- dùng chung ------------------------------------------------

    /** DÙNG CHUNG (parseCategory, parseEquipment, parsePage) — "11" → 11; null, rỗng, chữ, số quá lớn → null (URL sửa tay không làm lỗi trang). */
    private static Integer parseId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}

/*
 * ====================================================================================================================
 * PHỤ LỤC — DTO ExerciseLibraryView (biến JSP "library") ← hàm nào điền field nào
 *   JSP in: WEB-INF/jsp/common/_exercise-library.jspf (include ở customer/exercises.jsp và pt/exercises.jsp)
 *   Servlet đặt thêm: "libraryError" (câu lỗi khi fail) · "libraryUrl" ("/customer/exercises" | "/pt/exercises")
 * ====================================================================================================================
 *
 * A. CHUNG
 *   pt                   ← getPage đầu hàm: view.setPt(isPt)                       JSP: PT thì bỏ nút "Xem các gói"
 *
 * B. MÀN KHÓA (chỉ khách chưa đủ quyền) — getPage B1
 *   access               ← accessOf(decideAccess(findUnlockPeriods))               mặc định OPEN (PT luôn OPEN)
 *                          JSP không in chữ: ${library.open} = isOpen() = (access == OPEN) quyết định màn khóa / thư viện
 *   lockDate             ← lockDateOf                                               JSP KHÔNG đọc, đã ghép vào lockMessage
 *   lockMessage          ← lockMessage(access, lockDate)                            JSP: <p> câu giải thích
 *   unlockingPackages    ← packageLines(DAO findUnlockingPackages)                  JSP: <li> "Gói 1 Tháng · 799.000đ"
 *
 * C. DÒNG TÓM TẮT — getPage B2 + B5
 *   syncedAtText         ← DateTimeUtil.formatDateTime(syncIfNeeded)                "cập nhật 05/11/2026 09:00"
 *   stale                ← lastFailureAt != null && needsSync                       cảnh báo "Chưa cập nhật được từ wger…"
 *   libraryTotal         ← DAO countAll                                             "823 bài"
 *
 * D. BỘ LỌC — getPage B3 (giá trị đang chọn) + B5 (các lựa chọn)
 *   query                ← cleanQuery(q)                                            điền lại ô tìm tên
 *   categoryId           ← parseCategory(category)                                  chip đang chọn, ghép link
 *   equipmentId          ← parseEquipment(equipment)                                ô dụng cụ đang chọn
 *   sort                 ← parseSort(sort)                                          ô sắp xếp đang chọn
 *   categories  List<Option> ← categoryOptions(DAO countByCategory, categoryId)    8 chip "Ngực 78"
 *   equipments  List<Option> ← equipmentOptions(equipmentId)                       12 dòng ô Dụng cụ
 *   sorts       List<Option> ← sortOptions(sortKey)                                2 dòng ô Sắp xếp
 *     Option: value (gửi lên URL) · label (chữ hiện) · count (số bài, -1 = không in) · selected (đang chọn)
 *
 * E. LƯỚI BÀI + PHÂN TRANG — getPage B4
 *   items  List<ExerciseItem> ← DAO findPage → fillLabels → ExerciseImagePool.assign   tối đa 12 thẻ
 *   total                ← DAO countFiltered                                        "Tìm thấy 25 bài"
 *   page                 ← min(parsePage(page), totalPages)                         nút trang đang sáng
 *   totalPages           ← totalPages(total)                                        số nút trang
 *   firstIndex           ← total == 0 ? 0 : offset + 1                              "Hiện 13–24 / 25"
 *   lastIndex            ← offset + items.size()
 *
 * F. 1 THẺ BÀI — ExerciseItem
 *   Từ DAO findPage (cột bảng Exercises, gốc là JSON wger, WgerClient.parseExercise đọc lúc đồng bộ):
 *     exerciseId     ← ExerciseId   (wger id)                   không in
 *     name           ← Name         (tên tiếng Anh)             tiêu đề thẻ
 *     categoryId     ← CategoryId   (8 Arms … 15 Cardio)         → categoryLabel
 *     muscleIds      ← MuscleIds    ",4,5," → [4, 5]             → muscleText
 *     equipmentIds   ← EquipmentIds ",1,8," → [1, 8]             → equipmentText
 *     imageUrl       ← ImageUrl     link ảnh trên wger.de (null = wger không có ảnh)   <img src>
 *     description    ← Description  mô tả đã bỏ thẻ HTML        phần "Cách tập"
 *   Service điền (fillLabels):
 *     categoryLabel  "Ngực" · equipmentText "Thanh đòn, Ghế tập" · muscleText "Ngực, Tay sau"
 *   ExerciseImagePool.assign điền (chỉ bài imageUrl == null):
 *     fallbackImage  "/images/exercises/library/chest/….jpg" (kho 194 ảnh trong dự án, danh sách ở resources/exercise-images.json)
 *     fallbackExact  true = khớp đúng tên → "Ảnh minh họa" · false = cùng nhóm cơ → "Ảnh minh họa nhóm Ngực"
 *
 * G. KIỂU NỘI BỘ (không in ra trang)
 *   Period (startDate, endDate, status) ← DAO findUnlockPeriods; chỉ dùng trong B1 để quyết định access.
 * ====================================================================================================================
 */