const deleteButton = document.getElementById('delete-btn');

if (deleteButton) {
    deleteButton.addEventListener('click', () => {
        const id = document.getElementById('article-id').value;
        httpRequest('DELETE', `/api/articles/${id}`, null, () => {
            alert('삭제가 완료되었습니다.');
            location.replace('/articles');
        }, () => {
            alert('삭제에 실패했습니다.');
        });
    });
}

const modifyButton = document.getElementById('modify-btn');

if (modifyButton) {
    modifyButton.addEventListener('click', () => {
        const id = new URLSearchParams(location.search).get('id');
        const requestBody = createArticleRequestBody();
        if (!requestBody) {
            return;
        }

        httpRequest('PUT', `/api/articles/${id}`, requestBody, () => {
            alert('수정이 완료되었습니다.');
            location.replace(`/articles/${id}`);
        }, () => {
            alert('수정에 실패했습니다.');
        });
    });
}

const createButton = document.getElementById('create-btn');

if (createButton && document.getElementById('content')) {
    createButton.addEventListener('click', () => {
        const requestBody = createArticleRequestBody();
        if (!requestBody) {
            return;
        }

        httpRequest('POST', '/api/articles', requestBody, article => {
            alert('등록이 완료되었습니다.');
            location.replace(`/articles/${article.id}`);
        }, () => {
            alert('등록에 실패했습니다.');
        });
    });
}

function createArticleRequestBody() {
    const title = document.getElementById('title').value.trim();
    const content = window.articleEditor
        ? window.articleEditor.getHtml()
        : document.getElementById('content').value;

    const contentContainer = document.createElement('div');
    contentContainer.innerHTML = content;
    const hasContent = contentContainer.textContent.trim()
        || contentContainer.querySelector('iframe');

    if (!title || !hasContent) {
        alert('제목과 내용을 입력해주세요.');
        return null;
    }

    return JSON.stringify({ title, content });
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
