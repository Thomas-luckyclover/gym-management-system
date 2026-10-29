package com.gms.service;

import com.gms.constant.Messages;
import com.gms.dao.CounterSaleDAO;
import com.gms.dao.PtSaleDAO;
import com.gms.dto.CounterCustomerRow;
import com.gms.dto.CounterPackageCard;
import com.gms.dto.CounterPackageView;
import com.gms.dto.ServiceResult;
import com.gms.entity.Package;
import com.gms.util.DBContext;
import com.gms.util.DateTimeUtil;
import com.gms.util.MoneyUtil;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * SERVICE MÀN LỄ TÂN BÁN GÓI TẠI QUẦY (/receptionist/sales), SRS #37, đợt 1: Chọn gói + Chọn khách.
 *
 * Luồng đã chốt (khác thứ tự SRS 7.2.1, cần báo nhóm): Chọn gói, Chọn khách, Voucher, Thanh toán.
 * Đợt này chỉ có 2 bước đầu. Chưa có tiền, voucher, đơn hàng nên không có transaction.
 *
 *   getPackageView    màn Chọn gói: 3 tab thẻ gói
 *   getPackageCard    một thẻ gói (cột "Tóm tắt đơn" ở bước sau)
 *   searchCustomers   màn Chọn khách: kết quả ô tìm
 *   getCustomer       khách đã chọn (bước sau)
 */
public class CounterSaleService {

    // Tối thiểu bao nhiêu ký tự thì mới tìm khách (gõ 1 chữ sẽ ra quá nhiều người).
    private static final int MIN_SEARCH_LENGTH = 2;

    // Chỉ gói tập từ chừng này tháng trở lên mới hiện nhãn "-13%" (so với mua lẻ gói 1 tháng).
    private static final int MIN_MONTHS_FOR_SAVING = 3;

    private final CounterSaleDAO counterSaleDAO = new CounterSaleDAO();
    private final PtSaleDAO ptSaleDAO = new PtSaleDAO();
    private final SystemConfigService configService = new SystemConfigService();

    /**
     * Dựng dữ liệu màn Chọn gói.
     * @param accountId mã tài khoản từ session (không lấy từ request)
     * @return 3 danh sách thẻ theo tab, hoặc thông báo lỗi
     */
    public ServiceResult<CounterPackageView> getPackageView(int accountId) {
        try (Connection connection = DBContext.getConnection()) {
            // B1: chỉ Lễ tân đang làm việc mới vào được màn này.
            int[] receptionist = ptSaleDAO.findReceptionist(connection, accountId);
            if (receptionist == null) {
                return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
            }

            // B2: lấy gói đang bán, đã sắp thứ tự hiển thị.
            List<Package> packages = counterSaleDAO.findActivePackages(connection);

            // B3: tìm giá gói 1 tháng để tính % tiết kiệm của các gói dài hơn, và đọc thuế suất VAT (P-10) một lần.
            long oneMonthPrice = findOneMonthPrice(packages);
            int vatRate = configService.getInt(SystemConfigService.VAT_PERCENT);

            // B4: mỗi gói thành một thẻ, rồi bỏ vào tab đúng loại.
            List<CounterPackageCard> gymCards = new ArrayList<>();
            List<CounterPackageCard> ptCards = new ArrayList<>();
            List<CounterPackageCard> sessionCards = new ArrayList<>();
            for (Package pkg : packages) {
                CounterPackageCard card = buildCard(pkg, oneMonthPrice, vatRate);
                Package.Type packageType = pkg.getPackageType();
                if (packageType == Package.Type.MEMBERSHIP || packageType == Package.Type.DAY_PASS) {
                    gymCards.add(card);
                } else if (packageType == Package.Type.SINGLE_SESSION) {
                    sessionCards.add(card);
                } else {
                    ptCards.add(card);
                }
            }
            return ServiceResult.ok(new CounterPackageView(gymCards, ptCards, sessionCards));
        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /**
     * Lấy một thẻ gói theo mã, dùng ở các bước sau Chọn gói.
     * @param accountId mã tài khoản từ session
     * @param packageId mã gói từ URL
     * @return thẻ gói, hoặc lỗi nếu gói không có / đã tắt bán
     */
    public ServiceResult<CounterPackageCard> getPackageCard(int accountId, int packageId) {
        try (Connection connection = DBContext.getConnection()) {
            int[] receptionist = ptSaleDAO.findReceptionist(connection, accountId);
            if (receptionist == null) {
                return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
            }

            Package pkg = counterSaleDAO.findActivePackage(connection, packageId);
            if (pkg == null) {
                return ServiceResult.fail(String.format(Messages.MSG08_NOT_FOUND, "gói"));
            }

            // Cần giá gói 1 tháng để thẻ có cùng số liệu như ở màn Chọn gói.
            List<Package> packages = counterSaleDAO.findActivePackages(connection);
            long oneMonthPrice = findOneMonthPrice(packages);
            int vatRate = configService.getInt(SystemConfigService.VAT_PERCENT);
            return ServiceResult.ok(buildCard(pkg, oneMonthPrice, vatRate));
        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /**
     * Tìm khách cho màn Chọn khách.
     * @param accountId mã tài khoản từ session
     * @param keyword chữ lễ tân gõ (tên / SĐT / mã KH); dưới 2 ký tự thì trả danh sách rỗng
     * @return danh sách khách (rỗng nếu chưa gõ đủ hoặc không khớp), hoặc thông báo lỗi
     */
    public ServiceResult<List<CounterCustomerRow>> searchCustomers(int accountId, String keyword) {
        // B1: chưa gõ đủ ký tự thì không truy vấn.
        String text = "";
        if (keyword != null) {
            text = keyword.trim();
        }
        if (text.length() < MIN_SEARCH_LENGTH) {
            return ServiceResult.ok(new ArrayList<>());
        }

        try (Connection connection = DBContext.getConnection()) {
            // B2: kiểm tra quyền Lễ tân.
            int[] receptionist = ptSaleDAO.findReceptionist(connection, accountId);
            if (receptionist == null) {
                return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
            }

            // B3: lấy riêng phần chữ số để tìm theo SĐT ("090 123" thành "090123").
            String digits = text.replaceAll("\\D", "");
            LocalDate today = DateTimeUtil.today();
            List<CounterCustomerRow> rows = counterSaleDAO.searchCustomers(connection, text, digits, today);
            return ServiceResult.ok(rows);
        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /**
     * Lấy khách đã chọn (URL có ?customer=…).
     * @param accountId mã tài khoản từ session
     * @param customerId mã khách từ URL
     * @return khách, hoặc lỗi nếu hồ sơ không có / đã xóa
     */
    public ServiceResult<CounterCustomerRow> getCustomer(int accountId, int customerId) {
        try (Connection connection = DBContext.getConnection()) {
            int[] receptionist = ptSaleDAO.findReceptionist(connection, accountId);
            if (receptionist == null) {
                return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
            }

            LocalDate today = DateTimeUtil.today();
            CounterCustomerRow row = counterSaleDAO.findCustomer(connection, customerId, today);
            if (row == null) {
                return ServiceResult.fail(String.format(Messages.MSG08_NOT_FOUND, "khách hàng"));
            }
            return ServiceResult.ok(row);
        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /**
     * Giá của gói tập 1 tháng rẻ nhất đang bán, làm mốc để tính % tiết kiệm.
     * @return giá gói 1 tháng; 0 nếu không có gói 1 tháng (khi đó không hiện nhãn tiết kiệm)
     */
    private long findOneMonthPrice(List<Package> packages) {
        long oneMonthPrice = 0;
        for (Package pkg : packages) {
            boolean isMembership = pkg.getPackageType() == Package.Type.MEMBERSHIP;
            boolean isOneMonth = pkg.getDurationMonths() == 1;
            boolean isCheaper = oneMonthPrice == 0 || pkg.getPrice() < oneMonthPrice;
            if (isMembership && isOneMonth && isCheaper) {
                oneMonthPrice = pkg.getPrice();
            }
        }
        return oneMonthPrice;
    }

    /**
     * Một gói trong DB thành một thẻ trên màn.
     * Ví dụ "Gói 3 Tháng", 2.190.000đ, mốc 1 tháng 799.000đ: giá mỗi tháng 730.000đ, tiết kiệm 8%.
     * @param pkg gói do DAO đọc
     * @param oneMonthPrice giá gói 1 tháng (0 nếu không có)
     * @param vatRate thuế suất % (P-10), dùng tách VAT ra khỏi giá đã gồm VAT (DBR-57)
     */
    private CounterPackageCard buildCard(Package pkg, long oneMonthPrice, int vatRate) {
        Package.Type packageType = pkg.getPackageType();
        int months = pkg.getDurationMonths();
        long price = pkg.getPrice();

        // B0: tách VAT khỏi giá (giá trong DB đã gồm VAT). Chưa có giảm giá nên số tiền phải trả = giá gói.
        long vatAmount = MoneyUtil.calculateVatAmount(price, vatRate);
        long preTaxAmount = MoneyUtil.calculatePreTaxAmount(price, vatAmount);

        // B1: nhãn loại gói và thời hạn viết sẵn (hiện số tháng, không hiện số ngày).
        String typeLabel = buildTypeLabel(packageType);
        String durationText = buildDurationText(packageType, months);

        // B2: giá mỗi tháng và % tiết kiệm, chỉ cho gói tập nhiều tháng.
        long monthlyPrice = 0;
        int savingPercent = 0;
        boolean isLongMembership = packageType == Package.Type.MEMBERSHIP && months >= 2;
        if (isLongMembership) {
            monthlyPrice = (price + months / 2) / months;
        }
        boolean canShowSaving = isLongMembership && months >= MIN_MONTHS_FOR_SAVING && oneMonthPrice > 0;
        if (canShowSaving) {
            long savedPerMonth = oneMonthPrice - monthlyPrice;
            if (savedPerMonth > 0) {
                savingPercent = (int) (savedPerMonth * 100 / oneMonthPrice);
            }
        }

        // B3: quy định theo loại gói; buổi PT lẻ chưa có màn bán tại quầy nên khóa nút.
        List<String> ruleLines = buildRuleLines(packageType);
        boolean isContinueAllowed = packageType != Package.Type.SINGLE_SESSION;
        String blockedReason = "";
        if (!isContinueAllowed) {
            blockedReason = "Chưa có màn bán buổi PT lẻ tại quầy";
        }

        return new CounterPackageCard(pkg.getPackageId(), pkg.getPackageName(), packageType, typeLabel,
                price, vatRate, vatAmount, preTaxAmount, durationText, monthlyPrice, savingPercent,
                pkg.getBenefitLines(), ruleLines, isContinueAllowed, blockedReason);
    }

    // Nhãn nhỏ viết hoa ở dải đầu thẻ.
    private String buildTypeLabel(Package.Type packageType) {
        switch (packageType) {
            case MEMBERSHIP:
                return "GÓI TẬP";
            case DAY_PASS:
                return "VÉ NGÀY";
            case PT_SINGLE:
                return "PT 1 NGƯỜI";
            case PT_COUPLE:
                return "PT ĐÔI";
            default:
                return "BUỔI PT LẺ";
        }
    }

    // Thời hạn viết sẵn: gói tháng "3 tháng", vé ngày "1 ngày", buổi lẻ "1 buổi".
    private String buildDurationText(Package.Type packageType, int months) {
        if (packageType == Package.Type.DAY_PASS) {
            return "1 ngày";
        }
        if (packageType == Package.Type.SINGLE_SESSION) {
            return "1 buổi";
        }
        return months + " tháng";
    }

    /**
     * Các dòng "QUY ĐỊNH" của thẻ. Số liệu bảo lưu (P-01, P-02) và tuổi tối thiểu (P-05) đọc từ SystemConfigs.
     * Nội dung theo SRS: gói tập được bảo lưu, áp dụng voucher, chụp ảnh mặt tại quầy;
     * vé ngày không bảo lưu, không chụp ảnh; gói PT và buổi lẻ không dùng voucher, không gồm quyền vào phòng (CBR-58).
     */
    private List<String> buildRuleLines(Package.Type packageType) {
        List<String> ruleLines = new ArrayList<>();
        try {
            if (packageType == Package.Type.MEMBERSHIP) {
                int maxFreezeCount = configService.getInt(SystemConfigService.MAX_FREEZE_COUNT);
                int maxFreezeMonths = configService.getInt(SystemConfigService.MAX_FREEZE_MONTHS);
                ruleLines.add("Bảo lưu tối đa " + maxFreezeCount + " lần, " + maxFreezeMonths + " tháng");
                ruleLines.add("Áp dụng voucher");
                ruleLines.add("Chụp ảnh mặt tại quầy");
            } else if (packageType == Package.Type.DAY_PASS) {
                ruleLines.add("Không bảo lưu");
                ruleLines.add("Áp dụng voucher");
                ruleLines.add("Không cần chụp ảnh");
            } else {
                int minAgeForPt = configService.getInt(SystemConfigService.MIN_AGE_FOR_PT);
                ruleLines.add("Không áp dụng voucher");
                ruleLines.add("Không gồm quyền vào phòng");
                ruleLines.add("Cần gói tập còn hạn");
                ruleLines.add("Từ " + minAgeForPt + " tuổi");
            }
        } catch (SQLException e) {
            // Đọc tham số lỗi thì thẻ vẫn hiện, chỉ thiếu phần quy định; lỗi thật ghi ra log cho dev.
            e.printStackTrace();
        }
        return ruleLines;
    }
}