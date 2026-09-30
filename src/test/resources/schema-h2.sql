-- H2（MODE=MySQL）下的对照册/处置单领域测试库；列与 doc/schema/adr.sql 对齐。
DROP TABLE IF EXISTS t_adr_case_note;
DROP TABLE IF EXISTS t_adr_case_flow;
DROP TABLE IF EXISTS t_adr_report_row;
DROP TABLE IF EXISTS t_adr_check_booklet;
DROP TABLE IF EXISTS t_adr_check_period;
DROP TABLE IF EXISTS t_adr_term_card;
DROP TABLE IF EXISTS t_adr_org_arch;

CREATE TABLE t_adr_org_arch (
  id bigint PRIMARY KEY,
  site_no varchar(64),
  site_name varchar(128),
  site_type varchar(32),
  road_name varchar(128),
  th1_max decimal(12,2),
  th2_max decimal(12,2),
  th3_max decimal(12,2),
  status int,
  del_flag int DEFAULT 0,
  create_by varchar(64),
  create_time datetime,
  update_by varchar(64),
  update_time datetime,
  remark varchar(500)
);

CREATE TABLE t_adr_term_card (
  id bigint PRIMARY KEY,
  bill_no varchar(64),
  node_no int,
  site_id int,
  site_no varchar(64),
  qty decimal(12,2),
  fine_amt decimal(12,2),
  content varchar(255),
  grade_level int,
  status int,
  del_flag int DEFAULT 0,
  create_by varchar(64),
  create_time datetime,
  update_by varchar(64),
  update_time datetime,
  remark varchar(500)
);

CREATE TABLE t_adr_check_period (
  id bigint PRIMARY KEY,
  period_no varchar(32) NOT NULL,
  carry_in_qty decimal(12,2) NOT NULL DEFAULT 0,
  used_qty decimal(12,2) NOT NULL DEFAULT 0,
  closed int NOT NULL DEFAULT 0,
  del_flag int NOT NULL DEFAULT 0,
  create_by varchar(64),
  create_time datetime,
  update_by varchar(64),
  update_time datetime,
  remark varchar(500),
  CONSTRAINT uk_period UNIQUE (period_no)
);

CREATE TABLE t_adr_check_booklet (
  id bigint PRIMARY KEY,
  batch_no varchar(64) NOT NULL,
  period_no varchar(32),
  row_cap int NOT NULL DEFAULT 500,
  total_rows int NOT NULL DEFAULT 0,
  linked_rows int NOT NULL DEFAULT 0,
  suspended_rows int NOT NULL DEFAULT 0,
  void_flag int DEFAULT 0,
  receipt_status int NOT NULL DEFAULT 0,
  del_flag int NOT NULL DEFAULT 0,
  create_by varchar(64),
  create_time datetime,
  update_by varchar(64),
  update_time datetime,
  remark varchar(500),
  CONSTRAINT uk_batch_live UNIQUE (batch_no, void_flag)
);

CREATE TABLE t_adr_report_row (
  id bigint PRIMARY KEY,
  booklet_id bigint NOT NULL,
  batch_no varchar(64),
  period_no varchar(32),
  row_no int,
  item_code varchar(64),
  qty decimal(12,2),
  posted_qty decimal(12,2),
  suspend_reason varchar(255),
  archive_phase int NOT NULL DEFAULT 0,
  cross_count int NOT NULL DEFAULT 0,
  archive_time datetime,
  status int,
  del_flag int NOT NULL DEFAULT 0,
  create_by varchar(64),
  create_time datetime,
  update_by varchar(64),
  update_time datetime,
  remark varchar(500),
  CONSTRAINT uk_booklet_row UNIQUE (booklet_id, row_no)
);
CREATE INDEX idx_code_phase ON t_adr_report_row (item_code, archive_phase, del_flag);

CREATE TABLE t_adr_case_flow (
  id bigint PRIMARY KEY,
  biz_no varchar(64),
  report_no varchar(64),
  site_no varchar(64),
  period_no varchar(32),
  change_fields varchar(255),
  version_no int NOT NULL DEFAULT 1,
  layer int NOT NULL DEFAULT 0,
  stage int,
  status int,
  content varchar(255),
  last_action varchar(16),
  receipt_no varchar(64),
  finalized_time datetime,
  terminal_time datetime,
  del_flag int DEFAULT 0,
  create_by varchar(64),
  create_time datetime,
  update_by varchar(64),
  update_time datetime,
  remark varchar(500)
);
CREATE INDEX idx_report_layer ON t_adr_case_flow (report_no, layer, status, del_flag);
CREATE INDEX idx_active ON t_adr_case_flow (report_no, status, stage, del_flag);

CREATE TABLE t_adr_case_note (
  id bigint PRIMARY KEY,
  flow_id bigint NOT NULL,
  seq int NOT NULL,
  stage int NOT NULL,
  action varchar(16) NOT NULL,
  layer int NOT NULL DEFAULT 0,
  content varchar(500),
  operator varchar(64),
  prev_hash char(32),
  hash char(32),
  create_time datetime,
  CONSTRAINT uk_flow_seq UNIQUE (flow_id, seq)
);
CREATE INDEX idx_flow_layer ON t_adr_case_note (flow_id, layer);

-- 种子底册：YF00 在册、YF01 已退出（与主库 doc/schema/adr.sql 同源）
INSERT INTO t_adr_org_arch (id, site_no, site_name, site_type, road_name, th1_max, th2_max, th3_max, status, del_flag, create_by, create_time)
VALUES (0, 'YF00', '澜川市第一人民医院（在册）', '医疗机构', '澜川省—澜川市—澜川县', 20.00, 20.00, 20.00, 0, 0, 'seed', NOW()),
       (1, 'YF01', '澜川制药股份有限公司（已退出）', '生产企业', '澜川省—澜川市—澜川县', 20.00, 20.00, 20.00, 1, 0, 'seed', NOW());
