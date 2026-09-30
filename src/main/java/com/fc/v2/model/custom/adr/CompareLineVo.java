package com.fc.v2.model.custom.adr;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.math.BigDecimal;

/**
 * 二次进册的逐行比对明细：只拿册上行去比已落库的存量行，比完即止。
 *
 * @author fuce
 * @date 2026-09-30
 */
@ApiModel(value = "CompareLineVo", description = "二次进册逐行比对明细")
public class CompareLineVo {

    @ApiModelProperty(value = "册上行次(进门排定,不可改)")
    private Integer rowNo;

    @ApiModelProperty(value = "册上追溯码")
    private String itemCode;

    @ApiModelProperty(value = "册上份数")
    private BigDecimal qty;

    @ApiModelProperty(value = "是否在已落库存量行中对上(按本册行次+追溯码,只认码)")
    private boolean matched;

    @ApiModelProperty(value = "对上的存量行主键(对不上为null)")
    private Long storedRowId;

    @ApiModelProperty(value = "存量行落点 0待核 1已挂接 2挂起(对不上为null)")
    private Integer storedStatus;

    public CompareLineVo() {
    }

    public CompareLineVo(Integer rowNo, String itemCode, BigDecimal qty, boolean matched,
                         Long storedRowId, Integer storedStatus) {
        this.rowNo = rowNo;
        this.itemCode = itemCode;
        this.qty = qty;
        this.matched = matched;
        this.storedRowId = storedRowId;
        this.storedStatus = storedStatus;
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

    public boolean isMatched() {
        return matched;
    }

    public void setMatched(boolean matched) {
        this.matched = matched;
    }

    public Long getStoredRowId() {
        return storedRowId;
    }

    public void setStoredRowId(Long storedRowId) {
        this.storedRowId = storedRowId;
    }

    public Integer getStoredStatus() {
        return storedStatus;
    }

    public void setStoredStatus(Integer storedStatus) {
        this.storedStatus = storedStatus;
    }
}
