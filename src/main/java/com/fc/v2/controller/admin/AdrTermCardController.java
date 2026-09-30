package com.fc.v2.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TAdrTermCard;
import com.fc.v2.service.ITAdrTermCardService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

/**
 * 术语分类卡 Controller
 *
 * @author fuce
 * @date 2026-09-12
 */
@Api(value = "术语分类卡")
@Controller
@RequestMapping("/AdrTermCardController")
public class AdrTermCardController extends BaseController {

    private final String prefix = "admin/adrTermCard";

    @Autowired
    private ITAdrTermCardService adrTermCardService;

    @ApiOperation(value = "分页跳转", notes = "分页跳转")
    @GetMapping("/view")
    @RequiresPermissions("adr:adrTermCard:view")
    public String view(ModelMap model) {
        return prefix + "/list";
    }

    @Log(title = "术语分类卡集合查询", action = "list")
    @ApiOperation(value = "分页查询", notes = "分页查询")
    @GetMapping("/list")
    @RequiresPermissions("adr:adrTermCard:list")
    @ResponseBody
    public ResultTable list(TAdrTermCard record) {
        QueryWrapper<TAdrTermCard> queryWrapper = new QueryWrapper<TAdrTermCard>();
        startPage();
        com.github.pagehelper.PageInfo<TAdrTermCard> page =
                new com.github.pagehelper.PageInfo<TAdrTermCard>(adrTermCardService.selectTAdrTermCardList(queryWrapper));
        return pageTable(page.getList(), page.getTotal());
    }

    @Log(title = "术语分类卡新增", action = "add")
    @ApiOperation(value = "新增", notes = "新增")
    @PostMapping("/add")
    @RequiresPermissions("adr:adrTermCard:add")
    @ResponseBody
    public AjaxResult add(TAdrTermCard record) {
        return toAjax(adrTermCardService.insertTAdrTermCard(record));
    }

    @Log(title = "术语分类卡修改", action = "edit")
    @ApiOperation(value = "修改保存", notes = "修改保存")
    @PostMapping("/edit")
    @RequiresPermissions("adr:adrTermCard:edit")
    @ResponseBody
    public AjaxResult editSave(TAdrTermCard record) {
        return toAjax(adrTermCardService.updateTAdrTermCard(record));
    }

    @Log(title = "术语分类卡删除", action = "remove")
    @ApiOperation(value = "删除", notes = "删除")
    @DeleteMapping("/remove")
    @RequiresPermissions("adr:adrTermCard:remove")
    @ResponseBody
    public AjaxResult remove(String ids) {
        return toAjax(adrTermCardService.deleteTAdrTermCardByIds(ids));
    }
}
