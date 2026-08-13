// 게시글 삭제와 삭제 후 목록 이동을 관리하는 스크립트
// 사용처: article.html

(function () {
    const deleteButton = document.getElementById('delete-btn');
    const articleId = document.getElementById('article-id')?.value;
    const actionMessage = document.getElementById('article-action-message');

    if (!deleteButton || !articleId) {
        return;
    }

    deleteButton.addEventListener('click', async function () {
        if (!window.confirm('정말 삭제하시겠습니까?')) {
            return;
        }

        const defaultContent = deleteButton.innerHTML;
        const defaultLabel = deleteButton.querySelector('span')?.textContent.trim()
            || deleteButton.textContent.trim();
        deleteButton.disabled = true;
        deleteButton.innerHTML = `<span class="spinner-border spinner-border-sm" aria-hidden="true"></span><span>${defaultLabel}</span>`;
        deleteButton.setAttribute('aria-busy', 'true');
        try {
            const response = await window.csrfFetch(`/api/articles/${articleId}`, {
                method: 'DELETE',
                credentials: 'same-origin'
            });
            if (response.status === 401) {
                location.replace('/login');
                return;
            }
            if (!response.ok) {
                throw new Error('article delete failed');
            }

            location.replace('/articles');
        } catch (error) {
            showActionMessage('게시글을 삭제하지 못했습니다. 잠시 후 다시 시도해주세요.');
            deleteButton.disabled = false;
            deleteButton.innerHTML = defaultContent;
            deleteButton.setAttribute('aria-busy', 'false');
        }
    });

    // 삭제 실패를 팝업 대신 게시글 안에서 안내
    function showActionMessage(message) {
        if (!actionMessage) {
            return;
        }
        actionMessage.textContent = message;
        actionMessage.classList.remove('d-none', 'alert-success');
        actionMessage.classList.add('alert-danger');
        actionMessage.setAttribute('role', 'alert');
    }
})();
