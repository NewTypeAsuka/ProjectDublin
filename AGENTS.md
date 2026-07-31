# ProjectDublin 에이전트 가이드

[//]: # (OpenAI Codex: AGENTS.md)
[//]: # (GitHub Copilot: .github/copilot-instructions.md / 일부 Copilot 에이전트는 AGENTS.md도 지원)
[//]: # (Google Gemini CLI 및 Gemini Code Assist: GEMINI.md / IntelliJ 에이전트는 AGENT.md도 지원)

## 프로젝트 개요

- 이 프로젝트는 Java 17과 Spring Boot 3.2를 사용하는 Gradle 기반 블로그 애플리케이션이다.
- 백엔드는 Spring MVC, Spring Data JPA, Spring Security, Google OAuth2를 사용한다.
- 서버 렌더링 화면은 Thymeleaf를 사용하며, 게시글 편집기는 Summernote를 사용한다.
- 운영 환경은 AWS Elastic Beanstalk이며 Amazon RDS의 MySQL 8.4를 사용한다.
- 테스트에서는 `src/test/resources/application.yml` 설정을 통해 H2를 사용한다.

## 인증 및 권한

- Google OAuth2만 로그인 방식으로 사용한다.
- JWT, 리프레시 토큰, 폼 로그인, 이메일/비밀번호 회원가입을 다시 추가하지 않는다.
- 사용자가 명시적으로 요청하지 않는 한 OpenID Connect 관련 기능을 추가하지 않는다.
- Google 인증 사용자는 `users.email`을 기준으로 내부 사용자와 연결한다.
- `users.role = 1`은 관리자, `2`는 일반 사용자, `3`은 기타 사용자로 취급한다.
- 권한 검사는 백엔드 서비스 또는 컨트롤러에서도 반드시 수행한다. 화면에서 버튼만 숨기는 것으로 권한 검사를 대신하지 않는다.

## 데이터베이스

- `src/main/resources/sql/scheme.sql`을 기준 스키마로 사용한다.
- 스키마와 JPA 매핑에서 사용하는 기존 테이블명과 컬럼명을 유지한다.
- 사용자가 명시적으로 요청하지 않는 한 SQL을 실행하거나 운영 DB를 수정하거나 파괴적인 DB 작업을 수행하지 않는다.
- Hibernate가 운영 DB의 스키마를 자동으로 생성하거나 마이그레이션한다고 가정하지 않는다.
- 새 게시글의 `view_count`와 `pinned` 초기값은 각각 `0`이다.
- 사용자는 게시글마다 좋아요를 한 번만 누를 수 있으며, `article_likes`는 `(user_id, article_id)`를 기본키로 사용한다.
- 고정 게시글은 게시글 목록에서 일반 게시글보다 먼저 표시한다.

## 게시글 동작

- Summernote에서 작성한 HTML은 정제한 후 `articles.content`에 저장한다.
- HTML 정제는 `ArticleContentSummarizer`에서 수행하며, 정제되지 않은 사용자 입력을 화면에 렌더링하지 않는다.
- 사용자가 명시적으로 요청하지 않는 한 이미지 업로드 저장소나 배포 설정을 추가하지 않는다.
- 게시글 상세 화면을 조회할 때마다 조회수를 증가시킨다. 동일 사용자나 작성자의 반복 조회도 모두 포함한다.
- 관리자만 게시글을 고정하거나 고정을 해제할 수 있다.
- 좋아요와 게시글 고정 상태 변경은 동일 요청을 반복해도 결과가 달라지지 않도록 멱등성을 유지한다.
- 게시글 목록에 좋아요 수와 같은 집계 데이터를 추가할 때 N+1 쿼리가 발생하지 않도록 한다.

## 웹디자인

- Thymeleaf 화면은 `src/main/resources/templates`에 작성한다.
- 화면은 반응형으로 작성하며, 모바일 환경에서도 사용자가 불편하지 않도록 한다.
- 깔끔하고 직관적인 UI를 선호한다.
- 색상 지정은 rgba 양식으로 통일한다.
- 기존 Bootstrap과 Bootstrap Icons는 유지하되 충돌하지 않는 범위에서 Tailwind CSS를 적극 활용한다.
- 아이콘은 모두 Bootstrap Icons을 사용하도록 한다.
- Tailwind CSS는 `tw:` 접두사를 사용하고 Preflight를 비활성화하는 등 기존 Bootstrap 및 Summernote 스타일과 격리한다.
- Apple이나 Toss처럼 넉넉한 여백, 명확한 정보 위계, 절제된 색상과 움직임을 활용한 세련되고 모던한 UI/UX를 지향한다.
- 버튼과 입력 영역의 상태 피드백, 키보드 포커스, 모션 감소 설정을 제공하여 직관성과 접근성을 함께 유지한다.

## 대규모 프론트 리팩토링

### 현재 상태와 후속 작업 방향

- 2026년 7월 Windows 환경에서 전체 프론트엔드의 1차 디자인 시안을 구현했다.
- 현재 결과물은 최종 디자인이 아니다. 사용자는 전체적인 리팩토링 방향은 긍정적으로 평가했지만 세부 디자인에는 수정할 부분이 많다고 판단했으므로, 이후 요청받는 구체적인 피드백에 따라 점진적으로 다듬는다.
- 기존 화면을 무조건 전면 교체하거나 1차 시안으로 되돌리지 말고, 사용자가 지적하는 화면과 요소를 먼저 확인한 뒤 관련 컴포넌트와 동적 렌더링 코드를 함께 수정한다.
- 브라우저 기반 시각 회귀 테스트는 수행하지 않았다. 데스크톱과 모바일의 실제 화면 밀도, 줄바꿈, 터치 영역, Summernote 툴바와 로그인 화면은 로컬 브라우저에서 직접 확인하며 조정한다.

### Tailwind CSS 도입 방식

- `common.html`의 `headLinks`에서 Bootstrap 4.6.1, Bootstrap Icons 1.13.1과 Tailwind CSS v4 Browser CDN을 함께 불러온다.
- Tailwind는 theme 및 utilities 레이어만 가져오고 Preflight는 제외했다. 모든 Tailwind 유틸리티는 `tw:` 접두사를 사용하며 `important` 옵션으로 명시적인 유틸리티만 Bootstrap보다 우선하도록 구성했다.
- 공통 Tailwind 테마에는 canvas, surface, ink, muted, brand 등의 rgba 색상 토큰과 시스템 글꼴, 카드 radius 및 shadow 토큰이 정의되어 있다.
- Tailwind Browser CDN은 디자인 검토용 개발 구성이다. 사용자가 디자인을 최종 채택하면 운영 배포 전에 정적 CSS 빌드 방식으로 전환하고 사용 중인 `tw:` 클래스가 빌드 결과에 포함되는지 확인한다.
- 별도의 Node 또는 Tailwind 빌드 파이프라인은 아직 추가하지 않았다. 기존 프로젝트 규칙에 따라 컴포넌트 고유 CSS는 각 Thymeleaf 템플릿의 `<style>` 안에 작성되어 있다.
- Bootstrap, Bootstrap Icons 및 Summernote 의존성을 제거하거나 Tailwind Preflight와 같은 전역 reset을 활성화하지 않는다.

### 1차 시안에서 변경한 화면

- `common.html`: 공통 디자인 토큰, 시스템 타이포그래피, 배경, skip link, 브랜드 헤더, glass 형태의 고정 내비게이션, 글쓰기·로그아웃 버튼과 푸터를 현대화했다.
- `articleList.html`: 포스트 소개 영역, 카드 radius와 hover/focus 상태, 고정 글 표시, 메타데이터 및 지표 pill, 빈 목록 CTA와 더보기 상태를 변경했다.
- `articleFeed.js`: 무한 스크롤로 추가되는 게시글 카드가 최초 서버 렌더링 카드와 동일한 구조와 클래스 및 고정 글 표시를 사용하도록 수정했다. 목록 디자인을 변경할 때 두 구현을 항상 함께 변경한다.
- `article.html`: 읽기 폭과 본문 타이포그래피, 이미지·동영상·인용문·코드 표현, 고정·좋아요·관리 액션, 댓글 작성과 대댓글 계층을 현대화했다.
- `articleLike.js`, `articlePin.js`, `articleDelete.js`, `articleComment.js`: 처리 중 상태와 오류를 브라우저 `alert` 대신 화면 내부 메시지와 비활성화 상태로 표시하도록 개선했다. 삭제 확인용 `window.confirm`은 유지한다.
- `newArticle.html`: 제목과 본문 중심의 집중형 편집 화면, Summernote 스타일, 모바일 대응 sticky 저장 영역, 취소 및 발행·수정 액션을 구성했다.
- `articleForm.js`: 작성과 수정을 하나의 form submit 흐름으로 통합하고 검증, 저장 중 상태, 성공·실패 메시지를 화면 내부에 표시하도록 변경했다.
- `oauthLogin.html`, `oauthLogin.js`: Google 아이콘을 사용하는 반응형 브랜드 로그인 화면과 이동 중 상태, 모션 감소 대응을 적용했다.
- 게시글과 댓글 작성자가 관리자일 때 이름 뒤에 `<i class="bi bi-patch-check-fill"></i>`를 표시한다. 공통 클래스는 `author-admin-badge`이며 Twitter 계열의 하늘색을 사용한다.

### 유지해야 하는 화면 계약

- JavaScript와 테스트가 참조하는 ID를 디자인 변경만으로 임의 변경하지 않는다. 주요 ID는 `site-nav`, `article-feed`, `article-id`, `like-btn`, `pin-btn`, `delete-btn`, `comment-form`, `comment-list`, `article-form`, `title`, `content`, `create-btn`, `modify-btn`, `google-login-link` 및 `google-login-loading`이다.
- `.site-header`, `.comment-replies`, `.comment-inline-form`, `d-none`, `text-danger`, `text-muted`, `alert-danger`, `alert-success` 등의 클래스는 JavaScript 상태 처리에서 사용된다.
- 관리자 아이콘은 작성자 이름 바로 뒤에 있어야 한다. 게시글 템플릿과 `articleFeed.js`, 댓글을 생성하는 `articleComment.js` 모두 동일한 `bi bi-patch-check-fill author-admin-badge` 구조와 접근성 라벨을 유지한다.
- 일부 Controller 테스트는 렌더링된 HTML의 class 문자열과 아이콘 class를 그대로 검사한다. 구조나 클래스를 변경할 때 `BlogApiControllerTest`와 `ArticleApiControllerTest`의 관련 assertion도 확인하되, 기능 hook을 단순히 테스트 통과 목적으로 제거하지 않는다.
- 게시글 목록의 최초 서버 렌더링과 무한 스크롤 렌더링, 댓글의 서버 응답과 JavaScript 렌더링 간에 디자인 및 관리자 표시가 달라지지 않도록 한다.

### 직전 검증 결과

- 1차 시안 적용 후 관련 Controller 테스트, 전체 `./gradlew test`, `./gradlew bootJar`, `git diff --check`가 통과했다.
- 당시 Windows 환경에 Node.js가 없어 별도의 `node --check`는 실행하지 못했으며 JavaScript는 수동 검토했다. macOS에서 후속 작업을 시작할 때 Node.js를 사용할 수 있다면 변경된 JavaScript의 문법 검사도 함께 수행한다.

## 코드 구조

- 도메인 엔티티는 `src/main/java/me/newtypeasuka/projectdublin/domain`에 작성한다.
- 컨트롤러는 `controller`, 비즈니스 로직은 `service`, 저장소는 `repository`, 요청 및 응답 모델은 `dto` 패키지에 작성한다.
- OAuth 권한 이름에만 의존하지 말고 DB 정보를 기준으로 백엔드 권한을 검사한다.
- 명확한 리팩토링 이점이 없다면 현재 사용 중인 Spring 및 Lombok 작성 방식을 따른다.
- 기존 한국어 사용자 문구와 반응형 Thymeleaf 디자인을 유지한다.

## 비밀정보 및 배포

- `.secret` 또는 `application-local.yml`에 저장된 인증 정보를 읽거나 출력하거나 수정하거나 커밋하지 않는다.
- 클라이언트 시크릿, DB 인증 정보, 운영 URL을 Git이 추적하는 설정 파일에 작성하지 않는다.
- 사용자가 명시적으로 요청하지 않는 한 AWS에 배포하거나 운영 DB에 연결하지 않는다.
- 작업에 필요하고 변경 내용을 검토한 경우가 아니라면 기존 OAuth2 및 배포 설정을 유지한다.
- 다만 `application.yml`에 외부에 노출되면 안되는 민감한 정보를 환경변수로 저장할 때 정말 필수적으로 숨겨야 하는 정보를 제외하고 모든 정보를 숨길 필요는 없다. 예를 들어 aws의 bucket, region, max-file-size 정도는 편의성을 위해 하드코딩하는 편을 선호한다.

## 검증

- 기능 동작을 변경했다면 관련 테스트를 추가하거나 수정한다.
- 가장 범위가 작은 관련 테스트를 먼저 실행한다.
- 백엔드 변경을 완료하기 전에 다음 명령을 실행한다.

```bash
./gradlew test
./gradlew bootJar
```

- `git diff --check`를 실행하고 최종 변경 내용을 검토한다.
- 실행하지 못한 검증 단계가 있다면 사용자에게 알린다.

## 변경 안전성

- 작업 트리에 사용자 변경 사항이 존재할 수 있다. 관련 없는 변경을 되돌리거나 덮어쓰지 않는다.
- 요청 범위 밖의 파일명 변경, 스키마 변경, 인증 흐름 변경을 수행하지 않는다.
- 가능하면 백엔드 권한 검사, 화면 동작, 관련 테스트를 포함하는 작고 완결된 변경을 구현한다.

## 파일명과 디렉토리 구조, 클래스와 메서드 명명 규칙 등

- 기능 별로 과도하게 클래스를 나누어 작성하는 것보다 가능한 한 하나의 클래스 안에 유사한 기능을 하는 메서드를 모아두는 편을 선호한다.
- 코드를 리팩토링할 때 가급적 기존에 작성된 코드 구조, 디렉토리 구조, 명명규칙 등을 따른다.
- `domain` 디렉토리는 각 파일이 어떤 테이블을 매핑하는지 파일명만으로 명확히 구분할 수 있는 구조를 선호한다.
- 특정 테이블의 매핑에만 사용하는 복합키 클래스는 별도 파일로 분리하지 않고 해당 엔티티 파일 안에 `public static` 중첩 클래스로 작성한다.
- 파일명은 카멜 케이스를 사용하며, 클래스명은 UpperCamelCase, 메서드명은 lowerCamelCase를 사용한다.
- css는 가급적 따로 파일을 만들지 않고 html 파일 안의 `<style>` 태그 안에서 해결한다. 반면 js는 기능별로 파일을 나누어 관리하는 것을 선호한다.

## Controller 기능 분류 기준

- Controller는 엔드포인트나 Service마다 기계적으로 나누지 않고, 사용자가 인식하는 대상 리소스와 기능의 응집도를 기준으로 분류한다.
- 동일한 리소스에 속하고 함께 관리하는 것이 자연스러운 소규모 기능은 각각 별도 Controller로 분리하지 않고 하나의 Controller에 모은다.
- 게시글의 작성, 조회, 수정, 삭제와 같은 핵심 CRUD API는 `BlogApiController`에서 관리하고, 글 고정, 이미지 첨부, 좋아요와 같은 게시글 부가 기능 API는 `ArticleApiController` 하나에서 관리한다.
- 사용하는 Service나 테이블이 다르다는 이유만으로 Controller를 분리하지 않는다. URL의 기준 리소스, 기능의 목적, 권한과 요청 흐름이 충분히 독립적일 때만 별도 Controller 분리를 고려한다.

## DTO와 record 관리 기준

- Java 코드는 작은 DTO나 record마다 파일을 기계적으로 분리하지 않고, 동일한 리소스와 기능에 속하는 요청 및 응답 타입을 기능별 DTO 컨테이너의 `public` 중첩 타입으로 모아 관리한다.
- 같은 Controller에서 사용하는 작고 단순한 응답 record는 `ArticleApiDto`와 같은 하나의 DTO 클래스 안에 묶는다.
- Repository나 Service 한 곳에서만 사용하는 projection 또는 값 타입은 가능한 한 해당 기능을 소유한 클래스의 중첩 타입으로 작성한다.
- 여러 계층에서 사용하는 DTO를 Controller 내부 타입으로 작성하여 Service가 Controller에 의존하게 만들지 않는다. 이런 DTO는 `dto` 패키지의 기능별 컨테이너에 작성한다.
- 변환 및 검증 로직이 복잡하거나 여러 기능에서 독립적으로 재사용되어 별도 책임이 명확한 타입만 별도 파일로 분리한다.

## 계층별 Java 파일 응집도 기준

- `config`, `controller`, `domain`, `dto`, `repository`, `util` 등의 계층은 관련된 작은 타입과 유사 기능을 현재 프로젝트 수준의 비교적 높은 응집도로 묶어 관리하고, 파일을 과도하게 세분화하지 않는다.
- `service`는 비즈니스 로직이 길어지기 쉬우므로 다른 Java 계층보다 조금 더 느슨한 응집도를 적용하며, JavaScript 파일처럼 사용자가 인식하는 세부 기능 단위로 파일을 나누어 관리한다.
- 같은 Controller나 리소스에서 사용하는 기능이라는 이유만으로 Service까지 하나로 합치지 않는다. 예를 들어 좋아요와 이미지 첨부가 모두 `ArticleApiController`에 속하더라도 각각 `ArticleLikeService`, `ArticleImageService`로 관리할 수 있다.
- 하나의 Service 안에서는 동일 기능의 조회, 등록, 수정, 삭제와 관련 검증 및 보조 메서드를 함께 관리하고, 메서드마다 Service 클래스를 새로 만드는 식의 과도한 분리는 피한다.
- Service 분리는 단순한 코드 줄 수보다 기능의 목적, 사용하는 외부 시스템과 Repository, 트랜잭션 및 권한 흐름, 변경 이유와 테스트 범위가 독립적인지를 기준으로 판단한다.

## 나의 요청사항

- 새로운 기능을 추가할 시 간단한 주석으로 해당 코드가 어떤 기능을 하는지 적어둔다.
- 내가 달아놓은 주석을 마음대로 수정하거나 삭제하지 않길 바란다.
