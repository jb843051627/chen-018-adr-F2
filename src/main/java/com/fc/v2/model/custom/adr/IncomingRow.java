package com.fc.v2.model.custom.adr;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.math.BigDecimal;

/**
 * 进册时册上的一行（中心下发的原始行：追溯码段 + 份数）。
 *
 * @author fuce
 * @date 2026-09-30
 */
@ApiModel(value = "IncomingRow", description = "进册原始行")
public class IncomingRow {

    @ApiModelProperty(value = "报告卡追溯码(只认码,单位名号相近不算对上)")
    private String itemCode;

    @ApiModelProperty(value = "册上本行份数")
    private BigDecimal qty;

    public IncomingRow() {
    }

    public IncomingRow(String itemCode, BigDecimal qty) {
        this.itemCode = itemCode;
        this.qty = qty;
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
}
