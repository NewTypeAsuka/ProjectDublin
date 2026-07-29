// 게시글 작성·수정과 제목 40자 제한을 관리하는 스크립트
const modifyButton = document.getElementById('modify-btn');
const createButton = document.getElementById('create-btn');
const titleInput = document.getElementById('title');
const titleLength = document.getElementById('title-length');
const titleError = document.getElementById('title-error');
const maxTitleLength = Number(titleInput?.dataset.maxLength) || 40;

// 글 작성과 수정 화면에서 제목 글자 수를 실시간으로 표시(40자 제한)
if (titleInput && titleLength) {
    titleInput.addEventListener('input', updateTitleLengthCounter);
    updateTitleLengthCounter();
}

// 게시글 수정
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

// 게시글 등록
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

// 게시글 저장 요청 본문 생성
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

// 게시글 작성·수정 API 요청
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
