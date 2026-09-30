package com.fc.v2.model.custom.adr;

/**
 * 立处置单命令（收单三要件）：
 * 报告卡代号、报告单位底册代号、这一版动了哪几栏——三样少一样进不了门。
 *
 * @author fuce
 * @date 2026-09-30
 */
public class CaseOpenCmd {

    /** 报告卡代号（追溯码） */
    private String reportNo;

    /** 报告单位底册代号 */
    private String siteNo;

    /** 本单所涉核对期次号（查这家单位那一期压没压着未结核对事务） */
    private String periodNo;

    /** 这一版动了哪几栏（多栏照逗号隔开） */
    private String changeFields;

    /** 收单格落的那一句 */
    private String content;

    /** 立单（收单）承办人 */
    private String operator;

    public String getReportNo() {
        return reportNo;
    }

    public void setReportNo(String reportNo) {
        this.reportNo = reportNo;
    }

    public String getSiteNo() {
        return siteNo;
    }

    public void setSiteNo(String siteNo) {
        this.siteNo = siteNo;
    }

    public String getPeriodNo() {
        return periodNo;
    }

    public void setPeriodNo(String periodNo) {
        this.periodNo = periodNo;
    }

    public String getChangeFields() {
        return changeFields;
    }

    public void setChangeFields(String changeFields) {
        this.changeFields = changeFields;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }
}
