package com.gms.entity;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Dòng quyền lợi "Thư viện bài tập" lấy từ cột UnlocksExerciseLibrary, không phụ thuộc chữ quản lý gõ. */
class PackageBenefitTest {

    @Test
    void unlockingPackageGetsLibraryLineAfterTypedBenefits() {
        Package pkg = new Package(2);
        pkg.setBenefits("Tập không giới hạn giờ mở cửa\r\n\r\n  Miễn phí nước uống ");
        pkg.setUnlocksExerciseLibrary(true);

        assertEquals(List.of("Tập không giới hạn giờ mở cửa", "Miễn phí nước uống", Package.EXERCISE_LIBRARY_BENEFIT),
                pkg.getBenefitLines());
    }

    @Test
    void dayPassWithoutFlagHasNoLibraryLine() {
        Package pkg = new Package(1);
        pkg.setBenefits("Vào tập 1 lượt trong ngày");

        assertEquals(List.of("Vào tập 1 lượt trong ngày"), pkg.getBenefitLines());
    }

    @Test
    void flagAloneStillShowsTheLine() {
        Package pkg = new Package(8);
        pkg.setUnlocksExerciseLibrary(true);

        assertEquals(List.of(Package.EXERCISE_LIBRARY_BENEFIT), pkg.getBenefitLines());
    }
}
