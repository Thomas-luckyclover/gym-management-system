package com.gms.dto;

/**
 * MỘT LỰA CHỌN trong ô chọn của màn Bán gói PT: 1 gói hoặc 1 PT.
 *
 *   AI TẠO?   SERVICE, bằng constructor: PtSaleService.loadPackage (từ PtSaleDAO.findPtPackages → PackageRow)
 *             và PtSaleService.loadPt (từ PtSaleDAO.findWorkingPts → PtRow). DAO không tạo trực tiếp object này.
 *   AI IN?    _pt-sale-step1.jspf: các ô chọn Gói và PT.
 *
 *   value  mã gửi lên server (mã gói "9", mã PT "5") → value của radio              KHÔNG in chữ
 *   label  chữ chính ("Gói PT 2 Tháng", "Đinh Công Sơn")                             CÓ
 *   note   chữ phụ ("16.500.000đ · 2 tháng · 1 người"; với PT là chữ tắt "ĐS")       CÓ
 */
public class PtSaleOption {

    private final String value;
    private final String label;
    private final String note;

    public PtSaleOption(String value, String label, String note) {
        this.value = value;
        this.label = label;
        this.note = note;
    }

    public String getValue() { return value; }

    public String getLabel() { return label; }

    public String getNote() { return note; }
}
