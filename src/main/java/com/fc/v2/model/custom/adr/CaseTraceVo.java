package com.fc.v2.model.custom.adr;

import java.util.ArrayList;
import java.util.List;

/**
 * 处置单倒读结果：从已结案那格一路数回收单，笔笔要与当日那一屏对得拢。
 * 多出一笔、缺一格、事后手补（链断/hash 对不上/seq 有空洞），见一处即 valid=false。
 *
 * @author fuce
 * @date 2026-09-30
 */
public class CaseTraceVo {

    /** 处置单 id */
    private Long flowId;

    /** 是否走到头（已结案）；没走到头的单不在倒读判废之列 */
    private boolean finished;

    /** 权威档号（照记载重放） */
    private int stage;

    /** 倒读对不拢时 false */
    private boolean valid;

    /** 链条上的记载总数（含沉底旧记） */
    private int chainCount;

    /** 本屏进门档序（正常走到头为 0,1,2,3） */
    private List<Integer> enterStages = new ArrayList<>();

    /** 逐条点出的毛病 */
    private List<String> problems = new ArrayList<>();

    public Long getFlowId() {
        return flowId;
    }

    public void setFlowId(Long flowId) {
        this.flowId = flowId;
    }

    public boolean isFinished() {
        return finished;
    }

    public void setFinished(boolean finished) {
        this.finished = finished;
    }

    public int getStage() {
        return stage;
    }

    public void setStage(int stage) {
        this.stage = stage;
    }

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public int getChainCount() {
        return chainCount;
    }

    public void setChainCount(int chainCount) {
        this.chainCount = chainCount;
    }

    public List<Integer> getEnterStages() {
        return enterStages;
    }

    public void setEnterStages(List<Integer> enterStages) {
        this.enterStages = enterStages;
    }

    public List<String> getProblems() {
        return problems;
    }

    public void setProblems(List<String> problems) {
        this.problems = problems;
    }
}
