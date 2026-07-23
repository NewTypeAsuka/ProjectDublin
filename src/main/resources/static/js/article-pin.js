(function () {
    const pinButton = document.getElementById('pin-btn');
    const articleId = document.getElementById('article-id')?.value;
    const pinLabel = document.getElementById('pin-label');
    const pinnedCorner = document.getElementById('pinned-corner');

    if (!pinButton || !articleId || !pinLabel || !pinnedCorner) {
        return;
    }

    let pinned = pinButton.dataset.pinned === 'true';

    pinButton.addEventListener('click', function () {
        updatePinned(pinned ? 'DELETE' : 'PUT');
    });

    function updatePinned(method) {
        pinButton.disabled = true;

        fetch(`/api/articles/${articleId}/pin`, {
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
                alert('게시글 고정 상태를 변경하지 못했습니다');
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
        pinLabel.textContent = pinned ? '고정 해제' : '글 고정';
        pinnedCorner.classList.toggle('d-none', !pinned);
    }
})();
