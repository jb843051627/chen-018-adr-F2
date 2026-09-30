package com.fc.v2.model.auto;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.util.Date;

/**
 * 报告处置单对象 t_adr_case_flow
 *
 * 口径要改先立处置单，口头与纸面都不作数。四格（收单/初核/评价/已结案）只能一格一格推；
 * 权威档号不看这张单子上的 stage，照 t_adr_case_note 已过完的进门记载重算（见服务层）。
 *
 * @author fuce
 * @date 2026-09-12
 */
@TableName("t_adr_case_flow")
@ApiModel(value = "TAdrCaseFlow", description = "报告处置单")
public class TAdrCaseFlow implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 报告处置单号 */
    @TableField("biz_no")
    @ApiModelProperty(value = "报告处置单号")
    private String bizNo;

    /** 报告卡代号（只认追溯码，不拿单位名号对门） */
    @TableField("report_no")
    @ApiModelProperty(value = "报告卡代号")
    private String reportNo;

    /** 报告单位底册代号 */
    @TableField("site_no")
    @ApiModelProperty(value = "报告单位底册代号")
    private String siteNo;

    /** 本单所涉核对期次号（查这家单位那一期压没压着未结事务） */
    @TableField("period_no")
    @ApiModelProperty(value = "本单所涉核对期次号")
    private String periodNo;

    /** 这一版动了哪几栏（收单三要件之一，逗号分隔） */
    @TableField("change_fields")
    @ApiModelProperty(value = "这一版动了哪几栏")
    private String changeFields;

    /** 同一份报告名下的版次序（作废单也占序） */
    @TableField("version_no")
    @ApiModelProperty(value = "同一份报告名下的版次序")
    private Integer versionNo;

    /** 版层 0现行名录 1历史层（新版进现行那一刻旧版自动沉入） */
    @TableField("layer")
    @ApiModelProperty(value = "版层 0现行名录 1历史层")
    private Integer layer;

    /** 当前档次 0..3（收单/初核/评价/已结案）；纸面档号，权威档号照记载重算 */
    @TableField("stage")
    @ApiModelProperty(value = "当前档次 0..3（收单/初核/评价/已结案）")
    private Integer stage;

    /** 处置落点 0未起 1在办（已结案时stage=3）2已作废 3已止步 */
    @TableField("status")
    @ApiModelProperty(value = "处置落点 0未起 1在办 2已作废 3已止步")
    private Integer status;

    /** 处置记事（随各格进门那句走，不许另补） */
    @TableField("content")
    @ApiModelProperty(value = "处置记事")
    private String content;

    /** 最近一次过口动作 */
    @TableField("last_action")
    @ApiModelProperty(value = "最近一次过口动作")
    private String lastAction;

    /** 对外回执号（已结案那格同出） */
    @TableField("receipt_no")
    @ApiModelProperty(value = "对外回执号")
    private String receiptNo;

    /** 走完一趟（已结案）时刻 */
    @TableField("finalized_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "走完一趟（已结案）时刻")
    private Date finalizedTime;

    /** 止步/作废时刻 */
    @TableField("terminal_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "止步/作废时刻")
    private Date terminalTime;

    /** 删除标记 0正常（废单也留着，年底按废单查当年口径，不许删） */
    @TableField("del_flag")
    @ApiModelProperty(value = "删除标记 0正常 1删除")
    private Integer delFlag;

    /** 创建者 */
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建者")
    private String createBy;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "创建时间")
    private Date createTime;

    /** 更新者 */
    @TableField(value = "update_by", fill = FieldFill.UPDATE)
    @ApiModelProperty(value = "更新者")
    private String updateBy;

    /** 更新时间 */
    @TableField(value = "update_time", fill = FieldFill.UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "更新时间")
    private Date updateTime;

    /** 备注 */
    @TableField("remark")
    @ApiModelProperty(value = "备注")
    private String remark;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBizNo() {
        return bizNo;
    }

    public void setBizNo(String bizNo) {
        this.bizNo = bizNo;
    }

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

    public Integer getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(Integer versionNo) {
        this.versionNo = versionNo;
    }

    public Integer getLayer() {
        return layer;
    }

    public void setLayer(Integer layer) {
        this.layer = layer;
    }

    public Integer getStage() {
        return stage;
    }

    public void setStage(Integer stage) {
        this.stage = stage;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getLastAction() {
        return lastAction;
    }

    public void setLastAction(String lastAction) {
        this.lastAction = lastAction;
    }

    public String getReceiptNo() {
        return receiptNo;
    }

    public void setReceiptNo(String receiptNo) {
        this.receiptNo = receiptNo;
    }

    public Date getFinalizedTime() {
        return finalizedTime;
    }

    public void setFinalizedTime(Date finalizedTime) {
        this.finalizedTime = finalizedTime;
    }

    public Date getTerminalTime() {
        return terminalTime;
    }

    public void setTerminalTime(Date terminalTime) {
        this.terminalTime = terminalTime;
    }

    public Integer getDelFlag() {
        return delFlag;
    }

    public void setDelFlag(Integer delFlag) {
        this.delFlag = delFlag;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public String getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
