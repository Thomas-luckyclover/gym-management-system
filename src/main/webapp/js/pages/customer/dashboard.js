// dashboard.jsp: nút data-schedule-day chọn nội dung .customer-day-sessions đã được server dựng sẵn.
document.addEventListener('DOMContentLoaded', function () {
    const dayButtons = document.querySelectorAll('[data-schedule-day]');
    for (const dayButton of dayButtons) {
        dayButton.addEventListener('click', function () {
            selectScheduleDay(dayButton);
        });
    }
});

// Chỉ đổi ngày đang hiển thị; không sửa lịch hoặc gọi API.
function selectScheduleDay(selectedButton) {
    const dayButtons = document.querySelectorAll('[data-schedule-day]');
    for (const dayButton of dayButtons) {
        const isSelected = dayButton === selectedButton;
        dayButton.classList.toggle('is-active', isSelected);
        dayButton.setAttribute('aria-pressed', String(isSelected));
        const panelId = dayButton.getAttribute('aria-controls');
        const dayPanel = document.getElementById(panelId);
        if (dayPanel) {
            dayPanel.hidden = !isSelected;
        }
    }
}
