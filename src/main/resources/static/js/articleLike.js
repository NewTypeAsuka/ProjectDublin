// 현재 표시 언어로 게시글 좋아요 조회·변경과 화면 상태를 관리하는 스크립트
// 사용처: article.html

(function () {
    const likeButton = document.getElementById('like-btn');
    const articleId = document.getElementById('article-id')?.value;
    const likeIcon = document.getElementById('like-icon');
    const likeCount = document.getElementById('like-count');
    const articleLikeCount = document.getElementById('article-like-count');
    const actionMessage = document.getElementById('article-action-message');
    const messageConfig = document.getElementById('article-detail-messages');

    if (!likeButton || !articleId || !likeIcon || !likeCount || !articleLikeCount
            || !messageConfig) {
        return;
    }

    const messages = messageConfig.dataset;
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
                    showActionMessage(messages.likeError);
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
        likeButton.setAttribute(
            'aria-label',
            liked ? messages.likeRemoveAria : messages.likeAddAria
        );
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
