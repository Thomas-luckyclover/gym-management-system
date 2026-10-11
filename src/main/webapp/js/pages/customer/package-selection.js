/**
 * Màn Mua gói (Customer): popup chi tiết gói, bộ lọc tự gửi, thanh kéo khoảng giá, chip bỏ lọc, mở cột lọc trên điện thoại.
 * Hook HTML: [data-dialog-target], [data-dialog-close], #packageFilterForm (form rỗng, các ô lọc trỏ tới nó bằng attribute form),
 * [data-price-range] + [data-price-min]/[data-price-max], [data-chip-field]/[data-chip-value], [data-filter-toggle].
 * Nút Xóa bộ lọc do common.js xử lý (data-clear-filters).
 */
(function () {
    'use strict';

    var FORM_ID = 'packageFilterForm';

    function getFilterForm() {
        return document.getElementById(FORM_ID);
    }

    /**
     * Bấm nút có data-dialog-target thì mở <dialog> tương ứng bằng showModal()
     * (dialog native tự có phím Escape và trả focus về nút đã mở).
     */
    function bindDetailButtons() {
        var buttons = document.querySelectorAll('[data-dialog-target]');
        buttons.forEach(function (button) {
            button.addEventListener('click', function () {
                var dialog = document.getElementById(button.getAttribute('data-dialog-target'));
                if (dialog && typeof dialog.showModal === 'function') {
                    dialog.showModal();
                }
            });
        });
    }

    /**
     * Nút X đóng popup; bấm ra vùng nền (chính là thẻ dialog, ngoài panel) cũng đóng.
     */
    function bindDialogClosing() {
        var dialogs = document.querySelectorAll('.package-dialog');
        dialogs.forEach(function (dialog) {
            var closeButton = dialog.querySelector('[data-dialog-close]');
            if (closeButton) {
                closeButton.addEventListener('click', function () {
                    dialog.close();
                });
            }
            dialog.addEventListener('click', function (event) {
                if (event.target === dialog) {
                    dialog.close();
                }
            });
        });
    }

    /**
     * Đổi ô sắp xếp hoặc chip lọc (radio loại gói, checkbox thời hạn / hình thức mua / tiết kiệm) thì gửi form luôn.
     * Khoảng giá không tự gửi vì kéo liên tục: khách bấm nút "Áp dụng".
     */
    function bindAutoSubmitChips() {
        var form = getFilterForm();
        if (!form) {
            return;
        }
        var sortSelect = document.getElementById('packageSort');
        if (sortSelect) {
            sortSelect.addEventListener('change', function () {
                form.requestSubmit();
            });
        }
        var inputs = document.querySelectorAll('.filter-chip input');
        inputs.forEach(function (input) {
            input.addEventListener('change', function () {
                form.requestSubmit();
            });
        });
    }

    /**
     * Hai thanh kéo giá và hai ô số luôn khớp nhau; thanh thấp không vượt thanh cao.
     * Thanh kéo không có name nên không gửi lên server; chỉ hai ô số gửi.
     */
    function bindPriceRange() {
        var range = document.querySelector('[data-price-range]');
        var minInput = document.querySelector('[data-price-min]');
        var maxInput = document.querySelector('[data-price-max]');
        if (!range || !minInput || !maxInput) {
            return;
        }
        var minSlider = range.querySelector('[data-range-min]');
        var maxSlider = range.querySelector('[data-range-max]');
        var fill = range.querySelector('[data-range-fill]');

        /** Tô đoạn đường ray nằm giữa hai nút kéo. */
        function updateFill() {
            if (!fill) {
                return;
            }
            var boundMin = parseInt(minSlider.min, 10);
            var boundMax = parseInt(minSlider.max, 10);
            var span = boundMax - boundMin;
            if (span <= 0) {
                return;
            }
            var lowPercent = (parseInt(minSlider.value, 10) - boundMin) * 100 / span;
            var highPercent = (parseInt(maxSlider.value, 10) - boundMin) * 100 / span;
            fill.style.left = lowPercent + '%';
            fill.style.right = (100 - highPercent) + '%';
        }

        function syncFromSliders(changed) {
            var low = parseInt(minSlider.value, 10);
            var high = parseInt(maxSlider.value, 10);
            if (low > high) {
                if (changed === minSlider) {
                    low = high;
                    minSlider.value = low;
                } else {
                    high = low;
                    maxSlider.value = high;
                }
            }
            minInput.value = low;
            maxInput.value = high;
            updateFill();
        }

        function syncFromInputs() {
            var low = parseInt(minInput.value, 10);
            var high = parseInt(maxInput.value, 10);
            if (!isNaN(low)) {
                minSlider.value = low;
            }
            if (!isNaN(high)) {
                maxSlider.value = high;
            }
            updateFill();
        }

        updateFill();
        minSlider.addEventListener('input', function () { syncFromSliders(minSlider); });
        maxSlider.addEventListener('input', function () { syncFromSliders(maxSlider); });
        minInput.addEventListener('change', syncFromInputs);
        maxInput.addEventListener('change', syncFromInputs);
    }

    /**
     * Chip "bộ lọc đang bật": bấm ✕ thì bỏ ô lọc tương ứng rồi gửi lại form.
     * field = type → chọn lại "Tất cả"; price → trả hai ô giá về đầu mút; còn lại → bỏ tick đúng value.
     */
    function bindActiveChips() {
        var form = getFilterForm();
        var chips = document.querySelectorAll('[data-chip-field]');
        if (!form) {
            return;
        }
        chips.forEach(function (chip) {
            chip.addEventListener('click', function () {
                var field = chip.getAttribute('data-chip-field');
                var value = chip.getAttribute('data-chip-value');

                if (field === 'type') {
                    var allOption = document.querySelector('input[name="type"][value="all"]');
                    if (allOption) {
                        allOption.checked = true;
                    }
                } else if (field === 'price') {
                    var minInput = document.querySelector('[data-price-min]');
                    var maxInput = document.querySelector('[data-price-max]');
                    minInput.value = minInput.min;
                    maxInput.value = maxInput.max;
                } else {
                    var selector = 'input[name="' + field + '"]';
                    if (value) {
                        selector += '[value="' + value + '"]';
                    }
                    var target = document.querySelector(selector);
                    if (target) {
                        target.checked = false;
                    }
                }
                form.requestSubmit();
            });
        });
    }

    /**
     * Màn hẹp (điện thoại) dùng placeholder ngắn trong data-placeholder-short để không bị cắt chữ;
     * màn rộng giữ placeholder đầy đủ trong HTML.
     */
    function bindSearchPlaceholder() {
        var input = document.getElementById('packageKeyword');
        if (!input || !window.matchMedia) {
            return;
        }
        var shortText = input.getAttribute('data-placeholder-short');
        var fullText = input.getAttribute('placeholder');
        if (!shortText) {
            return;
        }
        var query = window.matchMedia('(max-width: 600px)');
        function apply() {
            input.setAttribute('placeholder', query.matches ? shortText : fullText);
        }
        apply();
        query.addEventListener('change', apply);
    }

    /** Điện thoại: nút "Bộ lọc" mở / đóng cột lọc. */
    function bindFilterToggle() {
        var toggle = document.querySelector('[data-filter-toggle]');
        var panel = document.getElementById('packageFilters');
        if (!toggle || !panel) {
            return;
        }
        toggle.addEventListener('click', function () {
            var isOpen = panel.classList.toggle('is-open');
            toggle.setAttribute('aria-expanded', isOpen ? 'true' : 'false');
        });
    }

    bindDetailButtons();
    bindDialogClosing();
    bindAutoSubmitChips();
    bindPriceRange();
    bindActiveChips();
    bindFilterToggle();
    bindSearchPlaceholder();
})();
