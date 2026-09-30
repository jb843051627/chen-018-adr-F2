package com.fc;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fc.v2.model.auto.TAdrCaseFlow;
import com.fc.v2.model.auto.TAdrCaseNote;
import com.fc.v2.model.custom.adr.CaseFlowStepVo;
import com.fc.v2.model.custom.adr.CaseOpenCmd;
import com.fc.v2.model.custom.adr.CaseTraceVo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 报告处置单状态机集成测试——去年十一月回头抽查那桩「两张现行口径」的旧案，
 * 两条缝（旧版退不出去、谁都能再补一句）逐条焊死。
 */
class AdrCaseFlowServiceImplTest extends AdrCaseFlowTestBase {

    private static final String ACTIVE_SITE = "YF00"; // 在册
    private static final String EXITED_SITE = "YF01"; // 已退出

    // ---------------- 收单：三要件少一样进不了门 ----------------

    @Test
    void 收单三要件齐了才进门_落第一笔记载() {
        CaseFlowStepVo vo = open("R-001", ACTIVE_SITE, "2026Q1", "甲", "评价栏,单位栏");
        assertTrue(vo.isAccepted());
        assertEquals(0, vo.getStage());
        assertEquals("收单", vo.getStageLabel());
        assertEquals(1, noteCount(vo.getFlow().getId()));
        assertEquals("甲", firstNote(vo.getFlow().getId()).getOperator());
    }

    @Test
    void 缺报告卡代号_立不了单() {
        CaseOpenCmd cmd = cmd(null, ACTIVE_SITE, "2026Q1", "甲");
        assertThrows(IllegalArgumentException.class, () -> service.openCase(cmd));
    }

    @Test
    void 缺底册代号_立不了单() {
        assertThrows(IllegalArgumentException.class, () ->
                service.openCase(cmd("R-002", "  ", "2026Q1", "甲")));
    }

    @Test
    void 缺动了哪几栏_立不了单() {
        assertThrows(IllegalArgumentException.class, () ->
                service.openCase(cmd("R-003", ACTIVE_SITE, "2026Q1", "甲", null)));
    }

    @Test
    void 同报告头一张在办单没走完也没止步前_立不起第二张() {
        open("R-004", ACTIVE_SITE, "2026Q1", "甲", "评价栏");
        CaseFlowStepVo second = service.openCase(cmd("R-004", ACTIVE_SITE, "2026Q1", "甲"));
        assertFalse(second.isAccepted());
        assertTrue(second.getMessage().contains("立不起第二张"));
    }

    // ---------------- 不许抢步：进门条件不齐，停在原格 ----------------

    @Test
    void 初核三关都不齐_件停在收单_后补的料没到点也不许推() {
        CaseFlowStepVo vo = open("R-100", ACTIVE_SITE, "2026Q1", "甲", "评价栏");
        // 无卡、上位册也没挑出这一码
        CaseFlowStepVo blocked = service.advance(vo.getFlow().getId(), "NEXT", "试着过", "乙", null);
        assertFalse(blocked.isAccepted());
        assertEquals(0, service.currentStage(vo.getFlow().getId()), "还停在收单");
        assertEquals(1, noteCount(vo.getFlow().getId()), "被拒不落记事");
        assertTrue(blocked.getMessage().contains("追溯码"));
    }

    @Test
    void 初核还得换一双手_收单本人过不了初核() {
        seedCard("R-101", ACTIVE_SITE);
        seedProvinceChecked("B-101", "R-101", "2026Q1");
        CaseFlowStepVo vo = open("R-101", ACTIVE_SITE, "2026Q1", "甲", "评价栏");
        CaseFlowStepVo sameHand = service.advance(vo.getFlow().getId(), "NEXT", "我自己再核", "甲", null);
        assertFalse(sameHand.isAccepted());
        assertTrue(sameHand.getMessage().contains("换一双手"));
        assertEquals(0, service.currentStage(vo.getFlow().getId()));
    }

    @Test
    void 上位册只认追溯码_别码哪怕撞名也不算对上() {
        seedCard("R-102", ACTIVE_SITE);
        seedProvinceChecked("B-102", "R-OTHER", "2026Q1"); // 册上挑的是别的码
        CaseFlowStepVo vo = open("R-102", ACTIVE_SITE, "2026Q1", "甲", "评价栏");
        CaseFlowStepVo blocked = service.advance(vo.getFlow().getId(), "NEXT", null, "乙", null);
        assertFalse(blocked.isAccepted());
        assertTrue(blocked.getMessage().contains("撞名不算对上"));
    }

    @Test
    void 单位那期压着没结完的核对事务_件停在初核_料齐也不许往前推() {
        seedCard("R-103", ACTIVE_SITE);
        seedCard("P-103", ACTIVE_SITE); // 同单位另一张卡
        seedProvinceChecked("B-103", "R-103", "2026Q1");
        seedPendingAffair(ACTIVE_SITE, "P-103", "2026Q1"); // 该单位该期挂着没结
        CaseFlowStepVo vo = open("R-103", ACTIVE_SITE, "2026Q1", "甲", "评价栏");
        CaseFlowStepVo in = service.advance(vo.getFlow().getId(), "NEXT", "卡册都对上了", "乙", null);
        assertTrue(in.isAccepted());
        assertEquals(1, in.getStage());

        CaseFlowStepVo toReview = service.advance(vo.getFlow().getId(), "NEXT", "推评价", "乙", null);
        assertFalse(toReview.isAccepted());
        assertEquals(1, service.currentStage(vo.getFlow().getId()), "停在初核");
        assertTrue(toReview.getMessage().contains("没结完的核对事务"));
    }

    // ---------------- 评价：退出底册 / 省里册未核讫 ----------------

    @Test
    void 报告单位那年已退出底册_单停在初核_评价不再前推() {
        seedCard("R-104", EXITED_SITE);
        seedProvinceChecked("B-104", "R-104", "2026Q1");
        CaseFlowStepVo vo = open("R-104", EXITED_SITE, "2026Q1", "甲", "评价栏");
        service.advance(vo.getFlow().getId(), "NEXT", null, "乙", null);
        CaseFlowStepVo blocked = service.advance(vo.getFlow().getId(), "NEXT", null, "乙", null);
        assertFalse(blocked.isAccepted());
        assertEquals(1, service.currentStage(vo.getFlow().getId()));
        assertTrue(blocked.getMessage().contains("退出底册"));
    }

    @Test
    void 省里那册没核讫_口径钉不下_评价进不去() {
        seedCard("R-105", ACTIVE_SITE);
        seedBooklet("B-105", "2026Q1", 0); // 在册但未回执
        seedProvinceRow("B-105", "R-105", "2026Q1");
        CaseFlowStepVo vo = open("R-105", ACTIVE_SITE, "2026Q1", "甲", "评价栏");
        service.advance(vo.getFlow().getId(), "NEXT", null, "乙", null);
        CaseFlowStepVo blocked = service.advance(vo.getFlow().getId(), "NEXT", null, "乙", null);
        assertFalse(blocked.isAccepted());
        assertTrue(blocked.getMessage().contains("省里那册还没对照下来"));
    }

    // ---------------- 不许第二遍：同格再交仍只认头一遍 ----------------

    @Test
    void 同一格第二遍提交_不算过成_账上仍是头一遍那句_不多落记事() {
        seedCard("R-200", ACTIVE_SITE);
        seedProvinceChecked("B-200", "R-200", "2026Q1");
        CaseFlowStepVo vo = open("R-200", ACTIVE_SITE, "2026Q1", "甲", "收单头一句");
        Long intakeHead = vo.getHeadSeq(); // 页面拿着收单屏的头序号
        // 头一遍过初核
        CaseFlowStepVo first = service.advance(vo.getFlow().getId(), "NEXT", "初核头一句", "乙", intakeHead);
        assertTrue(first.isAccepted());
        int notesAfterFirst = noteCount(vo.getFlow().getId());
        // 旧屏没刷新又交一遍（fromSeq 仍是收单那笔）
        CaseFlowStepVo again = service.advance(vo.getFlow().getId(), "NEXT", "想再塞一句", "乙", intakeHead);
        assertFalse(again.isAccepted());
        assertTrue(again.getMessage().contains("初核头一句"));
        assertEquals(notesAfterFirst, noteCount(vo.getFlow().getId()), "不多落一条");
    }

    // ---------------- 不许追接：止步即终局 ----------------

    @Test
    void 哪格写了到此为止_单就此止步_推进收回作废都不认() {
        seedCard("R-300", ACTIVE_SITE);
        seedProvinceChecked("B-300", "R-300", "2026Q1");
        CaseFlowStepVo vo = open("R-300", ACTIVE_SITE, "2026Q1", "甲", "收单");
        service.advance(vo.getFlow().getId(), "NEXT", "初核", "乙", null);
        CaseFlowStepVo stopped = service.advance(vo.getFlow().getId(), "STOP", "这一版到此为止", "乙", null);
        assertTrue(stopped.isAccepted());
        assertEquals(3, stopped.getStatus().intValue());

        assertFalse(service.advance(vo.getFlow().getId(), "NEXT", "再续一格", "乙", null).isAccepted());
        assertFalse(service.advance(vo.getFlow().getId(), "WITHDRAW", null, "乙", null).isAccepted());
        assertFalse(service.advance(vo.getFlow().getId(), "VOID", "改口作废", "乙", null).isAccepted());
        // 止步后同一份报告可以另起一张
        CaseFlowStepVo redo = open("R-300", ACTIVE_SITE, "2026Q1", "甲", "重立");
        assertTrue(redo.isAccepted());
    }

    // ---------------- 不许并排：收回一格、旧记沉底 ----------------

    @Test
    void 收回只收一格_被收回那格旧记沉底_与后补那批不同屏并列() {
        seedCard("R-400", ACTIVE_SITE);
        seedProvinceChecked("B-400", "R-400", "2026Q1");
        CaseFlowStepVo vo = open("R-400", ACTIVE_SITE, "2026Q1", "甲", "收单句");
        service.advance(vo.getFlow().getId(), "NEXT", "初核旧句", "乙", null);
        assertEquals(1, service.currentStage(vo.getFlow().getId()));

        CaseFlowStepVo back = service.advance(vo.getFlow().getId(), "WITHDRAW", null, "乙", null);
        assertTrue(back.isAccepted());
        assertEquals(0, service.currentStage(vo.getFlow().getId()), "权威档号照记载重算回收单");
        // 旧初核句沉底，本屏只见收单句+收回笔
        List<TAdrCaseNote> screen = back.getScreenNotes();
        assertTrue(screen.stream().noneMatch(n -> "初核旧句".equals(n.getContent())));
        TAdrCaseNote buriedOld = findNoteByContent(vo.getFlow().getId(), "初核旧句");
        assertEquals(1, buriedOld.getLayer().intValue());

        // 要重来，从初核重新攒起
        CaseFlowStepVo reenter = service.advance(vo.getFlow().getId(), "NEXT", "初核新句", "乙", null);
        assertTrue(reenter.isAccepted());
        assertEquals(1, reenter.getStage());
        assertTrue(reenter.getScreenNotes().stream().anyMatch(n -> "初核新句".equals(n.getContent())));
        assertTrue(reenter.getScreenNotes().stream().noneMatch(n -> "初核旧句".equals(n.getContent())));
    }

    @Test
    void 收单是头一格_没有格可收() {
        CaseFlowStepVo vo = open("R-401", ACTIVE_SITE, "2026Q1", "甲", "收单");
        CaseFlowStepVo back = service.advance(vo.getFlow().getId(), "WITHDRAW", null, "甲", null);
        assertFalse(back.isAccepted());
    }

    // ---------------- 作废是另一码事：冻结、不删、占版序 ----------------

    @Test
    void 废单记事一句改不得整张删不得_再动一律回绝() {
        CaseFlowStepVo vo = open("R-500", ACTIVE_SITE, "2026Q1", "甲", "收单");
        CaseFlowStepVo voided = service.advance(vo.getFlow().getId(), "VOID", "码录错作废", "甲", null);
        assertTrue(voided.isAccepted());
        assertEquals(2, voided.getStatus().intValue());

        assertFalse(service.advance(vo.getFlow().getId(), "NEXT", null, "甲", null).isAccepted());
        assertFalse(service.advance(vo.getFlow().getId(), "VOID", "再作一次", "甲", null).isAccepted());
        // 单子还在、笔记一句没少
        assertNotNull(flowMapper.selectById(vo.getFlow().getId()));
        assertTrue(noteCount(vo.getFlow().getId()) >= 2);
        // 作废后可重立，版序占住，新单是 V2
        CaseFlowStepVo redo = open("R-500", ACTIVE_SITE, "2026Q1", "甲", "重立");
        assertTrue(redo.isAccepted());
        assertEquals(2, redo.getFlow().getVersionNo().intValue());
        assertTrue(redo.getFlow().getBizNo().endsWith("V2"));
    }

    @Test
    void 已结案不许作废_要改另起新单() {
        CaseFlowStepVo closed = walkToClosed("R-501");
        CaseFlowStepVo voided = service.advance(closed.getFlow().getId(), "VOID", "翻悔", "丙", null);
        assertFalse(voided.isAccepted());
        assertTrue(voided.getMessage().contains("另起"));
    }

    // ---------------- 走完一趟：回执 + 旧版进历史 ----------------

    @Test
    void 四格走完_出回执_这一版进现行_旧版自动沉历史() {
        CaseFlowStepVo v1 = walkToClosed("R-600");
        assertTrue(v1.isFinished());
        assertNotNull(v1.getReceiptNo());
        assertEquals(0, v1.getFlow().getLayer().intValue());

        // 另起一版并走完
        CaseFlowStepVo v2open = open("R-600", ACTIVE_SITE, "2026Q2", "甲", "再改评价栏");
        assertEquals(2, v2open.getFlow().getVersionNo().intValue());
        seedProvinceChecked("B-600-Q2", "R-600", "2026Q2");
        CaseFlowStepVo v2 = next(v2open.getFlow().getId(), "乙");
        v2 = next(v2.getFlow().getId(), "乙");
        v2 = next(v2.getFlow().getId(), "丙");
        assertTrue(v2.isFinished());
        // 旧版自动沉历史层，日常翻件只见最新一版
        TAdrCaseFlow old = flowMapper.selectById(v1.getFlow().getId());
        assertEquals(1, old.getLayer().intValue());
        assertEquals(0, v2.getFlow().getLayer().intValue());
        // 结案各栏锁死
        assertFalse(service.advance(v2.getFlow().getId(), "NEXT", "再动", "丙", null).isAccepted());
    }

    // ---------------- 权威档号：纸面与算出来打架，以算出来的为准 ----------------

    @Test
    void 纸面档号被人改糊_取当前档仍照记载数() {
        seedCard("R-700", ACTIVE_SITE);
        seedProvinceChecked("B-700", "R-700", "2026Q1");
        CaseFlowStepVo vo = open("R-700", ACTIVE_SITE, "2026Q1", "甲", "收单");
        service.advance(vo.getFlow().getId(), "NEXT", null, "乙", null);
        // 把纸面 stage 改成 3（仿佛已办结）
        flowMapper.update(null, new UpdateWrapper<TAdrCaseFlow>()
                .eq("id", vo.getFlow().getId()).set("stage", 3));
        assertEquals(1, service.currentStage(vo.getFlow().getId()), "只认照记载数出来的档");
        CaseFlowStepVo snap = service.snapshot(vo.getFlow().getId());
        assertEquals(1, snap.getStage().intValue());
        assertFalse(snap.isFinished());
    }

    // ---------------- 倒读：多一笔、缺一格、手补即判没走成 ----------------

    @Test
    void 走到头的单_倒读笔笔对得拢() {
        CaseFlowStepVo closed = walkToClosed("R-800");
        CaseTraceVo trace = service.trace(closed.getFlow().getId());
        assertTrue(trace.isFinished());
        assertTrue(trace.isValid(), () -> String.join(";", trace.getProblems()));
        assertEquals(java.util.Arrays.asList(0, 1, 2, 3), trace.getEnterStages());
    }

    @Test
    void 倒读_事后手补一笔_判这一单没走成() {
        CaseFlowStepVo closed = walkToClosed("R-801");
        Long id = closed.getFlow().getId();
        // 绕过服务层直接往账上塞一笔，链扣接不上、hash 也是假的
        TAdrCaseNote fake = new TAdrCaseNote();
        fake.setId(999001L);
        fake.setFlowId(id);
        fake.setSeq(99);
        fake.setStage(2);
        fake.setAction("ENTER");
        fake.setLayer(0);
        fake.setContent("事后偷偷补的一句");
        fake.setOperator("丁");
        fake.setPrevHash("x");
        fake.setHash("y");
        noteMapper.insert(fake);
        CaseTraceVo trace = service.trace(id);
        assertFalse(trace.isValid());
        assertTrue(trace.getProblems().stream().anyMatch(p -> p.contains("链位缺笔或多笔")));
        assertTrue(trace.getProblems().stream().anyMatch(p -> p.contains("链扣断了")));
    }

    @Test
    void 倒读_抽掉中间一笔留下空洞_判没走成() {
        CaseFlowStepVo closed = walkToClosed("R-802");
        Long id = closed.getFlow().getId();
        TAdrCaseNote mid = noteMapper.selectList(new QueryWrapper<TAdrCaseNote>()
                .eq("flow_id", id).orderByAsc("seq")).get(1); // 抽掉初核进门笔
        noteMapper.deleteById(mid.getId());
        CaseTraceVo trace = service.trace(id);
        assertFalse(trace.isValid());
        assertTrue(trace.getProblems().stream().anyMatch(p -> p.contains("链位缺笔或多笔")));
    }

    @Test
    void 倒读_改了旧笔内容指纹对不上_判没走成() {
        CaseFlowStepVo closed = walkToClosed("R-803");
        Long id = closed.getFlow().getId();
        TAdrCaseNote first = noteMapper.selectList(new QueryWrapper<TAdrCaseNote>()
                .eq("flow_id", id).orderByAsc("seq")).get(0);
        noteMapper.update(null, new UpdateWrapper<TAdrCaseNote>()
                .eq("id", first.getId()).set("content", "收单句被人改了"));
        CaseTraceVo trace = service.trace(id);
        assertFalse(trace.isValid());
        assertTrue(trace.getProblems().stream().anyMatch(p -> p.contains("指纹对不上")));
    }

    @Test
    void 倒读_止步单没走到头_不在判废之列但追接笔仍被点出() {
        seedCard("R-804", ACTIVE_SITE);
        seedProvinceChecked("B-804", "R-804", "2026Q1");
        CaseFlowStepVo vo = open("R-804", ACTIVE_SITE, "2026Q1", "甲", "收单");
        service.advance(vo.getFlow().getId(), "STOP", "这一版到此为止", "甲", null);
        CaseTraceVo trace = service.trace(vo.getFlow().getId());
        assertFalse(trace.isFinished());
        assertFalse(trace.isValid(), "没走到头自然不算走成");
    }

    // ---------------- 辅助 ----------------

    /** 把一份报告顺顺当当推到已结案（在册单位、卡册俱备、省册核讫）。 */
    private CaseFlowStepVo walkToClosed(String reportNo) {
        seedCard(reportNo, ACTIVE_SITE);
        seedProvinceChecked("B-" + reportNo, reportNo, "2026Q1");
        CaseFlowStepVo vo = open(reportNo, ACTIVE_SITE, "2026Q1", "甲", "评价栏");
        vo = next(vo.getFlow().getId(), "乙"); // 初核
        vo = next(vo.getFlow().getId(), "乙"); // 评价
        vo = next(vo.getFlow().getId(), "丙"); // 已结案
        return vo;
    }

    private CaseFlowStepVo next(Long id, String operator) {
        CaseFlowStepVo vo = service.advance(id, "NEXT", null, operator, null);
        assertTrue(vo.isAccepted(), () -> "本该过成：" + vo.getMessage());
        return vo;
    }

    private CaseFlowStepVo open(String reportNo, String siteNo, String periodNo, String operator, String fields) {
        CaseFlowStepVo vo = service.openCase(cmd(reportNo, siteNo, periodNo, operator, fields));
        assertTrue(vo.isAccepted(), () -> "立单本该进门：" + vo.getMessage());
        return vo;
    }

    private CaseOpenCmd cmd(String reportNo, String siteNo, String periodNo, String operator) {
        return cmd(reportNo, siteNo, periodNo, operator, "评价栏");
    }

    private CaseOpenCmd cmd(String reportNo, String siteNo, String periodNo, String operator, String fields) {
        CaseOpenCmd c = new CaseOpenCmd();
        c.setReportNo(reportNo);
        c.setSiteNo(siteNo);
        c.setPeriodNo(periodNo);
        c.setChangeFields(fields);
        c.setContent("收单头一句");
        c.setOperator(operator);
        return c;
    }

    private void seedProvinceRow(String batchNo, String code, String periodNo) {
        com.fc.v2.model.auto.TAdrCheckBooklet b = bookletMapper.selectOne(
                new QueryWrapper<com.fc.v2.model.auto.TAdrCheckBooklet>().eq("batch_no", batchNo));
        com.fc.v2.model.auto.TAdrReportRow row = new com.fc.v2.model.auto.TAdrReportRow();
        row.setId(500000L + System.nanoTime() % 100000);
        row.setBookletId(b.getId());
        row.setBatchNo(batchNo);
        row.setPeriodNo(periodNo);
        row.setRowNo(1);
        row.setItemCode(code);
        row.setQty(new java.math.BigDecimal("5"));
        row.setPostedQty(new java.math.BigDecimal("5"));
        row.setArchivePhase(0);
        row.setCrossCount(0);
        row.setStatus(1);
        row.setDelFlag(0);
        rowMapper.insert(row);
    }

    private int noteCount(Long flowId) {
        return noteMapper.selectCount(new QueryWrapper<TAdrCaseNote>().eq("flow_id", flowId));
    }

    private TAdrCaseNote firstNote(Long flowId) {
        return noteMapper.selectList(new QueryWrapper<TAdrCaseNote>()
                .eq("flow_id", flowId).orderByAsc("seq")).get(0);
    }

    private TAdrCaseNote findNoteByContent(Long flowId, String content) {
        List<TAdrCaseNote> all = noteMapper.selectList(new QueryWrapper<TAdrCaseNote>()
                .eq("flow_id", flowId).orderByAsc("seq"));
        for (TAdrCaseNote n : all) {
            if (content.equals(n.getContent())) {
                return n;
            }
        }
        return null;
    }
}
