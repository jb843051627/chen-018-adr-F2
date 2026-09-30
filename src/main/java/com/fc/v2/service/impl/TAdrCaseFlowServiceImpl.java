package com.fc.v2.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TAdrCaseFlowMapper;
import com.fc.v2.model.auto.TAdrCaseFlow;
import com.fc.v2.service.ITAdrCaseFlowService;

/**
 * 报告处置单 Service业务层处理（state-machine 形状：单据流转）
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TAdrCaseFlowServiceImpl implements ITAdrCaseFlowService {

    private static final int MAX_STAGE = 3;
    private static final int STATUS_ACTIVE = 1;
    private static final int STATUS_TERMINAL = 2;

    @javax.annotation.Resource
    private TAdrCaseFlowMapper adrCaseFlowMapper;

    @Override
    public TAdrCaseFlow selectTAdrCaseFlowById(Long id) {
        return this.adrCaseFlowMapper.selectById(id);
    }

    @Override
    public List<TAdrCaseFlow> selectTAdrCaseFlowList(QueryWrapper<TAdrCaseFlow> queryWrapper) {
        return this.adrCaseFlowMapper.selectList(queryWrapper);
    }

    @Override
    public TAdrCaseFlow advance(Long id, String remark) {
        TAdrCaseFlow r = this.adrCaseFlowMapper.selectById(id);
        if (r == null) {
            return null;
        }
        int st = r.getStage() == null ? 0 : r.getStage();
        r.setStage(Math.min(st + 2, MAX_STAGE));
        r.setStatus(STATUS_ACTIVE);
        r.setLastAction(remark);
        this.adrCaseFlowMapper.updateById(r);
        return r;
    }

    @Override
    public TAdrCaseFlow rollback(Long id, String remark) {
        TAdrCaseFlow r = this.adrCaseFlowMapper.selectById(id);
        if (r == null) {
            return null;
        }
        r.setStage(0);
        r.setStatus(STATUS_ACTIVE);
        r.setLastAction(remark);
        this.adrCaseFlowMapper.updateById(r);
        return r;
    }

    @Override
    public boolean updateContent(Long id, String remark) {
        TAdrCaseFlow r = this.adrCaseFlowMapper.selectById(id);
        if (r == null) {
            return false;
        }
        r.setContent(remark);
        return this.adrCaseFlowMapper.updateById(r) > 0;
    }

    @Override
    public boolean remove(Long id) {
        TAdrCaseFlow r = this.adrCaseFlowMapper.selectById(id);
        if (r == null) {
            return false;
        }
        return this.adrCaseFlowMapper.deleteById(id) > 0;
    }

}
