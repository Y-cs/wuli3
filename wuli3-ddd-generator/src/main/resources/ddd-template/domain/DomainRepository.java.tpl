package {{basePackage}}.domain.{{domainPackage}};

/**
 * {{domainType}} 聚合仓储契约。
 *
 * 注意：当前示例仅演示聚合加载；实际写用例应按聚合语义补充保存能力，不能借用查询仓储写入。
 *
 * @author GuoYang create on 2026/9/7 15:30
 */
public interface {{domainType}}Repository {
    /** 加载示例聚合。 */
    {{domainType}} load();
}
