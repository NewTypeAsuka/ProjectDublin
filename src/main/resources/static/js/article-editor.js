(function ($) {
    const $content = $('#content');
    if ($content.length === 0) {
        return;
    }

    const initialHtml = $content.val();

    $content.summernote({
        lang: 'ko-KR',
        placeholder: '내용을 입력하세요',
        tabsize: 2,
        height: 360,
        toolbar: [
            ['style', ['style']],
            ['font', ['bold', 'italic', 'underline', 'clear']],
            ['para', ['ul', 'ol', 'paragraph']],
            ['insert', ['link', 'video']],
            ['view', ['fullscreen']]
        ]
    });

    $content.summernote('code', initialHtml || '');

    window.articleEditor = {
        getHtml: function () {
            return $content.summernote('code');
        }
    };
})(window.jQuery);
