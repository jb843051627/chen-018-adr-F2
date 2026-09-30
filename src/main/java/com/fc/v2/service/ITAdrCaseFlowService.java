package com.fc.v2.service;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.model.auto.TAdrCaseFlow;
import com.fc.v2.model.custom.adr.CaseFlowStepVo;
import com.fc.v2.model.custom.adr.CaseOpenCmd;
import com.fc.v2.model.custom.adr.CaseTraceVo;

/**
 * 报告处置单 Service 接口（state-machine 形状：单据流转，不提供增删改查全套）。
 *
 * 口径要改先立处置单，口头与纸面都不作数。四格（收单/初核/评价/已结案）只能从「待发起」
 * 一格格推到「办结」；这截道理只由服务层这一个推进方法 {@link #advance} 算给回：
 * 停在哪一档（照处置记载重算，不看纸面、不看承办人心记）、能不能过下一档、这一次算不算过成。
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITAdrCaseFlowService {

    /** 动作：进紧邻的下一格 */
    String ACT_NEXT = "NEXT";
    /** 动作：收回一格（只收一格，旧记沉底） */
    String ACT_WITHDRAW = "WITHDRAW";
    /** 动作：这一版到此为止（止步，事后不得追接） */
    String ACT_STOP = "STOP";
    /** 动作：作废（另一码事；记事冻结、整张不删） */
    String ACT_VOID = "VOID";

    /** 按主键查询单据（旧查询路子，签名不动；纸面档号不作数，权威档号走 {@link #currentStage}） */
    TAdrCaseFlow selectTAdrCaseFlowById(Long id);

    /** 列表查询（流转台账） */
    List<TAdrCaseFlow> selectTAdrCaseFlowList(QueryWrapper<TAdrCaseFlow> queryWrapper);

    /**
     * 立处置单（收单格进门）。三要件：报告卡代号、报告单位底册代号、这一版动了哪几栏，
     * 三样少一样进不了门；另注所涉核对期次供初核对册。
     * 同一份报告名下，头一张在办单没走完也没止步/作废之前，立不起第二张。
     */
    CaseFlowStepVo openCase(CaseOpenCmd cmd);

    /**
     * 唯一的推进口子（换一种走法，仍由这个同名方法接住）：
     * action 取 NEXT / WITHDRAW / STOP / VOID。抢步跳格倒格、同格第二遍、止步后追接、
     * 已结案再动、作废后改记，全在这一层回绝且不多落记事。
     *
     * @param fromSeq 本屏此前最后一笔记载的次序（防重放：页面照盘算回传，不传=头一笔）；
     *                账上已往前走时，同一格第二遍提交不认，回的仍是头一遍那句
     */
    CaseFlowStepVo advance(Long id, String action, String content, String operator, Long fromSeq);

    /** 取一张单此刻的盘算：权威档号、本屏记载、下一档进门条件卡在哪一句 */
    CaseFlowStepVo snapshot(Long id);

    /** 取当前档（权威档号照处置记载数已过完的格得出；纸面 stage 与它打架以它为准）。查无返回 -1 */
    int currentStage(Long id);

    /**
     * 倒读：走到头的单，处置记载要能从已结案一路数回收单。链断一笔、缺一格、多一笔、
     * 事后手补进去的，见着一处就在 problems 里点出并判这一单没走成。
     */
    CaseTraceVo trace(Long id);
}
