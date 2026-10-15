package {{basePackage}}.infra.persistence.{{domainPackage}}.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import {{basePackage}}.infra.persistence.{{domainPackage}}.entity.{{domainType}}Po;

/**
 * {{domainType}} 持久化对象的 MyBatis-Plus Mapper。
 *
 * @author GuoYang create on 2026/9/7 15:30
 */
public interface {{domainType}}Mapper extends BaseMapper<{{domainType}}Po> {}
