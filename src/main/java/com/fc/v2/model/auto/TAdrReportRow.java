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
import java.math.BigDecimal;
import java.util.Date;

/**
 * 上级下发关联册核对行对象 t_adr_report_row
 *
 * @author fuce
 * @date 2026-09-12
 */
@TableName("t_adr_report_row")
@ApiModel(value = "TAdrReportRow", description = "上级下发关联册核对行")
public class TAdrReportRow implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 所属下发册(=t_adr_check_booklet.id) */
    @TableField("booklet_id")
    @ApiModelProperty(value = "所属下发册")
    private Long bookletId;

    /** 上级中心下发册次号 */
    @TableField("batch_no")
    @ApiModelProperty(value = "上级中心下发册次号")
    private String batchNo;

    /** 进册核对期次号 */
    @TableField("period_no")
    @ApiModelProperty(value = "进册核对期次号")
    private String periodNo;

    /** 原册行次(进册照先后排定,此后不可改) */
    @TableField("row_no")
    @ApiModelProperty(value = "原册行次")
    private Integer rowNo;

    /** 被挑出的报告卡追溯码(只认码,撞名不算) */
    @TableField("item_code")
    @ApiModelProperty(value = "被挑出的报告卡追溯码")
    private String itemCode;

    /** 册上本行申报份数 */
    @TableField("qty")
    @ApiModelProperty(value = "册上本行申报份数")
    private BigDecimal qty;

    /** 实际落账份数(以本期挂下额度封顶,超出不记本行也不移抵他行) */
    @TableField("posted_qty")
    @ApiModelProperty(value = "实际落账份数")
    private BigDecimal postedQty;

    /** 挂起缘故(挂起必填,一句话) */
    @TableField("suspend_reason")
    @ApiModelProperty(value = "挂起缘故")
    private String suspendReason;

    /** 归档档层 0现行 1往期(连跨两个核对期仍挂起则入往期) */
    @TableField("archive_phase")
    @ApiModelProperty(value = "归档档层 0现行 1往期")
    private Integer archivePhase;

    /** 已跨过的核对期界数(到2仍挂起则搬入往期层) */
    @TableField("cross_count")
    @ApiModelProperty(value = "已跨过的核对期界数")
    private Integer crossCount;

    /** 归入往期时刻 */
    @TableField("archive_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "归入往期时刻")
    private Date archiveTime;

    /** 行落地情形 0待核 1已挂接 2挂起 */
    @TableField("status")
    @ApiModelProperty(value = "行落地情形 0待核 1已挂接 2挂起")
    private Integer status;

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

    public Long getBookletId() {
        return bookletId;
    }

    public void setBookletId(Long bookletId) {
        this.bookletId = bookletId;
    }

    public String getPeriodNo() {
        return periodNo;
    }

    public void setPeriodNo(String periodNo) {
        this.periodNo = periodNo;
    }

    public BigDecimal getPostedQty() {
        return postedQty;
    }

    public void setPostedQty(BigDecimal postedQty) {
        this.postedQty = postedQty;
    }

    public String getSuspendReason() {
        return suspendReason;
    }

    public void setSuspendReason(String suspendReason) {
        this.suspendReason = suspendReason;
    }

    public Integer getArchivePhase() {
        return archivePhase;
    }

    public void setArchivePhase(Integer archivePhase) {
        this.archivePhase = archivePhase;
    }

    public Date getArchiveTime() {
        return archiveTime;
    }

    public void setArchiveTime(Date archiveTime) {
        this.archiveTime = archiveTime;
    }

    public Integer getCrossCount() {
        return crossCount;
    }

    public void setCrossCount(Integer crossCount) {
        this.crossCount = crossCount;
    }

    public String getBatchNo() {
        return batchNo;
    }

    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
    }

    public Integer getRowNo() {
        return rowNo;
    }

    public void setRowNo(Integer rowNo) {
        this.rowNo = rowNo;
    }

    public String getItemCode() {
        return itemCode;
    }

    public void setItemCode(String itemCode) {
        this.itemCode = itemCode;
    }

    public BigDecimal getQty() {
        return qty;
    }

    public void setQty(BigDecimal qty) {
        this.qty = qty;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
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
