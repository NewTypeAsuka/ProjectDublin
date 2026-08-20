// 인증 화면의 한국어·일본어 선택을 현재 URL에 반영하고 입력 중인 닉네임을 보호하는 스크립트
// 사용처: oauthLogin.html, nicknameSignup.html

document.addEventListener('DOMContentLoaded', () => {
    const nicknameInput = document.getElementById('nickname');
    const unsavedMessage = document.body.dataset.languageChangeConfirm;

    document.querySelectorAll('[data-language-toggle]:not(:disabled)').forEach(toggle => {
        toggle.addEventListener('click', event => {
            const currentLanguage = toggle.dataset.currentLanguage === 'ja' ? 'ja' : 'ko';
            const selectedOption = event.target.closest('[data-language]');
            const selectedLanguage = selectedOption?.dataset.language
                || (currentLanguage === 'ja' ? 'ko' : 'ja');

            if (selectedLanguage === currentLanguage) {
                return;
            }
            if (nicknameInput?.value.trim()
                    && unsavedMessage
                    && !window.confirm(unsavedMessage)) {
                return;
            }

            const targetUrl = new URL(window.location.href);
            targetUrl.searchParams.set('lang', selectedLanguage);
            window.location.assign(targetUrl.toString());
        });
    });
});
