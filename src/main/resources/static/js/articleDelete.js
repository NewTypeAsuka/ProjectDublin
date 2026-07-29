// 게시글 삭제와 삭제 후 목록 이동을 관리하는 스크립트
(function () {
    const deleteButton = document.getElementById('delete-btn');
    const articleId = document.getElementById('article-id')?.value;

    if (!deleteButton || !articleId) {
        return;
    }

    deleteButton.addEventListener('click', async function () {
        if (!window.confirm('정말 삭제하시겠습니까?')) {
            return;
        }

        deleteButton.disabled = true;
        try {
            const response = await fetch(`/api/articles/${articleId}`, {
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

            alert('삭제가 완료되었습니다');
            location.replace('/articles');
        } catch (error) {
            alert('삭제에 실패했습니다');
            deleteButton.disabled = false;
        }
    });
})();
