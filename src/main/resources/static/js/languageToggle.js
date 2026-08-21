// 한국어·일본어 선택을 현재 URL에 반영하고 언어 변경 시 작성 중인 입력을 보호하는 스크립트
// 사용처: common.html (chat.html, stocks.html에 선택 적용), oauthLogin.html, nicknameSignup.html

document.addEventListener('DOMContentLoaded', () => {
    const nicknameInput = document.getElementById('nickname');
    const guardedInputs = Array.from(
        document.querySelectorAll('[data-language-change-guard]')
    );
    if (nicknameInput && !guardedInputs.includes(nicknameInput)) {
        guardedInputs.push(nicknameInput);
    }
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
            const hasUnsavedInput = guardedInputs.some(input => input.value.trim());
            if (hasUnsavedInput
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
