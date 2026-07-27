const deleteButton = document.getElementById('delete-btn'); // 글 삭제 버튼
const deleteDialog = document.getElementById('article-delete-dialog');
const deleteCancelButton = document.getElementById('article-delete-cancel');
const deleteConfirmButton = document.getElementById('article-delete-confirm');
const modifyButton = document.getElementById('modify-btn'); // 글 수정 버튼
const createButton = document.getElementById('create-btn'); // 글 등록 버튼
const titleInput = document.getElementById('title');
const titleLength = document.getElementById('title-length');
const titleError = document.getElementById('title-error');
const maxTitleLength = Number(titleInput?.dataset.maxLength) || 40;

// 글 작성과 수정 화면에서 제목 글자 수를 실시간으로 표시(40자 제한)
if (titleInput && titleLength) {
    titleInput.addEventListener('input', updateTitleLengthCounter);
    updateTitleLengthCounter();
}

// 삭제 버튼 클릭
if (deleteButton) {
    const deleteArticle = () => {
        const id = document.getElementById('article-id').value;
        httpRequest('DELETE', `/api/articles/${id}`, null, () => {
            alert('삭제가 완료되었습니다');
            location.replace('/articles');
        }, () => {
            if (deleteCancelButton && deleteConfirmButton) {
                deleteCancelButton.disabled = false;
                deleteConfirmButton.disabled = false;
            }
            alert('삭제에 실패했습니다');
        });
    };

    if (deleteDialog && deleteCancelButton && deleteConfirmButton) {
        let previousFocus = null;

        const closeDeleteDialog = () => {
            deleteDialog.classList.add('d-none');
            deleteDialog.setAttribute('aria-hidden', 'true');
            document.body.classList.remove('article-delete-dialog-open');
            previousFocus?.focus();
        };

        const openDeleteDialog = () => {
            previousFocus = document.activeElement;
            deleteDialog.classList.remove('d-none');
            deleteDialog.setAttribute('aria-hidden', 'false');
            document.body.classList.add('article-delete-dialog-open');
            deleteCancelButton.focus();
        };

        deleteButton.addEventListener('click', openDeleteDialog);
        deleteCancelButton.addEventListener('click', closeDeleteDialog);
        deleteConfirmButton.addEventListener('click', () => {
            deleteCancelButton.disabled = true;
            deleteConfirmButton.disabled = true;
            deleteArticle();
        });
        deleteDialog.addEventListener('click', event => {
            if (event.target === deleteDialog) {
                closeDeleteDialog();
            }
        });
        document.addEventListener('keydown', event => {
            if (deleteDialog.classList.contains('d-none')) {
                return;
            }
            if (event.key === 'Escape') {
                event.preventDefault();
                closeDeleteDialog();
                return;
            }
            if (event.key !== 'Tab') {
                return;
            }
            if (event.shiftKey && document.activeElement === deleteCancelButton) {
                event.preventDefault();
                deleteConfirmButton.focus();
            } else if (!event.shiftKey && document.activeElement === deleteConfirmButton) {
                event.preventDefault();
                deleteCancelButton.focus();
            }
        });
    } else {
        deleteButton.addEventListener('click', () => {
            if (window.confirm('정말 삭제하시겠습니까?')) {
                deleteArticle();
            }
        });
    }
}

// 수정 버튼 클릭
if (modifyButton) {
    modifyButton.addEventListener('click', () => {
        const id = new URLSearchParams(location.search).get('id');
        const requestBody = createArticleRequestBody();
        if (!requestBody) {
            return;
        }

        httpRequest('PUT', `/api/articles/${id}`, requestBody, () => {
            alert('수정이 완료되었습니다');
            location.replace(`/articles/${id}`);
        }, () => {
            alert('수정에 실패했습니다');
        });
    });
}

// 등록 버튼 클릭
if (createButton && document.getElementById('content')) {
    createButton.addEventListener('click', () => {
        const requestBody = createArticleRequestBody();
        if (!requestBody) {
            return;
        }

        httpRequest('POST', '/api/articles', requestBody, article => {
            alert('등록이 완료되었습니다');
            location.replace(`/articles/${article.id}`);
        }, () => {
            alert('등록에 실패했습니다');
        });
    });
}

function createArticleRequestBody() {
    if (window.articleEditor && window.articleEditor.isUploading()) {
        alert('이미지 업로드가 완료된 후 저장해주세요');
        return null;
    }

    const title = titleInput.value.trim();
    const content = window.articleEditor
        ? window.articleEditor.getHtml()
        : document.getElementById('content').value;

    const contentContainer = document.createElement('div');
    contentContainer.innerHTML = content;
    const hasContent = contentContainer.textContent.trim()
        || contentContainer.querySelector('iframe, img');

    if (!title || !hasContent) {
        alert('제목과 내용을 입력해주세요');
        return null;
    }
    if (countCharacters(title) > maxTitleLength) {
        alert(`제목은 ${maxTitleLength}자 이내로 작성해주세요`);
        return null;
    }

    return JSON.stringify({ title, content });
}

function updateTitleLengthCounter() {
    const length = countCharacters(titleInput.value);
    const isOverLimit = length > maxTitleLength;

    titleLength.textContent = `${length} / ${maxTitleLength}`;
    titleLength.classList.toggle('text-danger', isOverLimit);
    titleLength.classList.toggle('text-muted', !isOverLimit);
    titleInput.setAttribute('aria-invalid', String(isOverLimit));

    if (titleError) {
        titleError.classList.toggle('d-none', !isOverLimit);
        titleError.setAttribute('aria-hidden', String(!isOverLimit));
    }
}

function countCharacters(value) {
    return Array.from(value).length;
}

function httpRequest(method, url, body, success, fail) {
    const options = {
        method,
        credentials: 'same-origin',
        headers: {
            'Content-Type': 'application/json'
        }
    };

    if (body) {
        options.body = body;
    }

    fetch(url, options)
        .then(async response => {
            if (response.ok) {
                const contentType = response.headers.get('content-type') || '';
                const responseBody = contentType.includes('application/json')
                    ? await response.json()
                    : null;
                success(responseBody);
                return;
            }

            if (response.status === 401) {
                location.replace('/login');
                return;
            }

            fail();
        })
        .catch(fail);
}
