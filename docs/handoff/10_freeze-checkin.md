# 10: Bảo lưu + check-in (UC-18, UC-19)

## Bảo lưu (UC-18, BR-20→26, CBR-07, 08, 62, DBR-13, 51)
1. B1: chỉ gói tập (BR-21), chưa hết hạn (BR-23), `FreezeUsed < MaxFreezesSnapshot` (BR-24)
2. B2: bắt đầu sớm nhất ngày mai; dài tối đa P-02 tháng và không quá số ngày còn lại (DBR-13)
3. B3: liệt kê buổi PT sẽ thành Missed để báo khách (CBR-62)
4. B4: lưu `FreezeRecords` + `FreezeUsed + 1`; gói và khách → `Frozen` khi tới ngày bắt đầu (BR-20)
5. B5: buổi PT trong kỳ → `Missed MembershipFrozen`, slot `Free` (CBR-62, 67, 68)
6. B6: audit

**Kết thúc** (job hoặc Lễ tân hủy sớm — BR-25, 26): `ActualEndDate`, `EndDate += số ngày thực bảo lưu` (DBR-51), khách `Active`. Buổi đã Missed không khôi phục.

## Check-in (UC-19, BR-27→34, CBR-35→38, 58, 63, 66)
1. B1: tra theo mã KH hoặc SĐT; Dependent tra theo số giám hộ (BR-28, CBR-33)
2. B2: canUseService. Chỉ gói tập còn hạn, không bảo lưu, hoặc gói ngày hôm nay chưa dùng (BR-27, CBR-58)
3. B3: gói ≥ 1 tháng → đối chiếu ảnh; chưa có ảnh thì chụp (BR-31, 33). Gói ngày không cần ảnh (BR-30)
4. B4: ghi `CheckIns`; gói ngày → `Subscriptions.Status = Used` (BR-29)
5. B5: hiện nhắc nhãn "Không có gói tập" cho buổi PT hôm nay nếu có (CBR-66)

## Case test
- Dũng: gói ngày đã dùng → chặn.
- Hạnh: gói ngày chưa dùng.
- Hồng: đang bảo lưu → chặn.
- Gia Bảo: Dependent.
- An: 2 lượt / ngày.
