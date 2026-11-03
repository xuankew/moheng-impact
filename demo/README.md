# 内置演示仓库

`setup.sh` 会在指定目录（默认 `/workspace/demo`）创建两个提交：

1. 初始版本：`OrderController` → `OrderService` → `CouponService`，公共 DTO `OrderView`。
2. 演示变更：`CouponService` 兜底值、`OrderService` 调用参数、`OrderView` 新增字段、`application.yml` 端口。

用默认项目设置（`HEAD~1 → HEAD`）运行分析时，可以看到：

- `GET /api/orders/{id}` 受影响，调用链包含构造器与 Lombok 风格访问器；
- `OrderView` 公共 DTO 变更触发 `PUBLIC_DTO_CHANGED` 与 Safe Mode；
- 配置变更触发 `CONFIG_CHANGED`。

本地使用：

```sh
sh demo/setup.sh /tmp/moheng-demo
MOHENG_WORKSPACE_ROOT=/tmp MOHENG_DEMO_REPO=/tmp/moheng-demo java -jar server/target/moheng-server.jar
```

脚本拒绝写入非空目录，不会删除已有文件。容器镜像在构建时已自动创建 `/workspace/demo`。
