# 墨衡 · 见微 产品需求文档 PRD V0.2

> **产品名称**：墨衡 · 见微  
> **英文能力名**：Impact  
> **CLI 主入口**：`mh`  
> **建议命令**：`mh impact` / `mh jw`  
> **产品定位**：面向 AI Coding / Vibe Coding 时代的开源代码变更影响分析与精准回归平台  
> **一句话说明**：从一次代码变化，看见它最终会影响哪里。  
> **文档状态**：需求讨论稿 V0.2  
> **当前阶段目标**：确认 MVP 范围、核心用户流程与产品交互，再进入详细设计与开发。

---

# 1. 背景

AI Coding、Vibe Coding 正在快速提高研发代码产出速度。

过去一次需求可能修改：

- 3 个文件；
- 2 个方法；
- 1 个接口。

现在借助 AI，一次提交可能快速修改：

- 十几个甚至几十个文件；
- 多个模块；
- 公共方法；
- 配置；
- API；
- 数据结构；
- 自动化测试。

研发交付速度变快，但测试人员理解变化的速度并没有同步提高。

因此出现新的质量问题：

1. 测试人员越来越难判断真实影响范围；
2. 研发描述的影响范围容易遗漏；
3. 代码 Diff 很大时，人工 Review 成本高；
4. 全量回归成本越来越高；
5. AI 生成代码增加了“改动很多但人并未真正理解”的风险；
6. 企业已有大量自动化测试资产，但缺少“本次应该运行哪些测试”的依据。

传统 Git Diff 只能回答：

> 改了哪些文件、哪些行。

而测试真正需要的是：

> 这次变化会影响哪些方法、模块、接口、业务链路和测试用例？

---

# 2. 产品定位

墨衡 · 见微不是 Git Diff Viewer，也不是单纯的 Call Graph 工具。

它的核心定位是：

> **Change Intelligence for Quality**

最终希望形成：

```text
Git Change
   ↓
Changed Symbol
   ↓
Code Impact
   ↓
Business Impact
   ↓
Test Impact
   ↓
Regression Plan
```

见微需要持续回答四个核心问题：

### 2.1 改了什么？

例如：

```text
CouponService.useCoupon()
```

### 2.2 会影响什么？

例如：

```text
CouponService.useCoupon()
        ↑
OrderService.submit()
        ↑
OrderController.submit()
        ↑
POST /api/orders
```

### 2.3 过去真实影响过什么？

例如：

```text
TC1001
  ↓ historical coverage
CouponService.useCoupon()
```

### 2.4 这次应该测试什么？

例如：

```text
Recommended Regression

自动化：
- TC1001 优惠券下单
- TC1012 订单取消返券

人工重点：
- 优惠券有效期边界
- 重复提交
```

---

# 3. 产品目标

## 3.1 第一阶段目标

第一阶段只解决：

> **代码发生变化后，快速、准确、可解释地告诉测试人员影响范围。**

V0.1 / MVP 重点：

```text
Git Diff
→ Changed Method
→ Static Call Graph
→ Spring API Impact
→ Web Report
```

---

## 3.2 中长期目标

后续逐步扩展为：

```text
代码变化
   ↓
业务影响
   ↓
动态链路
   ↓
测试映射
   ↓
精准回归
   ↓
自动执行
   ↓
质量门禁
```

最终成为：

> **AI Coding 时代的质量影响分析与回归决策平台。**

---

# 4. 产品原则

## 4.1 Evidence First

影响判断必须优先来自确定性证据。

证据来源包括：

```text
STATIC
静态代码关系

COVERAGE
测试覆盖关系

TRACE
运行时链路

RULE
用户配置规则

HISTORY
历史执行与失败数据

AI
AI 推断
```

AI 不能替代底层证据。

---

## 4.2 Explainable

任何“受影响”结果必须能够解释：

```text
为什么认为 POST /order 会受影响？
```

用户应该看到：

```text
CouponService.useCoupon
      ↑ CALLS
OrderService.submit
      ↑ CALLS
OrderController.submit
      ↑ EXPOSES_API
POST /api/order
```

而不是：

```text
AI 判断订单模块可能受影响。
```

---

## 4.3 Local First

开源项目优先满足：

```text
本地运行
私有代码
企业内网
CI Runner
```

用户不应因为使用见微而被迫上传源码到第三方 SaaS。

---

## 4.4 Web First

用户主要操作界面是 Web。

原因：

- QA 用户不一定熟悉复杂 CLI；
- 图谱与影响路径需要可视化；
- 方便产品、研发、测试一起查看；
- 方便未来形成质量平台。

CLI 主要服务：

```text
开发者
CI/CD
自动化
脚本集成
```

---

## 4.5 Open Source Friendly

开源版本必须：

> 真正可以在工作中用，而不是 Demo。

Community 版本至少能够完整完成：

```text
Repo
→ Commit Diff
→ Changed Symbol
→ Impact Graph
→ API Impact
→ Report
```

---

# 5. 目标用户

## 5.1 核心用户

第一阶段：

- 测试工程师；
- 测试开发工程师；
- QA Lead；
- 研发工程师；
- Java / Spring Boot 团队；
- 有 Git + CI/CD 的研发团队。

---

## 5.2 次级用户

后续：

- Tech Lead；
- 架构师；
- DevOps；
- Engineering Manager；
- 发布负责人。

---

# 6. 典型用户场景

## 6.1 场景 A：提测影响分析

研发告诉测试：

> 这次主要改了优惠券逻辑。

测试打开见微：

```text
Project:
order-service

Base:
main

Head:
feature/coupon-refactor
```

系统分析：

```text
Changed Files: 8
Changed Methods: 13

High Impact:
CouponService.useCoupon()

Affected APIs:
POST /api/order
POST /api/coupon/use

Affected Modules:
order
coupon
```

测试据此决定回归范围。

---

## 6.2 场景 B：PR 自动分析

研发提交 PR。

CI 自动执行：

```bash
mh impact analyze   --base origin/main   --head HEAD
```

PR 自动生成评论：

```text
墨衡 · 见微

Risk: HIGH

Changed Methods: 12

Affected APIs:
3

Affected Modules:
2

View Report:
https://...
```

---

## 6.3 场景 C：精准回归

后期系统已经积累：

```text
TC1001 → CouponService.useCoupon
TC1002 → OrderService.cancel
TC1003 → MemberService.query
```

本次修改：

```text
CouponService.useCoupon
```

系统输出：

```text
Recommended Tests:

TC1001
TC1012
TC1028
```

---

## 6.4 场景 D：测试负责人评估风险

系统显示：

```text
Risk Score: 82

Reasons:

- 修改核心下单链路
- 影响 3 个 API
- 修改公共 Service
- 自动化覆盖不足
```

---

# 7. MVP 范围

## 7.1 V0.1 必须支持

### 代码层

```text
Java
Spring Boot
Maven
Gradle
Git
```

### 分析能力

```text
Git Diff
Changed File
Changed Class
Changed Method
Static Call Graph
Reverse Impact
Spring REST API Mapping
Risk Level
```

### 产品能力

```text
Web UI
CLI
Docker Compose
JSON Output
Basic Project Management
Basic Analysis History
```

---

## 7.2 V0.1 暂不支持

```text
Python
Go
JavaScript
Rust

精准 Test Selection

UI 自动化关联

OpenTelemetry Trace

JaCoCo Test Mapping

Production Agent

复杂 AI Agent

跨 Repo 影响分析

大型 Graph Database
```

---

# 8. 用户首次使用流程

核心要求：

> **10 分钟内看到第一份影响分析报告。**

流程：

```text
docker compose up
       ↓
打开 Web
       ↓
创建 Project
       ↓
连接代码仓库
       ↓
选择 Base / Head
       ↓
点击 Analyze
       ↓
查看 Impact Report
```

---

# 9. 项目接入方式

## 9.1 本地目录模式

适合第一次体验。

例如：

```yaml
volumes:
  - /Users/me/order-service:/workspace/order-service
```

Web 配置：

```text
Repository Type:
Local

Path:
/workspace/order-service
```

优点：

```text
无需 Token
无需网络
非常适合 Demo / PoC
```

---

## 9.2 Git Clone 模式

支持：

```text
GitHub
GitLab
Generic Git
```

用户配置：

```text
Repository URL
Access Token
Default Branch
```

---

## 9.3 CI Push 模式

未来推荐企业采用：

```text
CI Runner
   ↓
mh impact analyze
   ↓
Result JSON
   ↓
ChangeScope Server
```

这里 Server 不需要保存源码。

只保存：

```text
Symbol
Edge
Analysis
Risk
Coverage
Metadata
```

---

# 10. Web 页面结构

第一阶段只做最核心页面。

---

## 10.1 首页 / Dashboard

展示：

```text
Projects
Recent Analyses
High Risk Changes
Analysis Status
```

第一版不要做复杂经营看板。

---

## 10.2 项目列表

字段：

```text
Project Name
Repository
Language
Framework
Default Branch
Last Analysis
Last Risk
```

操作：

```text
New Project
Analyze
View History
Settings
```

---

## 10.3 创建项目

配置项：

```text
Project Name

Repository Type
- Local
- Git

Repository URL / Path

Default Branch

Language
Java

Framework
Spring Boot
```

高级配置：

```text
Include Packages
Exclude Packages
Max Impact Depth
Safe Mode Rules
```

---

# 11. New Analysis 页面

用户选择：

```text
Project:
order-service

Base:
main

Head:
feature/coupon-refactor
```

支持：

```text
Branch
Commit SHA
Tag
```

点击：

```text
Analyze
```

高级选项：

```text
Max Depth: 5
Include Tests: false
Show Low Confidence: true
```

---

# 12. Analysis Overview

这是整个产品最核心页面。

顶部：

```text
Analysis #1023

Base:
main@abc123

Head:
feature/coupon@def456

Risk:
HIGH
```

关键指标：

```text
Files Changed      12

Methods Changed    18

Methods Impacted   42

APIs Impacted       3
```

---

## 12.1 Changed

展示：

```text
CouponService

  useCoupon()
  calculateExpireTime()

OrderService

  submit()
```

---

## 12.2 Impacted APIs

例如：

```text
POST /api/orders

Risk: HIGH

Reason:
CouponService.useCoupon
← OrderService.submit
← OrderController.submit
```

---

## 12.3 Impacted Modules

```text
Order
Coupon
Inventory
```

---

## 12.4 Risk Reasons

例如：

```text
HIGH

Reasons:

+20 Core API affected
+15 Shared Service modified
+10 Impact depth > 3
+20 Config file changed
```

必须可解释。

---

# 13. Impact Graph 页面

用户可以看到：

```text
POST /order
     ↓
OrderController.submit
     ↓
OrderService.submit
     ↓
CouponService.useCoupon
```

节点类型：

```text
FILE
CLASS
METHOD
API
MODULE
TEST
```

未来：

```text
SERVICE
RPC
MQ
TABLE
```

---

## 13.1 图操作

支持：

```text
Zoom
Pan
Expand
Collapse
Filter
Search
```

点击节点查看：

```text
Symbol
File
Line
Type
Changed?
Risk
Incoming
Outgoing
Evidence
```

---

# 14. Impact Detail

例如：

```text
CouponService.useCoupon()
```

显示：

```text
Type:
METHOD

File:
src/main/java/.../CouponService.java

Changed Lines:
73-96

Impact:
HIGH
```

关系：

```text
Incoming

OrderService.submit


Outgoing

CouponRepository.update
InventoryService.lock
```

入口：

```text
Affected APIs

POST /api/order
POST /api/coupon/use
```

---

# 15. CLI 设计

统一 CLI：

```bash
mh
```

第一款产品：

```bash
mh impact
```

中文别名：

```bash
mh jw
```

二者等价。

---

## 15.1 初始化

```bash
mh impact init
```

生成：

```text
.moheng.yml
```

---

## 15.2 分析

```bash
mh impact analyze   --base main   --head HEAD
```

简写：

```bash
mh jw analyze main HEAD
```

---

## 15.3 查看结果

```bash
mh impact show
```

---

## 15.4 JSON

```bash
mh impact analyze   --base main   --head HEAD   --format json
```

---

## 15.5 上传 Server

```bash
mh impact analyze   --server http://localhost:8080
```

---

# 16. 配置文件

建议：

```text
.moheng.yml
```

示例：

```yaml
project:
  name: order-service

analysis:
  maxDepth: 5

include:
  - com.company.*

exclude:
  - "**/generated/**"
  - "**/test/**"

safeMode:
  files:
    - pom.xml
    - build.gradle
    - "**/application.yml"

risk:
  corePackages:
    - com.company.order.*
    - com.company.payment.*

ai:
  enabled: false
```

---

# 17. 核心分析流程

```text
Git Diff
   ↓
Changed Lines
   ↓
Changed Symbols
   ↓
Static Graph
   ↓
Reverse Traversal
   ↓
Entry Points
   ↓
Impact Result
   ↓
Risk Engine
   ↓
Report
```

---

# 18. Git Diff

输入：

```text
base commit
head commit
```

输出：

```json
{
  "file": "CouponService.java",
  "type": "MODIFIED",
  "ranges": [
    [73, 96]
  ]
}
```

---

# 19. Changed Symbol

Changed Line 映射：

```text
package
class
method
field
```

例如：

```text
com.company.coupon.CouponService.useCoupon
```

---

# 20. Static Graph

第一阶段 Edge：

```text
DEFINES
CALLS
REFERENCES
IMPLEMENTS
EXTENDS
EXPOSES_API
```

Spring 专项：

```text
@RequestMapping
@GetMapping
@PostMapping
@PutMapping
@DeleteMapping
```

映射：

```text
API
→ Controller Method
```

---

# 21. Reverse Impact

例如：

```text
Changed:

CouponService.useCoupon
```

反查：

```text
CouponService.useCoupon
      ↑
OrderService.submit
      ↑
OrderController.submit
      ↑
POST /api/order
```

输出：

```text
Affected API:
POST /api/order
```

---

# 22. 最大影响深度

防止影响无限扩散。

默认：

```text
maxDepth = 5
```

未来允许不同 Edge 不同权重。

---

# 23. Confidence

每条影响路径需要：

```text
confidence
```

例如：

```text
DIRECT_CALL       1.0
INTERFACE_IMPL    0.9
REFERENCE         0.7
AI_RELATED        0.5
```

UI 可选择：

```text
High Confidence Only
```

---

# 24. Risk Engine

第一阶段不做 ML。

规则化评分：

```text
Risk Score
```

可考虑：

```text
Change Size

Impact Radius

Core API

Shared Component

Config Change

Dependency Change

Unknown Impact
```

示例：

```text
修改核心 Service       +15

影响 API >= 3         +15

修改公共 DTO          +10

pom.xml Changed       +20
```

输出：

```text
LOW
MEDIUM
HIGH
CRITICAL
```

---

# 25. Safe Mode

Certain changes should trigger wider regression.

包括：

```text
pom.xml
build.gradle
application.yml

DB schema

common module

auth

serialization

public DTO

framework config
```

触发：

```text
SAFE MODE
```

UI：

```text
⚠ Wide impact detected

Recommended:
Full regression or expanded regression
```

---

# 26. Analysis History

每次分析保存：

```text
Project
Base
Head
Commit
Changed Symbol
Impact Result
Risk
Created Time
```

支持：

```text
Compare Analysis
View History
```

---

# 27. Dynamic Analysis 规划

动态分析不是 V0.1 强制能力，但从数据模型上预留。

分三层。

---

## 27.1 JaCoCo Coverage

解决：

> 哪个测试真正执行过哪些代码？

建立：

```text
TEST
 ↓ COVERS
METHOD
```

例如：

```text
TC001
 ↓
CouponService.useCoupon
```

---

## 27.2 OpenTelemetry

解决：

> 一条 API / UI 自动化真实经过哪些服务？

例如：

```text
TC001
 ↓
POST /order
 ↓
Gateway
 ↓
Order Service
 ↓
Coupon Service
```

关联：

```text
trace_id
test_case_id
```

---

## 27.3 Runtime Agent

后期 Java Agent：

```text
ByteBuddy
```

记录：

```text
caller
callee
class
method
traceId
testCaseId
```

构建：

```text
Runtime Call Graph
```

---

# 28. Test Impact Analysis

V0.2+。

核心：

```text
Historical Test Coverage
       ↓
Test → Method
       ↓
Changed Method
       ↓
Reverse Lookup
       ↓
Recommended Tests
```

---

# 29. Shadow Mode

第一次不能直接 Skip。

应该：

```text
Recommended:
70 / 1000

Actual:
Run 1000
```

然后计算：

```text
Missed Failure
Recall
Reduction Rate
Time Saved
```

当长期可信：

```text
Recall > threshold
```

才允许：

```text
Enable Test Skipping
```

---

# 30. 测试资产接入

原则：

> 不要求用户迁移现有测试平台。

通过 Adapter 连接：

```text
JUnit
Pytest
Postman
JMeter
Playwright
Selenium
TestRail
禅道
自研平台
```

统一成：

```text
Test Asset
```

---

# 31. AI 能力

AI 默认：

```text
Optional
```

不开 AI：

```text
产品仍可完整工作。
```

---

## 31.1 AI V1

做：

```text
Change Summary
Risk Explanation
```

---

## 31.2 AI V2

做：

```text
Manual Regression Recommendation
Coverage Gap
Test Suggestion
```

输入必须优先使用：

```text
Impact Graph
Existing Tests
History
```

而不是直接把整个 Repo 交给 LLM 猜。

---

# 32. 开源版本要求

Community 必须包含：

```text
Web

CLI

Java Analyzer

Git Diff

Changed Symbol

Static Graph

API Impact

Impact Graph

Risk

Docker Compose
```

用户必须真正能完成：

```text
真实项目
→ 真实分析
→ 真实报告
```

---

# 33. 安装体验

目标：

```bash
git clone ...
cd ...
docker compose up -d
```

然后：

```text
http://localhost:8080
```

这是默认推荐体验。

开发者可单独运行：

```text
server
web
cli
analyzer
```

---

# 34. 技术架构建议

```text
Web
React + TypeScript

Server
Go

CLI
Go

Core
Go

Java Analyzer
Java

Static Analysis
SCIP + SootUp

Dynamic Coverage
JaCoCo

Runtime Agent
ByteBuddy

Trace
OpenTelemetry

Local DB
SQLite

Server DB
PostgreSQL
```

---

# 35. 模块结构

```text
moheng
├── server
├── web
├── cli
├── core
├── analyzers
│   └── java
├── collectors
│   ├── coverage
│   └── trace
├── integrations
│   ├── github
│   ├── gitlab
│   └── jenkins
└── deploy
```

---

# 36. Analyzer Plugin Protocol

从第一天就为多语言预留。

统一输出：

```json
{
  "nodes": [],
  "edges": []
}
```

未来：

```text
analyzer-java
analyzer-python
analyzer-go
analyzer-ts
```

Core 不依赖某一种语言实现。

---

# 37. Impact Graph 数据模型

## Node

```text
REPOSITORY
MODULE
FILE
CLASS
METHOD
API
TEST
```

未来：

```text
SERVICE
RPC
MQ
DATABASE
TABLE
BUG
REQUIREMENT
```

---

## Edge

```text
CONTAINS
DEFINES
CALLS
REFERENCES
IMPLEMENTS
EXTENDS
EXPOSES_API
```

未来：

```text
COVERS
TESTS
CALLS_API
READS
WRITES
PRODUCES
CONSUMES
TRACES_TO
```

---

# 38. Evidence

每一条关系包含：

```json
{
  "edge": "CALLS",
  "source": "STATIC",
  "confidence": 0.95
}
```

未来：

```text
STATIC
COVERAGE
TRACE
HISTORY
RULE
AI
```

---

# 39. 产品命名体系

母品牌：

```text
墨衡
```

第一款：

```text
墨衡 · 见微
```

能力：

```text
Change Intelligence
```

CLI：

```text
mh impact
```

Alias：

```text
mh jw
```

---

# 40. 后续产品矩阵

当前仅作为方向，不作为正式需求。

```text
墨衡 · 见微
Impact Intelligence

墨衡 · 循迹
Runtime Intelligence

墨衡 · 验真
Test Intelligence

墨衡 · 司衡
Quality Intelligence
```

其中：

```text
见微
回答：改了什么？影响哪里？

循迹
回答：运行时真实走了哪里？

验真
回答：应该测什么？结果是否正确？

司衡
回答：整个系统质量如何？
```

---

# 41. 商业化边界

当前优先：

> 开源获取用户与真实场景。

未来 Open Core。

---

## Community

永久免费：

```text
Local Analysis

CLI

Web

Java Analyzer

Impact Graph

Basic CI
```

---

## Team

未来：

```text
Hosted Dashboard

History

PR Bot

Team Collaboration

Test Analytics

Risk Trend
```

---

## Enterprise

未来：

```text
Private Deployment

SSO

RBAC

Audit

Multi Repo

Cross Service

Jenkins

GitLab Enterprise

Jira

禅道

Custom Rules

SLA
```

---

# 42. 核心成功指标

## 安装

```text
First Value < 10 min
```

---

## 分析质量

关注：

```text
Impact Recall

Impact Precision
```

---

## 可解释性

目标：

```text
所有 Impact 结果都有 Evidence Path
```

---

## 使用情况

关注：

```text
GitHub Stars

Active Projects

Weekly Analysis

Repeat Usage

PR Integration
```

---

# 43. 第一阶段开发顺序

建议：

```text
1. Graph Schema
      ↓
2. Git Diff
      ↓
3. Changed Symbol
      ↓
4. Java Static Graph
      ↓
5. Reverse Impact
      ↓
6. Spring API Mapping
      ↓
7. CLI
      ↓
8. Server API
      ↓
9. Web
      ↓
10. Docker Compose
      ↓
11. CI Integration
```

不建议一开始先做漂亮 Web。

核心必须先证明：

```text
Changed Method
→ Impacted Method
→ Impacted API
```

准确可用。

---

# 44. MVP 验收标准

V0.1 发布时至少满足：

### 用户体验

用户能够：

```text
安装
→ 创建项目
→ 选择两个 Commit
→ 点击 Analyze
→ 查看影响报告
```

---

### 分析结果

必须展示：

```text
Changed Files

Changed Methods

Impacted Methods

Impacted APIs

Impact Path

Risk Level

Risk Reasons
```

---

### CLI

支持：

```bash
mh impact analyze
```

并支持：

```text
JSON output
```

---

### 部署

支持：

```text
Docker Compose
```

---

### 开源

用户无需商业服务即可：

```text
完成完整 Change Impact Analysis。
```

---

# 45. 当前需要重点讨论的需求问题

下面这些问题建议后续逐条确认。

## P0

1. 第一版是否只支持 Java / Spring Boot？
2. Static Call Graph 精度需要做到什么程度？
3. Spring Bean / Interface / Feign 是否必须进入 V0.1？
4. Web 是否必须直接 Clone Git 仓库？
5. 是否第一版就支持 GitLab / GitHub PR？
6. Risk Score 是否需要用户自定义规则？
7. 是否必须支持私有化纯离线运行？

---

## P1

8. API Impact 是否只做 REST？
9. MyBatis / JPA / DB Table 是否进入第一阶段？
10. MQ 是否进入第一阶段？
11. 是否第一版就保存历史分析？
12. 是否加入 AI Change Summary？
13. 是否第一版就提供 PR Comment？

---

## P2

14. JaCoCo Test Mapping 何时进入？
15. OpenTelemetry 何时进入？
16. 是否需要 SaaS Cloud？
17. 第一个商业化功能是什么？

---

# 46. 当前建议的产品核心

现阶段最重要的一句话：

> **见微不是帮用户“看代码”，而是帮用户“决定该测哪里”。**

V0.1 虽然暂时不直接推荐完整测试用例，但所有能力都应该围绕这个终点设计：

```text
Code Change
   ↓
Impact
   ↓
Risk
   ↓
Regression Decision
```

而不是最终变成另一个：

```text
代码关系可视化工具。
```

这是产品设计过程中最需要坚持的边界。

---

# 47. 当前版本结论

现阶段建议正式确认：

```text
母品牌：
墨衡

产品：
墨衡 · 见微

产品能力：
Change Intelligence

CLI：
mh impact

Alias：
mh jw

MVP：
Java / Spring Boot
Git Diff
Changed Symbol
Static Call Graph
API Impact
Impact Graph
Web
CLI
Docker Compose
```

下一步不应该继续扩大功能范围，而应该逐条确认：

```text
V0.1 到底分析到什么粒度？
哪些 Spring 场景必须支持？
哪些场景允许误报？
哪些场景绝对不能漏报？
```

这些问题确认后，再进入技术详细设计。
