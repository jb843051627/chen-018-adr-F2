-- H2（MODE=MySQL）下的对照册领域测试库；列与 doc/schema/adr.sql 对齐。
DROP TABLE IF EXISTS t_adr_report_row;
DROP TABLE IF EXISTS t_adr_check_booklet;
DROP TABLE IF EXISTS t_adr_check_period;
DROP TABLE IF EXISTS t_adr_term_card;
DROP TABLE IF EXISTS t_adr_case_flow;
DROP TABLE IF EXISTS t_adr_org_arch;

CREATE TABLE t_adr_case_flow (
  id bigint PRIMARY KEY,
  biz_no varchar(64),
  stage int,
  status int,
  content varchar(255),
  last_action varchar(64),
  del_flag int DEFAULT 0,
  create_by varchar(64),
  create_time datetime,
  update_by varchar(64),
  update_time datetime,
  remark varchar(500)
);
CREATE INDEX idx_caseflow_bill ON t_adr_case_flow (biz_no, del_flag);

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
