create table if not exists device_base_entity
(
    dtype         varchar(31)  not null,
    id            integer      not null,
    creation_time timestamp(6) not null,
    entityid      varchar(255) not null,
    name          varchar(255),
    update_time   timestamp(6),
    version       integer,
    ieee_address  varchar(255),
    json_data     varchar(100000),
    place         varchar(255),
    parent_id     integer,
    email         varchar(255),
    lang          varchar(255),
    password      varchar(255),
    user_type     varchar(255),
    primary key (id),
    constraint uk_o1ct16rkepk26gy0mcbvxfx2n
        unique (entityid),
    constraint uk_so0yx8eps47aaw0ycscphav96
        unique (entityid),
    constraint fktf6sbkqlknbrjbjgeuhb5rpa2
        foreign key (parent_id) references device_base_entity
);
