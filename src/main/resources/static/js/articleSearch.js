// 게시글 검색 입력창의 표시·제출과 번역된 접근성 문구를 관리하는 스크립트
// 사용처: articleList.html

(function () {
    const searchControl = document.querySelector('[data-article-search]');
    const messageConfig = document.getElementById('article-list-messages');

    if (!searchControl || !messageConfig) {
        return;
    }

    const searchInput = searchControl.querySelector('.article-list-search__input');
    const searchToggle = searchControl.querySelector('.article-list-search__toggle');

    if (!searchInput || !searchToggle) {
        return;
    }

    const messages = messageConfig.dataset;
    const maxSearchLength = Number(searchInput.dataset.maxLength) || 15;

    // 검색창의 표시 상태와 키보드 접근성을 함께 관리합니다.
    const setSearchOpen = function (open, focusInput) {
        searchControl.classList.toggle('is-open', open);
        searchToggle.setAttribute('aria-expanded', String(open));
        const toggleLabel = open ? messages.searchAction : messages.searchOpen;
        searchToggle.setAttribute('aria-label', toggleLabel);
        searchToggle.title = toggleLabel;
        searchInput.tabIndex = open ? 0 : -1;
        searchInput.setAttribute('aria-hidden', String(!open));

        if (open && focusInput) {
            window.requestAnimationFrame(function () {
                searchInput.focus();
            });
        }
    };

    // 붙여넣기를 포함해 검색어가 15자를 넘지 못하도록 입력 단계에서 잘라냅니다.
    searchInput.addEventListener('input', function () {
        const characters = Array.from(searchInput.value);
        if (characters.length > maxSearchLength) {
            searchInput.value = characters.slice(0, maxSearchLength).join('');
        }
    });

    searchControl.addEventListener('submit', function (event) {
        if (searchToggle.getAttribute('aria-expanded') !== 'true') {
            event.preventDefault();
            setSearchOpen(true, true);
            return;
        }

        // 검색어의 앞뒤 공백을 제거한 뒤 GET /articles 요청으로 제출합니다.
        searchInput.value = Array.from(searchInput.value.trim())
            .slice(0, maxSearchLength)
            .join('');
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

    // 검색 결과 페이지에서는 현재 검색어가 보이도록 입력창을 열린 상태로 유지합니다.
    setSearchOpen(searchControl.classList.contains('is-open'), false);
})();
