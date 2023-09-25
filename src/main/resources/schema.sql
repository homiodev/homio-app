create table if not exists device_base_entity
(
    dtype         varchar(31)  not null,
    entityid      varchar(128)  not null,
    creation_time timestamp(6) not null,
    name          varchar(255),
    update_time   timestamp(6) not null,
    version       integer,
    ieee_address  varchar(255),
    json_data     varchar(100000),
    place         varchar(64),
    primary key (entityid)
    );

create index if not exists dc
    on device_base_entity (update_time);

create table if not exists property_base_entity
(
    entityid       varchar(128)  not null,
    creation_time  timestamp(6) not null,
    name           varchar(255),
    update_time    timestamp(6) not null,
    version        integer,
    address        integer,
    description    varchar(255),
    json_data      varchar(10000),
    position       integer,
    owner_entityid varchar(128),
    primary key (entityid),
    constraint fk6dajs8tto3r6wtd64cv932u2g
    foreign key (owner_entityid) references device_base_entity
    );

create table if not exists script_entity
(
    entityid               varchar(128)  not null,
    creation_time          timestamp(6) not null,
    name                   varchar(255),
    update_time            timestamp(6) not null,
    version                integer,
    auto_start             boolean      not null,
    error                  varchar(1000),
    java_script            varchar(65535),
    java_script_parameters varchar(65535),
    json_data              varchar(10000),
    repeat_interval        integer      not null,
    status                 smallint     not null,
    primary key (entityid),
    constraint script_entity_status_check
    check ((status >= 0) AND (status <= 15))
    );

create table if not exists setting_entity
(
    entityid      varchar(128)  not null,
    creation_time timestamp(6) not null,
    name          varchar(255),
    update_time   timestamp(6) not null,
    version       integer,
    json_data     varchar(65535),
    value         varchar(65535),
    primary key (entityid)
    );

create table if not exists variable_backup
(
    id      integer not null,
    created bigint  not null,
    value   varchar(255),
    vid     varchar(128),
    primary key (id)
    );

create index if not exists vc
    on variable_backup (vid, created);

create table if not exists widget_tab_entity
(
    entityid      varchar(128)  not null,
    creation_time timestamp(6) not null,
    name          varchar(255),
    update_time   timestamp(6) not null,
    version       integer,
    json_data     varchar(10000),
    primary key (entityid)
    );

create table if not exists widget_base_entity
(
    dtype                      varchar(31)  not null,
    entityid                   varchar(128)  not null,
    creation_time              timestamp(6) not null,
    name                       varchar(255),
    update_time                timestamp(6) not null,
    version                    integer,
    json_data                  varchar(65535),
    widget_tab_entity_entityid varchar(128),
    primary key (entityid),
    constraint fklulpcwxpv8623qoph51b3i5d6
    foreign key (widget_tab_entity_entityid) references widget_tab_entity
    );

create table if not exists widget_series_entity
(
    dtype                  varchar(31)  not null,
    entityid               varchar(128)  not null,
    creation_time          timestamp(6) not null,
    name                   varchar(255),
    update_time            timestamp(6) not null,
    version                integer,
    json_data              varchar(65535),
    priority               integer      not null,
    widget_entity_entityid varchar(128),
    primary key (entityid),
    constraint fkt9ob3e4d6lewp7wmcqdb57its
    foreign key (widget_entity_entityid) references widget_base_entity
    );

create table if not exists workspace_entity
(
    entityid      varchar(128)  not null,
    creation_time timestamp(6) not null,
    name          varchar(255),
    update_time   timestamp(6) not null,
    version       integer,
    content       varchar(10485760),
    json_data     varchar(10000),
    primary key (entityid)
    );

create table if not exists workspace_group
(
    entityid        varchar(128)  not null,
    creation_time   timestamp(6) not null,
    name            varchar(255) not null,
    update_time     timestamp(6) not null,
    version         integer,
    description     varchar(255),
    group_id        varchar(255) not null,
    hidden          boolean      not null,
    icon            varchar(32),
    icon_color      varchar(32),
    json_data       varchar(1000),
    locked          boolean      not null,
    parent_entityid varchar(128),
    primary key (entityid),
    constraint uk_i4c44wwkkvfr1nqk4qob5wc9u
    unique (group_id),
    constraint fkfqeg6kt1gyvq0bea6u2gqvmwn
    foreign key (parent_entityid) references workspace_group
    );

create table if not exists workspace_variable
(
    entityid                 varchar(128)  not null,
    creation_time            timestamp(6) not null,
    name                     varchar(255),
    update_time              timestamp(6) not null,
    version                  integer,
    backup                   boolean      not null,
    description              varchar(255),
    icon                     varchar(32),
    icon_color               varchar(32),
    json_data                varchar(1000),
    quota                    integer      not null,
    read_only                boolean      not null,
    restriction              varchar(32),
    unit                     varchar(32),
    variable_id              varchar(128) not null,
    locked boolean not null,
    workspace_group_entityid varchar(128),
    primary key (entityid),
    constraint uk_ed7bugig9d8vnui1nll862fcx
    unique (variable_id),
    constraint fkasa2xfl3o3dalxw029mm5702v
    foreign key (workspace_group_entityid) references workspace_group
    );
