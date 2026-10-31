package com.gms.service;

import com.gms.constant.Messages;
import com.gms.constant.Role;
import com.gms.integration.EmailSendException;
import com.gms.dao.CounterSaleDAO;
import com.gms.dao.CustomerDAO;
import com.gms.dao.OrderDAO;
import com.gms.dao.PersonDAO;
import com.gms.dao.PtSaleDAO;
import com.gms.dao.ShiftDAO;
import com.gms.dao.SubscriptionDAO;
import com.gms.dao.VoucherDAO;
import com.gms.dto.CounterCheckoutInput;
import com.gms.dto.CounterCustomerCreateInput;
import com.gms.dto.CounterCustomerRow;
import com.gms.dto.CounterPackageCard;
import com.gms.dto.CounterPackageView;
import com.gms.dto.CounterSaleReceipt;
import com.gms.dto.CounterVoucherResult;
import com.gms.dto.ServiceResult;
import com.gms.entity.Customer;
import com.gms.entity.Order;
import com.gms.entity.Package;
import com.gms.entity.Person;
import com.gms.entity.Shift;
import com.gms.entity.Subscription;
import com.gms.entity.Voucher;
import com.gms.util.DBContext;
import com.gms.util.DateTimeUtil;
import com.gms.util.MoneyUtil;
import com.gms.util.ValidationUtil;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * SERVICE MÀN LỄ TÂN BÁN GÓI TẠI QUẦY (/receptionist/sales), SRS #37.
 *
 * Điều phối luồng bán gói trực tiếp tại quầy lễ tân:
 *   1. getPackageView    màn Chọn gói: 3 danh sách thẻ theo tab
 *   2. getPackageCard    tải một thẻ gói (cột Tóm tắt đơn ở các bước sau)
 *   3. searchCustomers   tìm khách theo từ khóa (tên / SĐT / mã KH)
 *   4. getCustomer       thông tin khách hàng đã chọn
 *   5. checkout          ghi nhận thanh toán thực tế (tiền mặt/chuyển khoản), lập đơn Orders,
 *                        ghi Transactions, cấp Subscriptions, ghi AuditLog trong 1 transaction
 */
public class CounterSaleService {

    // Tối thiểu bao nhiêu ký tự thì mới tìm khách (gõ 1 chữ sẽ ra quá nhiều người).
    private static final int MIN_SEARCH_LENGTH = 2;

    // Chỉ gói tập từ chừng này tháng trở lên mới hiện nhãn "-13%" (so với mua lẻ gói 1 tháng).
    private static final int MIN_MONTHS_FOR_SAVING = 3;

    private final CounterSaleDAO counterSaleDAO = new CounterSaleDAO();
    private final CustomerDAO customerDAO = new CustomerDAO();
    private final PersonDAO personDAO = new PersonDAO();
    private final PtSaleDAO ptSaleDAO = new PtSaleDAO();
    private final ShiftDAO shiftDAO = new ShiftDAO();
    private final OrderDAO orderDAO = new OrderDAO();
    private final SubscriptionDAO subscriptionDAO = new SubscriptionDAO();
    private final VoucherDAO voucherDAO = new VoucherDAO();
    private final SystemConfigService configService = new SystemConfigService();
    private final AuditLogService auditLogService = new AuditLogService();
    private final CounterAccountService counterAccountService = new CounterAccountService();

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
            // receptionist[1] là mã Person của lễ tân, để đánh dấu hồ sơ của chính họ (CBR-115).
            String digits = text.replaceAll("\\D", "");
            LocalDate today = DateTimeUtil.today();
            List<CounterCustomerRow> rows = counterSaleDAO.searchCustomers(connection, text, digits, today,
                    receptionist[1]);
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
     * @return khách, hoặc lỗi nếu chưa mở ca / hồ sơ không có / là hồ sơ của chính lễ tân
     */
    public ServiceResult<CounterCustomerRow> getCustomer(int accountId, int customerId) {
        try (Connection connection = DBContext.getConnection()) {
            int[] receptionist = ptSaleDAO.findReceptionist(connection, accountId);
            if (receptionist == null) {
                return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
            }

            // SRS 7.2.1 ghi chú 1: phải có ca đang mở thì mới bán (doanh thu tính vào ca đó).
            Shift currentShift = shiftDAO.findCurrentOrActiveShift(DateTimeUtil.today());
            boolean noShift = currentShift == null;
            boolean shiftClosed = !noShift && !currentShift.isOpen();
            if (noShift || shiftClosed) {
                return ServiceResult.fail("Chưa mở ca làm việc. Vui lòng mở ca ở màn Ca làm việc trước khi bán gói.");
            }

            LocalDate today = DateTimeUtil.today();
            CounterCustomerRow row = counterSaleDAO.findCustomer(connection, customerId, today, receptionist[1]);
            if (row == null) {
                return ServiceResult.fail(String.format(Messages.MSG08_NOT_FOUND, "khách hàng"));
            }

            // CBR-115: lễ tân không được bán cho hồ sơ của chính mình (chặn cả khi gõ thẳng ?customer=… lên URL).
            if (row.isOwnProfile()) {
                return ServiceResult.fail(Messages.MSG76_STAFF_OWN_CUSTOMER_PROFILE);
            }
            return ServiceResult.ok(row);
        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /**
     * TẠO NHANH HỒ SƠ KHÁCH HÀNG TẠI QUẦY (Step 2 - Counter Sales).
     *
     * Kiểm tra số điện thoại chống trùng 1-1 theo DBR-22.
     * Tạo Person và Customer trong 1 Transaction, sinh mã KH-nnnn, ghi AuditLog.
     *
     * @param accountId mã tài khoản lễ tân từ session
     * @param input thông tin tạo khách (FullName, Phone, DOB, CustomerType, Guardian...)
     * @return CounterCustomerRow thông tin khách vừa tạo để chọn ngay sang Bước 3
     */
    public ServiceResult<CounterCustomerRow> quickCreateCustomer(int accountId, CounterCustomerCreateInput input) {
        // B1: kiểm tra input hợp lệ
        if (input == null) {
            return ServiceResult.fail("Dữ liệu khách hàng không hợp lệ.");
        }

        String fullName = input.getFullName() == null ? "" : input.getFullName().trim();
        if (!ValidationUtil.isValidFullName(fullName)) {
            return ServiceResult.failField("fullName", "Họ và tên từ 2 đến 50 ký tự, chỉ chứa chữ cái và khoảng trắng.");
        }

        LocalDate today = DateTimeUtil.today();
        LocalDate dob = input.getDateOfBirth();
        if (dob == null || !ValidationUtil.isValidDateOfBirth(dob, today)) {
            return ServiceResult.failField("dateOfBirth", "Ngày sinh không hợp lệ hoặc vượt quá 100 tuổi.");
        }

        String rawType = input.getCustomerType();
        String customerType = "Dependent".equalsIgnoreCase(rawType) ? "Dependent" : "Self";
        boolean isDependent = "Dependent".equals(customerType);

        String phone = input.getPhone() == null ? "" : input.getPhone().trim();
        String guardianName = input.getGuardianName() == null ? "" : input.getGuardianName().trim();
        String guardianRel = input.getGuardianRelationship() == null ? "" : input.getGuardianRelationship().trim();
        String guardianPhone = input.getGuardianPhone() == null ? "" : input.getGuardianPhone().trim();
        String email = input.getEmail() == null ? "" : input.getEmail().trim();

        if (isDependent) {
            // Dependent: Phone cá nhân là NULL, bắt buộc thông tin giám hộ
            phone = null;
            if (guardianName.length() < 2 || guardianName.length() > 50) {
                return ServiceResult.failField("guardianName", "Tên người giám hộ từ 2 đến 50 ký tự.");
            }
            if (guardianRel.isEmpty() || guardianRel.length() > 30) {
                return ServiceResult.failField("guardianRelationship", "Mối quan hệ người giám hộ không được để trống (tối đa 30 ký tự).");
            }
            if (!ValidationUtil.isValidPhone(guardianPhone)) {
                return ServiceResult.failField("guardianPhone", "Số điện thoại người giám hộ phải gồm 10 chữ số bắt đầu bằng 0.");
            }
        } else {
            // Self: Bắt buộc Phone cá nhân (10 số, bắt đầu bằng 0)
            if (!ValidationUtil.isValidPhone(phone)) {
                return ServiceResult.failField("phone", "Số điện thoại phải gồm 10 chữ số bắt đầu bằng 0.");
            }
            guardianName = null;
            guardianRel = null;
            guardianPhone = null;
            // Hồ sơ Self tại quầy luôn có tài khoản (thay CBR-31) → email bắt buộc để gửi mật khẩu tạm (DBR-23).
            if (!ValidationUtil.isValidEmail(email)) {
                return ServiceResult.failField("email", "Nhập email đúng định dạng (VD: ten@gmail.com) để gửi mật khẩu tạm cho khách.");
            }
        }
        if (isDependent) {
            email = null;   // Dependent không có tài khoản (CBR-21), không lưu email ở form này
        }

        try (Connection connection = DBContext.getConnection()) {
            // B2: kiểm tra quyền lễ tân
            int[] receptionist = ptSaleDAO.findReceptionist(connection, accountId);
            if (receptionist == null) {
                return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
            }

            // B3: kiểm tra trùng số điện thoại (chỉ áp dụng với Self vì Phone cá nhân không được trùng)
            if (!isDependent) {
                Person existingPerson = personDAO.findByPhone(phone);
                if (existingPerson != null) {
                    return ServiceResult.failField("phone", "Số điện thoại " + phone + " đã tồn tại trong hệ thống với khách: " + existingPerson.getFullName() + ".");
                }
                // DBR-23: email không trùng Person đang hoạt động; chỉ báo "đã dùng", không lộ của ai.
                if (personDAO.findByEmail(email) != null) {
                    return ServiceResult.failField("email", "Email này đã được dùng cho một tài khoản khác.");
                }
            }

            // B4: thực hiện transaction tạo Person + Customer
            connection.setAutoCommit(false);
            try {
                // B4.1: tạo Person
                Person person = new Person();
                person.setFullName(fullName);
                person.setDateOfBirth(dob);
                person.setPhone(phone);
                person.setEmail(email);
                person.setReleased(false);
                int personId = personDAO.insert(connection, person);

                // B4.2: sinh mã khách hàng KH-nnnn
                String customerCode = customerDAO.generateNextCustomerCode(connection);

                // B4.3: tạo Customer
                Customer customer = new Customer();
                customer.setPersonId(personId);
                customer.setCustomerCode(customerCode);
                customer.setCustomerType(customerType);
                customer.setGuardianName(guardianName);
                customer.setGuardianRelationship(guardianRel);
                customer.setGuardianPhone(guardianPhone);
                customer.setStatus("New");

                int customerId = customerDAO.insert(connection, customer);

                // B4.4: ghi AuditLog
                auditLogService.logInTransaction(
                        connection,
                        accountId,
                        Role.RECEPTIONIST,
                        "Tạo nhanh hồ sơ khách hàng tại quầy",
                        "Customers",
                        customerId,
                        "CustomerCode",
                        null,
                        customerCode,
                        fullName
                );

                // B4.5: hồ sơ Self → tạo tài khoản + gửi mật khẩu tạm qua email (thay CBR-31, 31/10/2026).
                //       Gửi TRƯỚC commit: email lỗi → rollback cả hồ sơ, lễ tân thử lại (không có hồ sơ "mồ côi" thiếu tài khoản).
                String doneMessage = "Đã tạo hồ sơ khách hàng " + customerCode + ".";
                if (!isDependent) {
                    int newAccountId = counterAccountService.createWithTempPassword(connection, personId, email, fullName, customerCode);
                    auditLogService.logInTransaction(connection, accountId, Role.RECEPTIONIST,
                            "Tạo tài khoản tại quầy, gửi mật khẩu tạm qua email", "Accounts", newAccountId,
                            "PersonId", null, String.valueOf(personId), fullName);
                    doneMessage = "Đã tạo hồ sơ " + customerCode + " và tài khoản. Mật khẩu tạm đã gửi tới "
                            + CounterAccountService.maskEmail(email) + ".";
                }

                connection.commit();

                // B5: đọc lại dòng CounterCustomerRow vừa tạo
                CounterCustomerRow row = counterSaleDAO.findCustomer(connection, customerId, today, receptionist[1]);
                return ServiceResult.ok(row, doneMessage);
            } catch (EmailSendException e) {
                connection.rollback();
                return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /**
     * KIỂM TRA MÃ GIẢM GIÁ TẠI QUẦY (AJAX):
     * Lễ tân nhập mã ưu đãi của khách, hệ thống tính toán số tiền giảm và giá sau giảm.
     *
     * @param accountId mã tài khoản lễ tân từ session
     * @param packageId mã gói tập đang chọn
     * @param customerId mã khách hàng thụ hưởng
     * @param rawCode mã voucher do khách đọc
     * @return CounterVoucherResult kết quả kiểm tra và số tiền cập nhật, hoặc thông báo lỗi
     */
    public ServiceResult<CounterVoucherResult> checkVoucher(int accountId, int packageId, int customerId, String rawCode) {
        // B1: chuẩn hóa mã voucher
        String code = rawCode == null ? "" : rawCode.trim().toUpperCase(Locale.ROOT);
        if (code.isEmpty()) {
            return ServiceResult.failField("voucherCode", "Vui lòng nhập mã giảm giá.");
        }
        if (!ValidationUtil.isValidVoucherCode(code)) {
            return ServiceResult.failField("voucherCode", "Mã voucher phải có 4-20 ký tự chữ A-Z hoặc số.");
        }

        try (Connection connection = DBContext.getConnection()) {
            // B2: kiểm tra quyền lễ tân
            int[] receptionist = ptSaleDAO.findReceptionist(connection, accountId);
            if (receptionist == null) {
                return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
            }

            // B3: kiểm tra gói tập và khách hàng
            Package pkg = counterSaleDAO.findActivePackage(connection, packageId);
            if (pkg == null || !pkg.isActive()) {
                return ServiceResult.fail(String.format(Messages.MSG08_NOT_FOUND, "gói tập"));
            }

            LocalDate today = DateTimeUtil.today();
            CounterCustomerRow customer = counterSaleDAO.findCustomer(connection, customerId, today, receptionist[1]);
            if (customer == null) {
                return ServiceResult.fail(String.format(Messages.MSG08_NOT_FOUND, "khách hàng"));
            }

            // B4: tra cứu voucher
            List<Voucher> vouchers = voucherDAO.findForChange(connection, code, null);
            Voucher selected = null;
            for (Voucher v : vouchers) {
                if (code.equals(v.getVoucherCode())) {
                    selected = v;
                    break;
                }
            }

            if (selected == null) {
                return ServiceResult.failField("voucherCode", "Mã giảm giá không tồn tại.");
            }
            if (!selected.isActive()) {
                return ServiceResult.failField("voucherCode", "Mã giảm giá đã ngừng áp dụng.");
            }
            if (!selected.isValidOnDate(today)) {
                return ServiceResult.failField("voucherCode", "Mã giảm giá chưa đến ngày bắt đầu hoặc đã hết hạn.");
            }
            if (!selected.hasRemainingUsage()) {
                return ServiceResult.failField("voucherCode", "Mã giảm giá đã hết lượt sử dụng.");
            }

            boolean isMembership = pkg.getPackageType() == Package.Type.MEMBERSHIP;
            boolean isDayPass = pkg.getPackageType() == Package.Type.DAY_PASS;
            if (!isMembership && !isDayPass) {
                return ServiceResult.failField("voucherCode", "Mã chỉ áp dụng cho gói tập hoặc vé ngày.");
            }
            if (selected.getApplicableScope() == Voucher.ApplicableScope.MEMBERSHIP_ONLY && !isMembership) {
                return ServiceResult.failField("voucherCode", "Mã giảm giá chỉ áp dụng cho gói hội viên.");
            }

            long unitPrice = pkg.getPrice();
            if (unitPrice < selected.getMinOrderValue()) {
                return ServiceResult.failField("voucherCode", "Đơn hàng phải từ "
                        + MoneyUtil.formatMoney(selected.getMinOrderValue()) + " mới được áp dụng mã này.");
            }

            if (selected.getMaxUsagePerCustomer() != null) {
                int usedByCustomer = voucherDAO.countCustomerUses(connection, selected.getVoucherId(), customerId, 0);
                if (usedByCustomer >= selected.getMaxUsagePerCustomer()) {
                    return ServiceResult.failField("voucherCode", "Khách hàng đã dùng hết số lượt của mã này.");
                }
            }

            // B5: tính toán số tiền giảm, giá thanh toán và thuế VAT
            long discountAmount = selected.calculateDiscountAmount(unitPrice);
            long finalAmount = Math.max(0, unitPrice - discountAmount);
            int vatRate = configService.getInt(SystemConfigService.VAT_PERCENT);
            long vatAmount = MoneyUtil.calculateVatAmount(finalAmount, vatRate);
            long preTaxAmount = finalAmount - vatAmount;

            String successMsg = "Áp dụng thành công: giảm " + MoneyUtil.formatMoney(discountAmount);
            if (selected.getDiscountType() == Voucher.DiscountType.PERCENT) {
                successMsg += " (" + selected.getDiscountValue() + "%)";
            }

            CounterVoucherResult result = new CounterVoucherResult(
                    true,
                    selected.getVoucherId(),
                    selected.getVoucherCode(),
                    discountAmount,
                    MoneyUtil.formatMoney(discountAmount),
                    preTaxAmount,
                    MoneyUtil.formatMoney(preTaxAmount),
                    vatAmount,
                    MoneyUtil.formatMoney(vatAmount),
                    finalAmount,
                    MoneyUtil.formatMoney(finalAmount),
                    successMsg
            );

            return ServiceResult.ok(result, successMsg);
        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /**
     * THU TIỀN TẠI QUẦY: Lễ tân kiểm tra thực tế (tiền mặt / chuyển khoản) rồi bấm xác nhận.
     * Thực hiện trong 1 transaction ACID:
     *   B1: kiểm tra đầu vào và quyền Lễ tân đang làm việc.
     *   B2: kiểm tra ca trực đang mở (SRS 7.2.1 ghi chú 1).
     *   B3: kiểm tra hồ sơ khách hàng và gói dịch vụ còn bán.
     *   B4: tính toán ngày bắt đầu và ngày hết hạn theo DBR-52.
     *   B5: tính toán số tiền và thuế suất VAT P-10 (gồm kiểm tra & giữ lượt Voucher).
     *   B6: mở transaction ghi nhận Orders, Transactions, Subscriptions, AuditLogs.
     *   B7: đóng gói CounterSaleReceipt trả về.
     *
     * @param accountId mã tài khoản từ session của lễ tân đang thao tác
     * @param receptionistName tên hiển thị của lễ tân đang trực ca
     * @param input thông tin gói, khách, ngày bắt đầu, hình thức thu tiền và mã voucher từ form POST
     * @param now thời điểm ghi nhận đơn hàng và giao dịch
     * @return CounterSaleReceipt biên lai thu tiền chi tiết, hoặc lỗi nghiệp vụ
     */
    public ServiceResult<CounterSaleReceipt> checkout(int accountId, String receptionistName,
            CounterCheckoutInput input, LocalDateTime now) {
        // B1: kiểm tra đầu vào cơ bản và hình thức thu tiền.
        boolean isNullInput = input == null;
        if (isNullInput) {
            return ServiceResult.fail("Thông tin đơn hàng không hợp lệ.");
        }
        boolean isInvalidPackage = input.getPackageId() <= 0;
        boolean isInvalidCustomer = input.getCustomerId() <= 0;
        if (isInvalidPackage || isInvalidCustomer) {
            return ServiceResult.fail("Thông tin đơn hàng không hợp lệ.");
        }

        String method = input.getPaymentMethod();
        boolean isCash = "Cash".equals(method);
        boolean isTransfer = "Transfer".equals(method);
        boolean isValidMethod = isCash || isTransfer;
        if (!isValidMethod) {
            return ServiceResult.fail("Vui lòng chọn hình thức thu tiền: Tiền mặt hoặc Chuyển khoản.");
        }

        try (Connection connection = DBContext.getConnection()) {
            // B1.1: xác minh tài khoản thuộc Lễ tân đang làm việc.
            int[] receptionist = ptSaleDAO.findReceptionist(connection, accountId);
            if (receptionist == null) {
                return ServiceResult.fail(Messages.MSG07_NO_PERMISSION);
            }

            // B2: kiểm tra ca trực đang mở của ngày hôm nay (doanh thu tính vào ca đó).
            Shift currentShift = shiftDAO.findCurrentOrActiveShift(DateTimeUtil.today());
            boolean noShift = currentShift == null;
            boolean shiftClosed = !noShift && !currentShift.isOpen();
            if (noShift || shiftClosed) {
                return ServiceResult.fail("Chưa mở ca làm việc. Vui lòng mở ca trước khi thu tiền.");
            }
            int shiftId = currentShift.getShiftId();

            // B3: kiểm tra hồ sơ khách hàng và gói dịch vụ.
            LocalDate today = DateTimeUtil.today();
            CounterCustomerRow customer = counterSaleDAO.findCustomer(connection, input.getCustomerId(), today, receptionist[1]);
            if (customer == null) {
                return ServiceResult.fail(String.format(Messages.MSG08_NOT_FOUND, "khách hàng"));
            }
            if (customer.isOwnProfile()) {
                return ServiceResult.fail(Messages.MSG76_STAFF_OWN_CUSTOMER_PROFILE);
            }

            Package pkg = counterSaleDAO.findActivePackage(connection, input.getPackageId());
            boolean noPackage = pkg == null;
            boolean packageInactive = !noPackage && !pkg.isActive();
            if (noPackage || packageInactive) {
                return ServiceResult.fail(String.format(Messages.MSG08_NOT_FOUND, "gói tập"));
            }
            if (pkg.getPackageType() == Package.Type.SINGLE_SESSION) {
                return ServiceResult.fail("Chưa có màn bán buổi PT lẻ tại quầy.");
            }

            // B4: tính toán ngày bắt đầu và ngày hết hạn theo DBR-52.
            boolean hasPackageEnd = customer.getCurrentPackageEnd() != null;
            boolean isPackageNotExpired = hasPackageEnd && !customer.getCurrentPackageEnd().isBefore(today);
            boolean isChained = isPackageNotExpired;

            LocalDate startDate;
            if (isChained) {
                // Khách có gói còn hạn: bắt buộc tự động nối tiếp sau ngày hết hạn gói cũ.
                startDate = customer.getCurrentPackageEnd().plusDays(1);
            } else {
                // Khách mới hoặc hết hạn: mặc định hôm nay, hoặc cho phép hẹn trong 7 ngày tới.
                if (input.getStartDate() != null) {
                    LocalDate maxAllowedStartDate = today.plusDays(7);
                    boolean isBeforeToday = input.getStartDate().isBefore(today);
                    boolean isAfterMaxDate = input.getStartDate().isAfter(maxAllowedStartDate);
                    boolean isOutOfRange = isBeforeToday || isAfterMaxDate;
                    if (isOutOfRange) {
                        return ServiceResult.fail("Ngày bắt đầu chỉ được chọn trong vòng 7 ngày tới (từ "
                                + DateTimeUtil.formatDate(today) + " đến " + DateTimeUtil.formatDate(maxAllowedStartDate) + ").");
                    }
                    startDate = input.getStartDate();
                } else {
                    startDate = today;
                }
            }

            LocalDate endDate;
            if (pkg.getDurationMonths() > 0) {
                endDate = DateTimeUtil.calculateEndDateByMonths(startDate, pkg.getDurationMonths());
            } else {
                endDate = startDate; // DayPass hết hạn cùng ngày
            }

            // B5: tính số tiền, giảm giá và thuế VAT (P-10).
            long unitPrice = pkg.getPrice();
            long discountAmount = 0;
            Integer voucherId = null;
            String appliedVoucherCode = null;

            String rawVoucher = input.getVoucherCode();
            boolean hasVoucherInput = rawVoucher != null && !rawVoucher.trim().isEmpty();
            if (hasVoucherInput) {
                String voucherCode = rawVoucher.trim().toUpperCase(Locale.ROOT);
                if (!ValidationUtil.isValidVoucherCode(voucherCode)) {
                    return ServiceResult.fail("Mã voucher không đúng định dạng (4-20 ký tự chữ và số).");
                }
                List<Voucher> vouchers = voucherDAO.findForChange(connection, voucherCode, null);
                Voucher selectedVoucher = null;
                for (Voucher v : vouchers) {
                    if (voucherCode.equals(v.getVoucherCode())) {
                        selectedVoucher = v;
                        break;
                    }
                }
                if (selectedVoucher == null) {
                    return ServiceResult.fail("Mã voucher không tồn tại.");
                }
                if (!selectedVoucher.isActive()) {
                    return ServiceResult.fail("Mã voucher đã ngừng áp dụng.");
                }
                if (!selectedVoucher.isValidOnDate(today)) {
                    return ServiceResult.fail("Mã voucher chưa đến ngày bắt đầu hoặc đã hết hạn.");
                }
                if (!selectedVoucher.hasRemainingUsage()) {
                    return ServiceResult.fail("Mã voucher đã hết lượt sử dụng.");
                }
                boolean isMembership = pkg.getPackageType() == Package.Type.MEMBERSHIP;
                boolean isDayPass = pkg.getPackageType() == Package.Type.DAY_PASS;
                if (!isMembership && !isDayPass) {
                    return ServiceResult.fail("Mã chỉ áp dụng cho gói tập hoặc vé ngày.");
                }
                if (selectedVoucher.getApplicableScope() == Voucher.ApplicableScope.MEMBERSHIP_ONLY && !isMembership) {
                    return ServiceResult.fail("Mã giảm giá chỉ áp dụng cho gói hội viên.");
                }
                if (unitPrice < selectedVoucher.getMinOrderValue()) {
                    return ServiceResult.fail("Đơn hàng chưa đạt giá trị tối thiểu "
                            + MoneyUtil.formatMoney(selectedVoucher.getMinOrderValue()) + " để dùng voucher này.");
                }
                if (selectedVoucher.getMaxUsagePerCustomer() != null) {
                    int usedCount = voucherDAO.countCustomerUses(connection, selectedVoucher.getVoucherId(), customer.getCustomerId(), 0);
                    if (usedCount >= selectedVoucher.getMaxUsagePerCustomer()) {
                        return ServiceResult.fail("Khách hàng đã dùng hết số lượt của mã giảm giá này.");
                    }
                }

                voucherId = selectedVoucher.getVoucherId();
                appliedVoucherCode = selectedVoucher.getVoucherCode();
                discountAmount = selectedVoucher.calculateDiscountAmount(unitPrice);
            }

            long finalAmount = Math.max(0, unitPrice - discountAmount);
            int vatRate = configService.getInt(SystemConfigService.VAT_PERCENT);
            long vatAmount = MoneyUtil.calculateVatAmount(finalAmount, vatRate);

            // B6: mở transaction ghi nhận vào database.
            connection.setAutoCommit(false);
            try {
                // B6.0: nếu có voucher, khóa và giữ lượt sử dụng
                if (voucherId != null) {
                    int reserved = voucherDAO.reserveUse(connection, voucherId);
                    if (reserved != 1) {
                        connection.rollback();
                        return ServiceResult.fail("Mã giảm giá vừa hết lượt sử dụng. Vui lòng thử lại.");
                    }
                }

                // B6.1: sinh mã đơn tiếp theo dạng DH-yyyy-nnnn và chèn Orders mới.
                String orderCode = orderDAO.generateNextOrderCode(connection);

                Order order = new Order(0);
                order.setOrderCode(orderCode);
                order.setHolderCustomerId(customer.getCustomerId());
                order.setPackageId(pkg.getPackageId());
                order.setVoucherId(voucherId);
                order.setCreatedByEmployeeId(receptionist[0]);
                order.setShiftId(shiftId);
                order.setChannel(Order.Channel.COUNTER);
                order.setPackageNameSnapshot(pkg.getPackageName());

                String descriptionSnapshot = "";
                if (pkg.getDescription() != null) {
                    descriptionSnapshot = pkg.getDescription();
                }
                order.setPackageDescriptionSnapshot(descriptionSnapshot);

                String benefitsSnapshot = "";
                if (pkg.getBenefits() != null) {
                    benefitsSnapshot = pkg.getBenefits();
                }
                order.setPackageBenefitsSnapshot(benefitsSnapshot);

                order.setUnitPriceSnapshot(unitPrice);
                order.setDiscountAmount(discountAmount);
                order.setVatRateSnapshot(vatRate);
                order.setVatAmount(vatAmount);
                order.setStatus(Order.Status.PENDING);
                order.setCreatedAt(now);

                int orderId = orderDAO.insert(connection, order);

                // B6.2: cập nhật đơn sang Paid với thời điểm thu now.
                orderDAO.markPaid(connection, orderId, now);

                // B6.3: ghi nhận giao dịch Transactions (người nhận tiền là lễ tân đang trực ca).
                ptSaleDAO.insertTransaction(connection, orderId, finalAmount, method, null, receptionist[0], now);

                // B6.4: cấp gói Subscriptions trạng thái Active cho hội viên.
                Subscription subscription = new Subscription(0);
                subscription.setOrderId(orderId);
                subscription.setCustomerId(customer.getCustomerId());

                Subscription.Type subType = Subscription.Type.MEMBERSHIP;
                if (pkg.getPackageType() == Package.Type.DAY_PASS) {
                    subType = Subscription.Type.DAY_PASS;
                }
                subscription.setSubscriptionType(subType);
                subscription.setStartDate(startDate);
                subscription.setEndDate(endDate);
                if (subType == Subscription.Type.MEMBERSHIP) {
                    subscription.setMaxFreezesSnapshot(configService.getInt(SystemConfigService.MAX_FREEZE_COUNT));
                }
                subscription.setStatus(Subscription.Status.ACTIVE);
                subscriptionDAO.insertOnline(connection, subscription);

                // B6.5: ghi nhật ký AuditLogs kiểm soát ca trực.
                String auditMethodName = "chuyển khoản";
                if (isCash) {
                    auditMethodName = "tiền mặt";
                }
                String auditNote = "Thu " + auditMethodName + " " + finalAmount;
                if (appliedVoucherCode != null) {
                    auditNote += " (Voucher: " + appliedVoucherCode + " -" + discountAmount + ")";
                }
                auditLogService.logInTransaction(connection, accountId, Role.RECEPTIONIST,
                        "Bán gói tại quầy", "Orders", orderId, "Status", null,
                        Order.Status.PAID.getDbValue(), auditNote);

                connection.commit();

                // B7: đóng gói biên lai kết quả trả về.
                String methodText = "Chuyển khoản (POS / QR)";
                if (isCash) {
                    methodText = "Tiền mặt";
                }

                String durationText = pkg.getDurationMonths() + " tháng";
                if (pkg.getPackageType() == Package.Type.DAY_PASS) {
                    durationText = "1 ngày";
                }

                String finalReceptionistName = "Lễ tân quầy";
                boolean hasCustomName = receptionistName != null && !receptionistName.isBlank();
                if (hasCustomName) {
                    finalReceptionistName = receptionistName;
                }

                CounterSaleReceipt receipt = new CounterSaleReceipt(
                        orderId, orderCode, customer.getFullName(), customer.getCustomerCode(),
                        customer.getPhoneText(), pkg.getPackageName(), durationText,
                        DateTimeUtil.formatDate(startDate), DateTimeUtil.formatDate(endDate),
                        finalAmount, method, methodText,
                        DateTimeUtil.formatDateTime(now),
                        finalReceptionistName,
                        discountAmount,
                        appliedVoucherCode
                );
                return ServiceResult.ok(receipt, "Đã thu " + MoneyUtil.formatMoney(finalAmount) + " · Đơn " + orderCode + " hoàn tất!");
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /**
     * Giá của gói tập 1 tháng rẻ nhất đang bán, làm mốc để tính % tiết kiệm.
     * @param packages danh sách tất cả các gói đang bán do DAO đọc
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
     * @return thẻ gói hoàn chỉnh
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
                price, vatRate, vatAmount, preTaxAmount, durationText, months, monthlyPrice, savingPercent,
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
     * @param packageType loại gói dịch vụ
     * @return danh sách các dòng quy định
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
