// 게시글 좋아요 상태의 조회, 등록, 취소와 화면 표시를 관리하는 스크립트
(function () {
    const likeButton = document.getElementById('like-btn');
    const articleId = document.getElementById('article-id')?.value;
    const likeIcon = document.getElementById('like-icon');
    const likeCount = document.getElementById('like-count');
    const articleLikeCount = document.getElementById('article-like-count');
    const actionMessage = document.getElementById('article-action-message');

    if (!likeButton || !articleId || !likeIcon || !likeCount || !articleLikeCount) {
        return;
    }

    let liked = false;

    likeButton.addEventListener('click', function () {
        updateLike(liked ? 'DELETE' : 'PUT');
    });

    updateLike('GET', false);

    function updateLike(method, showError = true) {
        likeButton.disabled = true;

        window.csrfFetch(`/api/articles/${articleId}/likes`, {
            method,
            credentials: 'same-origin'
        })
            .then(response => {
                if (response.status === 401) {
                    location.replace('/login');
                    return null;
                }
                if (!response.ok) {
                    throw new Error('like request failed');
                }
                return response.json();
            })
            .then(state => {
                if (state) {
                    render(state);
                }
            })
            .catch(() => {
                if (showError) {
                    showActionMessage('좋아요를 변경하지 못했습니다. 잠시 후 다시 시도해주세요');
                }
            })
            .finally(() => {
                likeButton.disabled = false;
            });
    }

    function render(state) {
        liked = state.liked;
        likeIcon.textContent = '';
        likeIcon.classList.toggle('bi-heart', !liked);
        likeIcon.classList.toggle('bi-heart-fill', liked);
        likeCount.textContent = state.likeCount;
        articleLikeCount.textContent = state.likeCount;
        likeButton.classList.toggle('is-liked', liked);
        likeButton.setAttribute('aria-pressed', String(liked));
        likeButton.setAttribute('aria-label', liked ? '좋아요 취소' : '좋아요');
        hideActionMessage();
    }

    // 좋아요 오류를 팝업 대신 게시글 안에서 안내
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
