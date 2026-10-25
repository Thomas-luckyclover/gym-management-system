package com.gms.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * MỘT HÀNG của bảng "Lịch tập hằng tuần": 1 giờ bắt đầu × 5 cột T2–T6.
 *
 *   Ai tạo?   PtSaleService.buildGrid — giờ = các giờ PT đã công bố khung trống trong 14 ngày đầu kỳ.
 *   Mỗi ô (Cell) cho biết: trong cả kỳ gói, thứ đó lúc giờ đó dùng được bao nhiêu tuần (VD 8/9).
 *   Ai in?    pt-sales.jsp: ô là radio (name = t1..t5 = Thứ 2..Thứ 6); hàng có nút "Cả tuần".
 */
public class PtSaleGridRow {

    /** 1 ô: thứ (1 = Thứ 2 … 5 = Thứ 6), số tuần dùng được / tổng số tuần, ô này đang được chọn không. */
    public static class Cell {
        private final int weekday;
        private final int usable;
        private final int total;
        private final boolean selected;

        public Cell(int weekday, int usable, int total, boolean selected) {
            this.weekday = weekday;
            this.usable = usable;
            this.total = total;
            this.selected = selected;
        }

        public int getWeekday() { return weekday; }
        public int getUsable() { return usable; }
        public int getTotal() { return total; }
        public boolean isSelected() { return selected; }

        /** full = mọi tuần đều được · most = ≥ 75% · some = có nhưng ít · none = không tuần nào (khóa ô). */
        public String getLevel() {
            if (usable == 0) {
                return "none";
            }
            if (usable == total) {
                return "full";
            }
            return usable * 4 >= total * 3 ? "most" : "some";
        }
    }

    private final String time;        // "18:00" — giá trị gửi lên (t1..t5)
    private final String label;        // "18:00 – 19:30"
    private final List<Cell> cells = new ArrayList<>();

    public PtSaleGridRow(String time, String label) {
        this.time = time;
        this.label = label;
    }

    public String getTime() { return time; }
    public String getLabel() { return label; }
    public List<Cell> getCells() { return cells; }

    /** Cả 5 ô hàng này đều dùng được ít nhất 1 tuần → hiện nút "Cả tuần". */
    public boolean isWholeWeekPossible() {
        for (Cell cell : cells) {
            if (cell.getUsable() == 0) {
                return false;
            }
        }
        return !cells.isEmpty();
    }
}
