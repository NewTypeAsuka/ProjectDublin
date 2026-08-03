// 게시글 검색 입력창 표시
// 사용처: articleList.html

(function () {
    const searchControl = document.querySelector('[data-article-search]');

    if (!searchControl) {
        return;
    }

    const searchInput = searchControl.querySelector('.article-list-search__input');
    const searchToggle = searchControl.querySelector('.article-list-search__toggle');

    if (!searchInput || !searchToggle) {
        return;
    }

    // 실제 검색 기능을 연결하기 전까지 입력창의 표시 상태만 관리합니다.
    const setSearchOpen = function (open, focusInput) {
        searchControl.classList.toggle('is-open', open);
        searchToggle.setAttribute('aria-expanded', String(open));
        searchToggle.setAttribute('aria-label', open ? '검색' : '검색창 열기');
        searchToggle.title = open ? '검색' : '검색창 열기';
        searchInput.tabIndex = open ? 0 : -1;
        searchInput.setAttribute('aria-hidden', String(!open));

        if (open && focusInput) {
            window.requestAnimationFrame(function () {
                searchInput.focus();
            });
        }
    };

    searchControl.addEventListener('submit', function (event) {
        // 백엔드 검색을 연결하기 전에는 폼 제출로 페이지가 이동하지 않게 합니다.
        event.preventDefault();

        if (searchToggle.getAttribute('aria-expanded') !== 'true') {
            setSearchOpen(true, true);
            return;
        }

        if (!searchInput.value.trim()) {
            searchInput.focus();
        }
    });

    document.addEventListener('click', function (event) {
        if (searchToggle.getAttribute('aria-expanded') === 'true'
            && !searchControl.contains(event.target)) {
            setSearchOpen(false, false);
        }
    });

    document.addEventListener('keydown', function (event) {
        if (event.key !== 'Escape'
            || searchToggle.getAttribute('aria-expanded') !== 'true') {
            return;
        }

        setSearchOpen(false, false);
        searchToggle.focus();
    });
})();
