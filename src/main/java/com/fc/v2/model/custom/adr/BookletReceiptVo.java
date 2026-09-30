package com.fc.v2.model.custom.adr;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.math.BigDecimal;

/**
 * 回执三个数：本册多少行、挂接多少行、挂起多少行。
 * 口径与逐行落库后点出来的那一回算法出自同源——本对象永远由
 * {@link com.fc.v2.service.IAdrBookletService#receipt} 从落库行重算，
 * 不采信屏上自己点数、也不采信册头上可能被人改过的旧计数。
 *
 * @author fuce
 * @date 2026-09-30
 */
@ApiModel(value = "BookletReceiptVo", description = "对照册回执")
public class BookletReceiptVo {

    @ApiModelProperty(value = "下发册号")
    private String batchNo;

    @ApiModelProperty(value = "册主键")
    private Long bookletId;

    @ApiModelProperty(value = "回执文案:核讫 / 空册")
    private String label;

    @ApiModelProperty(value = "①本册行数(有效行;同码双行按位次留一)")
    private int totalRows;

    @ApiModelProperty(value = "②已挂接行数")
    private int linkedRows;

    @ApiModelProperty(value = "③挂起行数")
    private int suspendedRows;

    @ApiModelProperty(value = "②+③=① 勾稽是否相等")
    private boolean balanced;

    @ApiModelProperty(value = "②已挂接行落账份数合计")
    private BigDecimal postedTotal;

    public String getBatchNo() {
        return batchNo;
    }

    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
    }

    public Long getBookletId() {
        return bookletId;
    }

    public void setBookletId(Long bookletId) {
        this.bookletId = bookletId;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
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

    public boolean isBalanced() {
        return balanced;
    }

    public void setBalanced(boolean balanced) {
        this.balanced = balanced;
    }

    public BigDecimal getPostedTotal() {
        return postedTotal;
    }

    public void setPostedTotal(BigDecimal postedTotal) {
        this.postedTotal = postedTotal;
    }
}
