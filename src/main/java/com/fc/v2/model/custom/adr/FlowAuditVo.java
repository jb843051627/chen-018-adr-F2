package com.fc.v2.model.custom.adr;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 处置单倒读结果：一张走到头的单，它的处置记载要能从已结案那格一路数回收单，
 * 笔笔与当日那一屏对得拢；多出一笔、缺一格、或者事后由人手补进去的，
 * 见着一处就判定这一单没走成。
 *
 * @author fuce
 * @date 2026-09-30
 */
@ApiModel(value = "FlowAuditVo", description = "处置单倒读结果")
public class FlowAuditVo {

    @ApiModelProperty(value = "倒读是否对得拢（true=这一单走成了）")
    private boolean passed;

    @ApiModelProperty(value = "判定一句话（对不上时点出伤在哪）")
    private String message;

    @ApiModelProperty(value = "在册过格记事笔数（沉底的不计）")
    private int recordCount;

    public FlowAuditVo() {
    }

    public FlowAuditVo(boolean passed, String message, int recordCount) {
        this.passed = passed;
        this.message = message;
        this.recordCount = recordCount;
    }

    public boolean isPassed() {
        return passed;
    }

    public void setPassed(boolean passed) {
        this.passed = passed;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public int getRecordCount() {
        return recordCount;
    }

    public void setRecordCount(int recordCount) {
        this.recordCount = recordCount;
    }
}
