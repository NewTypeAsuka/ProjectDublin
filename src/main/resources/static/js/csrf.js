// 동일 출처의 상태 변경 요청에 Spring Security CSRF 토큰을 추가하는 스크립트
// 사용처: common.html (articleList.html, article.html, newArticle.html, oauthLogin.html에 공통 적용)

(function () {
    const safeMethods = new Set(['GET', 'HEAD', 'OPTIONS', 'TRACE']);

    window.csrfFetch = function (input, options = {}) {
        const requestInput = input instanceof Request;
        const method = String(
            options.method || (requestInput ? input.method : 'GET')
        ).toUpperCase();
        const requestUrl = new URL(
            requestInput ? input.url : String(input),
            window.location.href
        );
        const requestOptions = {
            ...options,
            credentials: options.credentials || 'same-origin'
        };

        if (!safeMethods.has(method)) {
            if (requestUrl.origin !== window.location.origin) {
                return Promise.reject(new Error('Cross-origin mutation is not allowed'));
            }

            const token = document.querySelector('meta[name="_csrf"]')?.content;
            const headerName = document.querySelector('meta[name="_csrf_header"]')?.content;
            if (!token || !headerName) {
                return Promise.reject(new Error('CSRF token is unavailable'));
            }

            const headers = new Headers(
                options.headers || (requestInput ? input.headers : {})
            );
            headers.set(headerName, token);
            requestOptions.headers = headers;
        }

        return window.fetch(input, requestOptions);
    };
})();
