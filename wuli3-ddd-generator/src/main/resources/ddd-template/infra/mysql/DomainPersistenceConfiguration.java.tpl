package {{basePackage}}.infra.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 配置 Mapper 扫描；默认启用，无数据库的组装测试可显式关闭。
 *
 * @author GuoYang create on 2026/10/14 15:30
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "application.persistence.enabled", havingValue = "true", matchIfMissing = true)
@MapperScan(basePackages = "{{basePackage}}.infra.persistence.**", annotationClass = Mapper.class)
public class {{domainType}}PersistenceConfiguration {
    /** 分页拦截器由 MyBatis-Plus 与默认防误操作拦截器一起聚合。 */
    @Bean
    public PaginationInnerInterceptor paginationInnerInterceptor() {
        return new PaginationInnerInterceptor(DbType.MYSQL);
    }
}
