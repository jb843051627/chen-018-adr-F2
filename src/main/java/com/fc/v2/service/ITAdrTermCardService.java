package com.fc.v2.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fc.v2.model.auto.TAdrTermCard;

import java.util.List;

/**
 * 术语分类卡 Service接口
 *
 * @author fuce
 * @date 2026-09-12
 */
public interface ITAdrTermCardService {

    /** 按主键查询 */
    TAdrTermCard selectTAdrTermCardById(Long id);

    /** 按条件查询列表（分页由调用方统一处理） */
    List<TAdrTermCard> selectTAdrTermCardList(Wrapper<TAdrTermCard> queryWrapper);

    /** 新增 */
    int insertTAdrTermCard(TAdrTermCard record);

    /** 修改 */
    int updateTAdrTermCard(TAdrTermCard record);

    /** 批量删除 */
    int deleteTAdrTermCardByIds(String ids);

    /** 按主键删除 */
    int deleteTAdrTermCardById(Long id);
}
