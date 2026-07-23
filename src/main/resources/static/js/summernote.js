(function ($) {
    // 글 작성 화면의 본문 textarea를 찾는다.
    const $content = $('#content');
    const uploadStatus = document.getElementById('image-upload-status');
    if ($content.length === 0) {
        return;
    }

    let activeUploads = 0;
    let uploadFailures = 0;

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
            ['insert', ['link', 'picture', 'video', 'hr']], // 링크, 이미지, 동영상 삽입
            ['view', ['fullscreen']] // 전체화면
        ],
        callbacks: {
            onImageUpload: function (files) {
                Array.from(files).forEach(uploadImage);
            }
        }
    });

    // 새 글이면 빈 문자열을, 수정이면 기존 HTML을 에디터에 표시한다.
    $content.summernote('code', initialHtml || '');

    // article.js가 글을 저장할 때 현재 Summernote HTML을 가져갈 수 있게 공개한다.
    window.articleEditor = {
        getHtml: function () {
            return $content.summernote('code');
        },
        isUploading: function () {
            return activeUploads > 0;
        }
    };

    async function uploadImage(file) {
        const formData = new FormData();
        formData.append('image', file);
        if (activeUploads === 0) {
            uploadFailures = 0;
        }
        activeUploads += 1;
        setUploadStatus('이미지를 업로드하고 있습니다...', false);

        try {
            const response = await fetch('/api/articles/images', {
                method: 'POST',
                credentials: 'same-origin',
                body: formData
            });

            if (response.status === 401) {
                location.replace('/login');
                return;
            }
            if (!response.ok) {
                throw new Error('image upload failed');
            }

            const uploadedImage = await response.json();
            $content.summernote('insertImage', uploadedImage.url, function ($image) {
                $image.attr('alt', file.name);
                $image.attr('loading', 'lazy');
                $image.attr('decoding', 'async');
                $image.addClass('img-fluid');
            });
        } catch (error) {
            uploadFailures += 1;
        } finally {
            activeUploads -= 1;
            if (activeUploads > 0) {
                setUploadStatus(`이미지를 업로드하고 있습니다... (${activeUploads}개 남음)`, false);
            } else if (uploadFailures > 0) {
                setUploadStatus(
                    `${uploadFailures}개 이미지 업로드에 실패했습니다. 파일 형식과 크기를 확인해주세요.`,
                    true
                );
            } else {
                setUploadStatus('이미지 업로드가 완료되었습니다.', false);
                window.setTimeout(function () {
                    setUploadStatus('', false);
                }, 2000);
            }
        }
    }

    function setUploadStatus(message, isError) {
        if (!uploadStatus) {
            return;
        }
        uploadStatus.textContent = message;
        uploadStatus.classList.toggle('text-danger', isError);
        uploadStatus.classList.toggle('text-muted', !isError);
    }
})(window.jQuery);
