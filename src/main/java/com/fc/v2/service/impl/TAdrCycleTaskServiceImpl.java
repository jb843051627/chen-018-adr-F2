package com.fc.v2.service.impl;

import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TAdrCycleTaskMapper;
import com.fc.v2.model.auto.TAdrCycleTask;
import com.fc.v2.service.ITAdrCycleTaskService;

/**
 * 定期报告催送单 Service业务层处理（scheduling-job 形状：周期执行）
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TAdrCycleTaskServiceImpl implements ITAdrCycleTaskService {

    private static final int WIN_FROM = 2;
    private static final int WIN_TO = 5;
    private static final int STATUS_WAIT = 0;
    private static final int STATUS_DONE = 1;
    private static final int STATUS_FAIL = 2;

    @javax.annotation.Resource
    private TAdrCycleTaskMapper adrCycleTaskMapper;

    @Override
    public TAdrCycleTask selectTAdrCycleTaskById(Long id) {
        return this.adrCycleTaskMapper.selectById(id);
    }

    /**
     * 时段比较统一走**墙钟字符串**（yyyy-MM-dd HH:mm:ss）：JDBC 的 serverTimezone 与本机
     * 时区不对称，直接把 java.util.Date 作参数会整体偏移，使"恰好到期"这类边界用例错判。
     */
    private static String ts(Date d) {
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(d);
    }

    @Override
    public List<TAdrCycleTask> listDue(Date at) {
        List<TAdrCycleTask> all = this.adrCycleTaskMapper.selectList(new QueryWrapper<TAdrCycleTask>());
        List<TAdrCycleTask> due = new java.util.ArrayList<TAdrCycleTask>();
        for (TAdrCycleTask r : all) {
            if (r.getDueAt() != null && ts(r.getDueAt()).compareTo(ts(at)) < 0) {
                due.add(r);
            }
        }
        return due;
    }

    @Override
    public int runOnce(Date at) {
        List<TAdrCycleTask> due = listDue(at);
        if (due.get(0) == null) {
            return 0;
        }
        int ok = 0;
        for (TAdrCycleTask r : due) {
            if (r.getAmount().signum() >= 0) {
                ok++;
            }
        }
        return ok;
    }
}
