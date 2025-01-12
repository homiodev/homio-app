create table if not exists devices
(
    dtype         varchar(31)  not null,
    entityid      varchar(128)  not null,
    creationTime  timestamp(6) not null,
    name          varchar(255),
    updateTime    timestamp(6) not null,
    version       integer,
    ieeeAddress   varchar(255),
    jsonData      varchar(100000),
    place         varchar(64),
    primary key (entityid)
    );

create index if not exists dc
    on devices (updateTime);

create table if not exists scripts
(
    entityid              varchar(128)  not null,
    creationTime          timestamp(6) not null,
    name                  varchar(255),
    updateTime            timestamp(6) not null,
    version               integer,
    autoStart             boolean,
    error                 varchar(1000),
    javaScript            varchar(65535),
    javaScriptParameters  varchar(65535),
    jsonData              varchar(10000),
    repeatInterval        integer      not null,
    status                smallint     not null,
    primary key (entityid),
    constraint scripts_status_check
    check ((status >= 0) AND (status <= 15))
    );

create table if not exists settings
(
    entityid      varchar(128)  not null,
    creationTime  timestamp(6) not null,
    name          varchar(255),
    updateTime    timestamp(6) not null,
    version       integer,
    jsonData      varchar(65535),
    value         varchar(65535),
    primary key (entityid)
    );

create table if not exists variable_backup
(
    id                              integer not null,
    created bigint                  not null,
    value                           varchar(32),
    workspaceVariable_entityID     varchar(128),
    primary key (id),
    constraint vb_2_v
    foreign key (workspaceVariable_entityID) references workspace_variable
    );

create index if not exists idvb on variable_backup (id);

create index if not exists vc on variable_backup (workspaceVariable_entityID, created);

create table if not exists widget_tabs
(
    entityid         varchar(128) not null,
    creationTime     timestamp(6) not null,
    name             varchar(255),
    updateTime       timestamp(6) not null,
    version          integer,
    locked           boolean,
    icon             varchar(32),
    iconColor        varchar(32),
    bgColor          varchar(32),
    jsonData         varchar(10000),
    primary key (entityid)
    );

create table if not exists widgets
(
    dtype                     varchar(31)  not null,
    entityid                  varchar(128)  not null,
    creationTime              timestamp(6) not null,
    name                      varchar(255),
    updateTime                timestamp(6) not null,
    version                   integer,
    jsonData                  varchar(65535),
    widgetTabEntity_entityID  varchar(128),
    primary key (entityid),
    constraint fklulpcwxpv8623qoph51b3i5d6
    foreign key (widgetTabEntity_entityID) references widget_tabs
    );

create table if not exists widget_series
(
    dtype                  varchar(31)  not null,
    entityid               varchar(128)  not null,
    creationTime           timestamp(6) not null,
    name                   varchar(255),
    updateTime             timestamp(6) not null,
    version                integer,
    jsonData               varchar(65535),
    priority               integer      not null,
    widgetEntity_entityID       varchar(128),
    primary key (entityid),
    constraint fkt9ob3e4d6lewp7wmcqdb57its
    foreign key (widgetEntity_entityID) references widgets
    );

create table if not exists workspaces
(
    entityid      varchar(128) not null,
    creationTime  timestamp(6) not null,
    name          varchar(255),
    updateTime    timestamp(6) not null,
    version       integer,
    locked        boolean      not null,
    icon          varchar(32),
    iconColor     varchar(32),
    content       varchar(10485760),
    jsonData      varchar(10000),
    primary key   (entityid)
    );

create table if not exists workspace_group
(
    entityid        varchar(128)  not null,
    creationTime    timestamp(6) not null,
    name            varchar(255) not null,
    updateTime      timestamp(6) not null,
    version         integer,
    description     varchar(255),
    hidden          boolean      not null,
    icon            varchar(32),
    iconColor       varchar(32),
    jsonData        varchar(1000),
    locked          boolean      not null,
    parent_entityid varchar(128),
    primary key (entityid),
    constraint fkfqeg6kt1gyvq0bea6u2gqvmwn
    foreign key (parent_entityid) references workspace_group
    );

create table if not exists workspace_variable
(
    entityid                 varchar(128)  not null,
    creationTime             timestamp(6) not null,
    name                     varchar(255),
    updateTime               timestamp(6) not null,
    version                  integer,
    backupDays               integer,
    backupAggregateValues    boolean      not null,
    description              varchar(255),
    icon                     varchar(32),
    iconColor                varchar(32),
    jsonData                 varchar(1000),
    quota                    integer      not null,
    readOnly                 boolean      not null,
    restriction              varchar(32),
    unit                     varchar(32),
    locked                   boolean not null,
    workspaceGroup_entityID  varchar(128),
    primary key (entityid),
    constraint fkasa2xfl3o3dalxw029mm5702v
    foreign key (workspaceGroup_entityID) references workspace_group
    );

