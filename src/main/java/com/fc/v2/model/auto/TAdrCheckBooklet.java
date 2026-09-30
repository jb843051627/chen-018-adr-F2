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
 * 上级下发对照册对象 t_adr_check_booklet
 * 一册一个下发册号，号进门即定死；二次进册只走比对一层，改口须走作废。
 *
 * @author fuce
 * @date 2026-09-30
 */
@TableName("t_adr_check_booklet")
@ApiModel(value = "TAdrCheckBooklet", description = "上级下发对照册")
public class TAdrCheckBooklet implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 下发册号(进门定死,册内各行不得混入他册) */
    @TableField("batch_no")
    @ApiModelProperty(value = "下发册号")
    private String batchNo;

    /** 本册进册核对期次号 */
    @TableField("period_no")
    @ApiModelProperty(value = "进册核对期次号")
    private String periodNo;

    /** 中心定下的单册行数上限(进册按整册把关) */
    @TableField("row_cap")
    @ApiModelProperty(value = "单册行数上限")
    private Integer rowCap;

    /** 本册行数(回执数①,与逐行落库同源点算) */
    @TableField("total_rows")
    @ApiModelProperty(value = "本册行数")
    private Integer totalRows;

    /** 已挂接行数(回执数②) */
    @TableField("linked_rows")
    @ApiModelProperty(value = "已挂接行数")
    private Integer linkedRows;

    /** 挂起行数(回执数③,②+③=①) */
    @TableField("suspended_rows")
    @ApiModelProperty(value = "挂起行数")
    private Integer suspendedRows;

    /** 作废标记 0在册 null已作废(作废后该册号方可重新进门) */
    @TableField("void_flag")
    @ApiModelProperty(value = "作废标记 0在册 null已作废")
    private Integer voidFlag;

    /** 回执情形 0未回执 1核讫 2空册(空册不许写核讫) */
    @TableField("receipt_status")
    @ApiModelProperty(value = "回执情形 0未回执 1核讫 2空册")
    private Integer receiptStatus;

    /** 删除标记 0正常 1删除 */
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

    public String getBatchNo() {
        return batchNo;
    }

    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
    }

    public String getPeriodNo() {
        return periodNo;
    }

    public void setPeriodNo(String periodNo) {
        this.periodNo = periodNo;
    }

    public Integer getRowCap() {
        return rowCap;
    }

    public void setRowCap(Integer rowCap) {
        this.rowCap = rowCap;
    }

    public Integer getTotalRows() {
        return totalRows;
    }

    public void setTotalRows(Integer totalRows) {
        this.totalRows = totalRows;
    }

    public Integer getLinkedRows() {
        return linkedRows;
    }

    public void setLinkedRows(Integer linkedRows) {
        this.linkedRows = linkedRows;
    }

    public Integer getSuspendedRows() {
        return suspendedRows;
    }

    public void setSuspendedRows(Integer suspendedRows) {
        this.suspendedRows = suspendedRows;
    }

    public Integer getVoidFlag() {
        return voidFlag;
    }

    public void setVoidFlag(Integer voidFlag) {
        this.voidFlag = voidFlag;
    }

    public Integer getReceiptStatus() {
        return receiptStatus;
    }

    public void setReceiptStatus(Integer receiptStatus) {
        this.receiptStatus = receiptStatus;
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
