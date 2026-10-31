if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", initNotificationBell);
} else {
    initNotificationBell();
}

function initNotificationBell() {
    ensureNotificationBellMarkup();
    setupNotificationBell();
}

function ensureNotificationBellMarkup() {
    if (document.getElementById("notificationBell")) {
        return;
    }
    // Tìm vị trí trên header (ngay trước thẻ thông tin user)
    const targetUser = document.querySelector(".tb-user, .pt-user-badge, .guest-account");
    if (!targetUser || !targetUser.parentNode) {
        return;
    }
    const bell = document.createElement("div");
    bell.className = "notification-bell";
    bell.id = "notificationBell";
    bell.innerHTML =
        '<button type="button" class="notification-bell-button" id="notificationBellButton" aria-label="Thông báo" aria-expanded="false" aria-controls="notificationPopover">' +
        '  <i class="fa-solid fa-bell" aria-hidden="true"></i>' +
        '  <span class="notification-bell-count" id="notificationBellCount" hidden></span>' +
        '</button>' +
        '<div class="notification-popover" id="notificationPopover" hidden>' +
        '  <div class="notification-popover-head" id="notificationPopoverHead">' +
        '    <div>' +
        '      <strong>Thông báo</strong>' +
        '      <small id="notificationUnreadText">Không có thông báo chưa đọc</small>' +
        '    </div>' +
        '    <button type="button" class="notification-read-all" id="notificationReadAllButton" hidden>Đánh dấu tất cả đã đọc</button>' +
        '  </div>' +
        '  <div class="notification-popover-list" id="notificationPopoverList">' +
        '    <div class="notification-state">Đang tải...</div>' +
        '  </div>' +
        '  <div class="notification-detail" id="notificationDetailView" hidden></div>' +
        '</div>';
    targetUser.parentNode.insertBefore(bell, targetUser);
}

function setupNotificationBell() {
    const root =
        document.getElementById(
            "notificationBell"
        );

    if (root === null || root.dataset.bellInitialized === "true") {
        return;
    }

    const button =
        document.getElementById(
            "notificationBellButton"
        );

    const popover =
        document.getElementById(
            "notificationPopover"
        );

    if (button === null || popover === null) {
        return;
    }

    root.dataset.bellInitialized = "true";

    const count =
        document.getElementById(
            "notificationBellCount"
        );

    const unreadText =
        document.getElementById(
            "notificationUnreadText"
        );

    const readAllButton =
        document.getElementById(
            "notificationReadAllButton"
        );

    const popoverHead =
        document.getElementById(
            "notificationPopoverHead"
        ) || popover.querySelector(".notification-popover-head");

    const list =
        document.getElementById(
            "notificationPopoverList"
        );

    let detailView =
        document.getElementById(
            "notificationDetailView"
        );

    if (detailView === null) {
        detailView = document.createElement("div");
        detailView.className = "notification-detail";
        detailView.id = "notificationDetailView";
        detailView.hidden = true;
        popover.appendChild(detailView);
    }

    let currentUnreadCount = 0;
    let currentItems = [];

    function showListView() {
        detailView.hidden = true;
        detailView.replaceChildren();
        if (popoverHead) {
            popoverHead.hidden = false;
        }
        if (list) {
            list.hidden = false;
        }
    }

    function showDetailView(item) {
        if (popoverHead) {
            popoverHead.hidden = true;
        }
        if (list) {
            list.hidden = true;
        }
        renderNotificationDetail(
            detailView,
            item,
            function () {
                showListView();
            }
        );
        detailView.hidden = false;
    }

    async function handleSelectNotification(item) {
        /*
         * Nếu thông báo chưa đọc thì cập nhật trạng thái đọc ngay
         * và gọi API đánh dấu đã đọc ở nền.
         */
        if (!item.read) {
            item.read = true;
            item.readAt = new Date().toISOString();
            currentUnreadCount = Math.max(0, currentUnreadCount - 1);

            updateUnreadUi(
                count,
                unreadText,
                readAllButton,
                currentUnreadCount
            );

            renderNotificationItems(
                list,
                currentItems,
                handleSelectNotification
            );

            postData(
                "/api/notifications",
                {
                    action: "read",
                    notificationId: item.notificationId
                }
            );
        }

        /*
         * Mọi thông báo khi bấm vào đều mở khung xem chi tiết đầy đủ
         * (người gửi, thời gian gửi, đối tượng nhận, nội dung đầy đủ,
         * và nút chuyển tới trang nghiệp vụ nếu có actionUrl).
         */
        showDetailView(item);
    }

    async function loadNotifications(
        showLoading
    ) {

        if (showLoading) {
            renderState(
                list,
                "Đang tải..."
            );
        }

        const result =
            await getJson(
                "/api/notifications"
            );

        if (!result.success) {

            if (showLoading) {
                renderState(
                    list,
                    "Không thể tải thông báo."
                );
            }

            return;
        }

        const data =
            result.data || {};

        currentItems =
            Array.isArray(data.items)
                ? data.items
                : [];

        currentUnreadCount =
            Number(
                data.unreadCount || 0
            );

        updateUnreadUi(
            count,
            unreadText,
            readAllButton,
            currentUnreadCount
        );

        renderNotificationItems(
            list,
            currentItems,
            handleSelectNotification
        );
    }

    // ===== Mở / đóng chuông =====

    button.addEventListener(
        "click",
        async function (event) {

            event.stopPropagation();

            const shouldOpen =
                popover.hidden;

            popover.hidden =
                !shouldOpen;

            button.setAttribute(
                "aria-expanded",
                String(shouldOpen)
            );

            if (shouldOpen) {
                showListView();
                await loadNotifications(
                    true
                );
            } else {
                showListView();
            }
        }
    );

    popover.addEventListener(
        "click",
        function (event) {
            event.stopPropagation();
        }
    );

    document.addEventListener(
        "click",
        function () {

            closeNotificationPopover(
                button,
                popover,
                showListView
            );
        }
    );

    document.addEventListener(
        "keydown",
        function (event) {

            if (event.key === "Escape") {

                if (!popover.hidden && !detailView.hidden) {
                    showListView();
                    return;
                }

                closeNotificationPopover(
                    button,
                    popover,
                    showListView
                );
            }
        }
    );

    // ===== Đánh dấu tất cả đã đọc =====

    readAllButton.addEventListener(
        "click",
        async function () {

            if (currentUnreadCount <= 0) {
                return;
            }

            const result =
                await postData(
                    "/api/notifications",
                    {
                        action: "readAll"
                    }
                );

            if (result.success) {
                await loadNotifications(
                    false
                );
            }
        }
    );

    /*
     * Load lần đầu:
     * chuông có badge ngay,
     * không cần người dùng click trước.
     */
    loadNotifications(false);

    /*
     * Poll nhẹ 30 giây/lần.
     * Không cần WebSocket cho BF-06.
     */
    window.setInterval(
        function () {
            loadNotifications(false);
        },
        30000
    );
}


function closeNotificationPopover(
    button,
    popover,
    onResetView
) {

    if (popover.hidden) {
        return;
    }

    popover.hidden = true;

    button.setAttribute(
        "aria-expanded",
        "false"
    );

    if (typeof onResetView === "function") {
        onResetView();
    }
}


function updateUnreadUi(
    count,
    unreadText,
    readAllButton,
    unreadCount
) {

    if (unreadCount <= 0) {

        count.hidden = true;
        count.textContent = "";

        unreadText.textContent =
            "Không có thông báo chưa đọc";

        readAllButton.hidden = true;

        return;
    }

    count.hidden = false;

    count.textContent =
        unreadCount > 99
            ? "99+"
            : String(unreadCount);

    unreadText.textContent =
        unreadCount
        + " thông báo chưa đọc";

    readAllButton.hidden = false;
}


function getNotificationTypeInfo(item) {
    const isMaintenance =
        typeof item.title === "string"
        && item.title.startsWith(
            "Bảo trì ·"
        );

    const modifierClass =
        isMaintenance
            ? "maintenance"
            : item.systemGenerated
                ? "system"
                : "";

    const iconClass =
        isMaintenance
            ? "fa-solid fa-screwdriver-wrench"
            : item.systemGenerated
                ? "fa-solid fa-circle-info"
                : "fa-solid fa-bullhorn";

    return {
        isMaintenance: isMaintenance,
        modifierClass: modifierClass,
        iconClass: iconClass
    };
}


function renderNotificationItems(
    container,
    items,
    onClick
) {

    container.replaceChildren();

    if (items.length === 0) {

        renderState(
            container,
            "Chưa có thông báo nào."
        );

        return;
    }

    for (const item of items) {

        const itemButton =
            document.createElement(
                "button"
            );

        itemButton.type = "button";

        itemButton.className =
            "notification-item";

        if (!item.read) {
            itemButton.classList.add(
                "unread"
            );
        }

        const typeInfo =
            getNotificationTypeInfo(item);

        if (typeInfo.modifierClass) {
            itemButton.classList.add(
                typeInfo.modifierClass
            );
        }

        // ===== Icon =====

        const icon =
            document.createElement(
                "span"
            );

        icon.className =
            "notification-item-icon";

        const iconElement =
            document.createElement(
                "i"
            );

        iconElement.className =
            typeInfo.iconClass;

        iconElement.setAttribute(
            "aria-hidden",
            "true"
        );

        icon.appendChild(
            iconElement
        );

        // ===== Body =====

        const body =
            document.createElement(
                "span"
            );

        body.className =
            "notification-item-body";

        const titleRow =
            document.createElement(
                "span"
            );

        titleRow.className =
            "notification-item-title-row";

        if (!item.read) {

            const dot =
                document.createElement(
                    "span"
                );

            dot.className =
                "notification-unread-dot";

            dot.setAttribute(
                "aria-hidden",
                "true"
            );

            titleRow.appendChild(dot);
        }

        const title =
            document.createElement(
                "span"
            );

        title.className =
            "notification-item-title";

        title.textContent =
            item.title
            || "Thông báo";

        titleRow.appendChild(title);

        const content =
            document.createElement(
                "span"
            );

        content.className =
            "notification-item-content";

        content.textContent =
            item.content || "";

        const time =
            document.createElement(
                "small"
            );

        time.className =
            "notification-item-time";

        time.textContent =
            formatNotificationTime(
                item.sentAt
            );

        body.append(
            titleRow,
            content,
            time
        );

        itemButton.append(
            icon,
            body
        );

        // ===== Mũi tên xem chi tiết cho MỌI thông báo =====

        const arrow =
            document.createElement(
                "i"
            );

        arrow.className =
            "fa-solid "
            + "fa-chevron-right "
            + "notification-item-arrow";

        arrow.setAttribute(
            "aria-hidden",
            "true"
        );

        itemButton.appendChild(
            arrow
        );

        itemButton.addEventListener(
            "click",
            function () {
                onClick(item);
            }
        );

        container.appendChild(
            itemButton
        );
    }
}


function renderNotificationDetail(
    container,
    item,
    onBack
) {
    container.replaceChildren();

    const typeInfo =
        getNotificationTypeInfo(item);

    // ===== 1. Thanh tiêu đề chi tiết (Nút Quay lại + Nhãn phân loại) =====
    const head =
        document.createElement("div");
    head.className =
        "notification-detail-head";

    const backButton =
        document.createElement("button");
    backButton.type = "button";
    backButton.className =
        "notification-back-button";

    const backIcon =
        document.createElement("i");
    backIcon.className =
        "fa-solid fa-arrow-left";
    backIcon.setAttribute("aria-hidden", "true");

    const backLabel =
        document.createElement("span");
    backLabel.textContent = "Quay lại";

    backButton.append(backIcon, backLabel);
    backButton.addEventListener("click", function () {
        if (typeof onBack === "function") {
            onBack();
        }
    });

    const categoryBadge =
        document.createElement("span");
    categoryBadge.className =
        "notification-detail-badge"
        + (typeInfo.modifierClass ? " " + typeInfo.modifierClass : "");
    categoryBadge.textContent =
        item.categoryLabel
        || (item.systemGenerated ? "Thông báo hệ thống" : "Thông báo nội bộ");

    head.append(backButton, categoryBadge);

    // ===== 2. Thân chi tiết (Cuộn dọc nếu nội dung dài) =====
    const body =
        document.createElement("div");
    body.className =
        "notification-detail-body";

    // Khối tiêu đề + icon
    const hero =
        document.createElement("div");
    hero.className =
        "notification-detail-hero"
        + (typeInfo.modifierClass ? " " + typeInfo.modifierClass : "");

    const iconWrap =
        document.createElement("span");
    iconWrap.className =
        "notification-item-icon";

    const iconElement =
        document.createElement("i");
    iconElement.className =
        typeInfo.iconClass;
    iconElement.setAttribute("aria-hidden", "true");
    iconWrap.appendChild(iconElement);

    const titleWrap =
        document.createElement("div");
    titleWrap.className =
        "notification-detail-title-wrap";

    const titleEl =
        document.createElement("h4");
    titleEl.className =
        "notification-detail-title";
    titleEl.textContent =
        item.title || "Thông báo";

    titleWrap.appendChild(titleEl);
    hero.append(iconWrap, titleWrap);

    // Khối thông tin người gửi, thời gian gửi, đối tượng nhận
    const metaList =
        document.createElement("dl");
    metaList.className =
        "notification-detail-meta";

    const senderText =
        (item.senderName || (item.systemGenerated ? "Hệ thống Super Gym" : "Ban quản lý"))
        + (item.senderSubline ? " (" + item.senderSubline + ")" : "");

    metaList.appendChild(
        createMetaRow(
            "fa-solid fa-user-shield",
            "Người gửi",
            senderText
        )
    );

    metaList.appendChild(
        createMetaRow(
            "fa-regular fa-clock",
            "Thời gian gửi",
            formatNotificationFullTime(item.sentAt)
        )
    );

    if (item.audienceLabel) {
        metaList.appendChild(
            createMetaRow(
                "fa-solid fa-users",
                "Đối tượng nhận",
                item.audienceLabel
            )
        );
    }

    // Khối nội dung đầy đủ
    const contentSection =
        document.createElement("div");
    contentSection.className =
        "notification-detail-content-box";

    const contentLabel =
        document.createElement("span");
    contentLabel.className =
        "notification-detail-content-label";
    contentLabel.textContent =
        "Nội dung chi tiết";

    const contentText =
        document.createElement("div");
    contentText.className =
        "notification-detail-content";
    contentText.textContent =
        item.content || "Không có nội dung chi tiết.";

    contentSection.append(
        contentLabel,
        contentText
    );

    body.append(
        hero,
        metaList,
        contentSection
    );

    container.append(head, body);

    // ===== 3. Nút điều hướng tới trang liên quan (nếu có actionUrl) =====
    if (item.actionUrl) {
        const foot =
            document.createElement("div");
        foot.className =
            "notification-detail-foot";

        const actionBtn =
            document.createElement("button");
        actionBtn.type = "button";
        actionBtn.className =
            "notification-detail-action";

        const actionText =
            document.createElement("span");
        actionText.textContent =
            item.actionLabel || "Đi tới trang liên quan";

        const actionIcon =
            document.createElement("i");
        actionIcon.className =
            "fa-solid fa-arrow-right";
        actionIcon.setAttribute("aria-hidden", "true");

        actionBtn.append(actionText, actionIcon);
        actionBtn.addEventListener("click", function () {
            window.location.href =
                buildUrl(item.actionUrl);
        });

        foot.appendChild(actionBtn);
        container.appendChild(foot);
    }
}


function createMetaRow(
    iconClass,
    label,
    value
) {
    const row =
        document.createElement("div");
    row.className =
        "notification-meta-row";

    const dt =
        document.createElement("dt");

    const icon =
        document.createElement("i");
    icon.className = iconClass;
    icon.setAttribute("aria-hidden", "true");

    const labelSpan =
        document.createElement("span");
    labelSpan.textContent = label;

    dt.append(icon, labelSpan);

    const dd =
        document.createElement("dd");
    dd.textContent = value || "—";

    row.append(dt, dd);
    return row;
}


function renderState(
    container,
    text
) {

    container.replaceChildren();

    const state =
        document.createElement(
            "div"
        );

    state.className =
        "notification-state";

    state.textContent =
        text;

    container.appendChild(
        state
    );
}


function formatNotificationTime(value) {

    if (isBlank(value)) {
        return "";
    }

    const sentAt =
        new Date(value);

    if (
        Number.isNaN(
            sentAt.getTime()
        )
    ) {

        return formatDateTime(
            value
        );
    }

    const diffSeconds =
        Math.floor(
            (Date.now()
                - sentAt.getTime())
            / 1000
        );

    if (
        diffSeconds >= 0
        && diffSeconds < 60
    ) {
        return "Vừa xong";
    }

    if (
        diffSeconds >= 60
        && diffSeconds < 3600
    ) {

        return Math.floor(
            diffSeconds / 60
        ) + " phút trước";
    }

    if (
        diffSeconds >= 3600
        && diffSeconds < 86400
    ) {

        return Math.floor(
            diffSeconds / 3600
        ) + " giờ trước";
    }

    return formatDateTime(value);
}


function formatNotificationFullTime(value) {
    if (isBlank(value)) {
        return "—";
    }

    const exact = formatDateTime(value);
    const relative = formatNotificationTime(value);

    if (relative && relative !== exact) {
        return exact + " (" + relative + ")";
    }

    return exact;
}
