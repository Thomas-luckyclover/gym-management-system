package com.gms.dto;

import java.util.List;

/**
 * TOÀN BỘ DỮ LIỆU màn "Chọn gói cho khách" (/receptionist/sales, chưa chọn gói).
 *
 * Ai tạo: CounterSaleService.getPackageView, gom các CounterPackageCard vào 3 tab.
 * Ai dùng: counter-sale-packages.jsp, mỗi danh sách là một tab.
 */
public class CounterPackageView {

    // Tab "Gói tập": gói tháng (ngắn đến dài) rồi vé ngày ở cuối.
    private final List<CounterPackageCard> gymCards;

    // Tab "Gói PT": PT 1 người rồi PT đôi.
    private final List<CounterPackageCard> ptCards;

    // Tab "Buổi PT lẻ": gói SingleSession (hiện mờ ở đợt 1).
    private final List<CounterPackageCard> sessionCards;

    public CounterPackageView(List<CounterPackageCard> gymCards, List<CounterPackageCard> ptCards,
            List<CounterPackageCard> sessionCards) {
        this.gymCards = List.copyOf(gymCards);
        this.ptCards = List.copyOf(ptCards);
        this.sessionCards = List.copyOf(sessionCards);
    }

    // Thẻ của tab "Gói tập".
    public List<CounterPackageCard> getGymCards() {
        return gymCards;
    }

    // Thẻ của tab "Gói PT".
    public List<CounterPackageCard> getPtCards() {
        return ptCards;
    }

    // Thẻ của tab "Buổi PT lẻ".
    public List<CounterPackageCard> getSessionCards() {
        return sessionCards;
    }
}