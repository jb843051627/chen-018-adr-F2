package com.fc.v2.model.auto;

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
 * 报告处置单处置记载对象 t_adr_case_note
 *
 * 只许追加、不许改删：每过一格落一笔进门句；收回一格时被收回那格的进门笔沉到 layer=1，
 * 与后补那批不在同一屏并列。权威档号沿本单这笔笔记重算（seq 次序 + prev_hash 链）。
 *
 * @author fuce
 * @date 2026-09-30
 */
@TableName("t_adr_case_note")
@ApiModel(value = "TAdrCaseNote", description = "报告处置单处置记载")
public class TAdrCaseNote implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 所属处置单（=t_adr_case_flow.id） */
    @TableField("flow_id")
    @ApiModelProperty(value = "所属处置单")
    private Long flowId;

    /** 单内次序（照落笔记载先后，此后不可改） */
    @TableField("seq")
    @ApiModelProperty(value = "单内次序")
    private Integer seq;

    /** 本笔所指档 0收单 1初核 2评价 3已结案（收回/止步/作废记当下所在档） */
    @TableField("stage")
    @ApiModelProperty(value = "本笔所指档")
    private Integer stage;

    /** 过口动作 ENTER进门 WITHDRAW收回一格 STOP止步 VOID作废 */
    @TableField("action")
    @ApiModelProperty(value = "过口动作")
    private String action;

    /** 记载层 0本屏 1已沉底（被收回的旧记，与后补那批不同屏并列） */
    @TableField("layer")
    @ApiModelProperty(value = "记载层 0本屏 1已沉底")
    private Integer layer;

    /** 本格落的那一句（同格第二遍提交仍只认头一遍这句） */
    @TableField("content")
    @ApiModelProperty(value = "本格落的那一句")
    private String content;

    /** 落笔记办人（初核须与收单不是同一双手） */
    @TableField("operator")
    @ApiModelProperty(value = "落笔记办人")
    private String operator;

    /** 上一笔指纹（链条断口即事后手补） */
    @TableField("prev_hash")
    @ApiModelProperty(value = "上一笔指纹")
    private String prevHash;

    /** 本笔指纹 md5(seq|stage|action|layer|content|operator|prev_hash) */
    @TableField("hash")
    @ApiModelProperty(value = "本笔指纹")
    private String hash;

    /** 落笔记载时刻 */
    @TableField("create_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "落笔记载时刻")
    private Date createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getFlowId() {
        return flowId;
    }

    public void setFlowId(Long flowId) {
        this.flowId = flowId;
    }

    public Integer getSeq() {
        return seq;
    }

    public void setSeq(Integer seq) {
        this.seq = seq;
    }

    public Integer getStage() {
        return stage;
    }

    public void setStage(Integer stage) {
        this.stage = stage;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public Integer getLayer() {
        return layer;
    }

    public void setLayer(Integer layer) {
        this.layer = layer;
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

    public String getPrevHash() {
        return prevHash;
    }

    public void setPrevHash(String prevHash) {
        this.prevHash = prevHash;
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }
}
