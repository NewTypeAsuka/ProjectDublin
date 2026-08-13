// 댓글·대댓글의 조회, 작성, 수정, 삭제와 화면 렌더링을 관리하는 스크립트
// 사용처: article.html

(function () {
    const articleId = document.getElementById('article-id')?.value;
    const commentForm = document.getElementById('comment-form');
    const commentContent = document.getElementById('comment-content');
    const commentSubmit = document.getElementById('comment-submit');
    const commentLength = document.getElementById('comment-length');
    const commentCount = document.getElementById('comment-count');
    const articleCommentCount = document.getElementById('article-comment-count');
    const commentMessage = document.getElementById('comment-message');
    const commentLoading = document.getElementById('comment-loading');
    const commentList = document.getElementById('comment-list');
    const commentEmpty = document.getElementById('comment-empty');

    if (!articleId
        || !commentForm
        || !commentContent
        || !commentSubmit
        || !commentLength
        || !commentCount
        || !articleCommentCount
        || !commentMessage
        || !commentLoading
        || !commentList
        || !commentEmpty) {
        return;
    }

    const maxContentLength = Number(commentContent.dataset.maxLength) || 1000;
    const commentsUrl = `/api/articles/${articleId}/comments`;

    // 새 댓글 작성과 글자 수 표시
    commentContent.addEventListener('input', function () {
        updateLengthCounter(commentContent, commentLength);
    });

    commentForm.addEventListener('submit', async function (event) {
        event.preventDefault();
        const content = validateContent(commentContent.value);
        if (!content) {
            commentContent.focus();
            return;
        }

        setButtonBusy(commentSubmit, true);
        try {
            await request(commentsUrl, {
                method: 'POST',
                body: JSON.stringify({ content })
            });
            commentContent.value = '';
            updateLengthCounter(commentContent, commentLength);
            showMessage('댓글을 등록했습니다.', false);
            await loadComments();
        } catch (error) {
            showMessage(error.message, true);
        } finally {
            setButtonBusy(commentSubmit, false);
        }
    });

    updateLengthCounter(commentContent, commentLength);
    loadComments();

    // 댓글 목록 조회
    async function loadComments() {
        commentLoading.classList.remove('d-none');
        commentList.classList.add('d-none');
        commentEmpty.classList.add('d-none');

        try {
            const comments = await request(commentsUrl);
            renderComments(comments);
        } catch (error) {
            showMessage(error.message, true);
            commentList.replaceChildren();
            commentCount.textContent = '0';
        } finally {
            commentLoading.classList.add('d-none');
        }
    }

    // 댓글·대댓글 목록 렌더링
    function renderComments(comments) {
        commentList.replaceChildren();

        let activeCommentCount = 0;
        comments.forEach(comment => {
            if (!comment.deleted) {
                activeCommentCount += 1;
            }
            activeCommentCount += comment.replies.filter(reply => !reply.deleted).length;
            commentList.appendChild(createCommentElement(comment, false));
        });

        commentCount.textContent = String(activeCommentCount);
        articleCommentCount.textContent = String(activeCommentCount);
        commentList.classList.toggle('d-none', comments.length === 0);
        commentEmpty.classList.toggle('d-none', comments.length !== 0);
    }

    function createCommentElement(comment, reply) {
        const item = document.createElement('article');
        item.className = reply ? 'comment-item comment-item--reply' : 'comment-item';
        item.dataset.commentId = String(comment.id);

        const header = document.createElement('div');
        header.className = 'comment-item__header';

        const author = document.createElement('span');
        author.className = 'comment-item__author';
        const nickname = document.createElement('span');
        nickname.className = 'user-nickname';
        nickname.dataset.userId = String(comment.commenterId);
        nickname.textContent = comment.commenterNickname;
        author.appendChild(nickname);

        // 관리자 댓글 작성자의 이름 뒤에 관리자 아이콘을 추가
        if (comment.commenterAdmin) {
            const adminBadge = document.createElement('i');
            adminBadge.className = 'bi bi-patch-check-fill author-admin-badge';
            adminBadge.setAttribute('role', 'img');
            adminBadge.setAttribute('aria-label', '관리자');
            adminBadge.title = '관리자';
            author.appendChild(adminBadge);
        }

        const dateMeta = document.createElement('span');
        dateMeta.className = 'comment-item__date';

        const date = document.createElement('time');
        date.dateTime = comment.createdAt;
        date.textContent = formatDate(comment.createdAt);
        dateMeta.appendChild(date);

        if (isEdited(comment)) {
            const modifiedMarker = document.createElement('span');
            modifiedMarker.className = 'comment-item__modified-marker';
            modifiedMarker.textContent = '*';
            modifiedMarker.setAttribute('role', 'img');
            modifiedMarker.setAttribute('aria-label', '수정됨');
            modifiedMarker.title = '수정됨';
            dateMeta.appendChild(modifiedMarker);
        }

        header.append(author, dateMeta);

        const content = document.createElement('p');
        content.className = 'comment-item__content';
        if (comment.deleted) {
            content.classList.add('comment-item__content--deleted');
        }
        content.textContent = comment.content;

        item.append(header, content);

        const actions = document.createElement('div');
        actions.className = 'comment-item__actions';

        if (!reply && !comment.deleted) {
            actions.appendChild(createActionButton('답글', function () {
                openInlineForm(item, '', '등록', async value => {
                    await request(`${commentsUrl}/${comment.id}/replies`, {
                        method: 'POST',
                        body: JSON.stringify({ content: value })
                    });
                    showMessage('답글을 등록했습니다.', false);
                });
            }));
        }

        if (comment.editable) {
            actions.appendChild(createActionButton('수정', function () {
                openInlineForm(item, comment.content, '수정', async value => {
                    await request(`${commentsUrl}/${comment.id}`, {
                        method: 'PUT',
                        body: JSON.stringify({ content: value })
                    });
                    showMessage('댓글을 수정했습니다.', false);
                });
            }));
        }

        if (comment.deletable) {
            actions.appendChild(createActionButton('삭제', async function (deleteButton) {
                if (!window.confirm('댓글을 삭제하시겠습니까?')) {
                    return;
                }
                setButtonBusy(deleteButton, true);
                try {
                    await request(`${commentsUrl}/${comment.id}`, {
                        method: 'DELETE'
                    });
                    showMessage('댓글을 삭제했습니다.', false);
                    await loadComments();
                } catch (error) {
                    showMessage(error.message, true);
                } finally {
                    setButtonBusy(deleteButton, false);
                }
            }, 'text-danger'));
        }

        if (actions.childElementCount > 0) {
            item.appendChild(actions);
        }

        if (!reply && comment.replies.length > 0) {
            const replies = document.createElement('div');
            replies.className = 'comment-replies';
            comment.replies.forEach(child => {
                replies.appendChild(createCommentElement(child, true));
            });
            item.appendChild(replies);
        }

        return item;
    }

    function createActionButton(label, clickHandler, extraClass = '') {
        const button = document.createElement('button');
        button.type = 'button';
        button.className = `btn btn-link app-button text-secondary ${extraClass}`.trim();
        const iconClasses = {
            '답글': 'bi-reply',
            '수정': 'bi-pencil',
            '삭제': 'bi-trash3'
        };
        setButtonContent(button, iconClasses[label], label);
        button.addEventListener('click', function () {
            clickHandler(button);
        });
        return button;
    }

    // 동적으로 생성되는 액션 버튼도 Bootstrap 아이콘과 문구를 함께 표시합니다.
    function setButtonContent(button, iconClass, label) {
        const icon = document.createElement('i');
        icon.className = `bi ${iconClass}`;
        icon.setAttribute('aria-hidden', 'true');

        const text = document.createElement('span');
        text.textContent = label;
        button.replaceChildren(icon, text);
    }

    // 비동기 처리 중에는 아이콘만 스피너로 바꾸고 버튼 문구는 유지합니다.
    function setButtonBusy(button, busy) {
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

    // 답글 작성과 댓글 수정에 공통으로 사용하는 인라인 폼
    function openInlineForm(container, initialContent, submitLabel, submitHandler) {
        closeInlineForms();

        const form = document.createElement('form');
        form.className = 'comment-inline-form';

        const textarea = document.createElement('textarea');
        textarea.className = 'form-control';
        textarea.value = initialContent;
        textarea.setAttribute('aria-label', submitLabel);

        const controls = document.createElement('div');
        controls.className = 'd-flex align-items-center justify-content-between flex-wrap mt-2 comment-inline-form__controls';

        const counter = document.createElement('span');
        counter.className = 'small text-muted article-comments__counter';

        const buttons = document.createElement('div');
        buttons.className = 'comment-inline-form__actions';

        const cancelButton = document.createElement('button');
        cancelButton.type = 'button';
        cancelButton.className = 'btn btn-outline-secondary btn-sm app-button comment-inline-form__button';
        setButtonContent(cancelButton, 'bi-x-lg', '취소');
        cancelButton.addEventListener('click', function () {
            form.remove();
        });

        const submitButton = document.createElement('button');
        submitButton.type = 'submit';
        submitButton.className = 'btn btn-primary btn-sm app-button comment-inline-form__button';
        const submitIconClass = submitLabel === '등록'
            ? 'bi-reply'
            : 'bi-check2';
        setButtonContent(submitButton, submitIconClass, submitLabel);

        buttons.append(cancelButton, submitButton);
        controls.append(counter, buttons);
        form.append(textarea, controls);

        textarea.addEventListener('input', function () {
            updateLengthCounter(textarea, counter);
        });

        form.addEventListener('submit', async function (event) {
            event.preventDefault();
            const content = validateContent(textarea.value);
            if (!content) {
                textarea.focus();
                return;
            }

            setButtonBusy(submitButton, true);
            cancelButton.disabled = true;
            try {
                await submitHandler(content);
                form.remove();
                await loadComments();
            } catch (error) {
                showMessage(error.message, true);
                setButtonBusy(submitButton, false);
                cancelButton.disabled = false;
            }
        });

        const replies = container.querySelector(':scope > .comment-replies');
        if (replies) {
            container.insertBefore(form, replies);
        } else {
            container.appendChild(form);
        }

        updateLengthCounter(textarea, counter);
        textarea.focus();
        textarea.setSelectionRange(textarea.value.length, textarea.value.length);
    }

    function closeInlineForms() {
        document.querySelectorAll('.comment-inline-form').forEach(form => form.remove());
    }

    // 댓글 내용과 1000자 제한 검증
    function validateContent(value) {
        const content = value.trim();
        const length = countCharacters(content);

        if (!content) {
            showMessage('댓글 내용을 입력해주세요.', true);
            return null;
        }
        if (length > maxContentLength) {
            showMessage(`댓글은 ${maxContentLength}자 이하로 작성해주세요.`, true);
            return null;
        }
        return content;
    }

    function updateLengthCounter(textarea, counter) {
        const length = countCharacters(textarea.value);
        counter.textContent = `${length} / ${maxContentLength}`;
        counter.classList.toggle('text-danger', length > maxContentLength);
        counter.classList.toggle('text-muted', length <= maxContentLength);
        textarea.setAttribute('aria-invalid', String(length > maxContentLength));
    }

    function countCharacters(value) {
        return Array.from(value).length;
    }

    function formatDate(value) {
        if (!value) {
            return '';
        }
        return value.replace('T', ' ').slice(0, 16);
    }

    function isEdited(comment) {
        if (!comment.createdAt || !comment.updatedAt) {
            return false;
        }
        return comment.createdAt.slice(0, 19) !== comment.updatedAt.slice(0, 19);
    }

    function showMessage(message, error) {
        commentMessage.textContent = message;
        commentMessage.classList.remove('d-none', 'alert-danger', 'alert-success');
        commentMessage.classList.add(error ? 'alert-danger' : 'alert-success');
        commentMessage.setAttribute('role', error ? 'alert' : 'status');
    }

    // 댓글 API 요청과 오류 메시지 처리
    async function request(url, options = {}) {
        const requestOptions = {
            method: options.method || 'GET',
            credentials: 'same-origin',
            headers: {}
        };

        if (options.body) {
            requestOptions.headers['Content-Type'] = 'application/json';
            requestOptions.body = options.body;
        }

        const response = await window.csrfFetch(url, requestOptions);
        if (response.status === 401) {
            location.replace('/login');
            throw new Error('로그인이 필요합니다.');
        }
        if (!response.ok) {
            if (response.status === 403) {
                throw new Error('댓글을 변경할 권한이 없습니다.');
            }
            if (response.status === 400) {
                throw new Error('댓글 요청 내용을 확인해주세요.');
            }
            throw new Error('댓글 처리에 실패했습니다.');
        }
        if (response.status === 204) {
            return null;
        }
        return response.json();
    }
})();
