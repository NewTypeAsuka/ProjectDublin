(function () {
    const likeButton = document.getElementById('like-btn');
    const articleId = document.getElementById('article-id')?.value;
    const likeIcon = document.getElementById('like-icon');
    const likeCount = document.getElementById('like-count');

    if (!likeButton || !articleId || !likeIcon || !likeCount) {
        return;
    }

    let liked = false;

    likeButton.addEventListener('click', function () {
        updateLike(liked ? 'DELETE' : 'PUT');
    });

    updateLike('GET', false);

    function updateLike(method, showError = true) {
        likeButton.disabled = true;

        fetch(`/api/articles/${articleId}/likes`, {
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
                    alert('좋아요 처리에 실패했습니다');
                }
            })
            .finally(() => {
                likeButton.disabled = false;
            });
    }

    function render(state) {
        liked = state.liked;
        likeIcon.textContent = liked ? '♥' : '♡';
        likeCount.textContent = state.likeCount;
        likeButton.classList.toggle('is-liked', liked);
        likeButton.setAttribute('aria-pressed', String(liked));
        likeButton.setAttribute('aria-label', liked ? '좋아요 취소' : '좋아요');
    }
})();
