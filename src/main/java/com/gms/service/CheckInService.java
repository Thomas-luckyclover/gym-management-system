package com.gms.service;

import com.gms.constant.Messages;
import com.gms.dao.AccountDAO;
import com.gms.dao.CheckInDAO;
import com.gms.dao.CheckInDAO.CustomerLookupInfo;
import com.gms.dao.CheckInDAO.CustomerSubscriptionInfo;
import com.gms.dao.EmployeeDAO;
import com.gms.dto.CheckInLogItem;
import com.gms.dto.CheckInLookupResult;
import com.gms.dto.CheckInLookupResult.Decision;
import com.gms.dto.CustomerSearchItem;
import com.gms.dto.PageResult;
import com.gms.dto.ServiceResult;
import com.gms.entity.Account;
import com.gms.entity.CheckIn;
import com.gms.entity.Employee;
import com.gms.util.DateTimeUtil;
import com.gms.util.ValidationUtil;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Nghiệp vụ kiểm tra điều kiện và thực hiện Check-in hội viên tại quầy Lễ tân (UC-19, Business Flow BF-10 & BF-13).
 *
 * <p>Quy tắc nghiệp vụ tuân thủ triệt để:</p>
 * <ul>
 *   <li>CBR-37, CBR-38: Ảnh check-in thuộc hồ sơ hội viên do Lễ tân quản lý tại quầy, không dùng Avatar cá nhân.</li>
 *   <li>DBR-47, DBR-48, CBR-58: canUseService - chỉ gói Membership hoặc DayPass mới được quyền vào tập tự do.</li>
 *   <li>DBR-34: Trạng thái hội viên (Active, Frozen, Expired, New, Inactive).</li>
 *   <li>GBR-03: Ngày hết hạn tính trọn vẹn cả ngày đó (23:59:59).</li>
 *   <li>GBR-19: Chuỗi tìm kiếm luôn được trim để tránh khoảng trắng thừa.</li>
 *   <li>BR-27 & MSG83: Từ chối check-in nếu không có gói tập hoặc vé ngày hợp lệ hôm nay.</li>
 *   <li>CBR-36 & BR-29, MSG84: Mỗi vé ngày chỉ được check-in 1 lần duy nhất trong ngày.</li>
 * </ul>
 */
public class CheckInService {

    private final CheckInDAO checkInDAO;
    private final AccountDAO accountDAO;
    private final EmployeeDAO employeeDAO;

    public CheckInService() {
        this.checkInDAO = new CheckInDAO();
        this.accountDAO = new AccountDAO();
        this.employeeDAO = new EmployeeDAO();
    }

    public CheckInService(CheckInDAO checkInDAO, AccountDAO accountDAO, EmployeeDAO employeeDAO) {
        this.checkInDAO = checkInDAO;
        this.accountDAO = accountDAO;
        this.employeeDAO = employeeDAO;
    }

    /**
     * Tra cứu hồ sơ hội viên và đánh giá điều kiện check-in theo đúng các business
     * rules.
     *
     * @param query mã khách (KH-xxxx) hoặc số điện thoại
     * @return kết quả đánh giá điều kiện vào tập
     */
    public ServiceResult<CheckInLookupResult> lookupCustomer(String query) {
        if (ValidationUtil.isNullOrEmpty(query)) {// nếu từ khóa null hoặc rỗng
            return ServiceResult.fail(String.format(Messages.MSG09_REQUIRED, "Mã khách hoặc số điện thoại"));
        }

        try {
            CustomerLookupInfo customer = checkInDAO.findCustomerByKeyword(query.trim());
            if (customer == null) {// nếu không tìm thấy khách hàng
                CheckInLookupResult notFoundResult = CheckInLookupResult.notFound(
                        "Không tìm thấy hồ sơ. Kiểm tra lại mã khách hoặc số điện thoại. Khách chưa từng đến thì tạo hồ sơ mới.");
                return ServiceResult.ok(notFoundResult);
            }
            // CustomerLookupInfo là dữ liệu sơ thô được lấy từ database
            // CheckInLookupResult là kết quả sau khi xử lý nghiệp vụ từ dữ liệu thô kia
            // Lý do tách k cho CheckInLookupResult gọi thẳng checkinDAO hoặc các DAO khác
            // là để đảm bảo tính đóng gói và tuân thủ
            // nguyên lý đơn trách nhiệm (SRP), mỗi class chỉ làm duy nhất 1 vc mà nó giỏi
            // nhất ko ôm việc class khác
            // dto là dữ liệu đc mang sang giao diện

            CheckInLookupResult result = new CheckInLookupResult();
            //CheckinLookupResult là kết quả đã được xử lý nghiệp vụ từ dữ liệu thô CustomerLookupInfo
            result.setCustomerId(customer.customerId);
            result.setPersonId(customer.personId);
            result.setCustomerCode(customer.customerCode);
            result.setFullName(customer.fullName);
            result.setPhone(customer.phone);
            result.setAvatar(customer.avatar);
            result.setCheckInPhoto(customer.checkInPhoto);
            result.setCustomerStatus(customer.customerStatus);

            // Kiểm tra yêu cầu xoá đang chờ Quản lý duyệt (UC-17)
            boolean hasPendingDeletion = checkInDAO.hasPendingDeletionRequest(customer.customerId);
            result.setHasPendingDeletion(hasPendingDeletion);

            if (hasPendingDeletion) {
                result.setDecision(Decision.DENIED_DELETION_PENDING);
                result.setMessage("Hồ sơ đang chờ Quản lý duyệt xoá — không cho vào tập trong thời gian chờ duyệt.");
                result.setCanCheckIn(false);
                return ServiceResult.ok(result);
            }
            // Decision phân loại kết quả bao gồm title và alertType (xanh đỏ vàng)
            // Message giải thích chi tiết và hướng dẫn hành động cho lễ tân

            // Lấy danh sách gói tập của hội viên
            List<CustomerSubscriptionInfo> subs = checkInDAO.findSubscriptionsByCustomerId(customer.customerId);

            CustomerSubscriptionInfo activeGymSub = null;
            CustomerSubscriptionInfo notStartedGymSub = null;
            CustomerSubscriptionInfo frozenGymSub = null;
            CustomerSubscriptionInfo expiredGymSub = null;
            CustomerSubscriptionInfo usedDayPassSub = null;
            CustomerSubscriptionInfo ptSub = null;

            LocalDate today = LocalDate.now();

            for (CustomerSubscriptionInfo sub : subs) {
                boolean isGymAccess = "Membership".equalsIgnoreCase(sub.subscriptionType)
                        || "DayPass".equalsIgnoreCase(sub.subscriptionType);

                if (isGymAccess) {
                    if ("Active".equalsIgnoreCase(sub.status)) {
                        // Kiểm tra chưa đến ngày bắt đầu (startDate > today)
                        if (sub.startDate != null && sub.startDate.isAfter(today)) {
                            if (notStartedGymSub == null) {
                                notStartedGymSub = sub;
                            }
                        } else if (sub.endDate != null && !sub.endDate.isBefore(today)) {
                            // GBR-03: Ngày hết hạn tính trọn vẹn cả ngày (endDate >= today là còn hạn)
                            // Nếu là DayPass, kiểm tra đã dùng trong ngày chưa
                            if ("DayPass".equalsIgnoreCase(sub.subscriptionType)) {
                                boolean usedToday = checkInDAO.isDayPassUsedToday(sub.subscriptionId);
                                if (usedToday) {
                                    if (usedDayPassSub == null)
                                        usedDayPassSub = sub;
                                } else if (activeGymSub == null) {
                                    activeGymSub = sub;
                                }
                            } else if (activeGymSub == null) {
                                activeGymSub = sub;
                            }
                        } else if (expiredGymSub == null) {
                            expiredGymSub = sub;
                        }
                    } else if ("Frozen".equalsIgnoreCase(sub.status)) {
                        if (frozenGymSub == null)
                            frozenGymSub = sub;
                    } else if ("Used".equalsIgnoreCase(sub.status)) {
                        if (usedDayPassSub == null)
                            usedDayPassSub = sub;
                    } else if ("Expired".equalsIgnoreCase(sub.status)) {
                        if (expiredGymSub == null)
                            expiredGymSub = sub;
                    }
                } else {
                    // Gói PT (PTSingle, PTCouple, SingleSession)
                    if ("Active".equalsIgnoreCase(sub.status) && ptSub == null) {
                        ptSub = sub;
                    }
                }
            }

            // Ghi nhận thông tin gói PT nếu có
            if (ptSub != null) {
                result.setHasPtPackage(true);
                result.setPtPackageName(ptSub.packageName);
                result.setPtTotalSessions(ptSub.generatedSessionCount);
            }

            // Đánh giá điều kiện vào tập dựa trên các rule:
            if (activeGymSub != null) {
                result.setSubscriptionId(activeGymSub.subscriptionId);
                result.setSubscriptionType(activeGymSub.subscriptionType);
                result.setPackageName(activeGymSub.packageName);
                result.setStartDate(activeGymSub.startDate);
                result.setEndDate(activeGymSub.endDate);
                result.setSubscriptionStatus(activeGymSub.status);

                if ("DayPass".equalsIgnoreCase(activeGymSub.subscriptionType)) {
                    result.setDecision(Decision.ELIGIBLE_DAYPASS);
                    result.setMessage("Vé ngày chỉ vào được một lượt. Xác nhận là dùng hết vé.");
                    result.setCanCheckIn(true);
                } else {
                    // Gói Membership: Phải có ảnh check-in tại quầy (CBR-37, CBR-38)
                    if (ValidationUtil.isNullOrEmpty(customer.checkInPhoto)) {
                        result.setDecision(Decision.WARN_MISSING_PHOTO);
                        result.setMessage("Khách mua online, chưa có ảnh check-in. Chụp ảnh tại quầy rồi mới cho vào.");
                        result.setCanCheckIn(false);
                        result.setRequiresPhoto(true);
                    } else {
                        result.setDecision(Decision.ELIGIBLE);
                        String formattedEndDate = DateTimeUtil.formatDate(activeGymSub.endDate);
                        result.setMessage("Gói còn hạn tới " + formattedEndDate + ". Mời khách vào tập.");
                        result.setCanCheckIn(true);
                    }
                }
            } else if (frozenGymSub != null) {
                result.setSubscriptionId(frozenGymSub.subscriptionId);
                result.setSubscriptionType(frozenGymSub.subscriptionType);
                result.setPackageName(frozenGymSub.packageName);
                result.setStartDate(frozenGymSub.startDate);
                result.setEndDate(frozenGymSub.endDate);
                result.setSubscriptionStatus(frozenGymSub.status);

                LocalDate freezeEnd = checkInDAO.getPlannedFreezeEndDate(frozenGymSub.subscriptionId);
                result.setFreezeEndDate(freezeEnd);
                result.setDecision(Decision.DENIED_FROZEN);
                String freezeText = freezeEnd != null ? " tới " + DateTimeUtil.formatDate(freezeEnd) : "";
                result.setMessage("Gói đang bảo lưu" + freezeText + ". Khách phải huỷ bảo lưu trước khi vào tập.");
                result.setCanCheckIn(false);
            } else if (notStartedGymSub != null) {
                result.setSubscriptionId(notStartedGymSub.subscriptionId);
                result.setSubscriptionType(notStartedGymSub.subscriptionType);
                result.setPackageName(notStartedGymSub.packageName);
                result.setStartDate(notStartedGymSub.startDate);
                result.setEndDate(notStartedGymSub.endDate);
                result.setSubscriptionStatus(notStartedGymSub.status);

                result.setDecision(Decision.DENIED_NOT_STARTED);
                String startText = notStartedGymSub.startDate != null
                        ? "bắt đầu từ ngày " + DateTimeUtil.formatDate(notStartedGymSub.startDate)
                        : "chưa đến ngày kích hoạt";
                result.setMessage("Gói tập chưa đến ngày kích hoạt (" + startText + "). Chưa thể vào tập.");
                result.setCanCheckIn(false);
            } else if (usedDayPassSub != null) {
                result.setSubscriptionId(usedDayPassSub.subscriptionId);
                result.setSubscriptionType(usedDayPassSub.subscriptionType);
                result.setPackageName(usedDayPassSub.packageName);
                result.setDecision(Decision.DENIED_DAYPASS_USED);
                result.setMessage(
                        "Vé ngày đã dùng hôm nay. Mỗi vé chỉ vào được một lượt. Bán vé mới nếu khách muốn quay lại.");
                result.setCanCheckIn(false);
            } else if (expiredGymSub != null) {
                result.setSubscriptionId(expiredGymSub.subscriptionId);
                result.setSubscriptionType(expiredGymSub.subscriptionType);
                result.setPackageName(expiredGymSub.packageName);
                result.setStartDate(expiredGymSub.startDate);
                result.setEndDate(expiredGymSub.endDate);
                result.setSubscriptionStatus(expiredGymSub.status);

                result.setDecision(Decision.DENIED_EXPIRED);
                String expiredText = expiredGymSub.endDate != null
                        ? " " + DateTimeUtil.formatDate(expiredGymSub.endDate)
                        : "";
                result.setMessage("Gói " + expiredGymSub.packageName + " hết hạn" + expiredText
                        + ". Gia hạn hoặc bán gói mới để khách vào tập.");
                result.setCanCheckIn(false);
            } else if (ptSub != null) {
                // CBR-58: Khách chỉ có gói PT thì không được vào phòng tập tự do
                result.setDecision(Decision.DENIED_PT_ONLY);
                result.setMessage("Khách chỉ có gói PT, không có gói tập tự do.");
                result.setCanCheckIn(false);
            } else {
                result.setDecision(Decision.DENIED_NO_PACKAGE);
                result.setMessage("Hồ sơ chưa có gói tập nào.");
                result.setCanCheckIn(false);
            }

            return ServiceResult.ok(result);

        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /**
     * Xác nhận cho hội viên vào tập và ghi nhận vào bảng CheckIns (UC-19).
     *
     * @param subscriptionId    id quyền dùng dịch vụ
     * @param employeeAccountId id tài khoản lễ tân đang đăng nhập
     * @param photoVerified     cờ xác nhận đã đối chiếu ảnh
     * @return kết quả thực hiện
     */
    public ServiceResult<Void> performCheckIn(int subscriptionId, int employeeAccountId, boolean photoVerified) {
        try {
            Account account = accountDAO.findById(employeeAccountId);
            if (account == null) {
                return ServiceResult.fail(String.format(Messages.MSG08_NOT_FOUND, "tài khoản nhân viên"));
            }

            Employee employee = employeeDAO.findByPersonId(account.getPersonId());
            if (employee == null) {
                return ServiceResult.fail(String.format(Messages.MSG08_NOT_FOUND, "hồ sơ nhân viên lễ tân"));
            }

            CheckIn checkIn = new CheckIn();
            checkIn.setSubscriptionId(subscriptionId);
            checkIn.setVerifiedByEmployeeId(employee.getEmployeeId());
            checkIn.setCheckedInAt(LocalDateTime.now());
            checkIn.setPhotoVerified(photoVerified);

            checkInDAO.insertCheckIn(checkIn);

            return ServiceResult.ok(null);

        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /**
     * Cập nhật ảnh check-in chụp tại quầy cho hội viên (CBR-38).
     *
     * @param customerId id khách hàng
     * @param photoPath  đường dẫn ảnh đã lưu
     * @return kết quả thực hiện
     */
    public ServiceResult<Void> updateCheckInPhoto(int customerId, String photoPath) {
        if (customerId <= 0 || ValidationUtil.isNullOrEmpty(photoPath)) {
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }

        try {
            boolean updated = checkInDAO.updateCheckInPhoto(customerId, photoPath.trim());
            if (updated) {
                return ServiceResult.ok(null);
            }
            return ServiceResult.fail(String.format(Messages.MSG08_NOT_FOUND, "hồ sơ khách"));
        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /**
     * Tìm kiếm khách hàng theo một phần tên có phân trang (phục vụ quầy Check-in).
     *
     * @param keyword    từ khóa tìm kiếm
     * @param pageNumber số trang (bắt đầu từ 1)
     * @param pageSize   kích thước trang
     * @return đối tượng PageResult chứa danh sách khách hàng và thông tin phân
     *         trang
     */
    public PageResult<CustomerSearchItem> searchCustomersPage(String keyword, int pageNumber, int pageSize) {
        if (ValidationUtil.isNullOrEmpty(keyword)) {
            return new PageResult<>(List.of(), pageNumber, pageSize, 0);
        }
        try {
            long totalItems = checkInDAO.countCustomersByKeyword(keyword.trim()); //lấy tổng số khách hàng khớp từ khóa để tính phân trang
            if (totalItems == 0) {
                return new PageResult<>(List.of(), pageNumber, pageSize, 0);
            }
            List<CustomerSearchItem> items = checkInDAO.searchCustomersByKeyword(keyword.trim(), pageNumber, pageSize);
            return new PageResult<>(items, pageNumber, pageSize, totalItems);
        } catch (SQLException e) {
            e.printStackTrace();
            return new PageResult<>(List.of(), pageNumber, pageSize, 0);
        }
    }

    /**
     * Tìm kiếm danh sách khách hàng theo từ khóa.
     *
     * @param keyword từ khóa tìm kiếm
     * @return danh sách khách hàng khớp
     */
    public List<CustomerSearchItem> searchCustomers(String keyword) {
        if (ValidationUtil.isNullOrEmpty(keyword)) {
            return List.of();
        }
        try {
            return checkInDAO.searchCustomersByKeyword(keyword.trim());
        } catch (SQLException e) {
            e.printStackTrace();
            return List.of();
        }
    }

    /**
     * Lấy danh sách lượt vào tập trong ca hôm nay.
     *
     * @param limit số lượng bản ghi tối đa
     * @return danh sách lượt check-in
     */
    public List<CheckInLogItem> getTodayLog(int limit) {
        try {
            return checkInDAO.findTodayCheckIns(limit);
        } catch (SQLException e) {
            e.printStackTrace();
            return List.of();
        }
    }
}
