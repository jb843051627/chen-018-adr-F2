package com.fc.v2.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fc.v2.mapper.auto.TAdrCaseFlowMapper;
import com.fc.v2.mapper.auto.TAdrCheckBookletMapper;
import com.fc.v2.mapper.auto.TAdrOrgArchMapper;
import com.fc.v2.mapper.auto.TAdrReportRowMapper;
import com.fc.v2.mapper.auto.TAdrTermCardMapper;
import com.fc.v2.model.auto.TAdrCaseFlow;
import com.fc.v2.model.auto.TAdrReportRow;
import com.fc.v2.model.auto.TAdrTermCard;
import com.fc.v2.model.custom.adr.FlowAuditVo;
import com.fc.v2.service.ITAdrCaseFlowService;
import com.fc.v2.util.StringUtils;

/**
 * 报告处置单 Service业务层处理（state-machine 形状：单据流转）。
 *
 * 去年十一月省里回头抽查那桩旧案的断点：一份报告名下两版口径并排挂现行，追补按哪版走
 * 全看当日谁翻到哪一页。中心因此立规——口径要改，立处置单，口头与纸面都不作数。
 *
 * 存储形状（文书样式沿用现行那一张，不另起一套）：同一单号下一行单头＋每过一格落一行
 * 过格记事。单头是最小主键那行，档号永不落库（库里那格写了也不算）；此刻停在哪一档，
 * 由系统数已过完的格（在册记事的笔数）算出来。收回的那格记事置删除标记沉到底层，
 * 与后补的那批不在同一屏并列，但一句不毁，留待倒读。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TAdrCaseFlowServiceImpl implements ITAdrCaseFlowService {

    /** 四格格名：0收单 1初核 2评价 3已结案；从「待发起」起一格格推到「办结」 */
    private static final String[] STAGE_NAMES = {"收单", "初核", "评价", "已结案"};
    private static final int STAGE_ACCEPT = 0;
    private static final int STAGE_FIRST_CHECK = 1;
    private static final int STAGE_EVALUATE = 2;
    private static final int STAGE_CLOSED = 3;
    /** 走满四格（四条在册记事）才算办结 */
    private static final int FULL_STEPS = 4;

    /** 处置落点：未起（待发起） */
    private static final int STATUS_NOT_STARTED = 0;
    /** 处置落点：在办 */
    private static final int STATUS_ACTIVE = 1;
    /** 处置落点：已作废 */
    private static final int STATUS_VOIDED = 2;

    private static final int DEL_NORMAL = 0;
    /** 沉到底层（收回的那格旧记事；不毁、不与后补的并列） */
    private static final int DEL_SUNK = 1;

    /** 止步话头：哪一格记事写了「这一版到此为止」，单就此止步，不得再往后续一格 */
    private static final String STOP_MARK = "到此为止";

    /** 对照册回执情形：核讫（省里那册对照下来） */
    private static final int RECEIPT_DONE = 1;
    /** 核对行落点：挂起（没结完的核对事务） */
    private static final int ROW_SUSPENDED = 2;
    /** 核对行档层：现行 */
    private static final int PHASE_CURRENT = 0;
    /** 底册情形：已退出 */
    private static final int ORG_EXITED = 1;

    @javax.annotation.Resource
    private TAdrCaseFlowMapper adrCaseFlowMapper;
    @javax.annotation.Resource
    private TAdrReportRowMapper adrReportRowMapper;
    @javax.annotation.Resource
    private TAdrCheckBookletMapper adrCheckBookletMapper;
    @javax.annotation.Resource
    private TAdrTermCardMapper adrTermCardMapper;
    @javax.annotation.Resource
    private TAdrOrgArchMapper adrOrgArchMapper;

    @Override
    public TAdrCaseFlow selectTAdrCaseFlowById(Long id) {
        if (id == null) {
            return null;
        }
        TAdrCaseFlow row = this.adrCaseFlowMapper.selectById(id);
        if (row == null || !Integer.valueOf(DEL_NORMAL).equals(row.getDelFlag())) {
            // 沉到底层的旧记事原样回，只供查账，不参与算档
            return row;
        }
        TAdrCaseFlow head = headOf(row.getBizNo());
        if (head != null && head.getId().equals(row.getId())) {
            // 回的是单头：档号以系统数出来的为准。单头落库的档号若被人写花，
            // 先抹回空白——纸面写的与算出来的打架时，以算出来的为凭
            if (row.getStage() != null) {
                this.adrCaseFlowMapper.update(null, new UpdateWrapper<TAdrCaseFlow>()
                        .eq("id", row.getId())
                        .set("stage", null));
            }
            return headView(head);
        }
        return row;
    }

    @Override
    public List<TAdrCaseFlow> selectTAdrCaseFlowList(QueryWrapper<TAdrCaseFlow> queryWrapper) {
        return this.adrCaseFlowMapper.selectList(queryWrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TAdrCaseFlow fileBill(String bizNo, String cardCode, String siteNo, String changedFields, String note) {
        if (StringUtils.isEmpty(bizNo)) {
            throw new IllegalArgumentException("处置单号不许空着");
        }
        if (headOf(bizNo) != null) {
            // 单号在册，立重复的不许进门
            return null;
        }
        // 同一份报告名下，头一张在办的单没走完也没止步之前，立不起第二张
        if (StringUtils.isNotEmpty(cardCode) && hasActiveBillOfReport(cardCode)) {
            return null;
        }
        TAdrCaseFlow head = new TAdrCaseFlow();
        head.setBizNo(bizNo);
        // 待发起：档号不落库，留空白；此后停在哪一档一律由系统数已过完的格算
        head.setStage(null);
        head.setStatus(STATUS_NOT_STARTED);
        head.setContent(note);
        head.setLastAction("立单");
        head.setRemark(meta(cardCode, siteNo, changedFields));
        head.setDelFlag(DEL_NORMAL);
        this.adrCaseFlowMapper.insert(head);
        return head;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TAdrCaseFlow advance(Long id, String remark) {
        return doAdvance(resolveHead(id), remark);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TAdrCaseFlow advance(String bizNo, String remark) {
        if (StringUtils.isEmpty(bizNo)) {
            return null;
        }
        return doAdvance(headOf(bizNo), remark);
    }

    /** 推进那一格：一份报告此刻停在哪一档、能不能过下一档，只由这里算给回 */
    private TAdrCaseFlow doAdvance(TAdrCaseFlow head, String remark) {
        if (head == null) {
            return null;
        }
        if (Integer.valueOf(STATUS_VOIDED).equals(head.getStatus())) {
            return null; // 作废单一句改不得，更不许再走格
        }
        List<TAdrCaseFlow> records = liveRecords(head.getBizNo());
        if (records.size() >= FULL_STEPS) {
            return null; // 已办结：四格走完，没有下一格
        }
        if (isStopped(records)) {
            return null; // 不许追接：写了「这一版到此为止」，单就此止步
        }
        // 不许第二遍：同一格原话再提交，账上仍是头一遍那一句，不多落一条记事
        if (!records.isEmpty()) {
            TAdrCaseFlow top = records.get(records.size() - 1);
            if (remark != null && remark.equals(top.getContent())) {
                return headView(headOf(head.getBizNo()));
            }
        }
        // 不许抢步：只进紧邻的下一格，格号由系统数已过完的格得出，不许问屏上
        int next = records.size();
        if (!enterable(next, metaOf(head.getRemark()))) {
            return null; // 这一格的进门条件不齐，那一次不算过成
        }
        TAdrCaseFlow rec = new TAdrCaseFlow();
        rec.setBizNo(head.getBizNo());
        rec.setStage(next);
        rec.setStatus(STATUS_ACTIVE);
        rec.setContent(remark);
        // 已结案那一格：这一版进现行名录，对外那一页同时出一张回执
        rec.setLastAction(next == STAGE_CLOSED ? "结案·回执已出" : "过格·" + STAGE_NAMES[next]);
        rec.setDelFlag(DEL_NORMAL);
        this.adrCaseFlowMapper.insert(rec);
        // 单头只记落点与最近过口动作；档号永不落库
        this.adrCaseFlowMapper.update(null, new UpdateWrapper<TAdrCaseFlow>()
                .eq("id", head.getId())
                .set("status", STATUS_ACTIVE)
                .set("last_action", STAGE_NAMES[next])
                .set("stage", null));
        return headView(headOf(head.getBizNo()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TAdrCaseFlow rollback(Long id, String remark) {
        TAdrCaseFlow head = resolveHead(id);
        if (head == null || Integer.valueOf(STATUS_VOIDED).equals(head.getStatus())) {
            return null;
        }
        List<TAdrCaseFlow> records = liveRecords(head.getBizNo());
        if (records.size() >= FULL_STEPS) {
            return null; // 结案之后各栏锁死：再要动，另起一张新单
        }
        if (isStopped(records)) {
            return null; // 止步单不得借收回追接
        }
        if (records.isEmpty()) {
            return null; // 还停在待发起，无格可收
        }
        // 往回收只收一格：最上面那格的原记事沉到底层，与后补的那批不在同一屏并列
        TAdrCaseFlow top = records.get(records.size() - 1);
        this.adrCaseFlowMapper.update(null, new UpdateWrapper<TAdrCaseFlow>()
                .eq("id", top.getId())
                .set("del_flag", DEL_SUNK));
        int remain = records.size() - 1;
        String action = StringUtils.isEmpty(remark) ? "收回一档" : "收回一档：" + remark;
        this.adrCaseFlowMapper.update(null, new UpdateWrapper<TAdrCaseFlow>()
                .eq("id", head.getId())
                .set("status", remain == 0 ? STATUS_NOT_STARTED : STATUS_ACTIVE)
                .set("last_action", action.length() > 60 ? action.substring(0, 60) : action)
                .set("stage", null));
        return headView(headOf(head.getBizNo()));
    }

    @Override
    public boolean updateContent(Long id, String remark) {
        if (id == null) {
            return false;
        }
        TAdrCaseFlow row = this.adrCaseFlowMapper.selectById(id);
        if (row == null || !Integer.valueOf(DEL_NORMAL).equals(row.getDelFlag())) {
            return false; // 沉到底层的旧记事不许再翻上来改
        }
        TAdrCaseFlow head = headOf(row.getBizNo());
        if (head == null || Integer.valueOf(STATUS_VOIDED).equals(head.getStatus())) {
            return false; // 作废单的记事一句改不得
        }
        if (liveRecords(head.getBizNo()).size() >= FULL_STEPS) {
            return false; // 结案之后各栏锁死
        }
        row.setContent(remark);
        return this.adrCaseFlowMapper.updateById(row) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean remove(Long id) {
        TAdrCaseFlow head = resolveHead(id);
        if (head == null) {
            return false;
        }
        if (Integer.valueOf(STATUS_VOIDED).equals(head.getStatus())) {
            return false; // 作废单整张删不得——年底查账全凭这张废单
        }
        if (liveRecords(head.getBizNo()).size() >= FULL_STEPS) {
            return false; // 已办结不得删除
        }
        // 在办的单整张撤下：连单头带记事，含沉底那批
        this.adrCaseFlowMapper.delete(new QueryWrapper<TAdrCaseFlow>()
                .eq("biz_no", head.getBizNo()));
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean voidBill(Long id, String reason) {
        TAdrCaseFlow head = resolveHead(id);
        if (head == null) {
            return false;
        }
        if (Integer.valueOf(STATUS_VOIDED).equals(head.getStatus())) {
            return false; // 已是废单，不再作废第二遍
        }
        if (liveRecords(head.getBizNo()).size() >= FULL_STEPS) {
            return false; // 结案之后各栏锁死，要动另起一张新单
        }
        // 整单作废：连单头带记事（含沉底那批）一律置作废；记事原句保留，一句不改
        this.adrCaseFlowMapper.update(null, new UpdateWrapper<TAdrCaseFlow>()
                .eq("biz_no", head.getBizNo())
                .set("status", STATUS_VOIDED));
        this.adrCaseFlowMapper.update(null, new UpdateWrapper<TAdrCaseFlow>()
                .eq("id", head.getId())
                .set("last_action", "作废")
                .set("remark", (head.getRemark() == null ? "" : head.getRemark())
                        + "；作废：" + (reason == null ? "" : reason)));
        return true;
    }

    @Override
    public TAdrCaseFlow getCurrentBill(String cardCode) {
        if (StringUtils.isEmpty(cardCode)) {
            return null;
        }
        // 现行名录只认每份报告最新办结的那张单；旧那一版自动挪进历史那一层，
        // 不靠人翻页——名录是算出来的，日常翻件只见最新的一版
        TAdrCaseFlow current = null;
        for (Map.Entry<String, List<TAdrCaseFlow>> e : groupLiveByBill().entrySet()) {
            List<TAdrCaseFlow> rows = e.getValue();
            TAdrCaseFlow head = rows.get(0);
            if (!cardCode.equals(metaOf(head.getRemark())[0])) {
                continue;
            }
            if (Integer.valueOf(STATUS_VOIDED).equals(head.getStatus())) {
                continue;
            }
            if (rows.size() - 1 < FULL_STEPS) {
                continue; // 没办结的进不了现行名录
            }
            if (current == null || head.getId() > current.getId()) {
                current = head;
            }
        }
        return current == null ? null : headView(current);
    }

    @Override
    public FlowAuditVo auditTrail(Long id) {
        TAdrCaseFlow head = resolveHead(id);
        if (head == null) {
            return new FlowAuditVo(false, "查无此单", 0);
        }
        List<TAdrCaseFlow> records = liveRecords(head.getBizNo());
        int n = records.size();
        if (Integer.valueOf(STATUS_VOIDED).equals(head.getStatus())) {
            return new FlowAuditVo(false, "单已作废，谈不上走完一趟", n);
        }
        if (n < FULL_STEPS) {
            return new FlowAuditVo(false,
                    "缺一格：从已结案一路数回收单，在册记事只有" + n + "笔，断档了，这一单没走成", n);
        }
        if (n > FULL_STEPS) {
            return new FlowAuditVo(false,
                    "多出一笔：四格之外在册记事有" + n + "笔，这一单没走成", n);
        }
        // 按落库先后（主键升序）倒读，格序必须是收单、初核、评价、已结案，一格不多一格不少；
        // 事后由人手补进去的，落库先后与格序对不上，见着一处就判定没走成
        for (int i = 0; i < FULL_STEPS; i++) {
            if (!Integer.valueOf(i).equals(records.get(i).getStage())) {
                return new FlowAuditVo(false,
                        "第" + (i + 1) + "格（" + STAGE_NAMES[i] + "）对不上当日那一屏，这一单没走成", n);
            }
        }
        return new FlowAuditVo(true, "倒读四格笔笔对得上，这一单走成了", n);
    }

    // ----------------------------------------------------------------------

    /** 由任意在册行（单头或某格记事）找到这一单的单头 */
    private TAdrCaseFlow resolveHead(Long id) {
        if (id == null) {
            return null;
        }
        TAdrCaseFlow row = this.adrCaseFlowMapper.selectById(id);
        if (row == null || !Integer.valueOf(DEL_NORMAL).equals(row.getDelFlag())) {
            return null;
        }
        return headOf(row.getBizNo());
    }

    /** 单头：这一单在册行里最小主键那行（立单时写下的第一行） */
    private TAdrCaseFlow headOf(String bizNo) {
        List<TAdrCaseFlow> live = liveRows(bizNo);
        return live.isEmpty() ? null : live.get(0);
    }

    /** 这一单的在册行（单头＋在册记事），按落库先后升序；沉底的不在屏上 */
    private List<TAdrCaseFlow> liveRows(String bizNo) {
        if (StringUtils.isEmpty(bizNo)) {
            return new ArrayList<>();
        }
        return this.adrCaseFlowMapper.selectList(new QueryWrapper<TAdrCaseFlow>()
                .eq("biz_no", bizNo)
                .eq("del_flag", DEL_NORMAL)
                .orderByAsc("id"));
    }

    /** 在册过格记事（扣掉单头），按落库先后升序——数已过完的格就数它 */
    private List<TAdrCaseFlow> liveRecords(String bizNo) {
        List<TAdrCaseFlow> live = liveRows(bizNo);
        if (live.size() <= 1) {
            return new ArrayList<>();
        }
        return new ArrayList<>(live.subList(1, live.size()));
    }

    /** 单头视图：档号现算现给——已过完几格，就停在哪一档；待发起不落档号。
     *  另拷一份再写档号，不动库里捞出来那份（免得把会话里共享的实体带花） */
    private TAdrCaseFlow headView(TAdrCaseFlow head) {
        if (head == null) {
            return null;
        }
        TAdrCaseFlow view = copyOf(head);
        int passed = liveRecords(head.getBizNo()).size();
        view.setStage(passed == 0 ? null : passed - 1);
        return view;
    }

    private static TAdrCaseFlow copyOf(TAdrCaseFlow src) {
        TAdrCaseFlow dst = new TAdrCaseFlow();
        dst.setId(src.getId());
        dst.setBizNo(src.getBizNo());
        dst.setStage(src.getStage());
        dst.setStatus(src.getStatus());
        dst.setContent(src.getContent());
        dst.setLastAction(src.getLastAction());
        dst.setDelFlag(src.getDelFlag());
        dst.setCreateBy(src.getCreateBy());
        dst.setCreateTime(src.getCreateTime());
        dst.setUpdateBy(src.getUpdateBy());
        dst.setUpdateTime(src.getUpdateTime());
        dst.setRemark(src.getRemark());
        return dst;
    }

    /** 四格各有各的进门条件，不齐不许进门 */
    private boolean enterable(int next, String[] meta) {
        String cardCode = meta[0];
        String siteNo = meta[1];
        String fields = meta[2];
        switch (next) {
            case STAGE_ACCEPT:
                // 收单：报告卡代号、报告单位底册代号、这一版动了哪几栏，三样少一样进不了门
                return StringUtils.isNotEmpty(cardCode) && StringUtils.isNotEmpty(siteNo)
                        && StringUtils.isNotEmpty(fields);
            case STAGE_FIRST_CHECK:
                // 初核：卡面同上位那一册对着看，比对只走追溯码不走单位名号，撞名不算对上
                return traceCodeMatched(cardCode);
            case STAGE_EVALUATE:
                // 评价：单位已退出底册的，单停在初核；那家单位那一期还压着没结完的核对事务，
                // 件就停在初核，后补的料齐了也不许往前推；等省里那册对照下来，口径才钉得下
                if (unitExited(siteNo)) {
                    return false;
                }
                if (hasPendingReconcile(siteNo)) {
                    return false;
                }
                return provinceBookletChecked(cardCode);
            case STAGE_CLOSED:
                // 已结案：这一版进现行名录，对外那一页同时出一张回执（落在过格动作上）
                return true;
            default:
                return false;
        }
    }

    /** 追溯码在不在上位册的现行册行里（只认码，不认单位名号） */
    private boolean traceCodeMatched(String cardCode) {
        if (StringUtils.isEmpty(cardCode)) {
            return false;
        }
        Integer cnt = this.adrReportRowMapper.selectCount(new QueryWrapper<TAdrReportRow>()
                .eq("item_code", cardCode)
                .eq("archive_phase", PHASE_CURRENT)
                .eq("del_flag", 0));
        return cnt != null && cnt > 0;
    }

    /** 报告单位那年已经退出底册的 */
    private boolean unitExited(String siteNo) {
        if (StringUtils.isEmpty(siteNo)) {
            return false;
        }
        Integer cnt = this.adrOrgArchMapper.selectCount(new QueryWrapper<com.fc.v2.model.auto.TAdrOrgArch>()
                .eq("site_no", siteNo)
                .eq("status", ORG_EXITED)
                .eq("del_flag", 0));
        return cnt != null && cnt > 0;
    }

    /** 这家单位还压着没结完的核对事务（现行层挂起行，码归这家单位的卡） */
    private boolean hasPendingReconcile(String siteNo) {
        if (StringUtils.isEmpty(siteNo)) {
            return false;
        }
        List<TAdrTermCard> cards = this.adrTermCardMapper.selectList(new QueryWrapper<TAdrTermCard>()
                .eq("site_no", siteNo)
                .eq("del_flag", 0));
        List<String> codes = new ArrayList<>();
        for (TAdrTermCard c : cards) {
            if (StringUtils.isNotEmpty(c.getBillNo())) {
                codes.add(c.getBillNo());
            }
        }
        if (codes.isEmpty()) {
            return false;
        }
        Integer cnt = this.adrReportRowMapper.selectCount(new QueryWrapper<TAdrReportRow>()
                .in("item_code", codes)
                .eq("status", ROW_SUSPENDED)
                .eq("archive_phase", PHASE_CURRENT)
                .eq("del_flag", 0));
        return cnt != null && cnt > 0;
    }

    /** 省里那册对照下来没有：这张卡所在的在册册子已经核讫 */
    private boolean provinceBookletChecked(String cardCode) {
        if (StringUtils.isEmpty(cardCode)) {
            return false;
        }
        List<TAdrReportRow> rows = this.adrReportRowMapper.selectList(new QueryWrapper<TAdrReportRow>()
                .eq("item_code", cardCode)
                .eq("archive_phase", PHASE_CURRENT)
                .eq("del_flag", 0));
        if (rows.isEmpty()) {
            return false;
        }
        List<Long> bookletIds = new ArrayList<>();
        for (TAdrReportRow r : rows) {
            if (r.getBookletId() != null) {
                bookletIds.add(r.getBookletId());
            }
        }
        if (bookletIds.isEmpty()) {
            return false;
        }
        Integer cnt = this.adrCheckBookletMapper.selectCount(new QueryWrapper<com.fc.v2.model.auto.TAdrCheckBooklet>()
                .in("id", bookletIds)
                .eq("void_flag", 0)
                .eq("del_flag", 0)
                .eq("receipt_status", RECEIPT_DONE));
        return cnt != null && cnt > 0;
    }

    /** 同一份报告名下有没有在办的单（没走完、没止步、没作废的） */
    private boolean hasActiveBillOfReport(String cardCode) {
        for (Map.Entry<String, List<TAdrCaseFlow>> e : groupLiveByBill().entrySet()) {
            List<TAdrCaseFlow> rows = e.getValue();
            TAdrCaseFlow head = rows.get(0);
            if (!cardCode.equals(metaOf(head.getRemark())[0])) {
                continue;
            }
            if (Integer.valueOf(STATUS_VOIDED).equals(head.getStatus())) {
                continue; // 作废的不挡
            }
            List<TAdrCaseFlow> records = rows.subList(1, rows.size());
            if (records.size() >= FULL_STEPS) {
                continue; // 已办结的不挡
            }
            if (isStopped(records)) {
                continue; // 已止步的不挡
            }
            return true;
        }
        return false;
    }

    /** 哪一格写了「这一版到此为止」，单就此止步 */
    private boolean isStopped(List<TAdrCaseFlow> records) {
        for (TAdrCaseFlow r : records) {
            if (r.getContent() != null && r.getContent().contains(STOP_MARK)) {
                return true;
            }
        }
        return false;
    }

    /** 全部在册行按单号归拢（每单内部按落库先后升序，首行即单头） */
    private Map<String, List<TAdrCaseFlow>> groupLiveByBill() {
        Map<String, List<TAdrCaseFlow>> byBill = new LinkedHashMap<>();
        for (TAdrCaseFlow row : this.adrCaseFlowMapper.selectList(new QueryWrapper<TAdrCaseFlow>()
                .eq("del_flag", DEL_NORMAL)
                .orderByAsc("biz_no", "id"))) {
            byBill.computeIfAbsent(row.getBizNo(), k -> new ArrayList<>()).add(row);
        }
        return byBill;
    }

    /** 立单三样落进单头备注：报告卡代号、报告单位底册代号、这一版动了哪几栏 */
    private static String meta(String cardCode, String siteNo, String changedFields) {
        return "card=" + (cardCode == null ? "" : cardCode)
                + ";site=" + (siteNo == null ? "" : siteNo)
                + ";fields=" + (changedFields == null ? "" : changedFields);
    }

    /** 从单头备注里解出立单三样（作废缘由等后补的话跟在全角分号后，不碍解） */
    private static String[] metaOf(String remark) {
        String[] m = {"", "", ""};
        if (remark == null) {
            return m;
        }
        for (String kv : remark.split(";")) {
            int i = kv.indexOf('=');
            if (i < 0) {
                continue;
            }
            String k = kv.substring(0, i).trim();
            String v = kv.substring(i + 1);
            if ("card".equals(k)) {
                m[0] = v;
            } else if ("site".equals(k)) {
                m[1] = v;
            } else if ("fields".equals(k)) {
                m[2] = v;
            }
        }
        return m;
    }
}
