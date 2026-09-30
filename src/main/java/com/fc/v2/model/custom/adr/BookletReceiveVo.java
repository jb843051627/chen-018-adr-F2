package com.fc.v2.model.custom.adr;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * 进册结果：首进门给出落账概况；同一册号第二次进门只走比对一层，落点不动、行不增不改。
 *
 * @author fuce
 * @date 2026-09-30
 */
@ApiModel(value = "BookletReceiveVo", description = "进册结果")
public class BookletReceiveVo {

    @ApiModelProperty(value = "下发册号")
    private String batchNo;

    @ApiModelProperty(value = "是否整册收下(越线原册退回、一行不留时为false)")
    private boolean accepted;

    @ApiModelProperty(value = "是否二次进册只走比对")
    private boolean compareOnly;

    @ApiModelProperty(value = "册主键(首进门回填)")
    private Long bookletId;

    @ApiModelProperty(value = "进册核对期次号")
    private String periodNo;

    @ApiModelProperty(value = "中心定的单册行数上限")
    private int rowCap;

    @ApiModelProperty(value = "落库后本册有效行数(同码双行合一后)")
    private int totalRows;

    @ApiModelProperty(value = "已挂接行数")
    private int linkedRows;

    @ApiModelProperty(value = "挂起行数")
    private int suspendedRows;

    @ApiModelProperty(value = "拒收/挂起等说明")
    private String message;

    @ApiModelProperty(value = "二次进册时的逐行比对结果")
    private List<CompareLineVo> compareLines = new ArrayList<>();

    public static BookletReceiveVo rejected(String batchNo, String message) {
        BookletReceiveVo vo = new BookletReceiveVo();
        vo.batchNo = batchNo;
        vo.message = message;
        return vo;
    }

    public String getBatchNo() {
        return batchNo;
    }

    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
    }

    public boolean isAccepted() {
        return accepted;
    }

    public void setAccepted(boolean accepted) {
        this.accepted = accepted;
    }

    public boolean isCompareOnly() {
        return compareOnly;
    }

    public void setCompareOnly(boolean compareOnly) {
        this.compareOnly = compareOnly;
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

    public int getRowCap() {
        return rowCap;
    }

    public void setRowCap(int rowCap) {
        this.rowCap = rowCap;
    }

    public int getTotalRows() {
        return totalRows;
    }

    public void setTotalRows(int totalRows) {
        this.totalRows = totalRows;
    }

    public int getLinkedRows() {
        return linkedRows;
    }

    public void setLinkedRows(int linkedRows) {
        this.linkedRows = linkedRows;
    }

    public int getSuspendedRows() {
        return suspendedRows;
    }

    public void setSuspendedRows(int suspendedRows) {
        this.suspendedRows = suspendedRows;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<CompareLineVo> getCompareLines() {
        return compareLines;
    }

    public void setCompareLines(List<CompareLineVo> compareLines) {
        this.compareLines = compareLines;
    }
}
