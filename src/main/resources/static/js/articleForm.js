// 게시글 작성·수정 검증, 번역된 상태 문구, 제목 제한과 편집 상태를 관리하는 스크립트
// 사용처: newArticle.html

const articleForm = document.getElementById('article-form');
const modifyButton = document.getElementById('modify-btn');
const createButton = document.getElementById('create-btn');
const articleIdInput = document.getElementById('article-id');
const titleInput = document.getElementById('title');
const titleLength = document.getElementById('title-length');
const titleError = document.getElementById('title-error');
const formMessage = document.getElementById('article-form-message');
const contentField = document.getElementById('content');
const messageConfig = document.getElementById('article-form-messages');
const messages = messageConfig?.dataset;
const maxTitleLength = Number(titleInput?.dataset.maxLength) || 40;

// 글 작성과 수정 화면에서 제목 글자 수를 실시간으로 표시(40자 제한)
if (titleInput && titleLength) {
    titleInput.addEventListener('input', function () {
        updateTitleLengthCounter();
        markLanguageChangeDirty();
    });
    updateTitleLengthCounter();
}

// 본문 편집이나 Summernote 변경이 발생하면 언어 전환 전에 작성 내용 유실을 경고합니다.
contentField?.addEventListener('input', markLanguageChangeDirty);
window.addEventListener('article-editor-change', markLanguageChangeDirty);

// 게시글 등록·수정을 하나의 폼 제출 흐름으로 처리
if (articleForm && messages && (modifyButton || createButton)) {
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

        setFormBusy(submitButton, true);
        hideMessage();

        try {
            const article = await httpRequest(
                editing ? 'PUT' : 'POST',
                editing ? `/api/articles/${articleId}` : '/api/articles',
                requestBody
            );
            if (!editing && !article?.id) {
                throw new Error(messages.responseInvalid);
            }

            showMessage(editing ? messages.updated : messages.created, false);
            location.replace(editing ? `/articles/${articleId}` : `/articles/${article.id}`);
        } catch (error) {
            showMessage(error.message || messages.saveError, true);
            setFormBusy(submitButton, false);
        }
    });
}

// 게시글 저장 요청 본문 생성
function createArticleRequestBody() {
    if (window.articleEditor && window.articleEditor.isUploading()) {
        showMessage(messages.imageWait, true);
        return null;
    }

    const title = titleInput.value.trim();
    const content = window.articleEditor
        ? window.articleEditor.getHtml()
        : contentField.value;

    const contentContainer = document.createElement('div');
    contentContainer.innerHTML = content;
    const hasContent = contentContainer.textContent.trim()
        || contentContainer.querySelector('iframe, img');

    if (!title) {
        titleInput.setAttribute('aria-invalid', 'true');
        showMessage(messages.titleRequired, true);
        titleInput.focus();
        return null;
    }
    if (!hasContent) {
        showMessage(messages.contentRequired, true);
        const editableArea = document.querySelector('.note-editable');
        (editableArea || contentField).focus();
        return null;
    }
    if (countCharacters(title) > maxTitleLength) {
        showMessage(formatMessage(messages.titleLimit, maxTitleLength), true);
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

function formatMessage(template, ...values) {
    return values.reduce(
        (result, value, index) => result.split(`{${index}}`).join(String(value)),
        template
    );
}

function markLanguageChangeDirty() {
    if (document.body.hasAttribute('data-language-change-dirty')) {
        document.body.dataset.languageChangeDirty = 'true';
    }
}

// 저장 중 상태와 화면 내 성공·오류 피드백 표시
function setFormBusy(button, busy) {
    if (!button) {
        return;
    }

    if (!button.dataset.defaultContent) {
        button.dataset.defaultContent = button.innerHTML;
        button.dataset.defaultLabel = button.querySelector('span')?.textContent.trim()
            || button.textContent.trim();
    }
    button.disabled = busy;
    button.setAttribute('aria-busy', String(busy));

    if (!busy) {
        button.innerHTML = button.dataset.defaultContent;
        return;
    }

    const spinner = document.createElement('span');
    spinner.className = 'spinner-border spinner-border-sm';
    spinner.setAttribute('aria-hidden', 'true');

    const label = document.createElement('span');
    label.textContent = button.dataset.defaultLabel;
    button.replaceChildren(spinner, label);
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

    let response;
    try {
        response = await window.csrfFetch(url, options);
    } catch (error) {
        throw new Error(messages.saveError);
    }
    if (response.status === 401) {
        location.replace('/login');
        throw new Error(messages.loginRequired);
    }
    if (!response.ok) {
        if (response.status === 400) {
            throw new Error(messages.requestInvalid);
        }
        if (response.status === 403) {
            throw new Error(messages.permissionDenied);
        }
        throw new Error(messages.saveError);
    }

    const contentType = response.headers.get('content-type') || '';
    if (!contentType.includes('application/json')) {
        return null;
    }
    try {
        return await response.json();
    } catch (error) {
        throw new Error(messages.responseInvalid);
    }
}
