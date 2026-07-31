// 게시글 작성·수정과 제목 40자 제한을 관리하는 스크립트
const articleForm = document.getElementById('article-form');
const modifyButton = document.getElementById('modify-btn');
const createButton = document.getElementById('create-btn');
const articleIdInput = document.getElementById('article-id');
const titleInput = document.getElementById('title');
const titleLength = document.getElementById('title-length');
const titleError = document.getElementById('title-error');
const formMessage = document.getElementById('article-form-message');
const maxTitleLength = Number(titleInput?.dataset.maxLength) || 40;

// 글 작성과 수정 화면에서 제목 글자 수를 실시간으로 표시(40자 제한)
if (titleInput && titleLength) {
    titleInput.addEventListener('input', updateTitleLengthCounter);
    updateTitleLengthCounter();
}

// 게시글 등록·수정을 하나의 폼 제출 흐름으로 처리
if (articleForm && (modifyButton || createButton)) {
    articleForm.addEventListener('submit', async event => {
        event.preventDefault();
        const requestBody = createArticleRequestBody();
        if (!requestBody) {
            return;
        }

        const articleId = articleIdInput?.value
            || new URLSearchParams(location.search).get('id');
        const editing = Boolean(modifyButton && articleId);
        const submitButton = editing ? modifyButton : createButton;

        setFormBusy(submitButton, true, editing ? '수정하는 중' : '발행하는 중');
        hideMessage();

        try {
            const article = await httpRequest(
                editing ? 'PUT' : 'POST',
                editing ? `/api/articles/${articleId}` : '/api/articles',
                requestBody
            );
            if (!editing && !article?.id) {
                throw new Error('글 정보를 확인하지 못했습니다. 잠시 후 다시 시도해주세요.');
            }

            showMessage(editing ? '수정을 완료했습니다.' : '글을 발행했습니다.', false);
            location.replace(editing ? `/articles/${articleId}` : `/articles/${article.id}`);
        } catch (error) {
            showMessage(error.message || '글을 저장하지 못했습니다. 잠시 후 다시 시도해주세요.', true);
            setFormBusy(submitButton, false);
        }
    });
}

// 게시글 저장 요청 본문 생성
function createArticleRequestBody() {
    if (window.articleEditor && window.articleEditor.isUploading()) {
        showMessage('이미지 업로드가 완료된 뒤 저장해주세요.', true);
        return null;
    }

    const title = titleInput.value.trim();
    const contentField = document.getElementById('content');
    const content = window.articleEditor
        ? window.articleEditor.getHtml()
        : contentField.value;

    const contentContainer = document.createElement('div');
    contentContainer.innerHTML = content;
    const hasContent = contentContainer.textContent.trim()
        || contentContainer.querySelector('iframe, img');

    if (!title) {
        titleInput.setAttribute('aria-invalid', 'true');
        showMessage('제목을 입력해주세요.', true);
        titleInput.focus();
        return null;
    }
    if (!hasContent) {
        showMessage('본문 내용을 입력해주세요.', true);
        const editableArea = document.querySelector('.note-editable');
        (editableArea || contentField).focus();
        return null;
    }
    if (countCharacters(title) > maxTitleLength) {
        showMessage(`제목은 ${maxTitleLength}자 이내로 작성해주세요.`, true);
        titleInput.focus();
        return null;
    }

    return JSON.stringify({ title, content });
}

// 제목 글자 수와 제한 초과 안내 갱신
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

// 저장 중 상태와 화면 내 성공·오류 피드백 표시
function setFormBusy(button, busy, label) {
    if (!button) {
        return;
    }

    if (!button.dataset.defaultContent) {
        button.dataset.defaultContent = button.innerHTML;
    }
    button.disabled = busy;
    button.setAttribute('aria-busy', String(busy));
    button.innerHTML = busy
        ? `<span class="spinner-border spinner-border-sm" aria-hidden="true"></span><span>${label}</span>`
        : button.dataset.defaultContent;
}

function showMessage(message, error) {
    if (!formMessage) {
        return;
    }

    formMessage.textContent = message;
    formMessage.classList.remove('d-none', 'alert-danger', 'alert-success');
    formMessage.classList.add(error ? 'alert-danger' : 'alert-success');
    formMessage.setAttribute('role', error ? 'alert' : 'status');
}

function hideMessage() {
    if (!formMessage) {
        return;
    }
    formMessage.classList.add('d-none');
    formMessage.classList.remove('alert-danger', 'alert-success');
}

// 게시글 작성·수정 API 요청
async function httpRequest(method, url, body) {
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

    const response = await fetch(url, options);
    if (response.status === 401) {
        location.replace('/login');
        throw new Error('로그인이 필요합니다.');
    }
    if (!response.ok) {
        if (response.status === 400) {
            throw new Error('제목과 본문 내용을 다시 확인해주세요.');
        }
        if (response.status === 403) {
            throw new Error('글을 저장할 권한이 없습니다.');
        }
        throw new Error('글을 저장하지 못했습니다. 잠시 후 다시 시도해주세요.');
    }

    const contentType = response.headers.get('content-type') || '';
    return contentType.includes('application/json')
        ? response.json()
        : null;
}
