package com.fc.v2.model.custom.adr;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * 全辖对账结果：册头三数 / 全辖屏上总数，与逐行落库点出来的数接不平时，
 * 当场点出差在哪几行，不许拿一个成数糊过去。
 *
 * @author fuce
 * @date 2026-09-30
 */
@ApiModel(value = "ReconcileVo", description = "全辖对账结果")
public class ReconcileVo {

    @ApiModelProperty(value = "逐行点算的现行有效行数(权威口径)")
    private int rowCount;

    @ApiModelProperty(value = "逐行点算的已挂接行数")
    private int linkedCount;

    @ApiModelProperty(value = "逐行点算的挂起行数")
    private int suspendedCount;

    @ApiModelProperty(value = "各册册头三数与逐行点算是否全部接平")
    private boolean balanced;

    @ApiModelProperty(value = "对不平的差异明细(册级)")
    private List<BookletDiff> bookletDiffs = new ArrayList<>();

    @ApiModelProperty(value = "对不平的差异明细(行级:被人动过、册头对不上的具体行)")
    private List<RowDiff> rowDiffs = new ArrayList<>();

    @ApiModel(value = "BookletDiff")
    public static class BookletDiff {
        private String batchNo;
        private String field;
        private int bookletValue;
        private int rowValue;

        public BookletDiff() {
        }

        public BookletDiff(String batchNo, String field, int bookletValue, int rowValue) {
            this.batchNo = batchNo;
            this.field = field;
            this.bookletValue = bookletValue;
            this.rowValue = rowValue;
        }

        public String getBatchNo() {
            return batchNo;
        }

        public void setBatchNo(String batchNo) {
            this.batchNo = batchNo;
        }

        public String getField() {
            return field;
        }

        public void setField(String field) {
            this.field = field;
        }

        public int getBookletValue() {
            return bookletValue;
        }

        public void setBookletValue(int bookletValue) {
            this.bookletValue = bookletValue;
        }

        public int getRowValue() {
            return rowValue;
        }

        public void setRowValue(int rowValue) {
            this.rowValue = rowValue;
        }
    }

    @ApiModel(value = "RowDiff")
    public static class RowDiff {
        private Long rowId;
        private String batchNo;
        private Integer rowNo;
        private String itemCode;
        private Integer status;
        private String problem;

        public RowDiff() {
        }

        public RowDiff(Long rowId, String batchNo, Integer rowNo, String itemCode,
                       Integer status, String problem) {
            this.rowId = rowId;
            this.batchNo = batchNo;
            this.rowNo = rowNo;
            this.itemCode = itemCode;
            this.status = status;
            this.problem = problem;
        }

        public Long getRowId() {
            return rowId;
        }

        public void setRowId(Long rowId) {
            this.rowId = rowId;
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

        public Integer getStatus() {
            return status;
        }

        public void setStatus(Integer status) {
            this.status = status;
        }

        public String getProblem() {
            return problem;
        }

        public void setProblem(String problem) {
            this.problem = problem;
        }
    }

    public int getRowCount() {
        return rowCount;
    }

    public void setRowCount(int rowCount) {
        this.rowCount = rowCount;
    }

    public int getLinkedCount() {
        return linkedCount;
    }

    public void setLinkedCount(int linkedCount) {
        this.linkedCount = linkedCount;
    }

    public int getSuspendedCount() {
        return suspendedCount;
    }

    public void setSuspendedCount(int suspendedCount) {
        this.suspendedCount = suspendedCount;
    }

    public boolean isBalanced() {
        return balanced;
    }

    public void setBalanced(boolean balanced) {
        this.balanced = balanced;
    }

    public List<BookletDiff> getBookletDiffs() {
        return bookletDiffs;
    }

    public void setBookletDiffs(List<BookletDiff> bookletDiffs) {
        this.bookletDiffs = bookletDiffs;
    }

    public List<RowDiff> getRowDiffs() {
        return rowDiffs;
    }

    public void setRowDiffs(List<RowDiff> rowDiffs) {
        this.rowDiffs = rowDiffs;
    }
}
