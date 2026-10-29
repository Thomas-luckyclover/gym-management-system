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
 *   Nghiệp vụ: thư viện là ĐẶC QUYỀN HỘI VIÊN. Khách xem được khi đang có gói tập hoặc gói PT còn hiệu lực
 *   (Packages.UnlocksExerciseLibrary = 1, kể cả đang bảo lưu). PT luôn xem được để tra bài khi soạn giáo án.
 *
 *   Dữ liệu: API wger.de → bảng Exercises (bộ đệm). Quá 1 ngày thì tải lại; API lỗi thì vẫn hiện bản cũ
 *   kèm giờ tải, 5 phút sau mới thử lại. Lọc, đếm, sắp xếp, phân trang đều làm ở SQL.
 *
 *   PHẦN 1. HÀM CHÍNH       getPage (Servlet gọi 1 lần)
 *   PHẦN 2. ĐỒNG BỘ wger     syncIfNeeded
 *   PHẦN 3. TÍNH (static, ExerciseLibraryServiceTest)  quyền, đọc bộ lọc, nhãn tiếng Việt, phân trang
 */
public class ExerciseLibraryService {

    private static final Logger LOGGER = Logger.getLogger(ExerciseLibraryService.class.getName());

    static final int PAGE_SIZE = 12;
    static final int MAX_QUERY_LENGTH = 50;
    static final int CACHE_DAYS = 1;
    static final int RETRY_MINUTES = 5;
    static final String SORT_NAME = "name";
    static final String SORT_IMAGE = "image";

    // ORDER BY cố định theo ô "Sắp xếp". Chữ trên URL chỉ dùng để CHỌN 1 trong 2 hằng số này (chống SQL injection).
    private static final String ORDER_BY_NAME = "Name ASC, ExerciseId ASC";
    private static final String ORDER_BY_IMAGE = "CASE WHEN ImageUrl IS NULL THEN 1 ELSE 0 END, Name ASC, ExerciseId ASC";

    // 8 nhóm cơ cố định của wger (fixtures/categories.json) → chữ tiếng Việt, theo thứ tự hiện chip.
    static final Map<Integer, String> CATEGORY_LABELS = new LinkedHashMap<>();
    // 12 dụng cụ của wger (fixtures/equipment.json).
    static final Map<Integer, String> EQUIPMENT_LABELS = new LinkedHashMap<>();
    // 16 cơ của wger (fixtures/muscles.json). Mã lạ (wger thêm sau) → bỏ qua, không lỗi.
    static final Map<Integer, String> MUSCLE_LABELS = new LinkedHashMap<>();
    // Ảnh dự phòng theo nhóm cơ (webapp/images/exercises, public domain từ free-exercise-db, xem SOURCES.json).
    static final Map<Integer, String> FALLBACK_IMAGES = new LinkedHashMap<>();

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

        FALLBACK_IMAGES.put(11, "/images/exercises/chest.jpg");
        FALLBACK_IMAGES.put(12, "/images/exercises/back.jpg");
        FALLBACK_IMAGES.put(13, "/images/exercises/shoulders.jpg");
        FALLBACK_IMAGES.put(8, "/images/exercises/arms.jpg");
        FALLBACK_IMAGES.put(10, "/images/exercises/abs.jpg");
        FALLBACK_IMAGES.put(9, "/images/exercises/legs.jpg");
        FALLBACK_IMAGES.put(14, "/images/exercises/calves.jpg");
        FALLBACK_IMAGES.put(15, "/images/exercises/cardio.jpg");

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

    // Đồng bộ dùng chung cho mọi request: 1 khóa, 1 mốc lỗi gần nhất (static vì mỗi Servlet giữ 1 Service riêng).
    private static final Object SYNC_LOCK = new Object();
    private static volatile LocalDateTime lastFailureAt;

    private final ExerciseLibraryDAO libraryDAO = new ExerciseLibraryDAO();
    private final WgerClient wgerClient;

    public ExerciseLibraryService() {
        this(new WgerClient());
    }

    // Test truyền client giả (không gọi wger thật)
    ExerciseLibraryService(WgerClient wgerClient) {
        this.wgerClient = wgerClient;
    }

    // ================================================================================================================
    // PHẦN 1. HÀM CHÍNH
    // ================================================================================================================

    /**
     * HÀM CHÍNH — ExerciseLibraryServlet.doGet gọi.
     *
     * @param accountId tài khoản từ session (không lấy từ URL)
     * @param isPt      true khi vào bằng /pt/exercises (PtAreaFilter đã kiểm vai trò)
     * @param q, category, equipment, sort, page  chữ thô trên URL; Service tự làm sạch, giá trị lạ → mặc định
     * @param now       giờ Việt Nam (DateTimeUtil.now())
     * @return view (màn khóa hoặc thư viện), hoặc fail khi thư viện trống mà wger lỗi / DB lỗi
     */
    public ServiceResult<ExerciseLibraryView> getPage(int accountId, boolean isPt, String q, String category,
            String equipment, String sort, String page, LocalDateTime now) {
        try {
            ExerciseLibraryView view = new ExerciseLibraryView();
            view.setPt(isPt);

            // B1: quyền xem. Khách chưa đủ quyền → màn khóa, không đọc thư viện, không gọi wger.
            if (!isPt) {
                List<Period> periods = libraryDAO.findUnlockPeriods(accountId);
                Period decided = decideAccess(periods, now.toLocalDate());
                Access access = accessOf(decided, periods, now.toLocalDate());
                if (access != Access.OPEN) {
                    view.setAccess(access);
                    view.setLockDate(lockDateOf(access, periods, now.toLocalDate()));
                    view.setLockMessage(lockMessage(access, view.getLockDate()));
                    view.setUnlockingPackages(packageLines(libraryDAO.findUnlockingPackages()));
                    return ServiceResult.ok(view);
                }
            }

            // B2: dữ liệu quá 1 ngày → tải lại từ wger (lỗi thì giữ bản cũ).
            LocalDateTime lastSynced = syncIfNeeded(now);
            if (lastSynced == null) {
                return ServiceResult.fail("Chưa tải được thư viện bài tập từ wger. Vui lòng thử lại sau ít phút.");
            }
            view.setSyncedAtText(DateTimeUtil.formatDateTime(lastSynced));
            view.setStale(lastFailureAt != null && needsSync(lastSynced, now));

            // B3: đọc bộ lọc trên URL (giá trị lạ → bỏ lọc / mặc định).
            String query = cleanQuery(q);
            Integer categoryId = parseCategory(category);
            Integer equipmentId = parseEquipment(equipment);
            String sortKey = parseSort(sort);
            String namePattern = query == null ? null : SqlUtil.toContainsPattern(query);
            view.setQuery(query);
            view.setCategoryId(categoryId);
            view.setEquipmentId(equipmentId);
            view.setSort(sortKey);

            // B4: đếm + lấy 1 trang (kẹp số trang vào 1..totalPages: gõ ?page=999 → trang cuối).
            int total = libraryDAO.countFiltered(namePattern, categoryId, equipmentId);
            int totalPages = totalPages(total);
            int pageNumber = Math.min(parsePage(page), totalPages);
            int offset = (pageNumber - 1) * PAGE_SIZE;
            List<ExerciseItem> items = libraryDAO.findPage(namePattern, categoryId, equipmentId,
                    orderByOf(sortKey), offset, PAGE_SIZE);
            for (ExerciseItem item : items) {
                fillLabels(item);
            }
            view.setItems(items);
            view.setTotal(total);
            view.setPage(pageNumber);
            view.setTotalPages(totalPages);
            view.setFirstIndex(total == 0 ? 0 : offset + 1);
            view.setLastIndex(offset + items.size());

            // B5: các lựa chọn của bộ lọc + tổng số bài.
            view.setLibraryTotal(libraryDAO.countAll());
            view.setCategories(categoryOptions(libraryDAO.countByCategory(), categoryId));
            view.setEquipments(equipmentOptions(equipmentId));
            view.setSorts(sortOptions(sortKey));
            return ServiceResult.ok(view);
        } catch (SQLException exception) {
            exception.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    // ================================================================================================================
    // PHẦN 2. ĐỒNG BỘ wger → bảng Exercises
    // ================================================================================================================

    /**
     * Bảng trống hoặc quá 1 ngày → gọi wger và ghi lại cả bảng trong 1 transaction.
     * Lỗi API / lỗi ghi → giữ nguyên bản cũ, nhớ mốc lỗi, 5 phút sau mới thử lại.
     *
     * @return MAX(SyncedAt) sau khi xử lý; null = thư viện vẫn trống
     */
    LocalDateTime syncIfNeeded(LocalDateTime now) throws SQLException {
        LocalDateTime lastSynced = libraryDAO.findLastSyncedAt();
        if (!needsSync(lastSynced, now) || !canRetry(lastFailureAt, now)) {
            return lastSynced;
        }

        // synchronized: nhiều người mở trang cùng lúc chỉ 1 request gọi wger; người sau đọc lại kết quả.
        synchronized (SYNC_LOCK) {
            lastSynced = libraryDAO.findLastSyncedAt();
            if (!needsSync(lastSynced, now) || !canRetry(lastFailureAt, now)) {
                return lastSynced;
            }
            try {
                // B1: tải hết bài từ API (chưa đụng DB).
                List<ExerciseItem> items = wgerClient.fetchAll();

                // B2: xóa bản cũ + ghi bản mới trong 1 transaction; lỗi giữa chừng → rollback, bản cũ còn nguyên.
                try (Connection connection = DBContext.getConnection()) {
                    connection.setAutoCommit(false);
                    try {
                        libraryDAO.deleteAll(connection);
                        libraryDAO.insertAll(connection, items, now);
                        connection.commit();
                    } catch (SQLException exception) {
                        connection.rollback();
                        throw exception;
                    }
                }
                lastFailureAt = null;
                return now;
            } catch (WgerUnavailableException | SQLException exception) {
                LOGGER.log(Level.WARNING, "Không đồng bộ được Thư viện bài tập từ wger.", exception);
                lastFailureAt = now;
                return lastSynced;
            }
        }
    }

    // ================================================================================================================
    // PHẦN 3. TÍNH — static, không đọc DB (ExerciseLibraryServiceTest)
    // ================================================================================================================

    /** Quá 1 ngày (hoặc chưa từng tải) thì cần tải lại. */
    static boolean needsSync(LocalDateTime lastSynced, LocalDateTime now) {
        return lastSynced == null || !now.isBefore(lastSynced.plusDays(CACHE_DAYS));
    }

    /** Lần lỗi gần nhất đã qua 5 phút chưa (chưa lỗi lần nào → được thử). */
    static boolean canRetry(LocalDateTime lastFailure, LocalDateTime now) {
        return lastFailure == null || !now.isBefore(lastFailure.plusMinutes(RETRY_MINUTES));
    }

    /**
     * Gói đang mở khóa hôm nay: Active hoặc Frozen và StartDate ≤ hôm nay ≤ EndDate. Không có → null.
     * Bảo lưu vẫn mở: khách tạm ngưng tập nhưng vẫn là hội viên, xem bài không tốn gì của phòng.
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

    /** Có gói mở khóa → OPEN; gói Active chưa tới ngày → NOT_STARTED; từng có → EXPIRED; chưa từng → NEVER. */
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

    /** NOT_STARTED → ngày bắt đầu sớm nhất sắp tới; EXPIRED → ngày hết hạn muộn nhất; còn lại null. */
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

    /** Câu ở màn khóa, mỗi trạng thái một câu để khách hiểu phải làm gì. */
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

    /** "Gói 1 Tháng · 799.000đ" cho từng gói đang bán có đặc quyền. */
    static List<String> packageLines(List<Package> packages) {
        List<String> lines = new ArrayList<>();
        for (Package pkg : packages) {
            lines.add(pkg.getPackageName() + " · " + MoneyUtil.formatMoney(pkg.getPrice()));
        }
        return lines;
    }

    /** Ô tìm tên: bỏ khoảng trắng 2 đầu, rỗng → null, dài quá 50 ký tự → cắt. */
    static String cleanQuery(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim().replaceAll("\\s+", " ");
        return trimmed.length() > MAX_QUERY_LENGTH ? trimmed.substring(0, MAX_QUERY_LENGTH) : trimmed;
    }

    /** ?category= chỉ nhận 8 mã nhóm cơ có trong bảng; chữ lạ / mã lạ → null (không lọc). */
    static Integer parseCategory(String raw) {
        Integer id = parseId(raw);
        return id != null && CATEGORY_LABELS.containsKey(id) ? id : null;
    }

    /** ?equipment= chỉ nhận 12 mã dụng cụ có trong bảng; lạ → null. */
    static Integer parseEquipment(String raw) {
        Integer id = parseId(raw);
        return id != null && EQUIPMENT_LABELS.containsKey(id) ? id : null;
    }

    /** ?sort= chỉ nhận "image"; mọi giá trị khác (kể cả chữ SQL) → "name". */
    static String parseSort(String raw) {
        return SORT_IMAGE.equals(raw) ? SORT_IMAGE : SORT_NAME;
    }

    /** Khóa sắp xếp → mệnh đề ORDER BY cố định (whitelist). */
    static String orderByOf(String sortKey) {
        return SORT_IMAGE.equals(sortKey) ? ORDER_BY_IMAGE : ORDER_BY_NAME;
    }

    /** ?page= không phải số hoặc < 1 → 1 (trang lớn quá được kẹp ở getPage). */
    static int parsePage(String raw) {
        Integer page = parseId(raw);
        return page == null || page < 1 ? 1 : page;
    }

    /**
     * Số trang: 0 bài vẫn là 1 trang (trang trống).
     * Công thức làm tròn lên: (tổng + cỡ_trang − 1) ÷ cỡ_trang.
     * Muốn đổi số bài mỗi trang → sửa 1 hằng PAGE_SIZE ở đầu file này.
     */
    static int totalPages(int total) {
        return Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    /** Điền 3 nhãn tiếng Việt cho 1 bài; bài không có ảnh wger → ảnh dự phòng theo nhóm cơ. */
    static void fillLabels(ExerciseItem item) {
        item.setCategoryLabel(CATEGORY_LABELS.getOrDefault(item.getCategoryId(), "Khác"));
        String equipment = joinLabels(item.getEquipmentIds(), EQUIPMENT_LABELS);
        item.setEquipmentText(equipment.isEmpty() ? "Không dụng cụ" : equipment);
        item.setMuscleText(joinLabels(item.getMuscleIds(), MUSCLE_LABELS));
        if (item.getImageUrl() == null) {
            item.setFallbackImage(FALLBACK_IMAGES.get(item.getCategoryId()));
        }
    }

    /** [1, 8] + bảng nhãn → "Thanh đòn, Ghế tập"; mã không có trong bảng → bỏ qua. */
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

    /** Chip nhóm cơ theo thứ tự cố định, kèm số bài; nhóm không có bài vẫn hiện (0). */
    static List<Option> categoryOptions(Map<Integer, Integer> counts, Integer selected) {
        List<Option> options = new ArrayList<>();
        for (Map.Entry<Integer, String> entry : CATEGORY_LABELS.entrySet()) {
            int count = counts.getOrDefault(entry.getKey(), 0);
            boolean isSelected = entry.getKey().equals(selected);
            options.add(new Option(String.valueOf(entry.getKey()), entry.getValue(), count, isSelected));
        }
        return options;
    }

    /** 12 dụng cụ cho ô chọn. */
    static List<Option> equipmentOptions(Integer selected) {
        List<Option> options = new ArrayList<>();
        for (Map.Entry<Integer, String> entry : EQUIPMENT_LABELS.entrySet()) {
            boolean isSelected = entry.getKey().equals(selected);
            options.add(new Option(String.valueOf(entry.getKey()), entry.getValue(), -1, isSelected));
        }
        return options;
    }

    /** 2 cách sắp xếp cho ô chọn. */
    static List<Option> sortOptions(String selected) {
        List<Option> options = new ArrayList<>();
        options.add(new Option(SORT_NAME, "Tên A → Z", -1, SORT_NAME.equals(selected)));
        options.add(new Option(SORT_IMAGE, "Có hình trước", -1, SORT_IMAGE.equals(selected)));
        return options;
    }

    /** "11" → 11; null, chữ, số quá lớn → null. */
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
