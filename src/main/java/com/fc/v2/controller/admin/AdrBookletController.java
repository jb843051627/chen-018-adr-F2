package com.fc.v2.controller.admin;

import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.custom.adr.IncomingRow;
import com.fc.v2.service.IAdrBookletService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 上级下发对照册核对 Controller。
 * 进册/比对/作废/回执/跨期归档/全辖对账——三桩旧案（重复进册冲账、同码双行多记、空册写核讫）都在这一层断干净。
 *
 * @author fuce
 * @date 2026-09-30
 */
@Api(value = "上级下发对照册核对")
@Controller
@RequestMapping("/adrBooklet")
public class AdrBookletController extends BaseController {

    @Autowired
    private IAdrBookletService adrBookletService;

    @Log(title = "核对期开口", action = "openPeriod")
    @ApiOperation(value = "开口一个核对期", notes = "首期由中心给定挂下额度；期次号已存在则原样返回")
    @PostMapping("/openPeriod")
    @RequiresPermissions("adrBooklet:openPeriod")
    @ResponseBody
    public AjaxResult openPeriod(String periodNo, BigDecimal carryIn) {
        try {
            return retobject(200, adrBookletService.openPeriod(periodNo, carryIn));
        } catch (IllegalArgumentException e) {
            return AjaxResult.error(e.getMessage());
        }
    }

    @Log(title = "对照册进册", action = "receive")
    @ApiOperation(value = "进册", notes = "首进门逐行落账；同册号二次进门只比对不覆写；越线整册退回一行不留")
    @PostMapping("/receive")
    @RequiresPermissions("adrBooklet:receive")
    @ResponseBody
    public AjaxResult receive(@RequestBody Map<String, Object> body) {
        try {
            String batchNo = str(body.get("batchNo"));
            String periodNo = str(body.get("periodNo"));
            int rowCap = body.get("rowCap") == null ? 0 : Integer.parseInt(String.valueOf(body.get("rowCap")));
            List<IncomingRow> rows = com.alibaba.fastjson.JSON.parseObject(
                    com.alibaba.fastjson.JSON.toJSONString(body.get("rows")),
                    new com.alibaba.fastjson.TypeReference<List<IncomingRow>>() { });
            return retobject(200, adrBookletService.receiveBooklet(batchNo, periodNo, rowCap, rows));
        } catch (RuntimeException e) {
            return AjaxResult.error(e.getMessage());
        }
    }

    @Log(title = "对照册作废", action = "void")
    @ApiOperation(value = "作废重报", notes = "改口只许走作废；作废释放本册占用额度，同册号此后可重新进门")
    @PostMapping("/void")
    @RequiresPermissions("adrBooklet:void")
    @ResponseBody
    public AjaxResult voidBooklet(String batchNo, String reason) {
        int n = adrBookletService.voidBooklet(batchNo, reason);
        return n > 0 ? AjaxResult.success("册号" + batchNo + "已作废，" + n + "行旧版留痕")
                : AjaxResult.error("册号" + batchNo + "查无在册，无可作废");
    }

    @Log(title = "对照册回执", action = "receipt")
    @ApiOperation(value = "回执三数", notes = "三数与逐行落库同源重算；全册无行只落空册，不许写核讫")
    @GetMapping("/receipt")
    @RequiresPermissions("adrBooklet:receipt")
    @ResponseBody
    public AjaxResult receipt(@RequestParam String batchNo) {
        try {
            return retobject(200, adrBookletService.receipt(batchNo));
        } catch (IllegalStateException e) {
            return AjaxResult.error(e.getMessage());
        }
    }

    @Log(title = "挂起行复核", action = "review")
    @ApiOperation(value = "复核挂起行", notes = "卡点补上/份数补齐/老账已结后重过四校验；合则落挂接并按开口期额度封顶")
    @PostMapping("/review")
    @RequiresPermissions("adrBooklet:review")
    @ResponseBody
    public AjaxResult review(Long rowId, String note) {
        com.fc.v2.model.auto.TAdrReportRow r = adrBookletService.reviewSuspended(rowId, note);
        return r == null ? AjaxResult.error("该行查无、已入往期或已挂接，不走复核")
                : retobject(200, r);
    }

    @Log(title = "核对期结转", action = "openNextPeriod")
    @ApiOperation(value = "开新一期", notes = "跨两期仍挂起搬往期层；上期挂起份数结为本期挂下额度")
    @PostMapping("/openNextPeriod")
    @RequiresPermissions("adrBooklet:openNextPeriod")
    @ResponseBody
    public AjaxResult openNextPeriod(String periodNo) {
        try {
            return retobject(200, adrBookletService.openNextPeriod(periodNo));
        } catch (RuntimeException e) {
            return AjaxResult.error(e.getMessage());
        }
    }

    @Log(title = "全辖对账", action = "reconcile")
    @ApiOperation(value = "全辖对账", notes = "册头三数与逐行点算接不平，逐册逐行点出差在哪")
    @GetMapping("/reconcile")
    @RequiresPermissions("adrBooklet:reconcile")
    @ResponseBody
    public AjaxResult reconcile() {
        return retobject(200, adrBookletService.reconcile());
    }

    @ApiOperation(value = "本册现行各行", notes = "按位次升序；往期行不在册面")
    @GetMapping("/rows")
    @RequiresPermissions("adrBooklet:list")
    @ResponseBody
    public AjaxResult rows(@RequestParam String batchNo) {
        return retobject(200, adrBookletService.listRows(batchNo));
    }

    @ApiOperation(value = "往期层各行", notes = "只供事后查账")
    @GetMapping("/archived")
    @RequiresPermissions("adrBooklet:list")
    @ResponseBody
    public AjaxResult archived() {
        return retobject(200, adrBookletService.listArchivedRows());
    }

    @ApiOperation(value = "在册册头一览", notes = "已作废的不出现")
    @GetMapping("/list")
    @RequiresPermissions("adrBooklet:list")
    @ResponseBody
    public AjaxResult list() {
        return retobject(200, adrBookletService.listLiveBooklets());
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o).trim();
    }
}
