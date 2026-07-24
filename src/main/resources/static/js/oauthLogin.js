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
        googleLoginLoading.classList.add('is-visible');
    });
}

// 로그인 안내 문구를 5초마다 부드럽게 교체하는 스크립트
const loginMessage = document.getElementById('login-message');
const loginMessages = [
    '구글 계정을 사용하여<br>로그인 해주세요',
    'NewTypeBlog는<br>구글 계정 정보를 필요로 합니다'
];
let loginMessageIndex = 0;

if (loginMessage) {
    setInterval(() => {
        loginMessage.classList.add('is-changing');

        setTimeout(() => {
            loginMessageIndex = (loginMessageIndex + 1) % loginMessages.length;
            loginMessage.innerHTML = loginMessages[loginMessageIndex];
            loginMessage.classList.remove('is-changing');
        }, 350);
    }, 5000);
}
