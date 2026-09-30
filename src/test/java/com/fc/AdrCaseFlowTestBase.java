package com.fc;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fc.v2.mapper.auto.TAdrCaseFlowMapper;
import com.fc.v2.mapper.auto.TAdrCaseNoteMapper;
import com.fc.v2.mapper.auto.TAdrCheckBookletMapper;
import com.fc.v2.mapper.auto.TAdrCheckPeriodMapper;
import com.fc.v2.mapper.auto.TAdrOrgArchMapper;
import com.fc.v2.mapper.auto.TAdrReportRowMapper;
import com.fc.v2.mapper.auto.TAdrTermCardMapper;
import com.fc.v2.model.auto.TAdrCheckBooklet;
import com.fc.v2.model.auto.TAdrReportRow;
import com.fc.v2.model.auto.TAdrTermCard;
import com.fc.v2.service.impl.TAdrCaseFlowServiceImpl;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.h2.tools.RunScript;
import org.junit.jupiter.api.BeforeEach;

import javax.sql.DataSource;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 处置单状态机集成测试基台：H2(MODE=MySQL) + MyBatis-Plus，不起 Spring、不连 MySQL/Redis。
 */
public abstract class AdrCaseFlowTestBase {

    protected TAdrCaseFlowServiceImpl service;
    protected TAdrCaseFlowMapper flowMapper;
    protected TAdrCaseNoteMapper noteMapper;
    protected TAdrTermCardMapper termCardMapper;
    protected TAdrReportRowMapper rowMapper;
    protected TAdrCheckBookletMapper bookletMapper;
    protected TAdrCheckPeriodMapper periodMapper;
    protected TAdrOrgArchMapper orgArchMapper;

    private final AtomicLong idSeq = new AtomicLong(5000);

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:caseflow_" + getClass().getSimpleName() + "_" + System.nanoTime()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        ds.setUser("sa");

        try (Connection conn = ds.getConnection();
             Reader reader = new InputStreamReader(
                     getClass().getResourceAsStream("/schema-h2.sql"), StandardCharsets.UTF_8)) {
            RunScript.execute(conn, reader);
        }

        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);

        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource((DataSource) ds);
        factoryBean.setConfiguration(configuration);
        SqlSessionFactory factory = factoryBean.getObject();
        factory.getConfiguration().addMapper(TAdrCaseFlowMapper.class);
        factory.getConfiguration().addMapper(TAdrCaseNoteMapper.class);
        factory.getConfiguration().addMapper(TAdrTermCardMapper.class);
        factory.getConfiguration().addMapper(TAdrReportRowMapper.class);
        factory.getConfiguration().addMapper(TAdrCheckBookletMapper.class);
        factory.getConfiguration().addMapper(TAdrCheckPeriodMapper.class);
        factory.getConfiguration().addMapper(TAdrOrgArchMapper.class);

        SqlSession session = factory.openSession(true);
        this.flowMapper = session.getMapper(TAdrCaseFlowMapper.class);
        this.noteMapper = session.getMapper(TAdrCaseNoteMapper.class);
        this.termCardMapper = session.getMapper(TAdrTermCardMapper.class);
        this.rowMapper = session.getMapper(TAdrReportRowMapper.class);
        this.bookletMapper = session.getMapper(TAdrCheckBookletMapper.class);
        this.periodMapper = session.getMapper(TAdrCheckPeriodMapper.class);
        this.orgArchMapper = session.getMapper(TAdrOrgArchMapper.class);

        this.service = new TAdrCaseFlowServiceImpl();
        inject("caseFlowMapper", this.flowMapper);
        inject("caseNoteMapper", this.noteMapper);
        inject("termCardMapper", this.termCardMapper);
        inject("reportRowMapper", this.rowMapper);
        inject("checkBookletMapper", this.bookletMapper);
        inject("orgArchMapper", this.orgArchMapper);
    }

    private void inject(String name, Object value) throws Exception {
        Field f = TAdrCaseFlowServiceImpl.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(this.service, value);
    }

    /** 落一张在册术语卡（追溯码），可指单位代号 */
    protected void seedCard(String billNo, String siteNo) {
        TAdrTermCard card = new TAdrTermCard();
        card.setId(idSeq.incrementAndGet());
        card.setBillNo(billNo);
        card.setSiteNo(siteNo);
        card.setStatus(1);
        card.setDelFlag(0);
        this.termCardMapper.insert(card);
    }

    /** 上级下发在册（核讫）一册，挑出 code 这一行并落已挂接 */
    protected void seedProvinceChecked(String batchNo, String code, String periodNo) {
        seedBooklet(batchNo, periodNo, 1);
        TAdrCheckBooklet b = this.bookletMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<TAdrCheckBooklet>()
                        .eq("batch_no", batchNo));
        TAdrReportRow row = new TAdrReportRow();
        row.setId(idSeq.incrementAndGet());
        row.setBookletId(b.getId());
        row.setBatchNo(batchNo);
        row.setPeriodNo(periodNo);
        row.setRowNo(1);
        row.setItemCode(code);
        row.setQty(new BigDecimal("5"));
        row.setPostedQty(new BigDecimal("5"));
        row.setArchivePhase(0);
        row.setCrossCount(0);
        row.setStatus(1);
        row.setDelFlag(0);
        this.rowMapper.insert(row);
    }

    /** 这家单位这一期压着一桩没结完的核对事务：该单位卡码的现行挂起行 */
    protected void seedPendingAffair(String siteNo, String code, String periodNo) {
        TAdrReportRow row = new TAdrReportRow();
        row.setId(idSeq.incrementAndGet());
        row.setBookletId(seedBooklet("B-PEND-" + code, periodNo, 0).getId());
        row.setBatchNo("B-PEND-" + code);
        row.setPeriodNo(periodNo);
        row.setRowNo(1);
        row.setItemCode(code);
        row.setQty(new BigDecimal("3"));
        row.setArchivePhase(0);
        row.setCrossCount(0);
        row.setStatus(2);
        row.setSuspendReason("挂着没结");
        row.setDelFlag(0);
        this.rowMapper.insert(row);
    }

    protected TAdrCheckBooklet seedBooklet(String batchNo, String periodNo, int receiptStatus) {
        TAdrCheckBooklet b = new TAdrCheckBooklet();
        b.setId(idSeq.incrementAndGet());
        b.setBatchNo(batchNo);
        b.setPeriodNo(periodNo);
        b.setRowCap(500);
        b.setTotalRows(0);
        b.setLinkedRows(0);
        b.setSuspendedRows(0);
        b.setVoidFlag(0);
        b.setReceiptStatus(receiptStatus);
        b.setDelFlag(0);
        this.bookletMapper.insert(b);
        return b;
    }
}
