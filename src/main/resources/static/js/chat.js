// 공개 채팅 이력 조회와 STOMP WebSocket 실시간 송수신 및 48시간 만료·메시지 삭제
// 사용처: chat.html

document.addEventListener('DOMContentLoaded', () => {
    const connection = document.getElementById('chat-connection');
    const connectionLabel = document.getElementById('chat-connection-label');
    const viewport = document.getElementById('chat-viewport');
    const history = document.getElementById('chat-history');
    const historyButton = document.getElementById('chat-history-button');
    const loading = document.getElementById('chat-loading');
    const empty = document.getElementById('chat-empty');
    const loadError = document.getElementById('chat-load-error');
    const retryButton = document.getElementById('chat-retry-button');
    const messageList = document.getElementById('chat-message-list');
    const form = document.getElementById('chat-form');
    const input = document.getElementById('chat-message-input');
    const counter = document.getElementById('chat-message-counter');
    const submitButton = document.getElementById('chat-submit-button');
    const submitIcon = document.getElementById('chat-submit-icon');
    const formMessage = document.getElementById('chat-form-message');
    const messageConfig = document.getElementById('chat-messages');

    if (!connection || !connectionLabel || !viewport || !history
            || !historyButton || !loading || !empty || !loadError
            || !retryButton || !messageList || !form || !input
            || !counter || !submitButton || !submitIcon || !formMessage
            || !messageConfig) {
        return;
    }

    const messages = messageConfig.dataset;
    const displayLocale = document.documentElement.lang === 'ja' ? 'ja-JP' : 'ko-KR';
    const historyEndpoint = '/api/menu/chat/messages';
    const topicDestination = '/topic/chat';
    const sendDestination = '/app/chat/messages';
    const errorDestination = '/user/queue/chat/errors';
    const messageRetentionMillis = 48 * 60 * 60 * 1000;
    const expirationCheckIntervalMillis = 60 * 1000;
    const messageElements = new Map();
    const queuedEvents = [];

    let stompClient = null;
    let currentUserId = null;
    let currentUserAdmin = false;
    let nextBeforeId = null;
    let hasMore = false;
    let historyLoaded = false;
    let historyLoading = false;
    let connected = false;
    let pendingClientMessageId = null;
    let pendingContent = null;
    let retryClientMessageId = null;
    let pendingTimer = null;
    let expirationTimer = null;

    function createElement(tagName, className, text) {
        const element = document.createElement(tagName);
        if (className) {
            element.className = className;
        }
        if (text !== undefined) {
            element.textContent = text;
        }
        return element;
    }

    function setConnectionState(state, label) {
        connection.className = 'chat-connection';
        if (state) {
            connection.classList.add(`is-${state}`);
        }
        connectionLabel.textContent = label;
    }

    function setFormMessage(message, state = '') {
        formMessage.textContent = message;
        formMessage.className = 'chat-composer__message';
        if (state) {
            formMessage.classList.add(`is-${state}`);
        }
    }

    function contentLength(value) {
        return Array.from(value).length;
    }

    function updateCounter() {
        counter.textContent = `${contentLength(input.value)}/300`;
    }

    function updateFormAvailability() {
        const waiting = pendingClientMessageId !== null;
        input.disabled = !historyLoaded || !connected || waiting;
        submitButton.disabled = input.disabled || input.value.trim().length === 0;
        submitIcon.className = waiting
            ? 'spinner-border spinner-border-sm'
            : 'bi bi-send-fill';
    }

    function resizeInput() {
        input.style.height = 'auto';
        input.style.height = `${Math.min(input.scrollHeight, 128)}px`;
    }

    function formatCreatedAt(value) {
        if (!value) {
            return '';
        }
        const date = new Date(value);
        if (Number.isNaN(date.getTime())) {
            return String(value).replace('T', ' ').slice(0, 16);
        }

        return new Intl.DateTimeFormat(displayLocale, {
            month: '2-digit',
            day: '2-digit',
            hour: '2-digit',
            minute: '2-digit',
            hour12: false
        }).format(date);
    }

    function isNearBottom() {
        return viewport.scrollHeight - viewport.scrollTop - viewport.clientHeight < 100;
    }

    function scrollToBottom() {
        viewport.scrollTop = viewport.scrollHeight;
    }

    function canDelete(message) {
        return currentUserAdmin || Number(message.senderId) === Number(currentUserId);
    }

    function getExpirationTime(message) {
        if (message && message.expiresAtEpochMillis !== null
                && message.expiresAtEpochMillis !== undefined
                && Number.isFinite(Number(message.expiresAtEpochMillis))) {
            return Number(message.expiresAtEpochMillis);
        }
        const createdTime = new Date(message && message.createdAt).getTime();
        return Number.isFinite(createdTime)
            ? createdTime + messageRetentionMillis
            : null;
    }

    function isExpiredMessage(message) {
        const expirationTime = getExpirationTime(message);
        return expirationTime !== null && expirationTime <= Date.now();
    }

    function createMessageElement(message) {
        const ownMessage = Number(message.senderId) === Number(currentUserId);
        const item = createElement(
            'li',
            `chat-message${ownMessage ? ' is-own' : ''}`
        );
        item.dataset.messageId = String(message.id);
        const expirationTime = getExpirationTime(message);
        if (expirationTime !== null) {
            item.dataset.expiresAt = String(expirationTime);
        }

        const avatar = createElement('span', 'chat-message__avatar');
        const avatarIcon = createElement('i', 'bi bi-person-fill');
        avatarIcon.setAttribute('aria-hidden', 'true');
        avatar.append(avatarIcon);

        const main = createElement('div', 'chat-message__main');
        const sender = createElement('div', 'chat-message__sender');
        const nickname = createElement(
            'span',
            'user-nickname',
            message.senderNickname || messages.user
        );
        nickname.dataset.userId = String(message.senderId);
        sender.append(nickname);
        if (message.senderAdmin) {
            const adminBadge = createElement(
                'i',
                'bi bi-patch-check-fill author-admin-badge chat-admin-badge'
            );
            adminBadge.setAttribute('aria-label', messages.admin);
            adminBadge.setAttribute('title', messages.admin);
            sender.append(adminBadge);
        }

        const bubble = createElement(
            'div',
            'chat-message__bubble',
            message.content || ''
        );
        const meta = createElement('div', 'chat-message__meta');
        const time = createElement(
            'time',
            'chat-message__time',
            formatCreatedAt(message.createdAt)
        );
        time.dateTime = message.createdAt || '';
        meta.append(time);

        if (canDelete(message)) {
            const deleteButton = createElement(
                'button',
                'chat-message__delete',
                messages.delete
            );
            deleteButton.type = 'button';
            deleteButton.addEventListener('click', () => {
                deleteMessage(message.id, deleteButton);
            });
            meta.append(deleteButton);
        }

        main.append(sender, bubble, meta);
        item.append(avatar, main);
        return item;
    }

    function addMessage(message, placement = 'append') {
        const messageId = Number(message && message.id);
        if (!Number.isSafeInteger(messageId)
                || messageElements.has(messageId)
                || isExpiredMessage(message)) {
            return false;
        }

        const element = createMessageElement(message);
        messageElements.set(messageId, element);
        if (placement === 'prepend') {
            messageList.prepend(element);
        } else {
            messageList.append(element);
        }
        empty.hidden = true;
        return true;
    }

    function prependMessages(messages) {
        const previousHeight = viewport.scrollHeight;
        const fragment = document.createDocumentFragment();

        messages.forEach(message => {
            const messageId = Number(message && message.id);
            if (!Number.isSafeInteger(messageId)
                    || messageElements.has(messageId)
                    || isExpiredMessage(message)) {
                return;
            }
            const element = createMessageElement(message);
            messageElements.set(messageId, element);
            fragment.append(element);
        });
        messageList.prepend(fragment);
        viewport.scrollTop += viewport.scrollHeight - previousHeight;
    }

    function removeMessage(messageId) {
        const normalizedId = Number(messageId);
        const element = messageElements.get(normalizedId);
        if (!element) {
            return;
        }
        element.remove();
        messageElements.delete(normalizedId);
        empty.hidden = messageElements.size !== 0;
    }

    // 브라우저를 계속 열어둔 경우에도 48시간이 지난 메시지를 화면에서 제거
    function removeExpiredMessages() {
        const currentTime = Date.now();
        messageElements.forEach((element, messageId) => {
            const expirationTime = Number(element.dataset.expiresAt);
            if (Number.isFinite(expirationTime) && expirationTime <= currentTime) {
                removeMessage(messageId);
            }
        });
    }

    function finishPendingMessage(clientMessageId) {
        if (!clientMessageId || clientMessageId !== pendingClientMessageId) {
            return;
        }
        window.clearTimeout(pendingTimer);
        pendingTimer = null;
        pendingClientMessageId = null;
        pendingContent = null;
        retryClientMessageId = null;
        input.value = '';
        updateCounter();
        resizeInput();
        setFormMessage('');
        updateFormAvailability();
        input.focus();
    }

    function applyChatEvent(event) {
        if (!historyLoaded) {
            queuedEvents.push(event);
            return;
        }
        if (event.type === 'MESSAGE_CREATED' && event.message) {
            const shouldScroll = isNearBottom()
                || Number(event.message.senderId) === Number(currentUserId);
            addMessage(event.message);
            finishPendingMessage(event.message.clientMessageId);
            if (shouldScroll) {
                scrollToBottom();
            }
            return;
        }
        if (event.type === 'MESSAGE_DELETED') {
            removeMessage(event.messageId);
        }
    }

    function renderHistoryButton(isLoading) {
        const icon = createElement(
            isLoading ? 'span' : 'i',
            isLoading
                ? 'spinner-border spinner-border-sm'
                : 'bi bi-clock-history'
        );
        icon.setAttribute('aria-hidden', 'true');
        historyButton.replaceChildren(
            icon,
            document.createTextNode(
                ` ${isLoading ? messages.historyLoading : messages.historyPrevious}`
            )
        );
    }

    async function loadMessages(beforeId = null) {
        if (historyLoading) {
            return;
        }
        historyLoading = true;
        const initialLoad = beforeId === null;

        if (initialLoad) {
            loading.hidden = false;
            loadError.hidden = true;
            empty.hidden = true;
        } else {
            historyButton.disabled = true;
            renderHistoryButton(true);
        }

        try {
            const parameters = new URLSearchParams({size: '30'});
            if (beforeId !== null) {
                parameters.set('beforeId', String(beforeId));
            }
            const response = await fetch(`${historyEndpoint}?${parameters.toString()}`, {
                headers: {'Accept': 'application/json'},
                credentials: 'same-origin'
            });
            if (!response.ok) {
                throw new Error(messages.historyLoadError);
            }

            const data = await response.json();
            currentUserId = data.currentUserId;
            currentUserAdmin = Boolean(data.currentUserAdmin);
            hasMore = Boolean(data.hasMore);
            nextBeforeId = data.nextBeforeId ?? null;
            const chatMessages = Array.isArray(data.messages) ? data.messages : [];

            if (initialLoad) {
                messageList.replaceChildren();
                messageElements.clear();
                chatMessages.forEach(message => addMessage(message));
                historyLoaded = true;
                loading.hidden = true;
                empty.hidden = messageElements.size !== 0;
                queuedEvents.splice(0).forEach(applyChatEvent);
                removeExpiredMessages();
                scrollToBottom();
            } else {
                prependMessages(chatMessages);
            }
            history.hidden = !hasMore;
            loadError.hidden = true;
        } catch (error) {
            if (initialLoad) {
                loading.hidden = true;
                loadError.hidden = false;
            }
            setFormMessage(messages.historyLoadError, 'error');
        } finally {
            historyLoading = false;
            historyButton.disabled = false;
            renderHistoryButton(false);
            updateFormAvailability();
        }
    }

    async function deleteMessage(messageId, button) {
        if (!window.confirm(messages.deleteConfirm)) {
            return;
        }
        button.disabled = true;
        setFormMessage('');

        try {
            const response = await window.csrfFetch(
                `${historyEndpoint}/${messageId}`,
                {
                    method: 'DELETE',
                    headers: {'Accept': 'application/json'}
                }
            );
            if (!response.ok) {
                throw new Error(messages.deleteError);
            }
            const event = await response.json();
            applyChatEvent(event);
        } catch (error) {
            button.disabled = false;
            setFormMessage(messages.deleteError, 'error');
        }
    }

    function createClientMessageId() {
        if (window.crypto && typeof window.crypto.randomUUID === 'function') {
            return window.crypto.randomUUID();
        }
        return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, character => {
            const random = Math.floor(Math.random() * 16);
            const value = character === 'x' ? random : (random & 0x3) | 0x8;
            return value.toString(16);
        });
    }

    function sendMessage() {
        const content = input.value.trim();
        const length = contentLength(content);
        if (!connected || !stompClient || !stompClient.connected) {
            setFormMessage(messages.connectionRequired, 'error');
            return;
        }
        if (length < 1 || length > 300) {
            setFormMessage(messages.validationLength, 'error');
            return;
        }

        pendingClientMessageId = pendingContent === content && retryClientMessageId
            ? retryClientMessageId
            : createClientMessageId();
        pendingContent = content;
        retryClientMessageId = null;
        setFormMessage(messages.sending);
        updateFormAvailability();

        stompClient.publish({
            destination: sendDestination,
            body: JSON.stringify({
                clientMessageId: pendingClientMessageId,
                content
            })
        });

        window.clearTimeout(pendingTimer);
        pendingTimer = window.setTimeout(() => {
            retryClientMessageId = pendingClientMessageId;
            pendingClientMessageId = null;
            pendingTimer = null;
            setFormMessage(
                messages.sendDelayed,
                'error'
            );
            updateFormAvailability();
        }, 10000);
    }

    function handleWebSocketError(payload) {
        // 서버 오류 응답의 언어와 무관하게 현재 화면 언어의 안전한 문구를 표시합니다.
        void payload;

        window.clearTimeout(pendingTimer);
        pendingTimer = null;
        retryClientMessageId = pendingClientMessageId;
        pendingClientMessageId = null;
        setFormMessage(messages.processError, 'error');
        updateFormAvailability();
    }

    function connectWebSocket() {
        if (!window.StompJs || typeof window.StompJs.Client !== 'function') {
            setConnectionState('disconnected', messages.moduleErrorLabel);
            setFormMessage(messages.moduleError, 'error');
            return;
        }

        const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
        const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;
        if (!csrfToken || !csrfHeader) {
            setConnectionState('disconnected', messages.securityErrorLabel);
            setFormMessage(messages.securityError, 'error');
            return;
        }

        const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        stompClient = new window.StompJs.Client({
            brokerURL: `${protocol}//${window.location.host}/ws/chat`,
            connectHeaders: {[csrfHeader]: csrfToken},
            reconnectDelay: 3000,
            heartbeatIncoming: 20000,
            heartbeatOutgoing: 20000,
            connectionTimeout: 8000,
            debug: () => {}
        });

        stompClient.onConnect = () => {
            connected = true;
            setConnectionState('connected', messages.connected);
            setFormMessage('');
            stompClient.subscribe(topicDestination, frame => {
                try {
                    applyChatEvent(JSON.parse(frame.body));
                } catch (error) {
                    setFormMessage(messages.displayError, 'error');
                }
            });
            stompClient.subscribe(errorDestination, frame => {
                handleWebSocketError(frame.body);
            });
            updateFormAvailability();
        };
        stompClient.onStompError = frame => {
            connected = false;
            setConnectionState('disconnected', messages.retrying);
            handleWebSocketError(frame.body || '{}');
        };
        stompClient.onWebSocketClose = () => {
            connected = false;
            setConnectionState('disconnected', messages.disconnectedRetrying);
            updateFormAvailability();
        };
        stompClient.onWebSocketError = () => {
            connected = false;
            setConnectionState('disconnected', messages.retrying);
            updateFormAvailability();
        };

        setConnectionState('', messages.connecting);
        stompClient.activate();
    }

    input.addEventListener('input', () => {
        updateCounter();
        resizeInput();
        if (pendingContent !== input.value.trim()) {
            pendingContent = null;
            retryClientMessageId = null;
        }
        setFormMessage('');
        updateFormAvailability();
    });
    input.addEventListener('keydown', event => {
        if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
            event.preventDefault();
            form.requestSubmit();
        }
    });
    form.addEventListener('submit', event => {
        event.preventDefault();
        sendMessage();
    });
    historyButton.addEventListener('click', () => {
        if (hasMore && nextBeforeId !== null) {
            loadMessages(nextBeforeId);
        }
    });
    viewport.addEventListener('scroll', () => {
        if (viewport.scrollTop < 60 && hasMore && !historyLoading) {
            loadMessages(nextBeforeId);
        }
    });
    document.addEventListener('visibilitychange', () => {
        if (!document.hidden) {
            removeExpiredMessages();
        }
    });
    retryButton.addEventListener('click', () => loadMessages());
    window.addEventListener('beforeunload', () => {
        window.clearInterval(expirationTimer);
        if (stompClient) {
            stompClient.deactivate();
        }
    });

    updateCounter();
    resizeInput();
    loadMessages();
    connectWebSocket();
    expirationTimer = window.setInterval(
        removeExpiredMessages,
        expirationCheckIntervalMillis
    );
});
