CREATE TABLE IF NOT EXISTS devices (
    dtype varchar(31) NOT NULL,
    entityid varchar(128) NOT NULL,
    creationTime timestamp(6) NOT NULL,
    name varchar(255),
    updateTime timestamp(6) NOT NULL,
    version integer,
    ieeeAddress varchar(255),
    jsonData varchar(100000),
    place varchar(64),
    PRIMARY KEY (entityid)
    );

CREATE INDEX IF NOT EXISTS dc ON devices (updateTime);

CREATE TABLE IF NOT EXISTS scripts (
    entityid varchar(128) NOT NULL,
    creationTime timestamp(6) NOT NULL,
    name varchar(255),
    updateTime timestamp(6) NOT NULL,
    version integer,
    autoStart boolean,
    error varchar(1000),
    javaScript varchar(65535),
    javaScriptParameters varchar(65535),
    jsonData varchar(10000),
    repeatInterval integer NOT NULL,
    status smallint NOT NULL,
    PRIMARY KEY (entityid),
    CONSTRAINT scripts_status_check CHECK (status >= 0 AND status <= 15)
    );

CREATE TABLE IF NOT EXISTS settings (
    entityid varchar(128) NOT NULL,
    creationTime timestamp(6) NOT NULL,
    name varchar(255),
    updateTime timestamp(6) NOT NULL,
    version integer,
    jsonData varchar(65535),
    value varchar(65535),
    PRIMARY KEY (entityid)
    );

CREATE TABLE IF NOT EXISTS widget_tabs (
    entityid varchar(128) NOT NULL,
    creationTime timestamp(6) NOT NULL,
    name varchar(255),
    updateTime timestamp(6) NOT NULL,
    version integer,
    locked boolean,
    icon varchar(32),
    iconColor varchar(32),
    bgColor varchar(32),
    jsonData varchar(10000),
    PRIMARY KEY (entityid)
    );

CREATE TABLE IF NOT EXISTS widgets (
    dtype varchar(31) NOT NULL,
    entityid varchar(128) NOT NULL,
    creationTime timestamp(6) NOT NULL,
    name varchar(255),
    updateTime timestamp(6) NOT NULL,
    version integer,
    jsonData varchar(65535),
    widgetTabEntity_entityID varchar(128),
    PRIMARY KEY (entityid),
    CONSTRAINT fk_widgets_widget_tab FOREIGN KEY (widgetTabEntity_entityID) REFERENCES widget_tabs
    );

CREATE TABLE IF NOT EXISTS widget_series (
    dtype varchar(31) NOT NULL,
    entityid varchar(128) NOT NULL,
    creationTime timestamp(6) NOT NULL,
    name varchar(255),
    updateTime timestamp(6) NOT NULL,
    version integer,
    jsonData varchar(65535),
    priority integer NOT NULL,
    widgetEntity_entityID varchar(128),
    PRIMARY KEY (entityid),
    CONSTRAINT fk_widget_series_widget FOREIGN KEY (widgetEntity_entityID) REFERENCES widgets
    );

CREATE TABLE IF NOT EXISTS workspaces (
    entityid varchar(128) NOT NULL,
    creationTime timestamp(6) NOT NULL,
    name varchar(255),
    updateTime timestamp(6) NOT NULL,
    version integer,
    locked boolean NOT NULL,
    icon varchar(32),
    iconColor varchar(32),
    content varchar(10485760),
    jsonData varchar(10000),
    PRIMARY KEY (entityid)
    );

CREATE TABLE IF NOT EXISTS workspace_group (
    entityid varchar(128) NOT NULL,
    creationTime timestamp(6) NOT NULL,
    name varchar(255) NOT NULL,
    updateTime timestamp(6) NOT NULL,
    version integer,
    description varchar(255),
    hidden boolean NOT NULL,
    icon varchar(32),
    iconColor varchar(32),
    jsonData varchar(1000),
    locked boolean NOT NULL,
    parent_entityid varchar(128),
    PRIMARY KEY (entityid),
    CONSTRAINT fk_workspace_group_parent FOREIGN KEY (parent_entityid) REFERENCES workspace_group
    );

CREATE TABLE IF NOT EXISTS workspace_variable (
    entityid varchar(128) NOT NULL,
    creationTime timestamp(6) NOT NULL,
    name varchar(255),
    updateTime timestamp(6) NOT NULL,
    version integer,
    backupDays integer,
    backupAggregateValues boolean NOT NULL,
    description varchar(255),
    icon varchar(32),
    iconColor varchar(32),
    jsonData varchar(1000),
    quota integer NOT NULL,
    readOnly boolean NOT NULL,
    restriction varchar(32),
    unit varchar(32),
    locked boolean NOT NULL,
    workspaceGroup_entityID varchar(128),
    PRIMARY KEY (entityid),
    CONSTRAINT fk_workspace_variable_group FOREIGN KEY (workspaceGroup_entityID) REFERENCES workspace_group
    );

CREATE TABLE IF NOT EXISTS variable_backup (
    id integer NOT NULL,
    created bigint NOT NULL,
    value varchar(32),
    workspaceVariable_entityID varchar(128),
    PRIMARY KEY (id),
    CONSTRAINT vb_2_v FOREIGN KEY (workspaceVariable_entityID) REFERENCES workspace_variable
    );

CREATE INDEX IF NOT EXISTS idvb ON variable_backup (id);

CREATE INDEX IF NOT EXISTS vc ON variable_backup (workspaceVariable_entityID, created);
