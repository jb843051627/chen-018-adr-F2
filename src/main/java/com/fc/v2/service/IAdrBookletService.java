package com.fc.v2.service;

import com.fc.v2.model.auto.TAdrCheckBooklet;
import com.fc.v2.model.auto.TAdrCheckPeriod;
import com.fc.v2.model.auto.TAdrReportRow;
import com.fc.v2.model.custom.adr.BookletReceiptVo;
import com.fc.v2.model.custom.adr.BookletReceiveVo;
import com.fc.v2.model.custom.adr.IncomingRow;
import com.fc.v2.model.custom.adr.ReconcileVo;

import java.math.BigDecimal;
import java.util.List;

/**
 * 上级下发对照册核对 Service。
 *
 * 进门两件事：一册一个下发册号、号进门定死；每行进门自动占本册内位次、照先后排定、此后不可改。
 * 行落点只有三个：待核、已挂接、挂起；不合只停那一行（四种情形），同册其余各行照旧往下走。
 *
 * @author fuce
 * @date 2026-09-30
 */
public interface IAdrBookletService {

    /** 开口一个核对期（首期由中心给定挂下额度）。期次号已存在则原样返回，不重复开口。 */
    TAdrCheckPeriod openPeriod(String periodNo, BigDecimal carryInQty);

    /**
     * 进册：
     * 1) 行数越线（&gt;rowCap）整册退回、一行不留；
     * 2) 同一册号第二次进门只走比对一层——拿册上每一行比已落库存量行，落点不动、行不增不改；
     * 3) 首进门逐行落账：位次照先后排定不可改；同码双行合一、留靠前位次、份数只计一次；
     *    四种不合情形单行挂起并记缘故；本期核销以本期挂下额度封顶，超出不记本行、不移抵他行。
     */
    BookletReceiveVo receiveBooklet(String batchNo, String periodNo, int rowCap, List<IncomingRow> rows);

    /**
     * 作废在册：册头作废、本册各行旧版置删；此后同册号方可重新进门。
     * 改口只许走这一路，不许拿第二遍去覆第一遍。作废释放本册已占用的期次额度。
     */
    int voidBooklet(String batchNo, String reason);

    /**
     * 回执三个数（本册行数/挂接/挂起），永远从逐行落库行重算——与落库点算同源，
     * 不采信册头旧计数、不采信屏上自点数。全册无行只落「空册」，不许写核讫。
     */
    BookletReceiptVo receipt(String batchNo);

    /**
     * 开新一期：连跨两个核对期仍挂着的行搬入往期层（现行册面不再出现、不牵住新开册）；
     * 上一期仍挂着的份数结为本期挂下额度；旧期封账。
     * 返回新开的期次账。期次号按期序比较（如 2026Q1 &lt; 2026Q2），编号须保持可字典排序。
     */
    TAdrCheckPeriod openNextPeriod(String newPeriodNo);

    /**
     * 复核一笔挂起行（挂起行拿到落点的正当入口）：照当下情形重过四种不合校验——
     * 卡点补上、份数补齐、更早那笔已结的，改落「已挂接」，按开口期剩余挂下额度封顶落账；
     * 仍不合的保持挂起并刷新缘故。行已入往期/查无/已挂接返回 null。
     */
    TAdrReportRow reviewSuspended(Long rowId, String note);

    /** 全辖对账：册头三数、全辖总数与逐行点算接不平时，逐册逐行点出差在哪。 */
    ReconcileVo reconcile();

    /** 按册查现行各行（按位次升序；往期行不在册面） */
    List<TAdrReportRow> listRows(String batchNo);

    /** 往期层行（只供事后查账） */
    List<TAdrReportRow> listArchivedRows();

    /** 在册册头一览（未作废） */
    List<TAdrCheckBooklet> listLiveBooklets();

    /** 按下发册号取在册册头（已作废/不存在返回null） */
    TAdrCheckBooklet getLiveBooklet(String batchNo);
}
