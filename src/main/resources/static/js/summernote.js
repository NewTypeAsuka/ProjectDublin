// 현재 표시 언어로 Summernote를 초기화하고 게시글 이미지 업로드를 관리하는 스크립트
// 사용처: newArticle.html

(function ($) {
    // 글 작성 화면의 본문 textarea를 찾는다.
    const $content = $('#content');
    const uploadStatus = document.getElementById('image-upload-status');
    const messageConfig = document.getElementById('article-form-messages');
    if ($content.length === 0 || !messageConfig) {
        return;
    }

    const messages = messageConfig.dataset;
    let activeUploads = 0;
    let uploadFailures = 0;
    let editorReady = false;

    // 수정 화면에서는 서버가 textarea에 넣어준 기존 HTML을 보관한다.
    const initialHtml = $content.val();

    // 선택된 언어의 동영상 입력창에는 공급자 목록 없이 간결한 URL 라벨만 표시한다.
    const editorLanguage = messages.editorLanguage;
    const localizedLanguage = $.summernote.lang[editorLanguage];
    if (localizedLanguage?.video) {
        localizedLanguage.video.providers = '';
    }

    // textarea를 Summernote 에디터로 초기화한다.
    $content.summernote({
        lang: editorLanguage,
        placeholder: messages.contentPlaceholder,
        tabsize: 2,
        height: 360,
        dialogsInBody: true, // 이미지·링크 창이 편집기 카드의 overflow에 잘리지 않게 한다.
        linkAddNoOpener: true,
        linkAddNoReferrer: true,
        toolbar: [
            // ['style', ['style']], // 글자 크기
            ['font', ['bold', 'strikethrough', 'underline', 'fontsize', 'color', 'clear']], // 글자 스타일
            // ['para', ['ul', 'ol', 'paragraph']], // 문단 스타일
            ['insert', ['link', 'picture', 'video', 'hr']], // 링크, 이미지, 동영상 삽입
            ['view', ['fullscreen']] // 전체화면
        ],
        callbacks: {
            onImageUpload: function (files) {
                Array.from(files).forEach(uploadImage);
            },
            onPaste: function (event) {
                insertYoutubeVideoOnPaste(event);
            },
            onChange: function () {
                if (editorReady) {
                    window.dispatchEvent(new Event('article-editor-change'));
                }
            }
        }
    });

    const $editor = $content.siblings('.note-editor').first();
    // Summernote 드롭다운이 모바일 뷰포트 좌우를 벗어나지 않도록 열린 뒤 위치를 보정한다.
    $editor.on('shown.bs.dropdown', function (event) {
        const group = event.target.closest?.('.note-btn-group');
        const menu = group && Array.from(group.children)
            .find(child => child.classList.contains('dropdown-menu'));
        if (!menu) {
            return;
        }

        window.requestAnimationFrame(function () {
            keepDropdownInViewport(menu);
        });
    });
    $editor.on('hidden.bs.dropdown', function (event) {
        const group = event.target.closest?.('.note-btn-group');
        const menu = group && Array.from(group.children)
            .find(child => child.classList.contains('dropdown-menu'));
        if (menu) {
            menu.style.removeProperty('margin-left');
        }
    });

    // 새 글이면 빈 문자열을, 수정이면 기존 HTML을 에디터에 표시한다.
    $content.summernote('code', initialHtml || '');
    editorReady = true;

    // articleForm.js가 글을 저장할 때 현재 Summernote HTML을 가져갈 수 있게 공개한다.
    window.articleEditor = {
        getHtml: function () {
            return $content.summernote('code');
        },
        isUploading: function () {
            return activeUploads > 0;
        }
    };

    function keepDropdownInViewport(menu) {
        const viewportGutter = 12;
        menu.style.removeProperty('margin-left');

        const bounds = menu.getBoundingClientRect();
        let offset = 0;
        if (bounds.right > window.innerWidth - viewportGutter) {
            offset -= bounds.right - (window.innerWidth - viewportGutter);
        }
        if (bounds.left + offset < viewportGutter) {
            offset += viewportGutter - (bounds.left + offset);
        }
        if (offset !== 0) {
            menu.style.marginLeft = `${offset}px`;
        }
    }

    // youtu.be 공유 링크만 붙여넣기 즉시 안전한 YouTube iframe으로 변환한다.
    function insertYoutubeVideoOnPaste(event) {
        const clipboardData = (event.originalEvent || event).clipboardData
            || window.clipboardData;
        if (!clipboardData) {
            return;
        }

        const pastedText = clipboardData.getData('text/plain')
            || clipboardData.getData('Text');
        const embedUrl = createYoutubeEmbedUrl(pastedText);
        if (!embedUrl) {
            return;
        }

        event.preventDefault();

        const iframe = document.createElement('iframe');
        iframe.src = embedUrl;
        iframe.width = '640';
        iframe.height = '360';
        iframe.title = messages.youtubeTitle;
        iframe.setAttribute('frameborder', '0');
        iframe.setAttribute(
            'allow',
            'accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share'
        );
        iframe.setAttribute('allowfullscreen', '');
        iframe.setAttribute('referrerpolicy', 'strict-origin-when-cross-origin');
        iframe.className = 'note-video-clip';

        $content.summernote('insertNode', iframe);
    }

    function createYoutubeEmbedUrl(value) {
        const candidate = value?.trim();
        if (!candidate || /\s/.test(candidate)) {
            return null;
        }

        try {
            const url = new URL(candidate);
            const host = url.hostname.toLowerCase();
            const pathParts = url.pathname.split('/').filter(Boolean);
            if (url.protocol !== 'https:'
                    || (host !== 'youtu.be' && host !== 'www.youtu.be')
                    || pathParts.length !== 1
                    || !/^[A-Za-z0-9_-]{11}$/.test(pathParts[0])) {
                return null;
            }

            return `https://www.youtube.com/embed/${pathParts[0]}`;
        } catch (error) {
            return null;
        }
    }

    async function uploadImage(file) {
        const formData = new FormData();
        formData.append('image', file);
        if (activeUploads === 0) {
            uploadFailures = 0;
        }
        activeUploads += 1;
        setUploadStatus(messages.imageUploading, false);

        try {
            const response = await window.csrfFetch('/api/articles/images', {
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
                setUploadStatus(
                    formatMessage(messages.imageUploadingRemaining, activeUploads),
                    false
                );
            } else if (uploadFailures > 0) {
                setUploadStatus(
                    formatMessage(messages.imageFailed, uploadFailures),
                    true
                );
            } else {
                setUploadStatus(messages.imageComplete, false);
                window.setTimeout(function () {
                    setUploadStatus('', false);
                }, 2000);
            }
        }
    }

    function formatMessage(template, ...values) {
        return values.reduce(
            (result, value, index) => result.split(`{${index}}`).join(String(value)),
            template
        );
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
