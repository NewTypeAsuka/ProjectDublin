// 공통 마이페이지 모달에서 사용자 정보 조회와 닉네임 변경을 관리하는 스크립트
// 사용처: common.html (articleList.html, article.html, newArticle.html, chat.html, stocks.html에 공통 적용)

(function () {
    const openButton = document.getElementById('user-profile-open');
    const dialog = document.getElementById('user-profile-dialog');
    const closeButton = document.getElementById('user-profile-close');
    const cancelButton = document.getElementById('user-profile-cancel');
    const retryButton = document.getElementById('user-profile-retry');
    const loading = document.getElementById('user-profile-loading');
    const loadError = document.getElementById('user-profile-load-error');
    const loadErrorMessage = document.getElementById('user-profile-load-error-message');
    const content = document.getElementById('user-profile-content');
    const currentNickname = document.getElementById('user-profile-current-nickname');
    const email = document.getElementById('user-profile-email');
    const articleCount = document.getElementById('user-profile-article-count');
    const commentCount = document.getElementById('user-profile-comment-count');
    const nicknameForm = document.getElementById('user-profile-nickname-form');
    const nicknameInput = document.getElementById('user-profile-nickname');
    const nicknameCounter = document.getElementById('user-profile-nickname-counter');
    const nicknameGuide = document.getElementById('user-profile-nickname-guide');
    const message = document.getElementById('user-profile-message');
    const submitButton = document.getElementById('user-profile-submit');
    const submitIcon = document.getElementById('user-profile-submit-icon');
    const messageConfig = document.getElementById('user-profile-messages');

    if (!openButton || !dialog || !closeButton || !cancelButton || !retryButton
            || !loading || !loadError || !loadErrorMessage || !content
            || !currentNickname || !email || !articleCount || !commentCount
            || !nicknameForm || !nicknameInput || !nicknameCounter
            || !nicknameGuide || !message || !submitButton || !submitIcon
            || !messageConfig
            || typeof window.csrfFetch !== 'function') {
        return;
    }

    const messages = messageConfig.dataset;
    const minimumLength = 3;
    const maximumLength = 12;
    const submitIconClass = submitIcon.className;
    let profile = null;
    let submitting = false;
    let serverInvalid = false;
    let loadSequence = 0;

    openButton.addEventListener('click', function () {
        const menu = openButton.closest('details');
        if (menu) {
            menu.removeAttribute('open');
        }

        if (!dialog.open) {
            if (typeof dialog.showModal === 'function') {
                dialog.showModal();
            } else {
                dialog.setAttribute('open', '');
            }
        }
        document.body.classList.add('user-profile-open');
        loadProfile();
    });

    closeButton.addEventListener('click', closeDialog);
    cancelButton.addEventListener('click', closeDialog);
    retryButton.addEventListener('click', loadProfile);

    dialog.addEventListener('close', function () {
        document.body.classList.remove('user-profile-open');
        openButton.focus();
    });

    dialog.addEventListener('click', function (event) {
        if (event.target !== dialog) {
            return;
        }

        const bounds = dialog.getBoundingClientRect();
        const outside = event.clientX < bounds.left
                || event.clientX > bounds.right
                || event.clientY < bounds.top
                || event.clientY > bounds.bottom;
        if (outside) {
            closeDialog();
        }
    });

    nicknameInput.addEventListener('input', function () {
        serverInvalid = false;
        clearMessage();
        updateNicknameState();
    });

    nicknameForm.addEventListener('submit', async function (event) {
        event.preventDefault();
        if (!profile || submitting || !updateNicknameState()) {
            nicknameInput.focus();
            return;
        }

        const requestedNickname = nicknameInput.value.trim();
        nicknameInput.value = requestedNickname;
        serverInvalid = false;
        setSubmitting(true);
        clearMessage();

        try {
            const response = await window.csrfFetch('/api/users/me/nickname', {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({ nickname: requestedNickname })
            });
            if (response.status === 401) {
                location.replace('/login');
                return;
            }

            const responseBody = await readJson(response);
            if (!response.ok) {
                const fallbackMessage = response.status === 409
                        ? messages.nicknameDuplicate
                        : messages.nicknameInvalid;
                serverInvalid = true;
                showMessage(fallbackMessage, 'error');
                return;
            }

            serverInvalid = false;
            if (profile) {
                profile.nickname = responseBody.nickname;
            }
            currentNickname.textContent = responseBody.nickname;
            nicknameInput.value = responseBody.nickname;
            updateVisibleNicknames(responseBody.userId, responseBody.nickname);
            updateNicknameState();
            showMessage(messages.nicknameUpdated, 'success');
        } catch (error) {
            showMessage(messages.nicknameUpdateError, 'error');
        } finally {
            setSubmitting(false);
        }
    });

    async function loadProfile() {
        const requestSequence = ++loadSequence;
        profile = null;
        setProfileLoading(true);
        clearMessage();

        try {
            const response = await window.csrfFetch('/api/users/me', {
                method: 'GET',
                cache: 'no-store'
            });
            if (response.status === 401) {
                location.replace('/login');
                return;
            }
            if (!response.ok) {
                throw new Error('profile request failed');
            }

            const responseBody = await readJson(response);
            if (requestSequence !== loadSequence) {
                return;
            }

            profile = responseBody;
            serverInvalid = false;
            currentNickname.textContent = responseBody.nickname;
            email.textContent = responseBody.email;
            articleCount.textContent = String(responseBody.articleCount);
            commentCount.textContent = String(responseBody.commentCount);
            nicknameInput.value = responseBody.nickname;
            updateNicknameState();
            setProfileLoading(false);
        } catch (error) {
            if (requestSequence !== loadSequence) {
                return;
            }
            showLoadError(messages.loadErrorRetry);
        }
    }

    function updateNicknameState() {
        const nickname = nicknameInput.value.trim();
        const length = nickname.length;
        const valid = length >= minimumLength
                && length <= maximumLength
                && !/[\r\n\t]/.test(nickname);
        const changed = profile !== null && nickname !== profile.nickname;

        nicknameCounter.textContent = `${length}/${maximumLength}`;
        submitButton.disabled = submitting || !valid || !changed;
        nicknameGuide.classList.toggle('is-valid', valid && !serverInvalid);
        nicknameGuide.classList.toggle(
                'is-invalid',
                length > 0 && (!valid || serverInvalid)
        );

        if (length === 0) {
            nicknameGuide.textContent = messages.guideDefault;
        } else if (length < minimumLength) {
            nicknameGuide.textContent = formatMessage(
                messages.guideShort,
                minimumLength - length
            );
        } else if (length > maximumLength) {
            nicknameGuide.textContent = formatMessage(messages.guideMax, maximumLength);
        } else if (serverInvalid) {
            nicknameGuide.textContent = messages.guideDifferent;
        } else {
            nicknameGuide.textContent = changed
                    ? messages.guideValid
                    : messages.guideCurrent;
        }

        if (length > 0) {
            nicknameInput.setAttribute('aria-invalid', String(!valid || serverInvalid));
        } else {
            nicknameInput.removeAttribute('aria-invalid');
        }
        return valid && changed;
    }

    function setProfileLoading(isLoading) {
        loading.classList.toggle('d-none', !isLoading);
        content.classList.toggle('d-none', isLoading);
        loadError.classList.add('d-none');
        retryButton.disabled = isLoading;
        dialog.setAttribute('aria-busy', String(isLoading));
    }

    function showLoadError(errorMessage) {
        loading.classList.add('d-none');
        content.classList.add('d-none');
        loadError.classList.remove('d-none');
        loadErrorMessage.textContent = errorMessage;
        retryButton.disabled = false;
        dialog.setAttribute('aria-busy', 'false');
    }

    function setSubmitting(isSubmitting) {
        submitting = isSubmitting;
        if (isSubmitting) {
            submitButton.disabled = true;
            submitIcon.className = 'spinner-border spinner-border-sm';
        } else {
            submitIcon.className = submitIconClass;
            updateNicknameState();
        }
    }

    function showMessage(text, state) {
        message.textContent = text;
        message.classList.remove('d-none', 'alert-danger', 'alert-success');
        message.classList.add(state === 'success' ? 'alert-success' : 'alert-danger');
        message.setAttribute('role', state === 'success' ? 'status' : 'alert');
    }

    function formatMessage(template, ...values) {
        return values.reduce(
            (result, value, index) => result.split(`{${index}}`).join(String(value)),
            template
        );
    }

    function clearMessage() {
        message.textContent = '';
        message.classList.add('d-none');
        message.classList.remove('alert-danger', 'alert-success');
        message.setAttribute('role', 'status');
    }

    function updateVisibleNicknames(userId, nickname) {
        document.querySelectorAll('.user-nickname[data-user-id]').forEach(function (element) {
            if (element.dataset.userId === String(userId)) {
                element.textContent = nickname;
            }
        });
    }

    function closeDialog() {
        if (typeof dialog.close === 'function') {
            dialog.close();
        } else {
            dialog.removeAttribute('open');
            document.body.classList.remove('user-profile-open');
            openButton.focus();
        }
    }

    async function readJson(response) {
        try {
            return await response.json();
        } catch (error) {
            return {};
        }
    }
})();
