// 게시글 무한 스크롤
// 사용처: articleList.html

// 게시글 목록 무한 스크롤
document.addEventListener('DOMContentLoaded', () => {
    const feed = document.getElementById('article-feed');
    const sentinel = document.getElementById('article-feed-sentinel');
    const loading = document.getElementById('article-feed-loading');
    const message = document.getElementById('article-feed-message');
    const loadButton = document.getElementById('article-feed-load-button');

    if (!feed || !sentinel || !loading || !message || !loadButton) {
        return;
    }

    let nextCursor = feed.dataset.nextCursor || '';
    let hasNext = feed.dataset.hasNext === 'true';
    let isLoading = false;
    let observer = null;

    function createElement(tagName, className, text) {
        const element = document.createElement(tagName);
        if (className) {
            element.className = className;
        }
        if (text !== undefined) {
            element.textContent = text;
        }
        return element;
    }

    function createSeparator() {
        const separator = createElement('span', 'article-card__separator', '·');
        separator.setAttribute('aria-hidden', 'true');
        return separator;
    }

    function createMetric(title, iconClass, countClass, count) {
        const metric = createElement('span', 'article-card__metric');
        metric.title = title;

        const icon = createElement('i', iconClass);
        icon.setAttribute('aria-hidden', 'true');
        metric.append(icon);
        metric.append(createElement('span', 'sr-only', title));
        metric.append(createElement('span', countClass, String(count ?? 0)));
        return metric;
    }

    function formatCreatedAt(createdAt) {
        if (typeof createdAt !== 'string') {
            return '';
        }
        return createdAt.replace('T', ' ').slice(0, 16);
    }

    function isModified(article) {
        return typeof article.createdAt === 'string'
            && typeof article.updatedAt === 'string'
            && article.updatedAt > article.createdAt;
    }

    function createArticleCard(article) {
        const link = createElement('a', 'article-card-link');
        link.href = `/articles/${encodeURIComponent(article.id)}`;

        const card = createElement('article', 'card border-0 shadow-sm article-card');
        const cardBody = createElement('div', 'card-body p-4');
        const header = createElement(
            'div',
            'article-card__header text-muted small mb-3'
        );
        const articleNumber = createElement('div', 'article-card__number');

        if (article.pinned === true) {
            const pin = createElement(
                'i',
                'bi bi-pin-angle-fill article-card__pin'
            );
            pin.setAttribute('aria-hidden', 'true');
            articleNumber.append(pin);
            articleNumber.append(createElement('span', 'sr-only', '고정 게시글'));
        }
        articleNumber.append(createElement('span', '', String(article.id)));

        const byline = createElement('div', 'article-card__byline');
        const bylineMain = createElement('span', 'article-card__byline-main');
        bylineMain.append(document.createTextNode(
            `${formatCreatedAt(article.createdAt)} by `
        ));
        bylineMain.append(createElement('span', '', article.author || ''));

        if (article.authorAdmin === true) {
            const adminBadge = createElement(
                'i',
                'bi bi-patch-check-fill author-admin-badge'
            );
            adminBadge.setAttribute('role', 'img');
            adminBadge.setAttribute('aria-label', '관리자');
            adminBadge.title = '관리자';
            bylineMain.append(adminBadge);
        }
        byline.append(bylineMain);

        if (isModified(article)) {
            byline.append(createSeparator());
            byline.append(createElement(
                'span',
                'article-card__modified',
                '수정됨'
            ));
        }

        header.append(articleNumber, byline);

        const content = createElement('div');
        content.append(createElement(
            'h3',
            'h5 mb-2 article-card__title',
            article.title || ''
        ));
        content.append(createElement(
            'p',
            'mb-0 text-secondary text-break',
            article.content || ''
        ));

        const metrics = createElement(
            'div',
            'article-card__metrics text-muted small mt-3'
        );
        metrics.append(createMetric(
            '조회수',
            'bi bi-eye',
            'article-card__view-count',
            article.viewCount
        ));
        metrics.append(createSeparator());
        metrics.append(createMetric(
            '좋아요',
            'bi bi-heart-fill article-card__like-icon',
            'article-card__like-count',
            article.likeCount
        ));
        metrics.append(createSeparator());
        metrics.append(createMetric(
            '댓글',
            'bi bi-chat-dots',
            'article-card__comment-count',
            article.commentCount
        ));

        cardBody.append(header, content, metrics);
        card.append(cardBody);
        link.append(card);
        return link;
    }

    function setMessage(text) {
        message.textContent = text;
        message.hidden = !text;
    }

    function finishFeed() {
        hasNext = false;
        feed.dataset.hasNext = 'false';
        sentinel.hidden = true;
        loadButton.hidden = true;
        if (observer) {
            observer.disconnect();
        }
        if (feed.children.length > 0) {
            setMessage('모든 게시글을 불러왔습니다');
        }
    }

    async function loadNextArticles() {
        if (isLoading || !hasNext || !nextCursor) {
            return;
        }

        isLoading = true;
        feed.setAttribute('aria-busy', 'true');
        loading.hidden = false;
        loadButton.hidden = true;
        setMessage('');
        if (observer) {
            observer.unobserve(sentinel);
        }

        let requestSucceeded = false;
        try {
            const params = new URLSearchParams({
                cursor: nextCursor,
                size: '10'
            });
            const response = await fetch(`/api/articles/feed?${params}`, {
                headers: {
                    Accept: 'application/json'
                }
            });
            if (!response.ok) {
                throw new Error(`게시글 조회 실패: ${response.status}`);
            }

            const page = await response.json();
            if (!page || !Array.isArray(page.articles)) {
                throw new Error('게시글 응답 형식이 올바르지 않습니다');
            }

            const fragment = document.createDocumentFragment();
            page.articles.forEach(article => {
                fragment.append(createArticleCard(article));
            });
            feed.append(fragment);

            nextCursor = typeof page.nextCursor === 'string'
                ? page.nextCursor
                : '';
            hasNext = page.hasNext === true;
            feed.dataset.nextCursor = nextCursor;
            feed.dataset.hasNext = String(hasNext);
            requestSucceeded = true;

            if (!hasNext) {
                finishFeed();
            } else if (!nextCursor) {
                throw new Error('다음 게시글 커서가 없습니다');
            }
        } catch (error) {
            console.error(error);
            setMessage('게시글을 불러오지 못했습니다. 다시 시도해주세요');
            loadButton.textContent = '다시 시도';
            loadButton.hidden = false;
        } finally {
            isLoading = false;
            feed.removeAttribute('aria-busy');
            loading.hidden = true;

            if (requestSucceeded && hasNext) {
                if (observer) {
                    observer.observe(sentinel);
                } else {
                    loadButton.textContent = '게시글 더보기';
                    loadButton.hidden = false;
                }
            }
        }
    }

    loadButton.addEventListener('click', loadNextArticles);

    if (!hasNext || !nextCursor) {
        sentinel.hidden = true;
        return;
    }

    if ('IntersectionObserver' in window) {
        observer = new IntersectionObserver(entries => {
            if (entries.some(entry => entry.isIntersecting)) {
                loadNextArticles();
            }
        }, {
            rootMargin: '500px 0px'
        });
        observer.observe(sentinel);
    } else {
        loadButton.hidden = false;
    }
});
