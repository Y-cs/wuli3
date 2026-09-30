-- 仅在独立集成测试数据库创建表，不重置开发机已有数据。
CREATE TABLE IF NOT EXISTS it_orders (
    id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(24) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB;
