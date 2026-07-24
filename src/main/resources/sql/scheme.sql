create database ProjectDublin
    character set utf8mb4 -- 이모티콘 지원
    collate utf8mb4_unicode_ci; -- 대소문자 구분 없는 정렬

use ProjectDublin;

create table users (
    id bigint unsigned not null auto_increment,
    email varchar(320) not null,
    nickname varchar(255) not null,
    password varchar(255),
    role tinyint unsigned not null default 2 comment '1: admin / 2: user / 3: other',
    auth_provider varchar(30) not null default 'GOOGLE',
    created_at datetime not null default current_timestamp,
    updated_at datetime not null default current_timestamp on update current_timestamp,
    primary key (id),
    constraint uq_users_email unique (email),
    constraint ck_users_role check (role in (1, 2, 3))
);

create table articles (
    id bigint unsigned not null auto_increment,
    author_id bigint unsigned not null,
    title varchar(255) not null,
    summary varchar(500) null,
    content longtext not null,
    view_count bigint unsigned not null default 0,
    pinned tinyint(1) not null default 0,
    language varchar(30) not null default 'korean',
    created_at datetime not null default current_timestamp,
    updated_at datetime not null default current_timestamp,
    primary key (id),
    constraint ck_article_pinned check (pinned in (0, 1)),
    constraint fk_article_author_id
            foreign key (author_id) references users (id)
            on update restrict  -- 이미 작성자가 있으면 작성자 수정 불가능
            on delete restrict, -- 작성글이 하나라도 있으면 사용자는 삭제 불가능
    index idx_article_author_id (author_id),
    index idx_article_list_created (language, pinned, created_at, id)
);

create table article_likes (
    user_id bigint unsigned not null,
    article_id bigint unsigned not null,
    created_at datetime not null default current_timestamp,
    primary key (user_id, article_id),
    constraint fk_article_likes_user_id
            foreign key (user_id) references users (id)
            on update restrict  -- 좋아요가 있으면 해당 사용자의 id 수정 불가능
            on delete cascade,  -- 사용자가 삭제되면 해당 사용자의 좋아요도 함께 삭제
    constraint fk_article_likes_article_id
            foreign key (article_id) references articles (id)
            on update restrict  -- 좋아요가 있으면 해당 글의 id 수정 불가능
            on delete cascade,  -- 글이 삭제되면 해당 글의 좋아요도 함께 삭제
    index idx_article_likes_article_id (article_id)
);

create table article_images (
    id bigint unsigned not null auto_increment,
    article_id bigint unsigned not null,
    s3_key varchar(512) collate utf8mb4_bin not null,
    original_filename varchar(255) not null,
    content_type varchar(100) not null,
    file_size bigint unsigned not null,
    created_at datetime not null default current_timestamp,
    primary key (id),
    constraint uq_article_images_s3_key unique (s3_key),
    constraint fk_article_images_article_id
            foreign key (article_id) references articles (id)
            on update restrict  -- 이미지가 있으면 해당 글의 id 수정 불가능
            on delete cascade,  -- 글이 삭제되면 해당 글의 이미지 정보도 함께 삭제
    index idx_article_images_article_id (article_id)
);

create table comments (
    id bigint unsigned not null auto_increment,
    article_id bigint unsigned not null,
    author_id bigint unsigned not null,
    parent_id bigint unsigned null,
    depth tinyint unsigned not null default 1 comment '1: 댓글 / 2: 대댓글',
    content text not null,
    created_at datetime not null default current_timestamp,
    updated_at datetime not null default current_timestamp on update current_timestamp,
    deleted_at datetime null,   -- 대댓글이 달린 댓글은 소프트 삭제(삭제된 댓글입니다로 처리)
    primary key (id),
    constraint ck_comments_depth_parent
            check (
                (depth = 1 and parent_id is null)           -- 일반 댓글은 부모 댓글이 없어야 함
                or (depth = 2 and parent_id is not null)    -- 대댓글은 부모 댓글이 반드시 있어야 함
            ),
    constraint fk_comments_article_id
            foreign key (article_id) references articles (id)
            on update restrict  -- 댓글이 있으면 해당 글의 id 수정 불가능
            on delete cascade,  -- 글이 삭제되면 댓글도 함께 삭제
    constraint fk_comments_author_id
            foreign key (author_id) references users (id)
            on update restrict  -- 댓글이 있으면 해당 작성자의 id 수정 불가능
            on delete restrict, -- 댓글이 있으면 해당 작성자 삭제 불가능
    constraint fk_comments_parent_id
            foreign key (parent_id) references comments (id)
            on update restrict  -- 대댓글이 있으면 부모 댓글의 id 수정 불가능
            on delete restrict, -- 대댓글이 있으면 부모 댓글을 실제 삭제할 수 없음(삭제된 댓글입니다로 처리)
    index idx_comments_article_parent_created (article_id, parent_id, created_at, id),
    index idx_comments_author_id (author_id),
    index idx_comments_parent_id (parent_id)
);

select * from users;
select * from articles;
select * from article_likes;
select * from article_images;
select * from comments;

update users set role = 1 where email = 'sangzoon0102@gmail.com' and nickname = '상준';