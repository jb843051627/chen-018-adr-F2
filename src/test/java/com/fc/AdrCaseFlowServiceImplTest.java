package com.fc;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.model.auto.TAdrCaseFlow;
import com.fc.v2.model.auto.TAdrReportRow;
import com.fc.v2.model.custom.adr.FlowAuditVo;
import com.fc.v2.model.custom.adr.IncomingRow;
import com.fc.v2.service.impl.TAdrCaseFlowServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 报告处置单走格规矩集成测试——去年十一月省里抽查翻出两版口径并排挂现行那桩旧案立下的规矩：
 * 四格（收单/初核/评价/已结案）一格格推；五条不许（抢步/问屏/第二遍/追接/并排）；
 * 作废与结案锁死；同一份报告在办只许一张；走到头的单要倒读得回去。
 */
class AdrCaseFlowServiceImplTest extends AdrBookletTestBase {

    private TAdrCaseFlowServiceImpl flowService;

    @BeforeEach
    void setUpFlow() throws Exception {
        this.flowService = new TAdrCaseFlowServiceImpl();
        injectField(this.flowService, "adrCaseFlowMapper", this.caseFlowMapper);
        injectField(this.flowService, "adrReportRowMapper", this.rowMapper);
        injectField(this.flowService, "adrCheckBookletMapper", this.bookletMapper);
        injectField(this.flowService, "adrTermCardMapper", this.termCardMapper);
        injectField(this.flowService, "adrOrgArchMapper", this.orgArchMapper);
    }

    private static List<IncomingRow> rows(Object... kv) {
        java.util.List<IncomingRow> list = new java.util.ArrayList<>();
        for (int i = 0; i < kv.length; i += 2) {
            list.add(new IncomingRow((String) kv[i], kv[i + 1] == null ? null : new BigDecimal(kv[i + 1].toString())));
        }
        return list;
    }

    /** 省里那册对照下来：码进册行、册子核讫 */
    private void seedCheckedBooklet(String batchNo, String code) {
        seedTermCard(code, null);
        service.openPeriod("2026Q1", new BigDecimal("1000"));
        assertTrue(service.receiveBooklet(batchNo, "2026Q1", 500, rows(code, 5)).isAccepted());
        service.receipt(batchNo);
    }

    private TAdrCaseFlow headOf(String bizNo) {
        List<TAdrCaseFlow> live = caseFlowMapper.selectList(new QueryWrapper<TAdrCaseFlow>()
                .eq("biz_no", bizNo).eq("del_flag", 0).orderByAsc("id"));
        return live.isEmpty() ? null : live.get(0);
    }

    /** 这一单全部行（含沉到底层的），按落库先后 */
    private List<TAdrCaseFlow> allRows(String bizNo) {
        return caseFlowMapper.selectList(new QueryWrapper<TAdrCaseFlow>()
                .eq("biz_no", bizNo).orderByAsc("id"));
    }

    private TAdrCaseFlow file(String bizNo, String card, String site) {
        return flowService.fileBill(bizNo, card, site, "栏A、栏B", "立单");
    }

    /** 把一张单从待发起一路推到办结，返回单头 */
    private TAdrCaseFlow walkToClose(String bizNo, String card, String site) {
        TAdrCaseFlow head = file(bizNo, card, site);
        assertNotNull(head);
        Long id = head.getId();
        assertNotNull(flowService.advance(id, "收单讫"));
        assertNotNull(flowService.advance(id, "初核讫"));
        assertNotNull(flowService.advance(id, "评价讫"));
        assertNotNull(flowService.advance(id, "结案讫"));
        return headOf(bizNo);
    }

    // ---------------- 立单与收单进门三样 ----------------

    @Test
    void 收单三样少一样进不了门() {
        seedOrg("YF00", 0);
        // 缺「这一版动了哪几栏」
        TAdrCaseFlow noFields = flowService.fileBill("D-F", "R-F", "YF00", null, "立单");
        assertNotNull(noFields);
        assertNull(flowService.advance(noFields.getId(), "收单"));
        // 缺「报告卡代号」
        TAdrCaseFlow noCard = flowService.fileBill("D-C", null, "YF00", "栏A", "立单");
        assertNull(flowService.advance(noCard.getId(), "收单"));
        // 缺「报告单位底册代号」
        TAdrCaseFlow noSite = flowService.fileBill("D-S", "R-S", "", "栏A", "立单");
        assertNull(flowService.advance(noSite.getId(), "收单"));
        // 三单都还在待发起：档号空着、落点未起、一条记事没有
        for (String biz : new String[]{"D-F", "D-C", "D-S"}) {
            TAdrCaseFlow head = headOf(biz);
            assertEquals(0, head.getStatus().intValue());
            assertNull(flowService.selectTAdrCaseFlowById(head.getId()).getStage());
            assertEquals(1, allRows(biz).size(), "只有单头，进不了门落不了记事");
        }
        // 三样齐全才进得了收单
        seedCheckedBooklet("B-Q", "R-Q");
        TAdrCaseFlow ok = file("D-OK", "R-Q", "YF00");
        assertNotNull(flowService.advance(ok.getId(), "收单讫"));
        assertEquals(0, flowService.selectTAdrCaseFlowById(ok.getId()).getStage().intValue());
    }

    // ---------------- 全程走格：一格格推到办结 ----------------

    @Test
    void 从待发起一格格推到办结_档号由系统数出来() {
        seedOrg("YF00", 0);
        seedCheckedBooklet("B1", "R1");
        TAdrCaseFlow head = file("D1", "R1", "YF00");
        Long id = head.getId();
        assertNull(flowService.selectTAdrCaseFlowById(id).getStage(), "待发起不落档号");

        assertEquals(0, flowService.advance(id, "收单讫").getStage().intValue());
        assertEquals(1, flowService.advance(id, "初核讫").getStage().intValue());
        assertEquals(2, flowService.advance(id, "评价讫").getStage().intValue());
        assertEquals(3, flowService.advance(id, "结案讫").getStage().intValue());

        // 取当前档那个旧法子照旧使得：按主键回查，档号是算出来的
        TAdrCaseFlow view = flowService.selectTAdrCaseFlowById(id);
        assertEquals(3, view.getStage().intValue(), "已结案那一格");
        assertEquals(1, view.getStatus().intValue());
        // 单头落库的档号始终是空白——纸面不写，写了也不算
        assertNull(headOf("D1").getStage());
        // 四格四笔记事，结案那笔同时出回执
        List<TAdrCaseFlow> all = allRows("D1");
        assertEquals(5, all.size(), "单头＋四格记事");
        assertEquals("结案·回执已出", all.get(4).getLastAction());
        // 回查某一格记事，回的是那一格自己的档（历史事实，不是当前档）
        assertEquals(0, flowService.selectTAdrCaseFlowById(all.get(1).getId()).getStage().intValue());
        // 办结之后没有下一格
        assertNull(flowService.advance(id, "还想再走"));
    }

    @Test
    void 不许抢步_一次只进紧邻的下一格() {
        seedOrg("YF00", 0);
        seedCheckedBooklet("B2", "R2");
        TAdrCaseFlow head = file("D2", "R2", "YF00");
        // 从待发起过一格，只能到收单，不许一脚跳进评价
        TAdrCaseFlow after1 = flowService.advance(head.getId(), "收单讫");
        assertEquals(0, after1.getStage().intValue(), "过一格是收单，不许抢步");
        // 从收单过一格，只能到初核，不许隔着初核落评价
        TAdrCaseFlow after2 = flowService.advance(head.getId(), "初核讫");
        assertEquals(1, after2.getStage().intValue());
        assertEquals(2, allRows("D2").size() - 1, "走两格就是两笔，一笔不多");
    }

    // ---------------- 四格各自的进门条件 ----------------

    @Test
    void 初核只认追溯码_撞名不算对上() {
        seedOrg("YF00", 0);
        seedCheckedBooklet("B-OTHER", "OTHER"); // 册子有、别家的码也在，就是没有这家的
        TAdrCaseFlow head = file("D3", "R-X", "YF00");
        assertNotNull(flowService.advance(head.getId(), "收单讫"));
        assertNull(flowService.advance(head.getId(), "初核讫"), "追溯码不在册行里就是对不上");
        assertEquals(0, flowService.selectTAdrCaseFlowById(head.getId()).getStage().intValue());
        // 按码把卡面对上，才进得了初核
        seedCheckedBooklet("B-X", "R-X");
        assertNotNull(flowService.advance(head.getId(), "初核讫"));
        assertEquals(1, flowService.selectTAdrCaseFlowById(head.getId()).getStage().intValue());
    }

    @Test
    void 单位压着没结完的核对事务_件停在初核() {
        seedOrg("YF00", 0);
        seedTermCard("C1", "YF00");
        // 这家单位那一期还压着一桩没结完的核对事务（挂起行）
        service.openPeriod("2026Q1", new BigDecimal("1000"));
        service.receiveBooklet("BS", "2026Q1", 500, rows("C1", null));
        assertEquals(2, rowMapper.selectOne(new QueryWrapper<TAdrReportRow>()
                .eq("item_code", "C1").eq("del_flag", 0)).getStatus().intValue());

        seedCheckedBooklet("B-R3", "R3");
        TAdrCaseFlow head = file("D4", "R3", "YF00");
        assertNotNull(flowService.advance(head.getId(), "收单讫"));
        assertNotNull(flowService.advance(head.getId(), "初核讫"));
        assertNull(flowService.advance(head.getId(), "评价讫"), "事务没结，件就停在初核");
        assertEquals(1, flowService.selectTAdrCaseFlowById(head.getId()).getStage().intValue());

        // 那桩核对事务结清后，才许往前推
        TAdrReportRow c1 = rowMapper.selectOne(new QueryWrapper<TAdrReportRow>()
                .eq("item_code", "C1").eq("del_flag", 0));
        c1.setStatus(1);
        rowMapper.updateById(c1);
        assertNotNull(flowService.advance(head.getId(), "评价讫"));
        assertEquals(2, flowService.selectTAdrCaseFlowById(head.getId()).getStage().intValue());
    }

    @Test
    void 单位已退出底册_单停在初核_评价不再前推() {
        seedOrg("YF01", 1); // 已退出
        seedCheckedBooklet("B5", "R5");
        TAdrCaseFlow head = file("D5", "R5", "YF01");
        assertNotNull(flowService.advance(head.getId(), "收单讫"));
        assertNotNull(flowService.advance(head.getId(), "初核讫"));
        assertNull(flowService.advance(head.getId(), "评价讫"), "退出底册的，评价往后不再前推");
        assertNull(flowService.advance(head.getId(), "评价再试"));
        assertEquals(1, flowService.selectTAdrCaseFlowById(head.getId()).getStage().intValue());
    }

    @Test
    void 省里那册没对照下来_口径钉不下() {
        seedOrg("YF00", 0);
        seedTermCard("R6", null);
        service.openPeriod("2026Q1", new BigDecimal("1000"));
        service.receiveBooklet("B6", "2026Q1", 500, rows("R6", 5)); // 册到了，还没对照（未核讫）
        TAdrCaseFlow head = file("D6", "R6", "YF00");
        assertNotNull(flowService.advance(head.getId(), "收单讫"));
        assertNotNull(flowService.advance(head.getId(), "初核讫"));
        assertNull(flowService.advance(head.getId(), "评价讫"), "册未核讫，口径钉不下");
        service.receipt("B6"); // 省里那册对照下来
        assertNotNull(flowService.advance(head.getId(), "评价讫"));
        assertEquals(2, flowService.selectTAdrCaseFlowById(head.getId()).getStage().intValue());
    }

    // ---------------- 五条不许 ----------------

    @Test
    void 不许第二遍_同格再提交账上仍是头一遍那一句() {
        seedOrg("YF00", 0);
        seedCheckedBooklet("B7", "R7");
        TAdrCaseFlow head = file("D7", "R7", "YF00");
        assertNotNull(flowService.advance(head.getId(), "材料齐"));
        // 同一格第二遍提交（重发、双击）：不多落一条记事，账上仍是头一遍那一句
        TAdrCaseFlow again = flowService.advance(head.getId(), "材料齐");
        assertNotNull(again);
        assertEquals(0, again.getStage().intValue(), "还在收单，没多走");
        assertEquals(1, allRows("D7").size() - 1, "不多落一条记事");
        assertEquals("材料齐", allRows("D7").get(1).getContent());
        // 换一句话才是真的过下一格
        assertNotNull(flowService.advance(head.getId(), "初核讫"));
        assertEquals(2, allRows("D7").size() - 1);
    }

    @Test
    void 不许追接_写了到此为止单就此止步() {
        seedOrg("YF00", 0);
        seedCheckedBooklet("B8", "R8");
        TAdrCaseFlow head = file("D8", "R8", "YF00");
        assertNotNull(flowService.advance(head.getId(), "收单讫"));
        assertNotNull(flowService.advance(head.getId(), "这一版到此为止"));
        assertEquals(1, flowService.selectTAdrCaseFlowById(head.getId()).getStage().intValue(),
                "写到哪一格，哪一格走完就止步");
        assertNull(flowService.advance(head.getId(), "想再续一格"), "事后不得再往后续一格");
        assertNull(flowService.rollback(head.getId(), "想收回来再接"));
        // 止步的单不挡这一份报告另起新单
        assertNotNull(file("D8-2", "R8", "YF00"));
    }

    @Test
    void 不许并排_收回只收一格_旧记沉底不并列() {
        seedOrg("YF00", 0);
        seedCheckedBooklet("B9", "R9");
        TAdrCaseFlow head = file("D9", "R9", "YF00");
        flowService.advance(head.getId(), "收");
        flowService.advance(head.getId(), "核");
        flowService.advance(head.getId(), "评");
        assertEquals(2, flowService.selectTAdrCaseFlowById(head.getId()).getStage().intValue());

        // 往回收只收一格：评价那格的原记事沉到底层
        TAdrCaseFlow back = flowService.rollback(head.getId(), "评错了");
        assertEquals(1, back.getStage().intValue(), "收一格回到初核");
        List<TAdrCaseFlow> all = allRows("D9");
        assertEquals(4, all.size(), "沉底的记事一句不毁，还在账上");
        TAdrCaseFlow sunk = all.get(3);
        assertEquals(1, sunk.getDelFlag().intValue(), "沉到底层");
        assertEquals("评", sunk.getContent(), "原记事原句留着");
        assertEquals(2, caseFlowMapper.selectCount(new QueryWrapper<TAdrCaseFlow>()
                        .eq("biz_no", "D9").eq("del_flag", 0)).intValue() - 1,
                "在册记事只剩两笔，沉底的不与后补的并列");

        // 要重来，从收回来那一格重新攒起：新记事另落一笔，不跟沉底那笔并排
        assertNotNull(flowService.advance(head.getId(), "评重来"));
        assertEquals(2, flowService.selectTAdrCaseFlowById(head.getId()).getStage().intValue());
        List<TAdrCaseFlow> all2 = allRows("D9");
        assertEquals(5, all2.size());
        assertEquals("评重来", all2.get(4).getContent());
        assertEquals(1, all2.get(3).getDelFlag().intValue(), "旧那笔还沉在底层");

        // 一路收到待发起，再收就没得收了
        flowService.rollback(head.getId(), null);
        flowService.rollback(head.getId(), null);
        flowService.rollback(head.getId(), null);
        TAdrCaseFlow restart = flowService.selectTAdrCaseFlowById(head.getId());
        assertNull(restart.getStage(), "收回到待发起");
        assertEquals(0, restart.getStatus().intValue());
        assertNull(flowService.rollback(head.getId(), null));
    }

    @Test
    void 屏上档号与算出来的打架_以算出来的为凭() {
        seedOrg("YF00", 0);
        seedCheckedBooklet("B10", "R10");
        TAdrCaseFlow head = file("D10", "R10", "YF00");
        flowService.advance(head.getId(), "收单讫");
        flowService.advance(head.getId(), "初核讫");
        // 纸面（库里的单头）被人写花：档号写成已结案
        TAdrCaseFlow tampered = headOf("D10");
        tampered.setStage(3);
        caseFlowMapper.updateById(tampered);
        // 系统数已过完的格：两格，该停在初核——以算出来的为凭
        assertEquals(1, flowService.selectTAdrCaseFlowById(head.getId()).getStage().intValue());
        assertNull(headOf("D10").getStage(), "写花的档号被抹回空白");
        // 承办人心里记的那个同样不算：过格只认系统数出来的下一格
        assertEquals(2, flowService.advance(head.getId(), "评价讫").getStage().intValue());
    }

    // ---------------- 作废与结案锁死 ----------------

    @Test
    void 作废单_记事一句改不得_整张删不得() {
        seedOrg("YF00", 0);
        seedCheckedBooklet("B11", "R11");
        TAdrCaseFlow head = file("D11", "R11", "YF00");
        flowService.advance(head.getId(), "收单讫");
        flowService.advance(head.getId(), "初核讫");
        Long recordId = allRows("D11").get(1).getId();

        assertTrue(flowService.voidBill(head.getId(), "口径作废"));
        assertEquals(2, headOf("D11").getStatus().intValue());
        assertNull(flowService.advance(head.getId(), "评价讫"), "作废单不许再走格");
        assertFalse(flowService.updateContent(recordId, "想改记事"), "记事一句改不得");
        assertFalse(flowService.updateContent(head.getId(), "想改单头"));
        assertFalse(flowService.remove(head.getId()), "整张删不得——年底查账全凭这张废单");
        assertNull(flowService.rollback(head.getId(), "想收回"));
        assertFalse(flowService.voidBill(head.getId(), "再作废一遍"));
        assertEquals(3, allRows("D11").size(), "单头带记事一笔不少，全留着");
        // 作废不挡这一份报告另起新单
        assertNotNull(file("D11-2", "R11", "YF00"));
    }

    @Test
    void 结案之后各栏锁死_要动另起新单() {
        seedOrg("YF00", 0);
        seedCheckedBooklet("B12", "R12");
        TAdrCaseFlow head = walkToClose("D12", "R12", "YF00");
        Long recordId = allRows("D12").get(1).getId();

        assertFalse(flowService.updateContent(recordId, "想改"), "结案之后各栏锁死");
        assertFalse(flowService.updateContent(head.getId(), "想改"));
        assertFalse(flowService.remove(head.getId()), "已办结不得删除");
        assertNull(flowService.advance(head.getId(), "没有下一格"));
        assertNull(flowService.rollback(head.getId(), "不许回"));
        assertFalse(flowService.voidBill(head.getId(), "不许废"));
        // 再要动，另起一张新单
        assertNotNull(file("D12-2", "R12", "YF00"));
    }

    // ---------------- 同一份报告在办只许一张 ----------------

    @Test
    void 头一张在办的单没走完也没止步_立不起第二张() {
        seedOrg("YF00", 0);
        assertNotNull(file("D13", "R13", "YF00"));
        assertNull(flowService.fileBill("D13-2", "R13", "YF00", "栏C", "立单"),
                "在办的单没走完也没止步，立不起第二张");
        assertNotNull(flowService.fileBill("D13-3", "R13-OTHER", "YF00", "栏C", "立单"),
                "别家报告不挡");
        // 头一张作废了，才立得起
        assertTrue(flowService.voidBill(headOf("D13").getId(), "不办了"));
        assertNotNull(flowService.fileBill("D13-4", "R13", "YF00", "栏C", "立单"));
    }

    // ---------------- 现行名录：新单一办结，旧版挪历史 ----------------

    @Test
    void 新单进现行名录那一刻_旧那一版自动挪进历史那一层() {
        seedOrg("YF00", 0);
        seedCheckedBooklet("B14", "R14");
        walkToClose("D14", "R14", "YF00");
        assertEquals("D14", flowService.getCurrentBill("R14").getBizNo(), "这一版进现行名录");

        // 口径再改，另起新单；新单一办结，旧版自动挪进历史那一层
        walkToClose("D14-2", "R14", "YF00");
        assertEquals("D14-2", flowService.getCurrentBill("R14").getBizNo(),
                "日常翻件只见最新的一版");
        assertEquals(3, flowService.selectTAdrCaseFlowById(headOf("D14").getId()).getStage().intValue(),
                "旧版还在历史那一层，一笔没动");
    }

    // ---------------- 倒读 ----------------

    @Test
    void 走到头的单_倒读笔笔对得拢() {
        seedOrg("YF00", 0);
        seedCheckedBooklet("B15", "R15");
        TAdrCaseFlow head = walkToClose("D15", "R15", "YF00");
        FlowAuditVo vo = flowService.auditTrail(head.getId());
        assertTrue(vo.isPassed(), vo.getMessage());
        assertEquals(4, vo.getRecordCount());
    }

    @Test
    void 倒读_多出一笔_判这一单没走成() {
        seedOrg("YF00", 0);
        seedCheckedBooklet("B16", "R16");
        TAdrCaseFlow head = walkToClose("D16", "R16", "YF00");
        // 事后多塞一笔记事
        TAdrCaseFlow extra = new TAdrCaseFlow();
        extra.setBizNo("D16");
        extra.setStage(1);
        extra.setStatus(1);
        extra.setContent("手补的一笔");
        extra.setDelFlag(0);
        caseFlowMapper.insert(extra);

        FlowAuditVo vo = flowService.auditTrail(head.getId());
        assertFalse(vo.isPassed());
        assertTrue(vo.getMessage().contains("多出一笔"));
    }

    @Test
    void 倒读_缺一格_判这一单没走成() {
        seedOrg("YF00", 0);
        seedCheckedBooklet("B17", "R17");
        TAdrCaseFlow head = walkToClose("D17", "R17", "YF00");
        // 中间那格记事被人抹了
        TAdrCaseFlow mid = allRows("D17").get(2);
        mid.setDelFlag(1);
        caseFlowMapper.updateById(mid);

        FlowAuditVo vo = flowService.auditTrail(head.getId());
        assertFalse(vo.isPassed());
        assertTrue(vo.getMessage().contains("缺一格"));
    }

    @Test
    void 倒读_事后由人手补进去的_判这一单没走成() {
        seedOrg("YF00", 0);
        seedCheckedBooklet("B18", "R18");
        TAdrCaseFlow head = walkToClose("D18", "R18", "YF00");
        // 初核那格先被抹掉，再事后补一笔——落库先后与格序对不上
        TAdrCaseFlow mid = allRows("D18").get(2);
        mid.setDelFlag(1);
        caseFlowMapper.updateById(mid);
        TAdrCaseFlow patched = new TAdrCaseFlow();
        patched.setBizNo("D18");
        patched.setStage(1);
        patched.setStatus(1);
        patched.setContent("事后补的初核");
        patched.setDelFlag(0);
        caseFlowMapper.insert(patched);

        FlowAuditVo vo = flowService.auditTrail(head.getId());
        assertFalse(vo.isPassed());
        assertTrue(vo.getMessage().contains("对不上"));
    }

    // ---------------- 在办单可撤、同名方法换一种走法 ----------------

    @Test
    void 在办的单可整张撤下() {
        seedOrg("YF00", 0);
        seedCheckedBooklet("B19", "R19");
        TAdrCaseFlow head = file("D19", "R19", "YF00");
        flowService.advance(head.getId(), "收单讫");
        assertTrue(flowService.remove(head.getId()), "在办的单可撤");
        assertTrue(allRows("D19").isEmpty(), "连单头带记事撤干净");
        assertFalse(flowService.remove(head.getId()), "查无此单");
    }

    @Test
    void 换一种走法_同名方法按单号接住() {
        seedOrg("YF00", 0);
        seedCheckedBooklet("B20", "R20");
        file("D20", "R20", "YF00");
        // 不按主键、按单号过格：另起的同名方法接住
        TAdrCaseFlow view = flowService.advance("D20", "收单讫");
        assertNotNull(view);
        assertEquals(0, view.getStage().intValue());
        assertEquals(0, flowService.selectTAdrCaseFlowById(headOf("D20").getId()).getStage().intValue());
    }
}
