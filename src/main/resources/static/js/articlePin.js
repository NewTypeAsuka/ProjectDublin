// 관리자용 게시글 고정 상태 변경과 화면 표시를 관리하는 스크립트
// 사용처: article.html

(function () {
    const pinButton = document.getElementById('pin-btn');
    const articleId = document.getElementById('article-id')?.value;
    const pinIcon = document.getElementById('pin-icon');
    const pinLabel = document.getElementById('pin-label');
    const pinnedMarker = document.getElementById('article-pinned-marker');
    const actionMessage = document.getElementById('article-action-message');

    if (!pinButton || !articleId || !pinIcon || !pinLabel || !pinnedMarker) {
        return;
    }

    let pinned = pinButton.dataset.pinned === 'true';

    pinButton.addEventListener('click', function () {
        updatePinned(pinned ? 'DELETE' : 'PUT');
    });

    function updatePinned(method) {
        pinButton.disabled = true;

        window.csrfFetch(`/api/articles/${articleId}/pin`, {
            method,
            credentials: 'same-origin'
        })
            .then(response => {
                if (response.status === 401) {
                    location.replace('/login');
                    return null;
                }
                if (response.status === 403) {
                    throw new Error('admin role required');
                }
                if (!response.ok) {
                    throw new Error('pin request failed');
                }
                return response.json();
            })
            .then(state => {
                if (state) {
                    render(state.pinned);
                }
            })
            .catch(() => {
                showActionMessage('게시글 고정 상태를 변경하지 못했습니다. 잠시 후 다시 시도해주세요');
            })
            .finally(() => {
                pinButton.disabled = false;
            });
    }

    function render(nextPinned) {
        pinned = nextPinned;
        pinButton.dataset.pinned = String(pinned);
        pinButton.classList.toggle('is-pinned', pinned);
        pinButton.setAttribute('aria-pressed', String(pinned));
        pinButton.setAttribute('aria-label', pinned ? '글 고정 해제' : '글 고정');
        pinIcon.classList.toggle('bi-pin-angle', !pinned);
        pinIcon.classList.toggle('bi-pin-angle-fill', pinned);
        pinLabel.textContent = pinned ? '해제' : '고정';
        pinnedMarker.classList.toggle('is-hidden', !pinned);
        pinnedMarker.setAttribute('aria-hidden', String(!pinned));
        hideActionMessage();
    }

    // 고정 상태 변경 오류를 게시글 안에서 바로 안내
    function showActionMessage(message) {
        if (!actionMessage) {
            return;
        }
        actionMessage.textContent = message;
        actionMessage.classList.remove('d-none', 'alert-success');
        actionMessage.classList.add('alert-danger');
        actionMessage.setAttribute('role', 'alert');
    }

    function hideActionMessage() {
        if (!actionMessage) {
            return;
        }
        actionMessage.classList.add('d-none');
        actionMessage.classList.remove('alert-danger', 'alert-success');
    }
})();
