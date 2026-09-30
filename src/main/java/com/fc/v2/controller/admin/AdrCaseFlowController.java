package com.fc.v2.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TAdrCaseFlow;
import com.fc.v2.model.custom.adr.CaseFlowStepVo;
import com.fc.v2.model.custom.adr.CaseOpenCmd;
import com.fc.v2.service.ITAdrCaseFlowService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

/**
 * 报告处置单 Controller（state-machine 形状：流转入口）。
 *
 * 口径要改先立处置单，口头与纸面都不作数。页面上只有一个推进把手：立单、推进/收回/止步/作废
 * 全走服务层那一个推进方法；停在哪一档、能不能过下一档、作废后改不改得动、收回时旧记沉不沉底，
 * 一律看服务层给回什么，屏上行号与服务层算出的档号打架时以服务层为准。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Api(value = "报告处置单")
@Controller
@RequestMapping("/adrCaseFlow")
public class AdrCaseFlowController extends BaseController {

    private final String prefix = "admin/adrCaseFlow";

    @Autowired
    private ITAdrCaseFlowService adrCaseFlowService;

    @ApiOperation(value = "流转台账跳转", notes = "流转台账跳转")
    @GetMapping("/view")
    @RequiresPermissions("adrCaseFlow:view")
    public String view(ModelMap model) {
        return prefix + "/list";
    }

    @Log(title = "报告处置单流转台账", action = "list")
    @ApiOperation(value = "流转台账", notes = "流转台账")
    @GetMapping("/list")
    @RequiresPermissions("adrCaseFlow:list")
    @ResponseBody
    public ResultTable list(TAdrCaseFlow record) {
        QueryWrapper<TAdrCaseFlow> queryWrapper = new QueryWrapper<TAdrCaseFlow>()
                .orderByDesc("create_time");
        startPage();
        com.github.pagehelper.PageInfo<TAdrCaseFlow> page =
                new com.github.pagehelper.PageInfo<TAdrCaseFlow>(adrCaseFlowService.selectTAdrCaseFlowList(queryWrapper));
        return pageTable(page.getList(), page.getTotal());
    }

    @Log(title = "报告处置单立单", action = "open")
    @ApiOperation(value = "立处置单（收单进门）", notes = "三要件：报告卡代号、报告单位底册代号、动了哪几栏")
    @PostMapping("/open")
    @RequiresPermissions("adrCaseFlow:open")
    @ResponseBody
    public AjaxResult open(CaseOpenCmd cmd) {
        try {
            CaseFlowStepVo vo = adrCaseFlowService.openCase(cmd);
            return vo == null ? AjaxResult.error("立单不成") : AjaxResult.successData(200, vo);
        } catch (IllegalArgumentException e) {
            return AjaxResult.error(e.getMessage());
        }
    }

    /**
     * 唯一的推进把手：action=NEXT 进下一格 / WITHDRAW 收回一格 / STOP 这一版到此为止 / VOID 作废。
     * 不摆第二个能推进、能作废、能改记事的口子。
     */
    @Log(title = "报告处置单过口", action = "advance")
    @ApiOperation(value = "过口（推进/收回/止步/作废同一个口子）", notes = "action: NEXT/WITHDRAW/STOP/VOID")
    @PostMapping("/advance")
    @RequiresPermissions("adrCaseFlow:advance")
    @ResponseBody
    public AjaxResult advance(Long id, String action, String content, Long fromSeq) {
        String operator = currentOperator();
        CaseFlowStepVo vo = adrCaseFlowService.advance(id, action, content, operator, fromSeq);
        if (vo == null) {
            return AjaxResult.error("查无此单");
        }
        // 过没过成、为什么没过，全看服务层给回什么
        AjaxResult ajax = vo.isAccepted()
                ? AjaxResult.success(vo.getMessage() == null ? "已过口" : vo.getMessage())
                : AjaxResult.error(vo.getMessage() == null ? "这一次没过成" : vo.getMessage());
        ajax.put("data", vo);
        return ajax;
    }

    /** 盘算：此刻停在哪一档、能不能过下一档，全看服务层给回什么 */
    @ApiOperation(value = "盘算当前档", notes = "权威档号照处置记载重算")
    @GetMapping("/snapshot")
    @RequiresPermissions("adrCaseFlow:view")
    @ResponseBody
    public AjaxResult snapshot(Long id) {
        CaseFlowStepVo vo = adrCaseFlowService.snapshot(id);
        return vo == null ? AjaxResult.error("查无此单") : AjaxResult.successData(200, vo);
    }

    /** 倒读：从已结案一路数回收单，多出一笔/缺一格/事后手补即判这一单没走成 */
    @ApiOperation(value = "倒读校验", notes = "走到头的单笔笔要对得拢")
    @GetMapping("/trace")
    @RequiresPermissions("adrCaseFlow:view")
    @ResponseBody
    public AjaxResult trace(Long id) {
        return AjaxResult.successData(200, adrCaseFlowService.trace(id));
    }

    private String currentOperator() {
        try {
            if (com.fc.v2.shiro.util.ShiroUtils.getUser() != null) {
                return com.fc.v2.shiro.util.ShiroUtils.getUser().getUsername();
            }
        } catch (RuntimeException ignore) {
            // 无 shiro 上下文（如定时/测试链路）时留空，由服务层按规矩判
        }
        return null;
    }
}
