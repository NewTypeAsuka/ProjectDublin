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

    commentContent.addEventListener('input', function () {
        updateLengthCounter(commentContent, commentLength);
    });

    commentForm.addEventListener('submit', async function (event) {
        event.preventDefault();
        const content = validateContent(commentContent.value);
        if (!content) {
            return;
        }

        commentSubmit.disabled = true;
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
            commentSubmit.disabled = false;
        }
    });

    updateLengthCounter(commentContent, commentLength);
    loadComments();

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
        author.textContent = comment.commenterNickname;

        const date = document.createElement('time');
        date.className = 'comment-item__date';
        date.dateTime = comment.createdAt;
        date.textContent = formatDate(comment.createdAt)
            + (isEdited(comment) ? ' · 수정됨' : '');

        header.append(author, date);

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
                openInlineForm(item, '', '답글 등록', async value => {
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
                openInlineForm(item, comment.content, '수정 완료', async value => {
                    await request(`${commentsUrl}/${comment.id}`, {
                        method: 'PUT',
                        body: JSON.stringify({ content: value })
                    });
                    showMessage('댓글을 수정했습니다.', false);
                });
            }));
        }

        if (comment.deletable) {
            actions.appendChild(createActionButton('삭제', async function () {
                if (!window.confirm('댓글을 삭제하시겠습니까?')) {
                    return;
                }
                try {
                    await request(`${commentsUrl}/${comment.id}`, {
                        method: 'DELETE'
                    });
                    showMessage('댓글을 삭제했습니다.', false);
                    await loadComments();
                } catch (error) {
                    showMessage(error.message, true);
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
        button.className = `btn btn-link text-secondary ${extraClass}`.trim();
        button.textContent = label;
        button.addEventListener('click', clickHandler);
        return button;
    }

    function openInlineForm(container, initialContent, submitLabel, submitHandler) {
        closeInlineForms();

        const form = document.createElement('form');
        form.className = 'comment-inline-form';

        const textarea = document.createElement('textarea');
        textarea.className = 'form-control';
        textarea.value = initialContent;
        textarea.setAttribute('aria-label', submitLabel);

        const controls = document.createElement('div');
        controls.className = 'd-flex align-items-center justify-content-between mt-2';

        const counter = document.createElement('span');
        counter.className = 'small text-muted article-comments__counter';

        const buttons = document.createElement('div');

        const cancelButton = document.createElement('button');
        cancelButton.type = 'button';
        cancelButton.className = 'btn btn-outline-secondary btn-sm mr-2';
        cancelButton.textContent = '취소';
        cancelButton.addEventListener('click', function () {
            form.remove();
        });

        const submitButton = document.createElement('button');
        submitButton.type = 'submit';
        submitButton.className = 'btn btn-primary btn-sm';
        submitButton.textContent = submitLabel;

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
                return;
            }

            submitButton.disabled = true;
            cancelButton.disabled = true;
            try {
                await submitHandler(content);
                form.remove();
                await loadComments();
            } catch (error) {
                showMessage(error.message, true);
                submitButton.disabled = false;
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
    }

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

        const response = await fetch(url, requestOptions);
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
