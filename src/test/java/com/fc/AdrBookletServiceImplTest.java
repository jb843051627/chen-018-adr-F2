package com.fc;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.model.auto.TAdrCheckBooklet;
import com.fc.v2.model.auto.TAdrCheckPeriod;
import com.fc.v2.model.auto.TAdrReportRow;
import com.fc.v2.model.custom.adr.BookletReceiptVo;
import com.fc.v2.model.custom.adr.BookletReceiveVo;
import com.fc.v2.model.custom.adr.IncomingRow;
import com.fc.v2.model.custom.adr.ReconcileVo;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 对照册核对规矩集成测试——今年三桩旧案逐条断干净：
 * ①同册号二次进门冲账 → 只比对不覆写；②同码双行多记 → 合一留前；③空册/查无此册写核讫 → 回绝/空册。
 */
class AdrBookletServiceImplTest extends AdrBookletTestBase {

    private static List<IncomingRow> rows(Object... kv) {
        java.util.List<IncomingRow> list = new java.util.ArrayList<>();
        for (int i = 0; i < kv.length; i += 2) {
            list.add(new IncomingRow((String) kv[i], kv[i + 1] == null ? null : new BigDecimal(kv[i + 1].toString())));
        }
        return list;
    }

    private TAdrReportRow findRow(String batchNo, int rowNo) {
        return rowMapper.selectOne(new QueryWrapper<TAdrReportRow>()
                .eq("batch_no", batchNo).eq("row_no", rowNo).eq("del_flag", 0)
                .last("limit 1"));
    }

    // ---------------- 进门两件事：册号定死、位次自动且不可改 ----------------

    @Test
    void 行次照进门先后自动排定_挂起也占住位次() {
        service.openPeriod("2026Q1", new BigDecimal("100"));
        seedCard("CODE-A");
        seedCard("CODE-C");
        BookletReceiveVo vo = service.receiveBooklet("B001", "2026Q1", 500, rows(
                "CODE-A", 10,   // 行1 可挂接
                "CODE-X", 5,    // 行2 找不到卡 → 挂起，位次仍是2
                "CODE-C", 20)); // 行3 可挂接
        assertTrue(vo.isAccepted());
        assertEquals(Arrays.asList(1, 2, 3), Arrays.asList(
                service.listRows("B001").get(0).getRowNo(),
                service.listRows("B001").get(1).getRowNo(),
                service.listRows("B001").get(2).getRowNo()));
        TAdrReportRow suspended = findRow("B001", 2);
        assertEquals(2, suspended.getStatus());
        assertEquals("追溯码段在本级找不到对应的卡", suspended.getSuspendReason());
        // 翻挂起那一笔，直接带出原排第几，不用回纸页数
        assertEquals(2, suspended.getRowNo());
    }

    @Test
    void 一册一行不许混进别册() {
        service.openPeriod("2026Q1", new BigDecimal("100"));
        seedCard("CODE-A");
        service.receiveBooklet("B001", "2026Q1", 500, rows("CODE-A", 10));
        service.receiveBooklet("B002", "2026Q1", 500, rows("CODE-A", 30));
        TAdrCheckBooklet b1 = service.getLiveBooklet("B001");
        TAdrCheckBooklet b2 = service.getLiveBooklet("B002");
        assertEquals(1, service.listRows("B001").size());
        assertEquals(1, service.listRows("B002").size());
        assertEquals(b1.getId(), findRow("B001", 1).getBookletId());
        assertEquals(b2.getId(), findRow("B002", 1).getBookletId());
    }

    // ---------------- 四种不合只停那一行 ----------------

    @Test
    void 四种不合逐行挂起_其余行照旧挂接() {
        service.openPeriod("2026Q1", new BigDecimal("1000"));
        seedCard("CODE-OK");
        seedCard("CODE-NULL");
        seedCard("CODE-ZERO");
        seedCard("CODE-NEG");
        // 先在上期挂一笔 CODE-PRIOR，本期同码进来属"更早那笔还挂着"
        service.openPeriod("2025Q4", new BigDecimal("1000"));
        BookletReceiveVo prior = service.receiveBooklet("BP", "2025Q4", 500, rows("CODE-PRIOR", 5));
        // CODE-PRIOR 无卡 → 挂起
        assertEquals(2, findRow("BP", 1).getStatus());
        seedCard("CODE-PRIOR"); // 本期补了卡，但更早那笔仍挂着没结

        BookletReceiveVo vo = service.receiveBooklet("B002", "2026Q1", 500, rows(
                "CODE-OK", 10,      // ① 合规 挂接
                "CODE-MISS", 5,     // ② 找不到卡 挂起
                "CODE-NULL", null,  // ③ 份数空着 挂起
                "CODE-ZERO", 0,     // ④ 份数为零 挂起
                "CODE-NEG", -3,     //    份数为负 挂起
                "CODE-PRIOR", 7));  // ⑤ 上一期同一笔还挂着 挂起
        assertEquals(1, vo.getLinkedRows());
        assertEquals(5, vo.getSuspendedRows());
        assertEquals(6, vo.getTotalRows());
        assertTrue(findRow("B002", 3).getSuspendReason().contains("份数"));
        assertTrue(findRow("B002", 4).getSuspendReason().contains("零或负"));
        assertTrue(findRow("B002", 5).getSuspendReason().contains("零或负"));
        assertEquals("上一期同一笔事还挂着没结", findRow("B002", 6).getSuspendReason());
    }

    // ---------------- 册级账一：本期核销以本期挂下数封顶 ----------------

    @Test
    void 本期核销以挂下额度封顶_多出的不记本行也不移抵他行() {
        service.openPeriod("2026Q1", new BigDecimal("30")); // 上期挂下三十
        seedCard("CODE-A");
        seedCard("CODE-B");
        BookletReceiveVo vo = service.receiveBooklet("B003", "2026Q1", 500, rows(
                "CODE-A", 20,   // 本行20，额度剩10
                "CODE-B", 50)); // 本行要50，只能落10；多出40既不记本行也不移抵
        assertEquals(0, new BigDecimal("20").compareTo(findRow("B003", 1).getPostedQty()));
        assertEquals(0, new BigDecimal("10").compareTo(findRow("B003", 2).getPostedQty()));
        assertEquals(2, vo.getLinkedRows()); // 两码都对上卡，落点都是已挂接；只是落账被截
        TAdrCheckPeriod p = service.openPeriod("2026Q1", null);
        assertEquals(0, new BigDecimal("30").compareTo(p.getUsedQty())); // 本期至多三十
    }

    // ---------------- 册级账二：同码双行合一 ----------------

    @Test
    void 同码双行落账合一_份数只计一次_位次留靠前() {
        service.openPeriod("2026Q1", new BigDecimal("1000"));
        seedCard("DUP");
        BookletReceiveVo vo = service.receiveBooklet("B004", "2026Q1", 500, rows(
                "OTHER", 3,
                "DUP", 7,
                "DUP", 7)); // 册上双行
        assertEquals(2, vo.getTotalRows(), "双行合一后只剩两行");
        assertNotNull(findRow("B004", 2));
        assertNull(findRow("B004", 3), "靠后那行不落库");
        int dupRows = rowMapper.selectCount(new QueryWrapper<TAdrReportRow>()
                .eq("batch_no", "B004").eq("item_code", "DUP").eq("del_flag", 0));
        assertEquals(1, dupRows);
        assertEquals(0, new BigDecimal("7").compareTo(findRow("B004", 2).getPostedQty()));
        assertTrue(vo.getMessage().contains("合一"));
    }

    // ---------------- 册级账三：二次进册只走比对 ----------------

    @Test
    void 同册号第二次进门只比对_落点不动行不增不改() {
        service.openPeriod("2026Q1", new BigDecimal("100"));
        seedCard("CODE-A");
        service.receiveBooklet("B005", "2026Q1", 500, rows("CODE-A", 10));
        TAdrReportRow first = findRow("B005", 1);

        BookletReceiveVo second = service.receiveBooklet("B005", "2026Q1", 500, rows(
                "CODE-A", 999,   // 份数改了也不覆写
                "CODE-NEW", 1)); // 多一行也不增
        assertTrue(second.isCompareOnly());
        assertFalse(second.isAccepted());
        assertEquals(2, second.getCompareLines().size());
        assertTrue(second.getCompareLines().get(0).isMatched());
        assertFalse(second.getCompareLines().get(1).isMatched());

        TAdrReportRow still = findRow("B005", 1);
        assertEquals(first.getId(), still.getId());
        assertEquals(0, new BigDecimal("10").compareTo(still.getQty()), "先头那遍原样留着");
        assertEquals(1, service.listRows("B005").size(), "行不增");
    }

    @Test
    void 改口先走作废_作废后同册号可重新进门() {
        service.openPeriod("2026Q1", new BigDecimal("100"));
        seedCard("CODE-A");
        service.receiveBooklet("B006", "2026Q1", 500, rows("CODE-A", 60));
        assertEquals(0, new BigDecimal("60").compareTo(
                service.openPeriod("2026Q1", null).getUsedQty()));

        int n = service.voidBooklet("B006", "码录错了");
        assertEquals(1, n);
        assertNull(service.getLiveBooklet("B006"), "在册查不到了");
        assertEquals(0, new BigDecimal("0").compareTo(
                service.openPeriod("2026Q1", null).getUsedQty()), "作废释放占用额度");
        assertEquals(0, service.listRows("B006").size());

        // 同册号重新进门
        BookletReceiveVo redo = service.receiveBooklet("B006", "2026Q1", 500, rows("CODE-A", 40));
        assertTrue(redo.isAccepted());
        assertFalse(redo.isCompareOnly());
        assertEquals(0, new BigDecimal("40").compareTo(findRow("B006", 1).getPostedQty()));
    }

    // ---------------- 兜底：整册上限、空册 ----------------

    @Test
    void 越线原册退回_一行不留() {
        service.openPeriod("2026Q1", new BigDecimal("100000"));
        seedCard("C");
        List<IncomingRow> big = new java.util.ArrayList<>();
        for (int i = 0; i < 11; i++) {
            big.add(new IncomingRow("C", BigDecimal.ONE));
        }
        BookletReceiveVo vo = service.receiveBooklet("B007", "2026Q1", 10, big);
        assertFalse(vo.isAccepted());
        assertNull(service.getLiveBooklet("B007"), "册头不留");
        Integer rowCnt = rowMapper.selectCount(new QueryWrapper<TAdrReportRow>().eq("batch_no", "B007"));
        assertEquals(0, rowCnt, "一行不留");
    }

    @Test
    void 全册无行回执只落空册_不许写核讫() {
        service.openPeriod("2026Q1", new BigDecimal("10"));
        service.receiveBooklet("B008", "2026Q1", 500, java.util.Collections.emptyList());
        BookletReceiptVo rc = service.receipt("B008");
        assertEquals("空册", rc.getLabel());
        assertEquals(0, rc.getTotalRows());
        assertEquals(2, service.getLiveBooklet("B008").getReceiptStatus().intValue());
        assertTrue(rc.isBalanced());
    }

    @Test
    void 回执写已核完但查无此册_直接回绝() {
        assertThrows(IllegalStateException.class, () -> service.receipt("GHOST"));
    }

    @Test
    void 回执三数与逐行落库同源_挂接加挂起等于行数() {
        service.openPeriod("2026Q1", new BigDecimal("1000"));
        seedCard("CODE-A");
        service.receiveBooklet("B009", "2026Q1", 500, rows(
                "CODE-A", 10, "CODE-A", 10, "MISS1", 1, "MISS2", 2));
        BookletReceiptVo rc = service.receipt("B009");
        assertEquals("核讫", rc.getLabel());
        assertEquals(3, rc.getTotalRows());      // 同码双行合一
        assertEquals(1, rc.getLinkedRows());
        assertEquals(2, rc.getSuspendedRows());
        assertTrue(rc.isBalanced());             // 1+2=3
    }

    // ---------------- 挂起跨两期搬往期 ----------------

    @Test
    void 连跨两个核对期仍挂起_搬入往期_不牵住新一年开册() {
        service.openPeriod("2026Q1", new BigDecimal("1000"));
        // 无卡挂起
        service.receiveBooklet("B010", "2026Q1", 500, rows("LOST", 5));
        assertEquals(1, service.listRows("B010").size());

        service.openNextPeriod("2026Q2"); // 跨过第一道期界，仍现行
        assertEquals(1, service.listRows("B010").size());
        TAdrCheckPeriod q2 = service.openPeriod("2026Q2", null);
        assertEquals(0, new BigDecimal("5").compareTo(q2.getCarryInQty()), "挂着的份数结为本期挂下");

        service.openNextPeriod("2026Q3"); // 连跨两期 → 搬往期
        assertEquals(0, service.listRows("B010").size(), "现行册面不再出现");
        assertEquals(1, service.listArchivedRows().size());
        TAdrCheckPeriod q3 = service.openPeriod("2026Q3", null);
        assertEquals(0, new BigDecimal("0").compareTo(q3.getCarryInQty()),
                "已搬往期的那笔不再结为挂下额度；往期层只供查账、不牵住新期");

        // 再往后开新一年，往期行不再牵住
        service.openNextPeriod("2027Q1");
        assertEquals(1, service.listArchivedRows().size());
        assertEquals(0, service.listRows("B010").size());
    }

    @Test
    void 挂起行复核补上卡点后落挂接_只认码不认单位名号() {
        service.openPeriod("2026Q1", new BigDecimal("100"));
        service.receiveBooklet("B011", "2026Q1", 500, rows("CODE-Z", 8));
        TAdrReportRow row = findRow("B011", 1);
        assertEquals(2, row.getStatus());

        // 哪怕单位名号再相近，没有该追溯码的卡就是对不上：复核仍挂起、缘故刷新
        TAdrReportRow stillSuspended = service.reviewSuspended(row.getId(), null);
        assertNotNull(stillSuspended);
        assertEquals(2, stillSuspended.getStatus());
        assertEquals("追溯码段在本级找不到对应的卡", stillSuspended.getSuspendReason());

        seedCard("CODE-Z"); // 按码把卡补上
        TAdrReportRow reviewed = service.reviewSuspended(row.getId(), "卡已补");
        assertEquals(1, reviewed.getStatus());
        assertEquals(0, new BigDecimal("8").compareTo(reviewed.getPostedQty()));
        assertEquals(1, reviewed.getRowNo(), "位次一生不改");
    }

    @Test
    void 更早那笔结清后_本期同码行可复核挂接() {
        service.openPeriod("2025Q4", new BigDecimal("1000"));
        service.receiveBooklet("BOLD", "2025Q4", 500, rows("CODE-K", 5)); // 无卡挂起
        service.openPeriod("2026Q1", new BigDecimal("1000"));
        seedCard("CODE-K");
        // 老账仍挂着：本期新进来的同码行被挡下
        service.receiveBooklet("BNEW", "2026Q1", 500, rows("CODE-K", 9));
        TAdrReportRow newRow = findRow("BNEW", 1);
        assertEquals(2, newRow.getStatus());

        // 把更早那笔在本期复核结清
        TAdrReportRow oldRow = findRow("BOLD", 1);
        TAdrReportRow oldCleared = service.reviewSuspended(oldRow.getId(), null);
        assertEquals(1, oldCleared.getStatus());
        // 新行再核：第四条不再命中，落挂接
        TAdrReportRow nowCleared = service.reviewSuspended(newRow.getId(), null);
        assertEquals(1, nowCleared.getStatus());
        assertEquals(0, new BigDecimal("9").compareTo(nowCleared.getPostedQty()));
    }

    // ---------------- 全辖对账 ----------------

    @Test
    void 复核改落点与跨期归档后_册头自动按逐行重算接平() {
        service.openPeriod("2026Q1", new BigDecimal("100"));
        service.receiveBooklet("B100", "2026Q1", 500, rows("CODE-Q", 8)); // 无卡挂起
        seedCard("CODE-Q");
        service.reviewSuspended(findRow("B100", 1).getId(), null);      // 落挂接
        // 没收过回执，册头也该是 1 挂接 / 0 挂起
        assertTrue(service.reconcile().isBalanced(), "复核后册头自动重算");
        assertEquals(1, service.getLiveBooklet("B100").getLinkedRows().intValue());
        assertEquals(0, service.getLiveBooklet("B100").getSuspendedRows().intValue());

        // 再挂一笔，跨两期入往期后，册头现行行数归 0，全辖仍接平
        service.receiveBooklet("B101", "2026Q1", 500, rows("GONE", 4));
        service.openNextPeriod("2026Q2");
        service.openNextPeriod("2026Q3");
        assertEquals(0, service.getLiveBooklet("B101").getTotalRows().intValue());
        assertTrue(service.reconcile().isBalanced(), "归档后册头自动重算");
    }

    @Test
    void 账平时全辖接平() {
        service.openPeriod("2026Q1", new BigDecimal("1000"));
        seedCard("CODE-A");
        service.receiveBooklet("B012", "2026Q1", 500, rows("CODE-A", 10, "MISS", 1));
        ReconcileVo vo = service.reconcile();
        assertTrue(vo.isBalanced());
        assertEquals(2, vo.getRowCount());
        assertEquals(1, vo.getLinkedCount());
        assertEquals(1, vo.getSuspendedCount());
        assertTrue(vo.getBookletDiffs().isEmpty());
        assertTrue(vo.getRowDiffs().isEmpty());
    }

    @Test
    void 册头被改与逐行点算接不平_当场点出差在哪几行() {
        service.openPeriod("2026Q1", new BigDecimal("1000"));
        seedCard("CODE-A");
        service.receiveBooklet("B013", "2026Q1", 500, rows("CODE-A", 10));
        // 模拟屏上自己点数、把册头改糊
        TAdrCheckBooklet b = service.getLiveBooklet("B013");
        b.setLinkedRows(99);
        bookletMapper.updateById(b);

        ReconcileVo vo = service.reconcile();
        assertFalse(vo.isBalanced());
        assertEquals(1, vo.getBookletDiffs().size());
        assertEquals("linkedRows", vo.getBookletDiffs().get(0).getField());
        assertEquals(99, vo.getBookletDiffs().get(0).getBookletValue());
        assertEquals(1, vo.getBookletDiffs().get(0).getRowValue());
        // 权威口径仍以逐行点算为准
        assertEquals(1, vo.getLinkedCount());
    }

    @Test
    void 行落点被人改成待核_对账号到具体行() {
        service.openPeriod("2026Q1", new BigDecimal("1000"));
        seedCard("CODE-A");
        service.receiveBooklet("B014", "2026Q1", 500, rows("CODE-A", 10));
        TAdrReportRow r = findRow("B014", 1);
        r.setStatus(0); // 打回待核
        rowMapper.updateById(r);

        ReconcileVo vo = service.reconcile();
        assertFalse(vo.isBalanced());
        assertEquals(1, vo.getRowDiffs().size());
        assertEquals(r.getId(), vo.getRowDiffs().get(0).getRowId());
    }
}
