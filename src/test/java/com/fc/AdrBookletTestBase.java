package com.fc;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fc.v2.mapper.auto.TAdrCaseFlowMapper;
import com.fc.v2.mapper.auto.TAdrCheckBookletMapper;
import com.fc.v2.mapper.auto.TAdrCheckPeriodMapper;
import com.fc.v2.mapper.auto.TAdrOrgArchMapper;
import com.fc.v2.mapper.auto.TAdrReportRowMapper;
import com.fc.v2.mapper.auto.TAdrTermCardMapper;
import com.fc.v2.service.impl.AdrBookletServiceImpl;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.h2.tools.RunScript;
import org.junit.jupiter.api.BeforeEach;

import javax.sql.DataSource;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 对照册领域服务集成测试基台：H2(MODE=MySQL) + MyBatis-Plus，不起 Spring、不连 MySQL/Redis。
 */
public abstract class AdrBookletTestBase {

    protected AdrBookletServiceImpl service;
    protected TAdrReportRowMapper rowMapper;
    protected TAdrCheckBookletMapper bookletMapper;
    protected TAdrCheckPeriodMapper periodMapper;
    protected TAdrTermCardMapper termCardMapper;
    protected TAdrCaseFlowMapper caseFlowMapper;
    protected TAdrOrgArchMapper orgArchMapper;

    private final AtomicLong idSeq = new AtomicLong(1000);

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:adr_" + getClass().getSimpleName() + "_" + System.nanoTime()
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
        factory.getConfiguration().addMapper(TAdrReportRowMapper.class);
        factory.getConfiguration().addMapper(TAdrCheckBookletMapper.class);
        factory.getConfiguration().addMapper(TAdrCheckPeriodMapper.class);
        factory.getConfiguration().addMapper(TAdrTermCardMapper.class);
        factory.getConfiguration().addMapper(TAdrCaseFlowMapper.class);
        factory.getConfiguration().addMapper(TAdrOrgArchMapper.class);

        SqlSession session = factory.openSession(true);
        this.rowMapper = session.getMapper(TAdrReportRowMapper.class);
        this.bookletMapper = session.getMapper(TAdrCheckBookletMapper.class);
        this.periodMapper = session.getMapper(TAdrCheckPeriodMapper.class);
        this.termCardMapper = session.getMapper(TAdrTermCardMapper.class);
        this.caseFlowMapper = session.getMapper(TAdrCaseFlowMapper.class);
        this.orgArchMapper = session.getMapper(TAdrOrgArchMapper.class);

        this.service = new AdrBookletServiceImpl();
        inject("bookletMapper", this.bookletMapper);
        inject("periodMapper", this.periodMapper);
        inject("rowMapper", this.rowMapper);
        inject("termCardMapper", this.termCardMapper);
    }

    private void inject(String name, Object value) throws Exception {
        Field f = AdrBookletServiceImpl.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(this.service, value);
    }

    /** 给本级术语卡（追溯码对应的卡）落一张在册卡 */
    protected void seedCard(String billNo) {
        com.fc.v2.model.auto.TAdrTermCard card = new com.fc.v2.model.auto.TAdrTermCard();
        card.setId(idSeq.incrementAndGet());
        card.setBillNo(billNo);
        card.setStatus(1);
        card.setDelFlag(0);
        this.termCardMapper.insert(card);
    }

    /** 测试自增主键 */
    protected long nextId() {
        return idSeq.incrementAndGet();
    }

    /** 反射给服务实现塞数据层（不起 Spring、不走 @Resource） */
    protected static void injectField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    /** 落一条报告单位底册：status 0在册 1已退出 */
    protected void seedOrg(String siteNo, int status) {
        com.fc.v2.model.auto.TAdrOrgArch org = new com.fc.v2.model.auto.TAdrOrgArch();
        org.setId(nextId());
        org.setSiteNo(siteNo);
        org.setSiteName(siteNo + "底册");
        org.setStatus(status);
        org.setDelFlag(0);
        this.orgArchMapper.insert(org);
    }

    /** 落一张带单位归属的术语卡（追溯码→报告单位的桥） */
    protected void seedTermCard(String billNo, String siteNo) {
        com.fc.v2.model.auto.TAdrTermCard card = new com.fc.v2.model.auto.TAdrTermCard();
        card.setId(nextId());
        card.setBillNo(billNo);
        card.setSiteNo(siteNo);
        card.setStatus(1);
        card.setDelFlag(0);
        this.termCardMapper.insert(card);
    }
}
