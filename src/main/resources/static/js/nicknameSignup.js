// 닉네임 최초 설정 스크립트
// 사용처: nicknameSignup.html

// 닉네임 길이에 따라 안내 문구와 가입 버튼 상태를 갱신
const nicknameForm = document.getElementById('nickname-signup-form');
const nicknameInput = document.getElementById('nickname');
const nicknameCounter = document.getElementById('nickname-counter');
const nicknameGuide = document.getElementById('nickname-guide');
const nicknameSubmit = document.getElementById('nickname-submit');

if (nicknameForm && nicknameInput && nicknameCounter && nicknameGuide && nicknameSubmit) {
    const minimumLength = 3;
    const maximumLength = 12;
    const guideText = nicknameGuide.querySelector('span');

    const updateNicknameState = () => {
        const nickname = nicknameInput.value.trim();
        const length = nickname.length;
        const valid = length >= minimumLength && length <= maximumLength;

        nicknameCounter.textContent = `${length}/${maximumLength}`;
        nicknameSubmit.disabled = !valid;
        nicknameGuide.classList.toggle('is-valid', valid);
        nicknameGuide.classList.toggle('is-invalid', length > 0 && !valid);

        if (guideText) {
            if (length === 0) {
                guideText.textContent = '3자 이상 12자 이하로 입력해주세요.';
            } else if (length < minimumLength) {
                guideText.textContent = `${minimumLength - length}자를 더 입력해주세요.`;
            } else {
                guideText.textContent = '사용 가능한 길이입니다. 중복 여부는 가입할 때 확인합니다.';
            }
        }

        if (length > 0) {
            nicknameInput.setAttribute('aria-invalid', String(!valid));
        } else {
            nicknameInput.removeAttribute('aria-invalid');
        }

        return valid;
    };

    nicknameInput.addEventListener('input', updateNicknameState);
    nicknameForm.addEventListener('submit', (event) => {
        nicknameInput.value = nicknameInput.value.trim();
        if (!updateNicknameState()) {
            event.preventDefault();
            nicknameInput.focus();
        }
    });

    updateNicknameState();
}
