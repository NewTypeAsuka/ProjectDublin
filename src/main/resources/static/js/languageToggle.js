// 한국어·일본어 선택을 현재 URL에 반영하고 언어 변경 시 작성 중인 입력을 보호하는 스크립트
// 사용처: common.html (articleList.html, article.html, newArticle.html, chat.html, stocks.html 등에 공통 적용), oauthLogin.html, nicknameSignup.html

document.addEventListener('DOMContentLoaded', () => {
    const nicknameInput = document.getElementById('nickname');
    const unsavedMessage = document.body.dataset.languageChangeConfirm;

    // 편집 화면은 초기값과 사용자가 실제로 변경한 상태를 구분하고, 다른 화면은 현재 입력값을 확인합니다.
    const hasUnsavedInput = function () {
        if (document.body.hasAttribute('data-language-change-dirty')) {
            return document.body.dataset.languageChangeDirty === 'true';
        }

        const guardedInputs = Array.from(
            document.querySelectorAll('[data-language-change-guard]')
        );
        if (nicknameInput && !guardedInputs.includes(nicknameInput)) {
            guardedInputs.push(nicknameInput);
        }
        return guardedInputs.some(input => input.value.trim());
    };

    document.querySelectorAll('[data-language-toggle]:not(:disabled)').forEach(toggle => {
        toggle.addEventListener('click', event => {
            const currentLanguage = toggle.dataset.currentLanguage === 'ja' ? 'ja' : 'ko';
            const selectedOption = event.target.closest('[data-language]');
            const selectedLanguage = selectedOption?.dataset.language
                || (currentLanguage === 'ja' ? 'ko' : 'ja');

            if (selectedLanguage === currentLanguage) {
                return;
            }
            if (hasUnsavedInput()
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
