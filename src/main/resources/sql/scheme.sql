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
    id bigint not null auto_increment,
    title varchar(255) not null,
    content varchar(255) not null,
    author varchar(255) not null,
    created_at timestamp,
    updated_at timestamp,
    primary key (id)
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

select * from users;
select * from articles;