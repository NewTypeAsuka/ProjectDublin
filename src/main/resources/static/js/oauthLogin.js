// Google 로그인 스크립트
// 사용처: oauthLogin.html

// Google 로그인 버튼 클릭 시 로딩 상태를 표시하는 스크립트
const googleLoginLink = document.getElementById('google-login-link');
const googleLoginLoading = document.getElementById('google-login-loading');
if (googleLoginLink && googleLoginLoading) {
    googleLoginLink.addEventListener('click', (event) => {
        if (googleLoginLink.classList.contains('is-loading')) {
            event.preventDefault();
            return;
        }

        googleLoginLink.classList.add('is-loading');
        googleLoginLink.setAttribute('aria-disabled', 'true');
        googleLoginLink.setAttribute('aria-busy', 'true');
        googleLoginLoading.classList.add('is-visible');
    });
}
