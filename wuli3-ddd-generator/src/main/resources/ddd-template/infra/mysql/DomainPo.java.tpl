package {{basePackage}}.infra.persistence.{{domainPackage}}.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import {{basePackage}}.domain.{{domainPackage}}.{{domainType}};
import {{basePackage}}.sharedkernel.EntityId;
import {{basePackage}}.sharedkernel.Status;

/**
 * {{domainType}} 的数据库持久化对象示例。
 *
 * @param id 数据库主键
 * @author GuoYang create on 2026/9/7 15:30
 */
@TableName("`{{domainTable}}`")
public record {{domainType}}Po(@TableId(type = IdType.INPUT) String id, Status status) {
    /** 转换为领域聚合。 */
    public {{domainType}} toDomain() {
        return new {{domainType}}(new EntityId(this.id), this.status);
    }
}
