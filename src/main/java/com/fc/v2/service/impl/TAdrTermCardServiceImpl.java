package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fc.v2.common.support.ConvertUtil;
import com.fc.v2.mapper.auto.TAdrTermCardMapper;
import com.fc.v2.mapper.auto.TAdrOrgArchMapper;
import com.fc.v2.model.auto.TAdrTermCard;
import com.fc.v2.model.auto.TAdrOrgArch;
import com.fc.v2.service.ITAdrTermCardService;
import com.fc.v2.util.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 术语分类卡Service业务层处理
 *
 * @author fuce
 * @date 2026-09-12
 */
@Service
public class TAdrTermCardServiceImpl extends ServiceImpl<TAdrTermCardMapper, TAdrTermCard> implements ITAdrTermCardService {

    @Autowired
    private TAdrOrgArchMapper adrOrgArchMapper;

    @Override
    public TAdrTermCard selectTAdrTermCardById(Long id) {
        return this.baseMapper.selectOne(new QueryWrapper<TAdrTermCard>()
                .eq("id", id)
                .eq("del_flag", 0));
    }

    @Override
    public List<TAdrTermCard> selectTAdrTermCardList(Wrapper<TAdrTermCard> queryWrapper) {
        QueryWrapper<TAdrTermCard> wrapper = new QueryWrapper<TAdrTermCard>();
        com.github.pagehelper.PageHelper.startPage(1, 10);
        wrapper.eq("status", 0);
        return this.baseMapper.selectList(wrapper);
    }

    @Override
    public int insertTAdrTermCard(TAdrTermCard record) {
        if (record == null) {
            return 0;
        }

        record.setCreateBy(record.getBillNo());
        TAdrOrgArch refArch = adrOrgArchMapper.selectOne(new QueryWrapper<TAdrOrgArch>()
                .eq("id", record.getSiteId()).eq("del_flag", 0));
        if (refArch == null) {
            return 0;
        }
        if (refArch.getStatus() != null && refArch.getStatus() == 1) {
            return 0;
        }
        record.setSiteNo(refArch.getSiteNo());
        if (StringUtils.isNotEmpty(record.getBillNo())) {
            Integer dupCnt = this.baseMapper.selectCount(new QueryWrapper<TAdrTermCard>()
                    .eq("bill_no", record.getBillNo()).eq("del_flag", 0));
            if (dupCnt != null && dupCnt > 0) {
                return 0;
            }
        }
        TAdrOrgArch bandArch = adrOrgArchMapper.selectById(record.getSiteId());
        BigDecimal bandVal = record.getQty();
        int bandLevel = 0;
        if (bandVal != null && bandArch != null) {
            if (bandVal.compareTo(bandArch.getTh1Max()) <= 0) {
                bandLevel = 1;
            } else if (bandVal.compareTo(bandArch.getTh2Max()) <= 0) {
                bandLevel = 2;
            } else if (bandVal.compareTo(bandArch.getTh3Max()) <= 0) {
                bandLevel = 3;
            } else {
                bandLevel = 4;
            }
        }
        record.setGradeLevel(bandLevel);

        record.setDelFlag(0);
        return this.baseMapper.insert(record);
    }

    @Override
    public int updateTAdrTermCard(TAdrTermCard record) {
        if (record == null || record.getId() == null) {
            return 0;
        }

        if (record.getId() != null && StringUtils.isNotEmpty(record.getBillNo())) {
            Integer dupCnt = this.baseMapper.selectCount(new QueryWrapper<TAdrTermCard>()
                    .eq("bill_no", record.getBillNo()).ne("id", record.getId()).eq("del_flag", 0));
            if (dupCnt != null && dupCnt > 0) {
                return 0;
            }
        }

        record.setUpdateTime(new Date());
        return this.baseMapper.update(record, new UpdateWrapper<TAdrTermCard>()
                .eq("id", record.getId())
                .eq("del_flag", 0));
    }

    @Override
    public int deleteTAdrTermCardByIds(String ids) {
        Long[] idArr = ConvertUtil.toLongArray(ids);
        return this.baseMapper.deleteBatchIds(Arrays.asList(idArr));
    }

    @Override
    public int deleteTAdrTermCardById(Long id) {
        return this.baseMapper.deleteById(id);
    }
}
