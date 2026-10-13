document.addEventListener(
    "DOMContentLoaded",
    function () {
        setupNotificationBell();
    }
);


function setupNotificationBell() {

    const root =
        document.getElementById(
            "notificationBell"
        );

    if (root === null) {
        return;
    }


    const button =
        document.getElementById(
            "notificationBellButton"
        );

    const count =
        document.getElementById(
            "notificationBellCount"
        );

    const popover =
        document.getElementById(
            "notificationPopover"
        );

    const unreadText =
        document.getElementById(
            "notificationUnreadText"
        );

    const readAllButton =
        document.getElementById(
            "notificationReadAllButton"
        );

    const list =
        document.getElementById(
            "notificationPopoverList"
        );


    let currentUnreadCount = 0;


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

        const items =
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
            items,
            async function (item) {

                /*
                 * Nếu chưa đọc thì đánh dấu
                 * trước khi điều hướng.
                 */
                if (!item.read) {

                    const readResult =
                        await postData(
                            "/api/notifications",
                            {
                                action: "read",
                                notificationId:
                                item.notificationId
                            }
                        );

                    if (!readResult.success) {
                        return;
                    }
                }


                /*
                 * Notification BF-06 có actionUrl
                 * → chuyển đến request liên quan.
                 */
                if (item.actionUrl) {

                    window.location.href =
                        buildUrl(
                            item.actionUrl
                        );

                    return;
                }


                /*
                 * Notification bình thường của Manager:
                 * chỉ đánh dấu đã đọc,
                 * vẫn ở nguyên trang.
                 */
                await loadNotifications(
                    false
                );
            }
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
                await loadNotifications(
                    true
                );
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
                popover
            );
        }
    );


    document.addEventListener(
        "keydown",
        function (event) {

            if (event.key === "Escape") {

                closeNotificationPopover(
                    button,
                    popover
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
    popover
) {

    if (popover.hidden) {
        return;
    }

    popover.hidden = true;

    button.setAttribute(
        "aria-expanded",
        "false"
    );
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


        const isMaintenance =
            typeof item.title === "string"
            && item.title.startsWith(
                "Bảo trì ·"
            );


        if (isMaintenance) {

            itemButton.classList.add(
                "maintenance"
            );

        } else if (
            item.systemGenerated
        ) {

            itemButton.classList.add(
                "system"
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
            isMaintenance
                ? "fa-solid fa-screwdriver-wrench"
                : item.systemGenerated
                    ? "fa-solid fa-circle-info"
                    : "fa-solid fa-bullhorn";


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


        // ===== Mũi tên nếu có destination =====

        if (item.actionUrl) {

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
        }


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