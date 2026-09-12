const state = {
    currentView: "overview",
    data: null,
    devicesLoaded: false,
    selectedDeviceId: null,
    selectedSessionId: null,
    detail: null
};

const viewLabels = {
    overview: "상태",
    devices: "기기"
};

const feedback = {
    pendingRequests: 0,
    loadingVisible: false,
    loadingTimer: null,
    errorMessage: null,
    errorTimer: null
};

const LOADING_FEEDBACK_DELAY_MS = 200;
const ERROR_FEEDBACK_DURATION_MS = 6000;

const formatDateTimeParts = value => {
    if (!value) return null;
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return null;
    const twoDigits = number => String(number).padStart(2, "0");
    return {
        date: `${date.getFullYear()}.${twoDigits(date.getMonth() + 1)}.${twoDigits(date.getDate())}`,
        time: `${twoDigits(date.getHours())}:${twoDigits(date.getMinutes())}:${twoDigits(date.getSeconds())}`
    };
};

const formatDateTime = value => {
    if (!value) return "기록 없음";
    const parts = formatDateTimeParts(value);
    return parts ? `${parts.date} ${parts.time}` : String(value);
};

const text = (tag, value, className) => {
    const node = document.createElement(tag);
    node.textContent = value ?? "";
    if (className) node.className = className;
    return node;
};

const formatRawValue = value => {
    if (value === null || value === undefined) return "null";
    if (typeof value === "object") return JSON.stringify(value);
    return String(value);
};

const setContainerMessage = (container, className, message) => {
    container.className = className;
    container.replaceChildren(text("div", message, "empty-message"));
};

const statusBadge = value => text("span", value ?? "null", "status-text");

const sessionStatusLabel = session => {
    const status = session?.status ?? "null";
    if (session?.hardRejectActive) {
        return `${status} · HARD_REJECT`;
    }
    return status;
};

const createSelectItem = ({ primary, status, meta = [], data = {}, title, onSelect }) => {
    const button = document.createElement("button");
    button.type = "button";
    button.className = "select-item";
    if (title) button.title = title;
    Object.entries(data).forEach(([key, value]) => {
        button.dataset[key] = value;
    });

    if (status !== undefined) {
        const heading = document.createElement("div");
        heading.className = "select-item-heading";
        heading.append(text("strong", primary), statusBadge(status));
        button.append(heading);
    } else {
        button.append(text("strong", primary));
    }

    meta.forEach(item => {
        const options = typeof item === "string" ? { value: item } : item;
        const className = ["subtext", options.className].filter(Boolean).join(" ");

        if (Array.isArray(options.entries)) {
            const group = document.createElement("dl");
            group.className = className;
            options.entries.forEach(([label, value]) => {
                const valueNode = text("dd", value === null || value === undefined ? "null" : value);
                valueNode.title = `${label} ${valueNode.textContent}`;
                group.append(text("dt", label), valueNode);
            });
            button.append(group);
            return;
        }

        const node = text("span", options.value, className);
        if (options.title) node.title = options.title;
        button.append(node);
    });

    if (onSelect) {
        button.addEventListener("click", event => onSelect(event.currentTarget));
    }
    return button;
};

const setCount = (id, count, unit) => {
    document.getElementById(id).textContent = `${count}${unit}`;
};

const api = async (path, options = {}) => {
    const headers = { Accept: "application/json", ...(options.headers || {}) };
    if (options.body && !headers["Content-Type"]) {
        headers["Content-Type"] = "application/json";
    }
    const response = await fetch(path, { ...options, cache: "no-store", headers });
    if (response.status === 401) {
        window.location.href = "/admin/login";
        throw new Error("관리자 비밀번호가 필요합니다.");
    }
    if (!response.ok) {
        let message = `${response.status} ${response.statusText}`;
        try {
            const body = await response.json();
            message = body.message || message;
        } catch (_) { }
        throw new Error(message);
    }
    return response.json();
};

const renderFeedback = () => {
    const node = document.getElementById("systemMessage");
    const showErrorMessage = Boolean(feedback.errorMessage);
    const showLoadingMessage = feedback.loadingVisible && feedback.pendingRequests > 0;

    if (!showErrorMessage && !showLoadingMessage) {
        node.hidden = true;
        node.textContent = "";
        return;
    }

    node.textContent = showErrorMessage
        ? `요청을 처리하지 못했습니다: ${feedback.errorMessage}`
        : "불러오는 중입니다.";
    node.classList.toggle("is-error", showErrorMessage);
    node.setAttribute("role", showErrorMessage ? "alert" : "status");
    node.setAttribute("aria-live", showErrorMessage ? "assertive" : "polite");
    node.hidden = false;
};

const setLoading = active => {
    if (active) {
        feedback.pendingRequests += 1;
        if (feedback.pendingRequests === 1) {
            window.clearTimeout(feedback.loadingTimer);
            feedback.loadingVisible = false;
            feedback.loadingTimer = window.setTimeout(() => {
                feedback.loadingVisible = true;
                renderFeedback();
            }, LOADING_FEEDBACK_DELAY_MS);
        }
        return;
    }

    feedback.pendingRequests = Math.max(0, feedback.pendingRequests - 1);
    if (feedback.pendingRequests === 0) {
        window.clearTimeout(feedback.loadingTimer);
        feedback.loadingTimer = null;
        feedback.loadingVisible = false;
    }
    renderFeedback();
};

const showError = error => {
    feedback.errorMessage = error.message || String(error);
    window.clearTimeout(feedback.errorTimer);
    renderFeedback();
    feedback.errorTimer = window.setTimeout(() => {
        feedback.errorMessage = null;
        feedback.errorTimer = null;
        renderFeedback();
    }, ERROR_FEEDBACK_DURATION_MS);
};

const setMobileNavOpen = open => {
    const trigger = document.getElementById("mobileNavTrigger");
    const menu = document.getElementById("mobileNavMenu");
    trigger.setAttribute("aria-expanded", String(open));
    trigger.setAttribute("aria-label", open ? "관리 메뉴 닫기" : "관리 메뉴 열기");
    trigger.textContent = `${viewLabels[state.currentView] || state.currentView} ${open ? "-" : "+"}`;
    menu.hidden = !open;
};

const setGeneratedAt = value => {
    const generatedAt = document.getElementById("generatedAt");
    const parts = formatDateTimeParts(value || new Date());
    document.getElementById("generatedDate").textContent = parts?.date || "기록 없음";
    document.getElementById("generatedTime").textContent = parts?.time || "";
    generatedAt.classList.remove("is-pending");
};

const loadAdminData = async (force = false) => {
    if (!force && state.data) return state.data;

    state.data = await api("/admin/data");
    if (force) state.devicesLoaded = false;
    setGeneratedAt(state.data.generatedAt);
    return state.data;
};

const showView = async view => {
    state.currentView = view;
    document.querySelectorAll("[data-view]").forEach(button => {
        const selected = button.dataset.view === view;
        button.classList.toggle("is-selected", selected);
        if (selected) button.setAttribute("aria-current", "page");
        else button.removeAttribute("aria-current");
    });
    setMobileNavOpen(false);
    document.querySelectorAll(".view").forEach(section => {
        section.classList.toggle("is-active", section.id === `${view}View`);
    });
    if (view === "overview") await loadOverview();
    if (view === "devices" && !state.devicesLoaded) await loadDevices();
};

const setDeviceStage = stage => {
    document.getElementById("deviceWorkspace").dataset.mobileStage = stage;
};

const loadOverview = async (force = false) => {
    setLoading(true);
    try {
        const data = await loadAdminData(force);
        const summary = data.summary || {};
        const metrics = document.getElementById("overviewMetrics");
        metrics.replaceChildren();
        [
            ["등록 기기", `${summary.deviceCount || 0}대`],
            ["활성 잠금 세션", `${summary.activeSessionCount || 0}개`],
            ["사용 허용 중", `${summary.activePermitCount || 0}개`],
            ["Hard Reject", `${summary.hardRejectSessionCount || 0}개`]
        ].forEach(([label, value]) => {
            const card = document.createElement("div");
            card.className = "metric";
            card.append(text("span", label), text("strong", value));
            metrics.append(card);
        });

    } catch (error) {
        showError(error);
    } finally {
        setLoading(false);
    }
};

const loadDevices = async (force = false) => {
    setLoading(true);
    try {
        const data = await loadAdminData(force);
        const devices = data.devices || [];
        state.devicesLoaded = true;
        const list = document.getElementById("deviceList");
        const sessionList = document.getElementById("sessionList");
        setCount("deviceCount", devices.length, "대");
        setCount("sessionCount", 0, "개");
        list.replaceChildren();
        if (!devices.length) {
            state.selectedDeviceId = null;
            state.selectedSessionId = null;
            setContainerMessage(list, "select-list", "등록된 기기가 없습니다.");
            setContainerMessage(sessionList, "select-list", "기기를 선택하면 잠금 세션이 표시됩니다.");
            resetConversationHeader();
            setContainerMessage(
                document.getElementById("conversationList"),
                "conversation-list",
                "세션을 선택하면 대화가 표시됩니다."
            );
            return;
        }
        devices.forEach(device => {
            const button = createSelectItem({
                primary: device.nickName || "이름 없는 기기",
                data: { deviceId: device.deviceId },
                title: device.deviceId,
                meta: [
                    { value: device.deviceId, className: "device-id", title: device.deviceId },
                    { value: formatDateTime(device.lastActivityAt), className: "device-time" }
                ],
                onSelect: selected => selectDevice(device, selected, true)
            });
            list.append(button);
        });
        await selectDevice(devices[0], list.firstElementChild, false);
        setDeviceStage("devices");
    } catch (error) {
        showError(error);
    } finally {
        setLoading(false);
    }
};

const selectDevice = async (device, button, navigate = true) => {
    state.selectedDeviceId = device.deviceId;
    state.selectedSessionId = null;
    if (navigate) setDeviceStage("sessions");
    document.querySelectorAll("#deviceList .select-item").forEach(item => item.classList.remove("is-selected"));
    button?.classList.add("is-selected");
    setCount("sessionCount", 0, "개");
    const sessionList = document.getElementById("sessionList");
    setContainerMessage(sessionList, "select-list", "잠금 세션을 불러오는 중입니다.");
    resetConversationHeader();
    setContainerMessage(
        document.getElementById("conversationList"),
        "conversation-list",
        "세션을 선택하면 대화가 표시됩니다."
    );

    try {
        const sessions = device.sessions || [];
        setCount("sessionCount", sessions.length, "개");
        sessionList.replaceChildren();
        if (!sessions.length) {
            sessionList.append(text("div", "이 기기의 잠금 세션이 없습니다.", "empty-message"));
            return;
        }
        sessions.forEach(session => {
            const buttonNode = createSelectItem({
                primary: session.appName || session.packageName || "알 수 없는 앱",
                status: sessionStatusLabel(session),
                data: { sessionId: session.sessionId },
                meta: [
                    {
                        className: "session-times",
                        entries: [
                            ["unlockCode", session.unlockCode || "-"],
                            ["openedAt", formatDateTime(session.openedAt)],
                            sessionEndEntry(session)
                        ]
                    }
                ],
                onSelect: selected => selectSession(session, selected, true)
            });
            sessionList.append(buttonNode);
        });
        await selectSession(sessions[0], sessionList.firstElementChild, false);
    } catch (error) {
        showError(error);
        setContainerMessage(sessionList, "select-list", "잠금 세션을 불러오지 못했습니다.");
    }
};

const sessionEndEntry = session => {
    if (session.closedAt) return ["closedAt", formatDateTime(session.closedAt)];
    return ["sessionExpiresAt", formatDateTime(session.sessionExpiresAt)];
};

const selectSession = async (session, button, navigate = true) => {
    state.selectedSessionId = session.sessionId;
    if (navigate) setDeviceStage("conversation");
    document.querySelectorAll("#sessionList .select-item").forEach(item => item.classList.remove("is-selected"));
    button?.classList.add("is-selected");
    renderConversationHeader(session);
    const list = document.getElementById("conversationList");
    setContainerMessage(list, "conversation-list", "대화를 불러오는 중입니다.");
    try {
        const device = (state.data?.devices || [])
                .find(item => item.deviceId === state.selectedDeviceId);
        const detail = {
            session: {
                ...session,
                deviceName: device?.nickName || "이름 없는 기기",
                hardReject: session.hardRejectActive
            },
            turns: (session.turns || []).map(turn => ({
                ...turn,
                isEmbedding: turn.embedding
            })),
            permits: session.permits || [],
            memories: session.permitMemories || []
        };
        renderConversation(detail);
    } catch (error) {
        showError(error);
        setContainerMessage(list, "conversation-list", "대화를 불러오지 못했습니다.");
    }
};

const resetConversationHeader = () => {
    document.getElementById("conversationTitle").textContent = "대화";
    const status = document.getElementById("conversationStatus");
    status.textContent = "";
    status.hidden = true;
};

const renderConversationHeader = session => {
    document.getElementById("conversationTitle").textContent = session.appName || session.packageName;
    const status = document.getElementById("conversationStatus");
    status.textContent = sessionStatusLabel(session);
    status.hidden = false;
};

const renderConversation = detail => {
    const list = document.getElementById("conversationList");
    list.className = "conversation-list";
    list.replaceChildren();
    const turns = detail.turns || [];
    const permits = detail.permits || [];
    const memories = detail.memories || [];
    if (!turns.length) {
        list.append(text("div", "이 세션에는 아직 대화가 없습니다.", "empty-message"));
    }
    turns.forEach((turn, index) => {
        const group = document.createElement("article");
        group.className = "conversation-group";
        const heading = document.createElement("div");
        heading.className = "turn-heading";
        heading.append(text("strong", `대화 ${index + 1}`), text("span", formatDateTime(turn.createdAt)));

        const userRow = document.createElement("div");
        userRow.className = "message-row user";
        const userMessage = text("div", turn.userInput || "", "message");
        userMessage.append(text("small", `사용자 · ${formatDateTime(turn.createdAt)}`));
        userRow.append(userMessage);

        const aiRow = document.createElement("div");
        aiRow.className = "message-row ai";
        const aiMessage = text("div", turn.aiMessage || "AI 메시지 기록 없음", "message");
        aiMessage.append(text("small", `AI · ${formatDateTime(turn.createdAt)}`));
        aiRow.append(aiMessage);

        const summary = document.createElement("div");
        summary.className = "decision-summary";
        summary.append(definitionList([
            ["messageIntent", turn.messageIntent],
            ["messageTone", turn.messageTone],
            ["aiAction", turn.aiAction],
            ["proposedSec", turn.proposedSec],
            ["failedStandards", turn.failedStandards],
            ["aiReason", turn.aiReason]
        ]));

        const linkedPermits = permits.filter(permit => Number(permit.grantingTurnId) === Number(turn.turnId));
        linkedPermits.forEach(permit => summary.append(renderPermit(permit)));
        const linkedMemories = memories.filter(memory => Number(memory.grantingTurnId) === Number(turn.turnId));
        linkedMemories.forEach(memory => summary.append(renderPermitMemory(memory)));
        if (turn.aiAction === "OFFER" && !linkedPermits.length) {
            summary.append(text("p", "연결된 사용권이 없습니다. 이후 재협상으로 OFFERED 사용권의 연결 턴이 변경됐을 수 있습니다."));
        }

        const actions = document.createElement("div");
        actions.className = "turn-actions";
        const detailButton = text("button", "상세 보기", "secondary-button");
        detailButton.type = "button";
        detailButton.addEventListener("click", () => openDetail(
            turn, linkedPermits, linkedMemories, detail.session));
        actions.append(detailButton);
        summary.append(actions);

        group.append(heading, userRow, aiRow, summary);
        list.append(group);
    });

    const unlinked = permits.filter(permit => !turns.some(turn => Number(turn.turnId) === Number(permit.grantingTurnId)));
    if (unlinked.length) {
        const block = document.createElement("section");
        block.className = "conversation-group";
        block.append(text("h3", "연결되지 않은 세션 사용권"));
        unlinked.forEach(permit => block.append(renderPermit(permit)));
        list.append(block);
    }
};

const definitionList = entries => {
    const dl = document.createElement("dl");
    entries.forEach(([label, value]) => {
        dl.append(text("dt", label), text("dd", formatRawValue(value)));
    });
    return dl;
};

const renderPermit = permit => {
    const block = document.createElement("div");
    block.className = "permit-summary";
    block.append(text("strong", `Permit #${permit.permitId}`));
    block.append(definitionList([
        ["status", permit.status],
        ["grantedSec", permit.grantedSec],
        ["startTurnId", permit.startTurnId],
        ["grantingTurnId", permit.grantingTurnId],
        ["issuedAt", formatDateTime(permit.issuedAt)],
        ["expiresAt", formatDateTime(permit.expiresAt)],
        ["closedAt", formatDateTime(permit.closedAt)],
        ["closeReason", permit.closeReason]
    ]));
    return block;
};

const renderPermitMemory = memory => {
    const block = document.createElement("div");
    block.className = "permit-summary";
    block.append(text("strong", `PermitMemory #${memory.memoryId}`));
    block.append(definitionList([
        ["permitId", memory.permitId],
        ["retrievalText", memory.retrievalText],
        ["grantedSec", memory.grantedSec],
        ["closeReason", memory.closeReason],
        ["createdAt", memory.createdAt]
    ]));
    return block;
};

const appendDetailBlock = (container, title, entries) => {
    const block = document.createElement("section");
    block.className = "detail-block";
    block.append(text("h3", title), definitionList(entries));
    container.append(block);
};

const openDetail = (turn, permits, memories, session) => {
    state.detail = { session, turn, permits, memories };
    document.getElementById("detailTitle").textContent = `대화 ${Number(turn.turnIndex) + 1} 상세`;
    document.getElementById("detailSubtitle").textContent = `${session.deviceName} · ${session.appName} · ${formatDateTime(turn.createdAt)}`;
    const summary = document.getElementById("detailSummary");
    summary.replaceChildren();

    appendDetailBlock(summary, "ChatSession", [
        ["status", session.status],
        ["hardRejectActive", session.hardRejectActive],
        ["hardRejectUntil", session.hardRejectUntil],
        ["openedAt", session.openedAt],
        ["sessionExpiresAt", session.sessionExpiresAt],
        ["closedAt", session.closedAt]
    ]);

    appendDetailBlock(summary, "ChatTurn", [
        ["messageIntent", turn.messageIntent],
        ["messageTone", turn.messageTone],
        ["aiAction", turn.aiAction],
        ["proposedSec", turn.proposedSec],
        ["failedStandards", turn.failedStandards],
        ["modelName", turn.modelName],
        ["aiReason", turn.aiReason],
        ["isEmbedding", turn.isEmbedding]
    ]);

    permits.forEach(permit => summary.append(renderPermit(permit)));
    memories.forEach(memory => summary.append(renderPermitMemory(memory)));

    const unavailable = document.createElement("section");
    unavailable.className = "detail-block";
    unavailable.append(
        text("h3", "구조화된 필드로 저장되지 않는 정보"),
        text("p", "baseScore, ragScore, finalScore, RAG 검색 후보, criteriaReason, 실제 프롬프트와 AI 원본 응답은 전용 컬럼이나 레코드로 저장되지 않습니다. aiReason 문자열에 일부가 포함될 수 있어도 관리자가 추정해 분리하지 않습니다.", "detail-note")
    );
    summary.append(unavailable);

    document.getElementById("detailJson").textContent = JSON.stringify(state.detail, null, 2);
    selectDetailView("summary");
    document.getElementById("detailDialog").showModal();
};

const selectDetailView = view => {
    document.querySelectorAll(".detail-tab").forEach(button => {
        button.classList.toggle("is-selected", button.dataset.detailView === view);
    });
    document.getElementById("detailSummary").hidden = view !== "summary";
    document.getElementById("detailJson").hidden = view !== "json";
};

document.querySelectorAll("[data-view]").forEach(button => {
    button.addEventListener("click", () => showView(button.dataset.view));
});
document.getElementById("mobileNavTrigger").addEventListener("click", () => {
    const menu = document.getElementById("mobileNavMenu");
    setMobileNavOpen(menu.hidden);
});
document.addEventListener("pointerdown", event => {
    const menu = document.getElementById("mobileNavMenu");
    if (!menu.hidden && !document.querySelector(".topbar-shell").contains(event.target)) {
        setMobileNavOpen(false);
    }
});
document.addEventListener("keydown", event => {
    const menu = document.getElementById("mobileNavMenu");
    if (event.key === "Escape" && !menu.hidden) {
        setMobileNavOpen(false);
        document.getElementById("mobileNavTrigger").focus();
    }
});
window.matchMedia("(max-width: 38.75rem)").addEventListener("change", () => setMobileNavOpen(false));
document.querySelectorAll(".detail-tab").forEach(button => {
    button.addEventListener("click", () => selectDetailView(button.dataset.detailView));
});
document.getElementById("closeDetail").addEventListener("click", () => {
    document.getElementById("detailDialog").close();
});
document.getElementById("sessionBackButton").addEventListener("click", () => setDeviceStage("devices"));
document.getElementById("conversationBackButton").addEventListener("click", () => setDeviceStage("sessions"));
document.getElementById("refreshButton").addEventListener("click", async () => {
    if (state.currentView === "overview") await loadOverview(true);
    if (state.currentView === "devices") {
        await loadDevices(true);
    }
});

showView("overview");
