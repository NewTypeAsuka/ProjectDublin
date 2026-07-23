(function ($) {
    // 글 작성 화면의 본문 textarea를 찾는다.
    const $content = $('#content');
    if ($content.length === 0) {
        return;
    }

    // 수정 화면에서는 서버가 textarea에 넣어준 기존 HTML을 보관한다.
    const initialHtml = $content.val();

    // textarea를 Summernote 에디터로 초기화한다.
    $content.summernote({
        lang: 'ko-KR',
        placeholder: '내용을 입력하세요',
        tabsize: 2,
        height: 360,
        toolbar: [
            // ['style', ['style']], // 글자 크기
            ['font', ['bold', 'underline', 'fontsize', 'color', 'clear']], // 글자 스타일
            // ['para', ['ul', 'ol', 'paragraph']], // 문단 스타일
            ['insert', ['link', 'video', 'hr']], // 링크, 동영상 삽입(picture는 나중에 구현 예정)
            ['view', ['fullscreen']] // 전체화면
        ]
    });

    // 새 글이면 빈 문자열을, 수정이면 기존 HTML을 에디터에 표시한다.
    $content.summernote('code', initialHtml || '');

    // article.js가 글을 저장할 때 현재 Summernote HTML을 가져갈 수 있게 공개한다.
    window.articleEditor = {
        getHtml: function () {
            return $content.summernote('code');
        }
    };
})(window.jQuery);
