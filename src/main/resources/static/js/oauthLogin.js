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

// 로그인 안내 문구를 5초마다 부드럽게 교체하는 스크립트
const loginMessage = document.getElementById('login-message');
const loginMessages = [
    'Google 계정 하나로 안전하고 간편하게 블로그를 계속 이용할 수 있어요.',
    '기록하던 생각과 새로운 이야기가 당신을 기다리고 있어요.' // 로그인 안내 문구
];
let loginMessageIndex = 0;
const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

if (loginMessage && !reduceMotion) {
    setInterval(() => {
        loginMessage.classList.add('is-changing');

        setTimeout(() => {
            loginMessageIndex = (loginMessageIndex + 1) % loginMessages.length;
            loginMessage.textContent = loginMessages[loginMessageIndex];
            loginMessage.classList.remove('is-changing');
        }, 500); // 0.8초 후에 문구 교체
    }, 10000); // 10초마다 문구 교체
}
