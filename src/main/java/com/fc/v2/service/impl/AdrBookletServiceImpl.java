package com.fc.v2.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fc.v2.mapper.auto.TAdrCheckBookletMapper;
import com.fc.v2.mapper.auto.TAdrCheckPeriodMapper;
import com.fc.v2.mapper.auto.TAdrReportRowMapper;
import com.fc.v2.mapper.auto.TAdrTermCardMapper;
import com.fc.v2.model.auto.TAdrCheckBooklet;
import com.fc.v2.model.auto.TAdrCheckPeriod;
import com.fc.v2.model.auto.TAdrReportRow;
import com.fc.v2.model.auto.TAdrTermCard;
import com.fc.v2.model.custom.adr.BookletReceiptVo;
import com.fc.v2.model.custom.adr.BookletReceiveVo;
import com.fc.v2.model.custom.adr.CompareLineVo;
import com.fc.v2.model.custom.adr.IncomingRow;
import com.fc.v2.model.custom.adr.ReconcileVo;
import com.fc.v2.service.IAdrBookletService;
import com.fc.v2.util.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 上级下发对照册核对 Service 实现。
 *
 * 三桩旧案的断点：
 * 1) 同册号二次进门只走比对一层（{@link #receiveBooklet} 分支），落点不动、行不增不改，
 *    先头那遍原样留着；改口只许走 {@link #voidBooklet} 作废重报——杜绝第二遍冲第一遍。
 * 2) 同一追溯码本册双行，落账合成一条（{@link #receiveBooklet} 去重），份数只计一次、位次留靠前。
 * 3) 回执「已核完」必须有在册（{@link #receipt} 对在册册头重算），查无此册直接回绝，不许凭空核讫。
 *
 * @author fuce
 * @date 2026-09-30
 */
@Service
public class AdrBookletServiceImpl implements IAdrBookletService {

    /** 行落点：待核 */
    private static final int STATUS_PENDING = 0;
    /** 行落点：已挂接 */
    private static final int STATUS_LINKED = 1;
    /** 行落点：挂起 */
    private static final int STATUS_SUSPENDED = 2;

    /** 档层：现行 */
    private static final int PHASE_CURRENT = 0;
    /** 档层：往期（只供事后查账） */
    private static final int PHASE_ARCHIVED = 1;

    /** 回执：未回执 */
    private static final int RECEIPT_NONE = 0;
    /** 回执：核讫 */
    private static final int RECEIPT_DONE = 1;
    /** 回执：空册（全册无行可核，不许写核讫） */
    private static final int RECEIPT_EMPTY = 2;

    /** 连跨两个核对期仍挂起，搬入往期层 */
    private static final int CROSS_LIMIT = 2;

    /** 单册行数上限缺省值（中心另有定数时以进门参数为准） */
    private static final int DEFAULT_ROW_CAP = 500;

    @Resource
    private TAdrCheckBookletMapper bookletMapper;
    @Resource
    private TAdrCheckPeriodMapper periodMapper;
    @Resource
    private TAdrReportRowMapper rowMapper;
    @Resource
    private TAdrTermCardMapper termCardMapper;

    @Override
    public TAdrCheckPeriod openPeriod(String periodNo, BigDecimal carryInQty) {
        if (StringUtils.isEmpty(periodNo)) {
            throw new IllegalArgumentException("期次号不许空着");
        }
        TAdrCheckPeriod exist = getPeriod(periodNo);
        if (exist != null) {
            return exist;
        }
        TAdrCheckPeriod p = new TAdrCheckPeriod();
        p.setPeriodNo(periodNo);
        p.setCarryInQty(carryInQty == null ? BigDecimal.ZERO : carryInQty);
        p.setUsedQty(BigDecimal.ZERO);
        p.setClosed(0);
        p.setDelFlag(0);
        this.periodMapper.insert(p);
        return p;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BookletReceiveVo receiveBooklet(String batchNo, String periodNo, int rowCap, List<IncomingRow> rows) {
        if (StringUtils.isEmpty(batchNo)) {
            throw new IllegalArgumentException("下发册号不许空着");
        }
        if (StringUtils.isEmpty(periodNo)) {
            throw new IllegalArgumentException("进册核对期次号不许空着");
        }
        List<IncomingRow> incoming = rows == null ? new ArrayList<>() : rows;

        TAdrCheckBooklet live = getLiveBooklet(batchNo);

        // —— 同一册号第二次进门：只走比对这一层，落点不动、行不增不改，先头那遍原样留着 ——
        if (live != null) {
            return compareOnly(live, incoming);
        }

        // —— 进册先按整册把关：越线原册退回、一行不留 ——
        int cap = rowCap > 0 ? rowCap : DEFAULT_ROW_CAP;
        if (incoming.size() > cap) {
            return BookletReceiveVo.rejected(batchNo,
                    "本册行数" + incoming.size() + "越过中心定的上限" + cap + "，原册退回、一行不留");
        }

        TAdrCheckPeriod period = getPeriod(periodNo);
        if (period == null) {
            period = openPeriod(periodNo, BigDecimal.ZERO);
        }
        // 期账行上锁：并发进册也串行化占用挂下额度，把"本期至多三十"这条账按住
        period = this.periodMapper.selectOne(new QueryWrapper<TAdrCheckPeriod>()
                .eq("id", period.getId())
                .last("for update"));
        TAdrCheckBooklet booklet = new TAdrCheckBooklet();
        booklet.setBatchNo(batchNo);
        booklet.setPeriodNo(periodNo);
        booklet.setRowCap(cap);
        booklet.setTotalRows(0);
        booklet.setLinkedRows(0);
        booklet.setSuspendedRows(0);
        booklet.setVoidFlag(0);
        booklet.setReceiptStatus(RECEIPT_NONE);
        booklet.setDelFlag(0);
        this.bookletMapper.insert(booklet);

        // 同一追溯码本册双行：合成一条，位次留靠前那一个；LinkedHashMap 保进门先后
        Map<String, Integer> firstSeat = new LinkedHashMap<>();
        int duplicateLines = 0;
        for (int i = 0; i < incoming.size(); i++) {
            String code = incoming.get(i).getItemCode() == null ? null : incoming.get(i).getItemCode().trim();
            if (code != null && firstSeat.containsKey(code)) {
                duplicateLines++;
                continue;
            }
            firstSeat.put(code, i);
        }

        BigDecimal quota = period.getCarryInQty() == null ? BigDecimal.ZERO : period.getCarryInQty();
        BigDecimal used = period.getUsedQty() == null ? BigDecimal.ZERO : period.getUsedQty();

        int linked = 0;
        int suspended = 0;
        for (Map.Entry<String, Integer> seat : firstSeat.entrySet()) {
            String code = seat.getKey();
            int index = seat.getValue();
            IncomingLine line = new IncomingLine(code, incoming.get(index).getQty());

            TAdrReportRow r = new TAdrReportRow();
            r.setBookletId(booklet.getId());
            r.setBatchNo(batchNo);
            r.setPeriodNo(periodNo);
            // 位次照进门先后排定（双行留靠前位次），此后这一行走到哪一步都改不掉它
            r.setRowNo(index + 1);
            r.setItemCode(line.code);
            r.setQty(line.qty);
            r.setArchivePhase(PHASE_CURRENT);
            r.setCrossCount(0);
            r.setDelFlag(0);

            String reason = suspendReason(line.code, line.qty, periodNo);
            if (reason != null) {
                // 不合只停这一行；同册其余各行照旧往下走
                r.setStatus(STATUS_SUSPENDED);
                r.setPostedQty(null);
                r.setSuspendReason(reason);
                suspended++;
            } else {
                // 本期能核销的以本期挂下数封顶；多出来的一截既不记本行，也不移抵他行
                BigDecimal remaining = quota.subtract(used);
                BigDecimal posted = line.qty.min(remaining.max(BigDecimal.ZERO));
                r.setStatus(STATUS_LINKED);
                r.setPostedQty(posted);
                r.setSuspendReason(null);
                used = used.add(posted);
                linked++;
            }
            this.rowMapper.insert(r);
        }

        int total = firstSeat.size();
        booklet.setTotalRows(total);
        booklet.setLinkedRows(linked);
        booklet.setSuspendedRows(suspended);
        this.bookletMapper.updateById(booklet);
        this.periodMapper.update(null, new UpdateWrapper<TAdrCheckPeriod>()
                .eq("id", period.getId())
                .set("used_qty", used));

        BookletReceiveVo vo = new BookletReceiveVo();
        vo.setAccepted(true);
        vo.setCompareOnly(false);
        vo.setBookletId(booklet.getId());
        vo.setBatchNo(batchNo);
        vo.setPeriodNo(periodNo);
        vo.setRowCap(cap);
        vo.setTotalRows(total);
        vo.setLinkedRows(linked);
        vo.setSuspendedRows(suspended);
        vo.setMessage(duplicateLines > 0
                ? "收下册讫；同码双行" + duplicateLines + "行已合一（份数只计一次、位次留靠前）"
                : "收下册讫");
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int voidBooklet(String batchNo, String reason) {
        TAdrCheckBooklet live = getLiveBooklet(batchNo);
        if (live == null) {
            return 0;
        }
        List<TAdrReportRow> all = this.rowMapper.selectList(new QueryWrapper<TAdrReportRow>()
                .eq("booklet_id", live.getId())
                .eq("del_flag", 0));

        // 开口期内释放本册已占用的挂下额度；已封账的期不动旧账
        TAdrCheckPeriod period = getPeriod(live.getPeriodNo());
        if (period != null && Integer.valueOf(0).equals(period.getClosed())) {
            BigDecimal release = BigDecimal.ZERO;
            for (TAdrReportRow r : all) {
                if (Integer.valueOf(STATUS_LINKED).equals(r.getStatus()) && r.getPostedQty() != null) {
                    release = release.add(r.getPostedQty());
                }
            }
            BigDecimal newUsed = period.getUsedQty().subtract(release).max(BigDecimal.ZERO);
            this.periodMapper.update(null, new UpdateWrapper<TAdrCheckPeriod>()
                    .eq("id", period.getId())
                    .set("used_qty", newUsed));
        }

        for (TAdrReportRow r : all) {
            r.setDelFlag(1);
            this.rowMapper.updateById(r);
        }
        // void_flag 置 NULL：唯一索引只挡在册重号，同册号作废后可重新进门、也允许多次作废
        this.bookletMapper.update(null, new UpdateWrapper<TAdrCheckBooklet>()
                .eq("id", live.getId())
                .set("void_flag", null)
                .set("receipt_status", RECEIPT_NONE)
                .set("remark", "已作废：" + (reason == null ? "" : reason)));
        return all.size();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TAdrReportRow reviewSuspended(Long rowId, String note) {
        if (rowId == null) {
            return null;
        }
        TAdrReportRow r = this.rowMapper.selectById(rowId);
        if (r == null || !Integer.valueOf(0).equals(r.getDelFlag())
                || !Integer.valueOf(PHASE_CURRENT).equals(r.getArchivePhase())
                || !Integer.valueOf(STATUS_SUSPENDED).equals(r.getStatus())) {
            // 查无、已删、已入往期、已挂接的，都不该走这个入口
            return null;
        }
        // 行的期次固定为进册期；跨期复核时，额度对准当前开口期（其挂下额度里正含这笔）
        TAdrCheckPeriod period = currentOpenPeriod();
        boolean periodOpen = period != null;

        // 照当下情形重过四种不合校验
        String reason = suspendReason(r.getItemCode(), r.getQty(), r.getPeriodNo());
        if (reason != null || !periodOpen) {
            // 仍不合（或没有开口期）：保持挂起，刷新缘故；位次一行不改
            r.setStatus(STATUS_SUSPENDED);
            r.setPostedQty(null);
            r.setSuspendReason(reason != null ? reason : "眼下没有开口的核对期，待新期挂下后再核");
            if (StringUtils.isNotEmpty(note)) {
                r.setRemark(note);
            }
            this.rowMapper.updateById(r);
            return r;
        }

        // 落「已挂接」：按开口期剩余挂下额度封顶；多出来的一截不记本行、不移抵他行
        BigDecimal quota = period.getCarryInQty() == null ? BigDecimal.ZERO : period.getCarryInQty();
        BigDecimal used = period.getUsedQty() == null ? BigDecimal.ZERO : period.getUsedQty();
        BigDecimal remaining = quota.subtract(used).max(BigDecimal.ZERO);
        BigDecimal posted = r.getQty().min(remaining);
        r.setStatus(STATUS_LINKED);
        r.setPostedQty(posted);
        r.setSuspendReason(null);
        if (StringUtils.isNotEmpty(note)) {
            r.setRemark(note);
        }
        this.rowMapper.updateById(r);
        this.periodMapper.update(null, new UpdateWrapper<TAdrCheckPeriod>()
                .eq("id", period.getId())
                .set("used_qty", used.add(posted)));
        // 行落点一变，册头三数当即按逐行重算刷新——屏上只读数，不许自己点数
        refreshBookletCounts(r.getBookletId(), false);
        return r;
    }

    /** 册头三数永远从现行层逐行重算回写；receiptOnly 时顺带按有无行定核讫/空册。 */
    private void refreshBookletCounts(Long bookletId, boolean stampReceipt) {
        int[] cnt = countByStatus(bookletId);
        int total = cnt[0] + cnt[1] + cnt[2];
        UpdateWrapper<TAdrCheckBooklet> uw = new UpdateWrapper<TAdrCheckBooklet>()
                .eq("id", bookletId)
                .eq("void_flag", 0)
                .set("total_rows", total)
                .set("linked_rows", cnt[1])
                .set("suspended_rows", cnt[2]);
        if (stampReceipt) {
            uw.set("receipt_status", total == 0 ? RECEIPT_EMPTY : RECEIPT_DONE);
        }
        this.bookletMapper.update(null, uw);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BookletReceiptVo receipt(String batchNo) {
        TAdrCheckBooklet live = getLiveBooklet(batchNo);
        if (live == null) {
            // 回执写「已核完」而系统查无此册——今年直接回绝，不许凭空核讫
            throw new IllegalStateException("册号" + batchNo + "在系统里查无在册，回执不许落核讫");
        }
        int[] cnt = countByStatus(live.getId());
        int total = cnt[0] + cnt[1] + cnt[2];
        int linked = cnt[1];
        int suspended = cnt[2];

        // 全册无行可核只落「空册」；核讫是报完成绩用的，空册不算成绩
        String label = total == 0 ? "空册" : "核讫";

        // 三个数与逐行落库点算同源：册头照逐行重算的结果回写，屏上自点数不算
        refreshBookletCounts(live.getId(), true);

        BigDecimal postedTotal = BigDecimal.ZERO;
        for (TAdrReportRow r : currentRows(live.getId())) {
            if (Integer.valueOf(STATUS_LINKED).equals(r.getStatus()) && r.getPostedQty() != null) {
                postedTotal = postedTotal.add(r.getPostedQty());
            }
        }

        BookletReceiptVo vo = new BookletReceiptVo();
        vo.setBookletId(live.getId());
        vo.setBatchNo(batchNo);
        vo.setLabel(label);
        vo.setTotalRows(total);
        vo.setLinkedRows(linked);
        vo.setSuspendedRows(suspended);
        vo.setBalanced(linked + suspended == total);
        vo.setPostedTotal(postedTotal);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TAdrCheckPeriod openNextPeriod(String newPeriodNo) {
        if (StringUtils.isEmpty(newPeriodNo)) {
            throw new IllegalArgumentException("新期次号不许空着");
        }
        if (getPeriod(newPeriodNo) != null) {
            throw new IllegalStateException("期次号" + newPeriodNo + "已经存在，不许重开");
        }

        // 所有仍开口的旧期一并封账（通常只有一个）
        List<TAdrCheckPeriod> openPeriods = this.periodMapper.selectList(new QueryWrapper<TAdrCheckPeriod>()
                .eq("closed", 0)
                .eq("del_flag", 0));

        BigDecimal carry = BigDecimal.ZERO;
        if (!openPeriods.isEmpty()) {
            // 仍挂着的现行行跨过一道期界
            this.rowMapper.update(null, new UpdateWrapper<TAdrReportRow>()
                    .eq("archive_phase", PHASE_CURRENT)
                    .eq("status", STATUS_SUSPENDED)
                    .eq("del_flag", 0)
                    .setSql("cross_count = cross_count + 1"));
            // 连跨两个核对期仍无落点：搬往期层，现行册面不再出现
            List<TAdrReportRow> expired = this.rowMapper.selectList(new QueryWrapper<TAdrReportRow>()
                    .eq("archive_phase", PHASE_CURRENT)
                    .eq("status", STATUS_SUSPENDED)
                    .eq("del_flag", 0)
                    .ge("cross_count", CROSS_LIMIT));
            Date now = new Date();
            java.util.Set<Long> touchedBooklets = new java.util.HashSet<>();
            for (TAdrReportRow r : expired) {
                touchedBooklets.add(r.getBookletId());
                this.rowMapper.update(null, new UpdateWrapper<TAdrReportRow>()
                        .eq("id", r.getId())
                        .set("archive_phase", PHASE_ARCHIVED)
                        .set("archive_time", now));
            }
            // 行搬入往期后，受影响册的册头三数也照现行层逐行重算
            for (Long bookletId : touchedBooklets) {
                refreshBookletCounts(bookletId, false);
            }
            // 本期挂下额度 = 上期仍挂着（未入往期）那一笔的份数；往期行不牵住新开册
            for (TAdrReportRow r : this.rowMapper.selectList(new QueryWrapper<TAdrReportRow>()
                    .eq("archive_phase", PHASE_CURRENT)
                    .eq("status", STATUS_SUSPENDED)
                    .eq("del_flag", 0))) {
                if (r.getQty() != null && r.getQty().compareTo(BigDecimal.ZERO) > 0) {
                    carry = carry.add(r.getQty());
                }
            }
            for (TAdrCheckPeriod p : openPeriods) {
                this.periodMapper.update(null, new UpdateWrapper<TAdrCheckPeriod>()
                        .eq("id", p.getId())
                        .set("closed", 1));
            }
        }

        TAdrCheckPeriod next = new TAdrCheckPeriod();
        next.setPeriodNo(newPeriodNo);
        next.setCarryInQty(carry);
        next.setUsedQty(BigDecimal.ZERO);
        next.setClosed(0);
        next.setDelFlag(0);
        this.periodMapper.insert(next);
        return next;
    }

    @Override
    public ReconcileVo reconcile() {
        ReconcileVo vo = new ReconcileVo();
        int rowTotal = 0;
        int linkedTotal = 0;
        int suspendedTotal = 0;
        boolean balanced = true;

        for (TAdrCheckBooklet b : listLiveBooklets()) {
            int[] cnt = countByStatus(b.getId());
            int total = cnt[0] + cnt[1] + cnt[2];
            rowTotal += total;
            linkedTotal += cnt[1];
            suspendedTotal += cnt[2];

            if (!Integer.valueOf(total).equals(b.getTotalRows())) {
                balanced = false;
                vo.getBookletDiffs().add(new ReconcileVo.BookletDiff(
                        b.getBatchNo(), "totalRows", nz(b.getTotalRows()), total));
            }
            if (!Integer.valueOf(cnt[1]).equals(b.getLinkedRows())) {
                balanced = false;
                vo.getBookletDiffs().add(new ReconcileVo.BookletDiff(
                        b.getBatchNo(), "linkedRows", nz(b.getLinkedRows()), cnt[1]));
            }
            if (!Integer.valueOf(cnt[2]).equals(b.getSuspendedRows())) {
                balanced = false;
                vo.getBookletDiffs().add(new ReconcileVo.BookletDiff(
                        b.getBatchNo(), "suspendedRows", nz(b.getSuspendedRows()), cnt[2]));
            }
            if (cnt[1] + cnt[2] != total) {
                balanced = false;
            }

            // 差在哪几行：当场逐行点出来，不许拿一个成数糊过去
            for (TAdrReportRow r : currentRows(b.getId())) {
                Integer st = r.getStatus();
                if (st == null || (st != STATUS_LINKED && st != STATUS_SUSPENDED)) {
                    balanced = false;
                    vo.getRowDiffs().add(new ReconcileVo.RowDiff(r.getId(), b.getBatchNo(), r.getRowNo(),
                            r.getItemCode(), st, "行还停在待核/落点非法，挂接+挂起接不平本册行数"));
                } else if (st == STATUS_SUSPENDED && StringUtils.isEmpty(r.getSuspendReason())) {
                    balanced = false;
                    vo.getRowDiffs().add(new ReconcileVo.RowDiff(r.getId(), b.getBatchNo(), r.getRowNo(),
                            r.getItemCode(), st, "挂起行缺缘故"));
                } else if (st == STATUS_LINKED && r.getPostedQty() == null) {
                    balanced = false;
                    vo.getRowDiffs().add(new ReconcileVo.RowDiff(r.getId(), b.getBatchNo(), r.getRowNo(),
                            r.getItemCode(), st, "已挂接行没落账份数"));
                }
            }
        }

        vo.setRowCount(rowTotal);
        vo.setLinkedCount(linkedTotal);
        vo.setSuspendedCount(suspendedTotal);
        vo.setBalanced(balanced);
        return vo;
    }

    @Override
    public List<TAdrReportRow> listRows(String batchNo) {
        TAdrCheckBooklet live = getLiveBooklet(batchNo);
        if (live == null) {
            return new ArrayList<>();
        }
        return currentRows(live.getId());
    }

    @Override
    public List<TAdrReportRow> listArchivedRows() {
        return this.rowMapper.selectList(new QueryWrapper<TAdrReportRow>()
                .eq("archive_phase", PHASE_ARCHIVED)
                .eq("del_flag", 0)
                .orderByAsc("batch_no", "row_no"));
    }

    @Override
    public List<TAdrCheckBooklet> listLiveBooklets() {
        return this.bookletMapper.selectList(new QueryWrapper<TAdrCheckBooklet>()
                .eq("void_flag", 0)
                .eq("del_flag", 0)
                .orderByAsc("batch_no"));
    }

    @Override
    public TAdrCheckBooklet getLiveBooklet(String batchNo) {
        return this.bookletMapper.selectOne(new QueryWrapper<TAdrCheckBooklet>()
                .eq("batch_no", batchNo)
                .eq("void_flag", 0)
                .eq("del_flag", 0)
                .last("limit 1"));
    }

    // ----------------------------------------------------------------------

    /** 二次进册：只拿册上每一行去比已经落库的存量行，比一比就完事，不做任何写入。 */
    private BookletReceiveVo compareOnly(TAdrCheckBooklet live, List<IncomingRow> incoming) {
        Map<Integer, TAdrReportRow> storedBySeat = new LinkedHashMap<>();
        for (TAdrReportRow r : this.rowMapper.selectList(new QueryWrapper<TAdrReportRow>()
                .eq("booklet_id", live.getId())
                .eq("del_flag", 0)
                .orderByAsc("row_no"))) {
            storedBySeat.put(r.getRowNo(), r);
        }
        List<CompareLineVo> lines = new ArrayList<>();
        for (int i = 0; i < incoming.size(); i++) {
            IncomingRow line = incoming.get(i);
            String code = line.getItemCode() == null ? null : line.getItemCode().trim();
            TAdrReportRow stored = storedBySeat.get(i + 1);
            // 只认码：位次相同且追溯码逐字相同才算对上；单位名号相近、撞名一律不算
            boolean matched = stored != null && code != null && code.equals(stored.getItemCode());
            lines.add(new CompareLineVo(i + 1, code, line.getQty(), matched,
                    matched ? stored.getId() : null,
                    matched ? stored.getStatus() : null));
        }
        BookletReceiveVo vo = new BookletReceiveVo();
        vo.setAccepted(false);
        vo.setCompareOnly(true);
        vo.setBookletId(live.getId());
        vo.setBatchNo(live.getBatchNo());
        vo.setPeriodNo(live.getPeriodNo());
        vo.setRowCap(nz(live.getRowCap()));
        int[] cnt = countByStatus(live.getId());
        vo.setTotalRows(cnt[0] + cnt[1] + cnt[2]);
        vo.setLinkedRows(cnt[1]);
        vo.setSuspendedRows(cnt[2]);
        vo.setCompareLines(lines);
        vo.setMessage("同一册号二次进门，只走比对一层；落点不动、行不增不改，先头那遍原样留着。要改口先走作废。");
        return vo;
    }

    /**
     * 四种不合情形，命中即挂起，返回一句话缘故；都不合规返回 null（可挂接）。
     * ①追溯码段在本级找不到对应的卡；②份数格子空着；③份数为零或负；④上一期同一笔事还挂着没结。
     */
    private String suspendReason(String code, BigDecimal qty, String periodNo) {
        if (StringUtils.isEmpty(code)) {
            return "追溯码段空着，没法对卡";
        }
        if (qty == null) {
            return "该填份数的格子空着";
        }
        if (qty.compareTo(BigDecimal.ZERO) <= 0) {
            return "份数写成零或负数（" + qty.stripTrailingZeros().toPlainString() + "）";
        }
        // 对卡口只认追溯码（卡号），不拿单位名号相近认门
        Integer cardCnt = this.termCardMapper.selectCount(new QueryWrapper<TAdrTermCard>()
                .eq("bill_no", code)
                .eq("del_flag", 0));
        if (cardCnt == null || cardCnt == 0) {
            return "追溯码段在本级找不到对应的卡";
        }
        // 上一期（更早的期次）同一笔事还挂着没结：现行层、期次序更早、仍挂起；
        // 只挡老账未结，不把本期册内同码的后一行当成"上一期"互相锁死
        Integer priorCnt = this.rowMapper.selectCount(new QueryWrapper<TAdrReportRow>()
                .eq("item_code", code)
                .eq("status", STATUS_SUSPENDED)
                .eq("archive_phase", PHASE_CURRENT)
                .eq("del_flag", 0)
                .lt("period_no", periodNo));
        if (priorCnt != null && priorCnt > 0) {
            return "上一期同一笔事还挂着没结";
        }
        return null;
    }

    private TAdrCheckPeriod getPeriod(String periodNo) {
        if (StringUtils.isEmpty(periodNo)) {
            return null;
        }
        return this.periodMapper.selectOne(new QueryWrapper<TAdrCheckPeriod>()
                .eq("period_no", periodNo)
                .eq("del_flag", 0)
                .last("limit 1"));
    }

    /** 当前开口期（多个时取期次序最大的那个）；开口期行上锁，串行化额度占用 */
    private TAdrCheckPeriod currentOpenPeriod() {
        List<TAdrCheckPeriod> open = this.periodMapper.selectList(new QueryWrapper<TAdrCheckPeriod>()
                .eq("closed", 0)
                .eq("del_flag", 0)
                .orderByDesc("period_no")
                .last("for update"));
        return open.isEmpty() ? null : open.get(0);
    }

    /** 现行层各行（往期行不在册面），按位次升序 */
    private List<TAdrReportRow> currentRows(Long bookletId) {
        return this.rowMapper.selectList(new QueryWrapper<TAdrReportRow>()
                .eq("booklet_id", bookletId)
                .eq("archive_phase", PHASE_CURRENT)
                .eq("del_flag", 0)
                .orderByAsc("row_no"));
    }

    /** 返回 [待核, 已挂接, 挂起] 现行行数 */
    private int[] countByStatus(Long bookletId) {
        int[] cnt = new int[3];
        for (TAdrReportRow r : currentRows(bookletId)) {
            Integer st = r.getStatus();
            if (Integer.valueOf(STATUS_LINKED).equals(st)) {
                cnt[1]++;
            } else if (Integer.valueOf(STATUS_SUSPENDED).equals(st)) {
                cnt[2]++;
            } else {
                cnt[0]++;
            }
        }
        return cnt;
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }

    /** 进册行的规整视图（码去空白） */
    private static final class IncomingLine {
        private final String code;
        private final BigDecimal qty;

        private IncomingLine(String code, BigDecimal qty) {
            this.code = code;
            this.qty = qty;
        }
    }
}
