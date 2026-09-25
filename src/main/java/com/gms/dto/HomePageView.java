package com.gms.dto;

import com.gms.entity.BlogPost;
import java.util.ArrayList;
import java.util.List;

/**
 * Toàn bộ dữ liệu trang chủ, HomeService gom 1 lần → HomeServlet đặt vào request với tên "home".
 * Trong JSP đọc: ${home.gymPackages}, ${home.pts}, ${home.openingHours}...
 *
 * Mọi list mặc định là list rỗng (không bao giờ null) → JSP chỉ cần kiểm tra ${empty home.pts}.
 *
 * @author Le Cong Minh
 */
public class HomePageView {

    private List<PackageCardView> gymPackages = new ArrayList<>();
    private List<PackageCardView> ptPackages = new ArrayList<>();
    private List<PTCardView> pts = new ArrayList<>();
    private List<BlogPost> latestPosts = new ArrayList<>();
    private String openingHours;

    /** Bảng giá gói tập: Membership, DayPass. */
    public List<PackageCardView> getGymPackages() {
        return gymPackages;
    }

    public void setGymPackages(List<PackageCardView> gymPackages) {
        this.gymPackages = gymPackages;
    }

    /** Bảng giá PT: PTSingle, PTCouple, SingleSession. */
    public List<PackageCardView> getPtPackages() {
        return ptPackages;
    }

    public void setPtPackages(List<PackageCardView> ptPackages) {
        this.ptPackages = ptPackages;
    }

    /** PT đang làm. Số PT trên trang = pts.size(). */
    public List<PTCardView> getPts() {
        return pts;
    }

    public void setPts(List<PTCardView> pts) {
        this.pts = pts;
    }

    /** Số PT đang làm — JSP: ${home.ptCount} */
    public int getPtCount() {
        return pts.size();
    }

    /** Tối đa 3 bài mới nhất; rỗng → JSP ẩn cả khối. */
    public List<BlogPost> getLatestPosts() {
        return latestPosts;
    }

    public void setLatestPosts(List<BlogPost> latestPosts) {
        this.latestPosts = latestPosts;
    }

    /** Giờ mở cửa đã định dạng, VD "05:30 – 22:00". */
    public String getOpeningHours() {
        return openingHours;
    }

    public void setOpeningHours(String openingHours) {
        this.openingHours = openingHours;
    }
}
