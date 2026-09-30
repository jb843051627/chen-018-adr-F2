package com.fc.v2.model.custom.adr;

import com.fc.v2.model.auto.TAdrCaseFlow;
import com.fc.v2.model.auto.TAdrCaseNote;

import java.util.ArrayList;
import java.util.List;

/**
 * 处置单推进结果——服务层那「一个推进方法」算给回的东西：
 * 此刻停在哪一档、能不能过下一档、这一次算不算过成，全看这里；
 * 页面不先把结论摆出来等人回填，也不许另设推进/作废/改记事的把手。
 *
 * @author fuce
 * @date 2026-09-30
 */
public class CaseFlowStepVo {

    /** 这一次算不算过成（抢步/重复/止步后追接一律 false，且不多落记事） */
    private boolean accepted;

    /** 处置单 */
    private TAdrCaseFlow flow;

    /** 权威档号（系统照处置记载重算；纸面/承办人心记与它打架时以它为准） */
    private Integer stage;

    /** 档名 收单/初核/评价/已结案 */
    private String stageLabel;

    /** 处置落点 1在办 2已作废 3已止步 */
    private Integer status;

    /** 落点名 */
    private String statusLabel;

    /** 是否已走完一趟（在已结案那格） */
    private boolean finished;

    /** 能不能替它过紧邻的下一档（进门条件由系统逐条算） */
    private boolean canAdvance;

    /** 能不能收回一格（只在已进过格、且单还在办时） */
    private boolean canWithdraw;

    /** 能不能就此止步（在办中） */
    private boolean canStop;

    /** 能不能作废（在办且未办结；作废是另一码事） */
    private boolean canVoid;

    /** 本屏此前最后一笔记载次序（页面下一笔照这个回传防重放） */
    private Long headSeq;

    /** 下一档进不去时卡在哪一句（进得去为 null） */
    private String blockReason;

    /** 回执号（已结案那格同出一张回执） */
    private String receiptNo;

    /** 给承办人看的那一句话（被拒原因；同格第二遍时仍是头一遍那句） */
    private String message;

    /** 本屏记载（沉了底的旧记不与后补那批并列） */
    private List<TAdrCaseNote> screenNotes = new ArrayList<>();

    public static CaseFlowStepVo of(TAdrCaseFlow flow, int stage, String message) {
        CaseFlowStepVo vo = new CaseFlowStepVo();
        vo.setFlow(flow);
        vo.setStage(stage);
        vo.setStatus(flow == null ? null : flow.getStatus());
        vo.setFinished(flow != null && Integer.valueOf(1).equals(flow.getStatus()) && stage == 3);
        vo.setMessage(message);
        return vo;
    }

    public boolean isAccepted() {
        return accepted;
    }

    public void setAccepted(boolean accepted) {
        this.accepted = accepted;
    }

    public TAdrCaseFlow getFlow() {
        return flow;
    }

    public void setFlow(TAdrCaseFlow flow) {
        this.flow = flow;
    }

    public Integer getStage() {
        return stage;
    }

    public void setStage(Integer stage) {
        this.stage = stage;
    }

    public String getStageLabel() {
        return stageLabel;
    }

    public void setStageLabel(String stageLabel) {
        this.stageLabel = stageLabel;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getStatusLabel() {
        return statusLabel;
    }

    public void setStatusLabel(String statusLabel) {
        this.statusLabel = statusLabel;
    }

    public boolean isFinished() {
        return finished;
    }

    public void setFinished(boolean finished) {
        this.finished = finished;
    }

    public boolean isCanAdvance() {
        return canAdvance;
    }

    public void setCanAdvance(boolean canAdvance) {
        this.canAdvance = canAdvance;
    }

    public String getBlockReason() {
        return blockReason;
    }

    public void setBlockReason(String blockReason) {
        this.blockReason = blockReason;
    }

    public boolean isCanWithdraw() {
        return canWithdraw;
    }

    public void setCanWithdraw(boolean canWithdraw) {
        this.canWithdraw = canWithdraw;
    }

    public boolean isCanStop() {
        return canStop;
    }

    public void setCanStop(boolean canStop) {
        this.canStop = canStop;
    }

    public boolean isCanVoid() {
        return canVoid;
    }

    public void setCanVoid(boolean canVoid) {
        this.canVoid = canVoid;
    }

    public Long getHeadSeq() {
        return headSeq;
    }

    public void setHeadSeq(Long headSeq) {
        this.headSeq = headSeq;
    }

    public String getReceiptNo() {
        return receiptNo;
    }

    public void setReceiptNo(String receiptNo) {
        this.receiptNo = receiptNo;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<TAdrCaseNote> getScreenNotes() {
        return screenNotes;
    }

    public void setScreenNotes(List<TAdrCaseNote> screenNotes) {
        this.screenNotes = screenNotes;
    }
}
