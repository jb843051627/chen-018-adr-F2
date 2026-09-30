package com.fc.v2.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fc.v2.mapper.auto.TAdrCaseFlowMapper;
import com.fc.v2.mapper.auto.TAdrCaseNoteMapper;
import com.fc.v2.mapper.auto.TAdrCheckBookletMapper;
import com.fc.v2.mapper.auto.TAdrOrgArchMapper;
import com.fc.v2.mapper.auto.TAdrReportRowMapper;
import com.fc.v2.mapper.auto.TAdrTermCardMapper;
import com.fc.v2.model.auto.TAdrCaseFlow;
import com.fc.v2.model.auto.TAdrCaseNote;
import com.fc.v2.model.auto.TAdrCheckBooklet;
import com.fc.v2.model.auto.TAdrOrgArch;
import com.fc.v2.model.auto.TAdrReportRow;
import com.fc.v2.model.auto.TAdrTermCard;
import com.fc.v2.model.custom.adr.CaseFlowStepVo;
import com.fc.v2.model.custom.adr.CaseOpenCmd;
import com.fc.v2.model.custom.adr.CaseTraceVo;
import com.fc.v2.service.ITAdrCaseFlowService;
import com.fc.v2.util.MD5Util;
import com.fc.v2.util.StringUtils;

import javax.annotation.Resource;

/**
 * 报告处置单状态机——口径要改先立处置单，口头与纸面都不作数。
 *
 * 两条旧缝在这一层焊死：
 * 1) 旧版退不出去 → 新版走到「已结案」那一刻才进现行名录，旧版自动沉历史层（{@link #enterStage}）；
 *    新版本没走完前，同一份报告名下立不起第二张在办单（{@link #openCase}）。
 * 2) 谁都能再补一句 → 处置记载只许追加（t_adr_case_note），没有改记/删单的口子；
 *    同格第二遍、抢步跳格、止步后追接一律不算过成、不多落记事。
 *
 * 权威档号永远照处置记载重放数出来（{@link #replay}）：不看纸面 stage、不看承办人心记。
 *
 * @author fuce
 * @date 2026-09-30
 */
@Service
public class TAdrCaseFlowServiceImpl implements ITAdrCaseFlowService {

    /** 档：收单 */
    private static final int STAGE_INTAKE = 0;
    /** 档：初核 */
    private static final int STAGE_PRECHECK = 1;
    /** 档：评价 */
    private static final int STAGE_REVIEW = 2;
    /** 档：已结案（办结） */
    private static final int STAGE_CLOSED = 3;
    private static final String[] STAGE_LABELS = {"收单", "初核", "评价", "已结案"};

    /** 落点：未起 */
    private static final int STATUS_IDLE = 0;
    /** 落点：在办（已结案时档号=3） */
    private static final int STATUS_ACTIVE = 1;
    /** 落点：已作废 */
    private static final int STATUS_VOID = 2;
    /** 落点：已止步（这一版到此为止） */
    private static final int STATUS_STOP = 3;

    /** 记载层：本屏 */
    private static final int LAYER_SCREEN = 0;
    /** 记载层：沉底（收回格的旧进门句） */
    private static final int LAYER_BURIED = 1;

    private static final String ACT_ENTER = "ENTER";
    private static final String ACT_WITHDRAW_NOTE = "WITHDRAW";
    private static final String ACT_STOP_NOTE = "STOP";
    private static final String ACT_VOID_NOTE = "VOID";

    @Resource
    private TAdrCaseFlowMapper caseFlowMapper;
    @Resource
    private TAdrCaseNoteMapper caseNoteMapper;
    @Resource
    private TAdrTermCardMapper termCardMapper;
    @Resource
    private TAdrReportRowMapper reportRowMapper;
    @Resource
    private TAdrCheckBookletMapper checkBookletMapper;
    @Resource
    private TAdrOrgArchMapper orgArchMapper;

    // ---------------------------------------------------------------- 查

    @Override
    public TAdrCaseFlow selectTAdrCaseFlowById(Long id) {
        return this.caseFlowMapper.selectById(id);
    }

    @Override
    public List<TAdrCaseFlow> selectTAdrCaseFlowList(QueryWrapper<TAdrCaseFlow> queryWrapper) {
        return this.caseFlowMapper.selectList(queryWrapper);
    }

    @Override
    public int currentStage(Long id) {
        TAdrCaseFlow flow = this.caseFlowMapper.selectById(id);
        if (flow == null) {
            return -1;
        }
        return replay(listNotes(id)).stage;
    }

    // ---------------------------------------------------------------- 立单（收单格进门）

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CaseFlowStepVo openCase(CaseOpenCmd cmd) {
        if (cmd == null) {
            throw new IllegalArgumentException("立单要件不许空着");
        }
        // 收单三要件：报告卡代号、报告单位底册代号、这一版动了哪几栏——三样少一样进不了门
        if (StringUtils.isEmpty(cmd.getReportNo())) {
            throw new IllegalArgumentException("收单缺报告卡代号，进不了门");
        }
        if (StringUtils.isEmpty(cmd.getSiteNo())) {
            throw new IllegalArgumentException("收单缺报告单位底册代号，进不了门");
        }
        if (StringUtils.isEmpty(cmd.getChangeFields())) {
            throw new IllegalArgumentException("收单缺这一版动了哪几栏，进不了门");
        }
        String reportNo = cmd.getReportNo().trim();

        // 同一份报告名下，头一张在办单没走完也没止步/作废之前，立不起第二张
        TAdrCaseFlow blocker = activeBlocker(reportNo);
        if (blocker != null) {
            CaseFlowStepVo vo = CaseFlowStepVo.of(blocker, replay(listNotes(blocker.getId())).stage,
                    "这份报告名下已有在办单 " + blocker.getBizNo() + "，没走完也没止步/作废之前立不起第二张");
            return decorate(vo);
        }

        int version = this.caseFlowMapper.selectCount(new QueryWrapper<TAdrCaseFlow>()
                .eq("report_no", reportNo).eq("del_flag", 0)) + 1;

        TAdrCaseFlow flow = new TAdrCaseFlow();
        flow.setBizNo(reportNo + "-V" + version);
        flow.setReportNo(reportNo);
        flow.setSiteNo(cmd.getSiteNo().trim());
        flow.setPeriodNo(StringUtils.isEmpty(cmd.getPeriodNo()) ? null : cmd.getPeriodNo().trim());
        flow.setChangeFields(cmd.getChangeFields().trim());
        flow.setVersionNo(version);
        flow.setLayer(0);
        flow.setStage(STAGE_INTAKE);
        flow.setStatus(STATUS_ACTIVE);
        flow.setDelFlag(0);
        flow.setContent(cmd.getContent());
        flow.setLastAction(ACT_ENTER);
        flow.setCreateBy(cmd.getOperator());
        this.caseFlowMapper.insert(flow);

        appendNote(flow, STAGE_INTAKE, ACT_ENTER, cmd.getContent(), cmd.getOperator(), null);
        CaseFlowStepVo opened = snapshot(flow.getId());
        opened.setAccepted(true);
        return opened;
    }

    // ---------------------------------------------------------------- 唯一的推进口子

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CaseFlowStepVo advance(Long id, String action, String content, String operator, Long fromSeq) {
        TAdrCaseFlow flow = this.caseFlowMapper.selectById(id);
        if (flow == null) {
            return null;
        }
        String act = action == null ? "" : action.trim().toUpperCase();
        List<TAdrCaseNote> notes = listNotes(id);
        Replay r = replay(notes);
        List<TAdrCaseNote> screen = screenNotes(notes);
        Long screenHeadSeq = screen.isEmpty() ? null : screen.get(screen.size() - 1).getSeq().longValue();

        if (ACT_VOID.equals(act)) {
            return doVoid(flow, r, content, operator);
        }
        if (ACT_STOP.equals(act)) {
            return doStop(flow, r, content, operator);
        }
        if (ACT_WITHDRAW.equals(act)) {
            return doWithdraw(flow, r, notes, content, operator, fromSeq, screenHeadSeq);
        }
        if (ACT_NEXT.equals(act)) {
            return doNext(flow, r, content, operator, fromSeq, screenHeadSeq);
        }
        return reject(flow, r, "只认 NEXT/WITHDRAW/STOP/VOID 四种走法，不认「" + action + "」");
    }

    /** 推进到紧邻的下一格；抢步、第二遍、进门条件不齐都在这回绝。 */
    private CaseFlowStepVo doNext(TAdrCaseFlow flow, Replay r, String content, String operator,
                                  Long fromSeq, Long screenHeadSeq) {
        if (!Integer.valueOf(STATUS_ACTIVE).equals(flow.getStatus()) || r.terminated) {
            return reject(flow, r, "这一单已止步/作废，止步之后不得再往后续一格");
        }
        if (r.stage >= STAGE_CLOSED) {
            return reject(flow, r, "已结案各栏锁死，再要动另起一张新单");
        }
        // 同格第二遍：页面拿着旧屏（headSeq 对不上账上）又交一次——不认、不多落记事，回的仍是头一遍那句
        if (fromSeq != null && screenHeadSeq != null && !fromSeq.equals(screenHeadSeq)) {
            TAdrCaseNote head = lastScreenEnter(flow.getId());
            return reject(flow, r, head != null && StringUtils.isNotEmpty(head.getContent())
                    ? "同一格不记第二遍，账上仍是头一遍那句：" + head.getContent()
                    : "同一格第二遍提交不算过成，账上仍是头一遍");
        }

        int target = r.stage + 1;
        String block = enterBlockReason(flow, target, operator);
        if (block != null) {
            // 后补的料没补到点上也不许往前推；这一次不算过成，账上不多落一条
            return reject(flow, r, block);
        }

        appendNote(flow, target, ACT_ENTER, content, operator, null);
        if (target == STAGE_CLOSED) {
            // 这一版进现行名录：出回执；同一份报告名下的旧版自动挪进历史层，日常翻件只见最新一版
            Date now = new Date();
            String receiptNo = flow.getBizNo() + "-R";
            this.caseFlowMapper.update(null, new UpdateWrapper<TAdrCaseFlow>()
                    .eq("id", flow.getId())
                    .set("stage", STAGE_CLOSED)
                    .set("status", STATUS_ACTIVE)
                    .set("receipt_no", receiptNo)
                    .set("finalized_time", now)
                    .set("content", content)
                    .set("last_action", ACT_ENTER)
                    .set("update_time", now));
            this.caseFlowMapper.update(null, new UpdateWrapper<TAdrCaseFlow>()
                    .eq("report_no", flow.getReportNo())
                    .ne("id", flow.getId())
                    .eq("layer", 0)
                    .set("layer", 1));
        } else {
            touchFlow(flow, target, ACT_ENTER, content);
        }
        CaseFlowStepVo stepped = snapshot(flow.getId());
        stepped.setAccepted(true);
        stepped.setMessage("已过" + STAGE_LABELS[target] + "格");
        return stepped;
    }

    /** 收回一格：只收一格；被收回那格原先落的进门句沉底，与后补那批不同屏并列。 */
    private CaseFlowStepVo doWithdraw(TAdrCaseFlow flow, Replay r, List<TAdrCaseNote> notes,
                                      String content, String operator, Long fromSeq, Long screenHeadSeq) {
        if (!Integer.valueOf(STATUS_ACTIVE).equals(flow.getStatus()) || r.terminated) {
            return reject(flow, r, "已止步/作废的单收不回来");
        }
        if (r.stage < STAGE_PRECHECK) {
            return reject(flow, r, "收单是头一格，往前没有格可收");
        }
        if (r.stage >= STAGE_CLOSED) {
            return reject(flow, r, "已结案锁死，不许收回");
        }
        if (fromSeq != null && screenHeadSeq != null && !fromSeq.equals(screenHeadSeq)) {
            return reject(flow, r, "屏上是旧一屏，先重新盘算再收回");
        }

        // 被收回那格的进门句（本屏最近一次 ENTER 到这档）沉到底层；要重来，从这一格重新攒起
        TAdrCaseNote buried = null;
        for (int i = notes.size() - 1; i >= 0; i--) {
            TAdrCaseNote n = notes.get(i);
            if (Integer.valueOf(LAYER_SCREEN).equals(n.getLayer())
                    && ACT_ENTER.equals(n.getAction())
                    && Integer.valueOf(r.stage).equals(n.getStage())) {
                buried = n;
                break;
            }
        }
        if (buried != null) {
            this.caseNoteMapper.update(null, new UpdateWrapper<TAdrCaseNote>()
                    .eq("id", buried.getId())
                    .set("layer", LAYER_BURIED));
        }
        appendNote(flow, r.stage, ACT_WITHDRAW_NOTE,
                StringUtils.isNotEmpty(content) ? content : "收回" + STAGE_LABELS[r.stage] + "一格",
                operator, null);
        touchFlow(flow, r.stage - 1, ACT_WITHDRAW_NOTE, content);
        CaseFlowStepVo withdrew = snapshot(flow.getId());
        withdrew.setAccepted(true);
        withdrew.setMessage("已收回" + STAGE_LABELS[r.stage] + "一格");
        return withdrew;
    }

    /** 止步：哪一格写了「这一版到此为止」，单就此止步，事后不得再续一格。 */
    private CaseFlowStepVo doStop(TAdrCaseFlow flow, Replay r, String content, String operator) {
        if (!Integer.valueOf(STATUS_ACTIVE).equals(flow.getStatus()) || r.terminated) {
            return reject(flow, r, "已止步/作废的单不能再次止步");
        }
        if (r.stage >= STAGE_CLOSED) {
            return reject(flow, r, "已走完一趟，无所谓止步");
        }
        appendNote(flow, r.stage, ACT_STOP_NOTE,
                StringUtils.isNotEmpty(content) ? content : "这一版到此为止", operator, null);
        Date now = new Date();
        this.caseFlowMapper.update(null, new UpdateWrapper<TAdrCaseFlow>()
                .eq("id", flow.getId())
                .set("status", STATUS_STOP)
                .set("terminal_time", now)
                .set("last_action", ACT_STOP_NOTE)
                .set("content", StringUtils.isNotEmpty(content) ? content : "这一版到此为止")
                .set("update_time", now));
        CaseFlowStepVo stopped = snapshot(flow.getId());
        stopped.setAccepted(true);
        stopped.setMessage("单已止步于此格，事后不再追接");
        return stopped;
    }

    /**
     * 作废是另一码事：记事一句改不得、整张删不得——年底查当年按哪版口径定结论全凭这张废单。
     * 已结案锁死，不能作废，要改另起新单。
     */
    private CaseFlowStepVo doVoid(TAdrCaseFlow flow, Replay r, String content, String operator) {
        if (Integer.valueOf(STATUS_VOID).equals(flow.getStatus()) || r.voided) {
            return reject(flow, r, "废单不能再作废，它的记事一句改不得、整张也删不得");
        }
        if (r.terminated) {
            return reject(flow, r, "已止步的单就此终局，不再另落作废——终点只许一个");
        }
        if (r.stage >= STAGE_CLOSED || flow.getFinalizedTime() != null) {
            return reject(flow, r, "已结案各栏锁死，作废不受理；要改另起一张新单");
        }
        appendNote(flow, r.stage, ACT_VOID_NOTE,
                StringUtils.isNotEmpty(content) ? content : "本单作废", operator, null);
        Date now = new Date();
        this.caseFlowMapper.update(null, new UpdateWrapper<TAdrCaseFlow>()
                .eq("id", flow.getId())
                .set("status", STATUS_VOID)
                .set("terminal_time", now)
                .set("last_action", ACT_VOID_NOTE)
                .set("content", StringUtils.isNotEmpty(content) ? content : "本单作废")
                .set("update_time", now));
        CaseFlowStepVo voided = snapshot(flow.getId());
        voided.setAccepted(true);
        voided.setMessage("已作废；记事一句改不得、整张删不得，年底按这张废单查当年口径");
        return voided;
    }

    // ---------------------------------------------------------------- 盘算

    @Override
    public CaseFlowStepVo snapshot(Long id) {
        TAdrCaseFlow flow = this.caseFlowMapper.selectById(id);
        if (flow == null) {
            return null;
        }
        return decorate(CaseFlowStepVo.of(flow, replay(listNotes(id)).stage, null));
    }

    /** 把权威档号、本屏记载、四个动作能不能走、下一格卡在哪一句算给回。 */
    private CaseFlowStepVo decorate(CaseFlowStepVo vo) {
        TAdrCaseFlow flow = vo.getFlow();
        int stage = vo.getStage();
        List<TAdrCaseNote> screen = screenNotes(listNotes(flow.getId()));
        vo.setScreenNotes(screen);
        vo.setStageLabel(stage >= 0 && stage <= 3 ? STAGE_LABELS[stage] : "待发起");
        vo.setStatusLabel(statusLabel(flow.getStatus()));
        vo.setHeadSeq(screen.isEmpty() ? null : screen.get(screen.size() - 1).getSeq().longValue());
        vo.setReceiptNo(flow.getReceiptNo());

        boolean open = Integer.valueOf(STATUS_ACTIVE).equals(flow.getStatus());
        boolean finished = open && stage == STAGE_CLOSED;
        vo.setFinished(finished);
        vo.setCanStop(open && stage < STAGE_CLOSED);
        vo.setCanWithdraw(open && stage >= STAGE_PRECHECK && stage < STAGE_CLOSED);
        vo.setCanVoid(!Integer.valueOf(STATUS_VOID).equals(flow.getStatus()) && !finished);
        if (open && stage < STAGE_CLOSED) {
            String block = enterBlockReason(flow, stage + 1, null);
            vo.setBlockReason(block);
            vo.setCanAdvance(block == null);
        } else {
            vo.setCanAdvance(false);
        }
        return vo;
    }

    private CaseFlowStepVo reject(TAdrCaseFlow flow, Replay r, String message) {
        CaseFlowStepVo vo = CaseFlowStepVo.of(flow, r.stage, message);
        vo.setAccepted(false);
        return decorate(vo);
    }

    // ---------------------------------------------------------------- 四格进门条件

    /** 下一格进不去时回一句话；进得去回 null。operator 为 null 表示只盘不算人（snapshot 用）。 */
    private String enterBlockReason(TAdrCaseFlow flow, int target, String operator) {
        if (target == STAGE_PRECHECK) {
            if (operator != null && StringUtils.isEmpty(operator)) {
                return "初核要落承办人，空着手进不了门";
            }
            // 换一双手：初核承办人不得与收单同一人
            TAdrCaseNote intake = firstEnterNote(flow.getId(), STAGE_INTAKE);
            if (operator != null && intake != null && StringUtils.isNotEmpty(intake.getOperator())
                    && operator.trim().equals(intake.getOperator().trim())) {
                return "初核得换一双手，收单与初核不许是同一人";
            }
            // 卡面：本级要有该追溯码的卡，只认码
            Integer cardCnt = this.termCardMapper.selectCount(new QueryWrapper<TAdrTermCard>()
                    .eq("bill_no", flow.getReportNo())
                    .eq("del_flag", 0));
            if (cardCnt == null || cardCnt == 0) {
                return "卡面上的追溯码在本级找不到对应的卡";
            }
            // 同上位那一册对着看：上位册现行层挑出过这个码；只走追溯码，单位名号再像、撞名也不算对上
            Integer rowCnt = this.reportRowMapper.selectCount(new QueryWrapper<TAdrReportRow>()
                    .eq("item_code", flow.getReportNo())
                    .eq("archive_phase", 0)
                    .eq("del_flag", 0));
            if (rowCnt == null || rowCnt == 0) {
                return "卡面同上位那一册对不上：比对只走追溯码，撞名不算对上";
            }
            return null;
        }
        if (target == STAGE_REVIEW) {
            // 报告单位那年已退出底册：单停在初核，评价往后不再前推
            TAdrOrgArch org = this.orgArchMapper.selectOne(new QueryWrapper<TAdrOrgArch>()
                    .eq("site_no", flow.getSiteNo())
                    .eq("del_flag", 0)
                    .last("limit 1"));
            if (org == null) {
                return "报告单位底册代号在底册里查无此户，评价进不了门";
            }
            if (Integer.valueOf(1).equals(org.getStatus())) {
                return "报告单位那年已经退出底册，单停在初核那一格，评价往后不再前推";
            }
            // 这家单位那一期还压着没结完的核对事务：件停在初核，后补的料齐了也不许往前推
            if (StringUtils.isNotEmpty(flow.getPeriodNo()) && hasPendingAffair(flow)) {
                return "这家单位这一期还压着一桩没结完的核对事务，件停在初核，不许往前推";
            }
            // 等省里那册对照下来：该码现行行已挂接、且所属下发册已写核讫，口径才钉得下
            if (!provinceBookletChecked(flow.getReportNo())) {
                return "省里那册还没对照下来，口径钉不下，评价再等等";
            }
            return null;
        }
        // 已结案：系统内过门，无外部要件
        return null;
    }

    /** 该单位该期还有现行挂起行（经卡面 site_no 串起单位与册行）。 */
    private boolean hasPendingAffair(TAdrCaseFlow flow) {
        List<TAdrTermCard> cards = this.termCardMapper.selectList(new QueryWrapper<TAdrTermCard>()
                .eq("site_no", flow.getSiteNo())
                .eq("del_flag", 0));
        if (cards.isEmpty()) {
            return false;
        }
        List<String> codes = new ArrayList<>();
        for (TAdrTermCard c : cards) {
            if (StringUtils.isNotEmpty(c.getBillNo())) {
                codes.add(c.getBillNo());
            }
        }
        if (codes.isEmpty()) {
            return false;
        }
        Integer cnt = this.reportRowMapper.selectCount(new QueryWrapper<TAdrReportRow>()
                .in("item_code", codes)
                .eq("period_no", flow.getPeriodNo())
                .eq("status", 2)
                .eq("archive_phase", 0)
                .eq("del_flag", 0));
        return cnt != null && cnt > 0;
    }

    /** 该追溯码现行行里，有一行已挂接且所属下发册在册、回执核讫。 */
    private boolean provinceBookletChecked(String reportNo) {
        List<TAdrReportRow> rows = this.reportRowMapper.selectList(new QueryWrapper<TAdrReportRow>()
                .eq("item_code", reportNo)
                .eq("archive_phase", 0)
                .eq("del_flag", 0)
                .eq("status", 1));
        for (TAdrReportRow row : rows) {
            TAdrCheckBooklet b = this.checkBookletMapper.selectById(row.getBookletId());
            if (b != null && Integer.valueOf(0).equals(b.getVoidFlag())
                    && Integer.valueOf(1).equals(b.getReceiptStatus())) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- 倒读

    @Override
    public CaseTraceVo trace(Long id) {
        TAdrCaseFlow flow = this.caseFlowMapper.selectById(id);
        CaseTraceVo vo = new CaseTraceVo();
        if (flow == null) {
            vo.setValid(false);
            vo.setStage(-1);
            vo.getProblems().add("查无此单");
            return vo;
        }
        vo.setFlowId(id);
        List<TAdrCaseNote> notes = listNotes(id);
        vo.setChainCount(notes.size());

        // ① 次序：从 1 起一笔不缺（空洞即事后抽掉过一笔）
        for (int i = 0; i < notes.size(); i++) {
            if (!Integer.valueOf(i + 1).equals(notes.get(i).getSeq())) {
                vo.getProblems().add("第" + (i + 1) + "条链位缺笔或多笔（账上次序="
                        + (notes.get(i).getSeq() == null ? "空" : notes.get(i).getSeq()) + "）");
            }
        }
        // ② hash 链：一笔对不上即事后由人手补/改过
        String prev = "";
        for (TAdrCaseNote n : notes) {
            String expect = fingerprint(n.getSeq(), n.getStage(), n.getAction(),
                    n.getContent(), n.getOperator(), prev);
            String linked = n.getPrevHash() == null ? "" : n.getPrevHash();
            if (!prev.equals(linked)) {
                vo.getProblems().add("第" + n.getSeq() + "笔链扣断了：上一笔指纹接不上（事后手补的痕迹）");
            }
            if (!expect.equals(n.getHash())) {
                vo.getProblems().add("第" + n.getSeq() + "笔指纹对不上：内容被人动过");
            }
            prev = n.getHash() == null ? "" : n.getHash();
        }

        // ③ 重放：进门（含后来沉底的）照物理先后压栈，只许压紧邻的下一档；
        //    收回时栈顶必须正是这一笔所指档（往回收只收一格），弹出并与一笔沉底旧进门配平。
        //    跳格、倒格、终止后续接、沉底数与收回数对不上，一律点出。
        java.util.Deque<Integer> liveStack = new java.util.ArrayDeque<>();
        java.util.Map<Integer, Integer> buriedPool = new java.util.HashMap<>();
        String terminator = null;
        int terminatorSeq = -1;
        int buriedEnters = 0;
        int withdraws = 0;
        for (TAdrCaseNote n : notes) {
            boolean buried = Integer.valueOf(LAYER_BURIED).equals(n.getLayer());
            if (ACT_ENTER.equals(n.getAction())) {
                if (terminator != null) {
                    vo.getProblems().add("第" + n.getSeq() + "笔是后追的进门：单早在第" + terminatorSeq
                            + "笔已「" + terminator + "」，事后不得再往后续一格");
                }
                int head = liveStack.isEmpty() ? -1 : liveStack.peek();
                if (!Integer.valueOf(head + 1).equals(n.getStage())) {
                    vo.getProblems().add("第" + n.getSeq() + "笔想落到" + labelOf(n.getStage())
                            + "，当下只许进紧邻的" + labelOf(head + 1) + "：跳着过、倒着过一概不认");
                }
                liveStack.push(n.getStage());
                if (buried) {
                    buriedEnters++;
                    buriedPool.merge(n.getStage(), 1, Integer::sum);
                } else {
                    vo.getEnterStages().add(n.getStage());
                }
            } else if (ACT_WITHDRAW_NOTE.equals(n.getAction())) {
                withdraws++;
                if (terminator != null) {
                    vo.getProblems().add("第" + n.getSeq() + "笔在「" + terminator + "」之后又收回，不认");
                }
                Integer top = liveStack.peek();
                if (top == null || !top.equals(n.getStage())) {
                    vo.getProblems().add("第" + n.getSeq() + "笔收回的是" + labelOf(n.getStage())
                            + "格，可当下停在" + (top == null ? "待发起" : labelOf(top))
                            + "：往回收只收一格");
                } else {
                    liveStack.pop();
                }
                Integer paired = buriedPool.get(n.getStage());
                if (paired == null || paired <= 0) {
                    vo.getProblems().add("第" + n.getSeq() + "笔收回" + labelOf(n.getStage())
                            + "格，却没有一笔该档沉底旧进门与它对上：旧记必须沉底");
                } else {
                    buriedPool.put(n.getStage(), paired - 1);
                }
            } else if (ACT_STOP_NOTE.equals(n.getAction()) || ACT_VOID_NOTE.equals(n.getAction())) {
                if (terminator != null) {
                    vo.getProblems().add("第" + n.getSeq() + "笔重复落「" + n.getAction() + "」，多出一笔");
                }
                terminator = ACT_STOP_NOTE.equals(n.getAction()) ? "止步" : "作废";
                terminatorSeq = n.getSeq();
            } else {
                vo.getProblems().add("第" + n.getSeq() + "笔动作名「" + n.getAction() + "」账上没有");
            }
        }
        int cur = liveStack.isEmpty() ? -1 : liveStack.peek();
        // 沉底进门笔笔都得有一笔收回配平；多一笔少一笔都是动过手脚
        if (buriedEnters != withdraws) {
            vo.getProblems().add("沉底进门笔" + buriedEnters + "笔、收回笔" + withdraws
                    + "笔，两数对不上：收回一格必沉一笔旧记");
        }
        for (java.util.Map.Entry<Integer, Integer> e : buriedPool.entrySet()) {
            if (e.getValue() > 0) {
                vo.getProblems().add(labelOf(e.getKey()) + "格有" + e.getValue()
                        + "笔沉底旧进门没有对应的收回笔，多出来一笔");
            }
        }
        vo.setStage(cur);
        boolean finished = cur == STAGE_CLOSED && terminator == null
                && Integer.valueOf(STATUS_ACTIVE).equals(flow.getStatus())
                && flow.getFinalizedTime() != null;
        vo.setFinished(finished);
        // 只有走到头的单才判这一趟成不成
        vo.setValid(finished && vo.getProblems().isEmpty());
        return vo;
    }

    // ---------------------------------------------------------------- 记账

    /** 追加一笔（只追加，从不改旧笔内容；layer 仅收回沉底时由系统改）。 */
    private void appendNote(TAdrCaseFlow flow, int stage, String action, String content,
                            String operator, String ignoredPrev) {
        List<TAdrCaseNote> notes = listNotes(flow.getId());
        int seq = notes.isEmpty() ? 1 : notes.get(notes.size() - 1).getSeq() + 1;
        String prev = notes.isEmpty() ? "" : (notes.get(notes.size() - 1).getHash() == null
                ? "" : notes.get(notes.size() - 1).getHash());
        TAdrCaseNote n = new TAdrCaseNote();
        n.setFlowId(flow.getId());
        n.setSeq(seq);
        n.setStage(stage);
        n.setAction(action);
        n.setLayer(LAYER_SCREEN);
        n.setContent(content);
        n.setOperator(operator);
        n.setPrevHash(prev);
        n.setHash(fingerprint(seq, stage, action, content, operator, prev));
        n.setCreateTime(new Date());
        this.caseNoteMapper.insert(n);
    }

    /** md5(seq|stage|action|content|operator|prevHash)；layer 不入指纹——沉底是系统正当动作。 */
    private static String fingerprint(Integer seq, Integer stage, String action,
                                      String content, String operator, String prevHash) {
        String raw = nz(seq) + "|" + nz(stage) + "|" + nz(action) + "|"
                + (content == null ? "" : content) + "|" + (operator == null ? "" : operator)
                + "|" + (prevHash == null ? "" : prevHash);
        return MD5Util.encode(raw);
    }

    private void touchFlow(TAdrCaseFlow flow, int stage, String action, String content) {
        Date now = new Date();
        this.caseFlowMapper.update(null, new UpdateWrapper<TAdrCaseFlow>()
                .eq("id", flow.getId())
                .set("stage", stage)
                .set("last_action", action)
                .set("content", StringUtils.isNotEmpty(content) ? content : flow.getContent())
                .set("update_time", now));
    }

    // ---------------------------------------------------------------- 辅助

    /** 同报告名下没走完也没止步/作废的在办单（作废单、止步单、已结案、历史版都不挡新单）。 */
    private TAdrCaseFlow activeBlocker(String reportNo) {
        List<TAdrCaseFlow> candidates = this.caseFlowMapper.selectList(new QueryWrapper<TAdrCaseFlow>()
                .eq("report_no", reportNo)
                .eq("status", STATUS_ACTIVE)
                .isNull("finalized_time")
                .eq("del_flag", 0));
        for (TAdrCaseFlow c : candidates) {
            if (replay(listNotes(c.getId())).stage < STAGE_CLOSED) {
                return c;
            }
        }
        return null;
    }

    private List<TAdrCaseNote> listNotes(Long flowId) {
        return this.caseNoteMapper.selectList(new QueryWrapper<TAdrCaseNote>()
                .eq("flow_id", flowId)
                .orderByAsc("seq"));
    }

    /** 本屏记载（沉了底的旧记不与后补那批并列）。 */
    private List<TAdrCaseNote> screenNotes(List<TAdrCaseNote> notes) {
        List<TAdrCaseNote> screen = new ArrayList<>();
        for (TAdrCaseNote n : notes) {
            if (Integer.valueOf(LAYER_SCREEN).equals(n.getLayer())) {
                screen.add(n);
            }
        }
        return screen;
    }

    private TAdrCaseNote firstEnterNote(Long flowId, int stage) {
        for (TAdrCaseNote n : listNotes(flowId)) {
            if (ACT_ENTER.equals(n.getAction()) && Integer.valueOf(stage).equals(n.getStage())
                    && Integer.valueOf(LAYER_SCREEN).equals(n.getLayer())) {
                return n;
            }
        }
        return null;
    }

    private TAdrCaseNote lastScreenEnter(Long flowId) {
        List<TAdrCaseNote> all = listNotes(flowId);
        Collections.reverse(all);
        for (TAdrCaseNote n : all) {
            if (Integer.valueOf(LAYER_SCREEN).equals(n.getLayer()) && ACT_ENTER.equals(n.getAction())) {
                return n;
            }
        }
        return null;
    }

    /**
     * 照处置记载重放权威档号：本屏 ENTER 紧邻着进；WITHDRAW 回到「该笔所指档−1」
     * （被收回的进门笔已沉底、重放时跳过，所以不能照当下档号简单减一）；
     * STOP/VOID 之后终止。纸面 stage 与它打架时，以它为凭。
     */
    private static Replay replay(List<TAdrCaseNote> notes) {
        Replay r = new Replay();
        r.stage = -1;
        for (TAdrCaseNote n : notes) {
            if (ACT_ENTER.equals(n.getAction())) {
                if (!Integer.valueOf(LAYER_BURIED).equals(n.getLayer()) && !r.terminated) {
                    r.stage = n.getStage();
                }
            } else if (ACT_WITHDRAW_NOTE.equals(n.getAction()) && !r.terminated) {
                r.stage = n.getStage() - 1;
            } else if (ACT_STOP_NOTE.equals(n.getAction())) {
                r.terminated = true;
            } else if (ACT_VOID_NOTE.equals(n.getAction())) {
                r.terminated = true;
                r.voided = true;
            }
        }
        return r;
    }

    private static String nz(Object v) {
        return v == null ? "" : v.toString();
    }

    private static String labelOf(int stage) {
        return stage >= 0 && stage <= 3 ? STAGE_LABELS[stage] : "待发起";
    }

    private static String statusLabel(Integer status) {
        if (status == null) {
            return "未起";
        }
        switch (status) {
            case STATUS_ACTIVE: return "在办";
            case STATUS_VOID: return "已作废";
            case STATUS_STOP: return "已止步";
            default: return "未起";
        }
    }

    /** 重放结果 */
    private static final class Replay {
        private int stage;
        private boolean terminated;
        private boolean voided;
    }
}
