package com.fc.v2.service;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.model.auto.TAdrCaseFlow;
import com.fc.v2.model.custom.adr.FlowAuditVo;

/**
 * 报告处置单 Service接口（state-machine 形状：单据流转，不提供增删改查全套）
 *
 * 一张单排四格：收单(0)、初核(1)、评价(2)、已结案(3)；从「待发起」起一格格推到「办结」
 * 才算走完一趟。此刻停在哪一档、能不能过下一档，只由推进方法算给回——格号由系统数
 * 已过完的格得出来，纸面写的、屏上摆的、承办人心里记的，一概不作数。
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITAdrCaseFlowService {

    /**
     * 按主键回查单据（取当前档那个旧法子，签名不动）。
     * 回的是单头时，档号以系统数出来的为准——单头落库的档号不算数。
     */
    TAdrCaseFlow selectTAdrCaseFlowById(Long id);

    /** 列表查询（流转台账） */
    List<TAdrCaseFlow> selectTAdrCaseFlowList(QueryWrapper<TAdrCaseFlow> queryWrapper);

    /**
     * 立处置单（口径要改，立处置单；口头与纸面都不作数）。
     * 同一份报告名下，头一张在办的单没走完也没止步之前，立不起第二张。
     * 立起的单停在「待发起」，返回单头；被挡返回 null。
     */
    TAdrCaseFlow fileBill(String bizNo, String cardCode, String siteNo, String changedFields, String note);

    /**
     * 推进一档：只进紧邻的下一格，格号由系统数已过完的格得出，不许问屏上。
     * 同一格第二遍提交，账上仍是头一遍那一句，不多落一条记事。
     * 进门条件不齐、已止步、已办结、已作废的，被拒返回 null。
     */
    TAdrCaseFlow advance(Long id, String remark);

    /** 换一种走法（按单号过格），另起一个同名方法接住 */
    TAdrCaseFlow advance(String bizNo, String remark);

    /**
     * 回退一档：往回收只收一格，收回来那一格原先落过的记事沉到底层，
     * 与后补的那批不在同一屏并列；要重来，从收回来那一格重新攒起。
     * 已止步、已办结、已作废的，被拒返回 null。
     */
    TAdrCaseFlow rollback(Long id, String remark);

    /** 修改内容：已办结、已作废的记事一句改不得，被拒返回 false */
    boolean updateContent(Long id, String remark);

    /** 删除单据：已办结、已作废的整张删不得，被拒返回 false */
    boolean remove(Long id);

    /**
     * 作废整单：记事原句保留、一句改不得，整张也删不得——
     * 年底查这一份报告当年按哪一版口径定的结论，全凭这张废单。
     * 已办结的单锁死，要动另起新单；已作废的不再作废。被拒返回 false。
     */
    boolean voidBill(Long id, String reason);

    /**
     * 现行名录：这份报告眼下作数的那一版（最新办结的那张单）。
     * 新单一办结进名录，旧那一版自动挪进历史那一层，日常翻件只见最新的一版。
     */
    TAdrCaseFlow getCurrentBill(String cardCode);

    /**
     * 倒读：一张走到头的单，从已结案那格一路数回收单，笔笔与当日那一屏对得拢；
     * 多出一笔、缺一格、或者事后由人手补进去的，见着一处就判定这一单没走成。
     */
    FlowAuditVo auditTrail(Long id);
}
