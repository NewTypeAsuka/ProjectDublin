// 스크롤 위치에 따라 공통 내비게이션의 고정 상태를 표시하는 스크립트
(function () {
    const siteHeader = document.querySelector('.site-header');
    const siteNav = document.getElementById('site-nav');

    if (!siteHeader || !siteNav) {
        return;
    }

    const syncStickyNavState = function () {
        siteNav.classList.toggle(
            'is-stuck',
            siteHeader.getBoundingClientRect().bottom <= 0
        );
    };

    syncStickyNavState();
    window.addEventListener('scroll', syncStickyNavState, { passive: true });
    window.addEventListener('resize', syncStickyNavState);
})();
