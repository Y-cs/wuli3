package {{basePackage}}.infra.persistence.{{domainPackage}}.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import {{basePackage}}.infra.persistence.{{domainPackage}}.entity.{{domainType}}Po;
import org.apache.ibatis.annotations.Mapper;

/**
 * {{domainType}} 持久化对象的 MyBatis-Plus Mapper。
 *
 * @author GuoYang create on 2026/9/7 15:30
 */
@Mapper
public interface {{domainType}}Mapper extends BaseMapper<{{domainType}}Po> {}
