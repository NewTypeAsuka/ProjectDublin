create database ProjectDublin
    character set utf8mb4 -- 이모티콘 지원
    collate utf8mb4_unicode_ci; -- 대소문자 구분 없는 정렬

use ProjectDublin;

create table users (
    id bigint not null auto_increment,
    email varchar(255) not null,
    nickname varchar(255),
    password varchar(255),
    created_at timestamp,
    updated_at timestamp,
    primary key (id)
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

select * from users;
select * from articles;