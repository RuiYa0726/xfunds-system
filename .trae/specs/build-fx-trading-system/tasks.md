# Tasks

## 阶段一：基础设施与数据库

- [x] Task 1: 初始化后端 Spring Boot + MyBatis 工程骨架
  - [x] SubTask 1.1: 创建 `xfunds-server` Maven 工程，引入 Spring Boot 3、MyBatis、MySQL Driver、Lombok、Validation、Spring Web 依赖
  - [x] SubTask 1.2: 配置 `application.yml`（数据源、MyBatis 驼峰映射、端口 8080）
  - [x] SubTask 1.3: 建立标准包结构（controller/service/mapper/entity/dto/config/common）
  - [x] SubTask 1.4: 配置全局异常处理、统一返回结果 `Result<T>`、CORS 跨域
  - [x] SubTask 1.5: 集成 MyBatis 生成器或手写 mapper xml 基础结构

- [x] Task 2: 初始化前端 Vue 3 + Vite 工程
  - [x] SubTask 2.1: 创建 `xfunds-web` Vite + Vue 3 工程，引入 Element Plus、Pinia、Vue Router、Axios
  - [x] SubTask 2.2: 配置路由、Pinia store、Axios 请求封装（统一错误处理、token 拦截）
  - [x] SubTask 2.3: 实现登录页与登录态管理
  - [x] SubTask 2.4: 实现首页三栏布局（左侧菜单 / 中间牌价 / 右侧待办）

- [x] Task 3: 设计并创建 MySQL 数据库脚本
  - [x] SubTask 3.1: 创建机构表 `fx_org`、用户表 `fx_user`、角色表 `fx_role`、用户角色关联表 `fx_user_role`
  - [x] SubTask 3.2: 创建客户表 `fx_customer`、客户账户表 `fx_customer_account`、客户余额表 `fx_customer_balance`
  - [x] SubTask 3.3: 创建牌价表 `fx_quote`（含即期/远期/掉期各层级买卖价、现汇现钞区分）
  - [x] SubTask 3.4: 创建交易主表 `fx_trade_master`（全局索引、跨产品查询）
  - [x] SubTask 3.5: 创建即期交易表 `fx_spot_trade`
  - [x] SubTask 3.6: 创建远期交易表 `fx_forward_trade`
  - [x] SubTask 3.7: 创建掉期交易表 `fx_swap_trade`（近端/远端两腿）
  - [x] SubTask 3.8: 创建期权交易表 `fx_option_trade`
  - [x] SubTask 3.9: 创建保证金账户表 `fx_margin_account`、保证金流水表 `fx_margin_txn`
  - [x] SubTask 3.10: 创建生命周期事件表 `fx_trade_lifecycle`、审批日志表 `fx_approval_log`
  - [x] SubTask 3.11: 创建待办任务表 `fx_task`
  - [x] SubTask 3.12: 创建授信额度表 `fx_credit_limit`、保证金参数表 `fx_margin_param`、期权参数表 `fx_option_param`、系统参数表 `fx_sys_param`
  - [x] SubTask 3.13: 初始化基础数据（机构、角色、管理员、示例客户、示例牌价）

## 阶段二：公共管理模块

- [x] Task 4: 实现机构、用户、角色权限管理后端
  - [x] SubTask 4.1: 机构 CRUD（总行/分行/支行树形结构，parent_org_code）
  - [x] SubTask 4.2: 用户 CRUD，绑定机构与角色（经办/复核/授权）
  - [x] SubTask 4.3: 角色权限校验逻辑（maker ≠ checker）
  - [x] SubTask 4.4: 系统参数与日期管理 API

- [x] Task 5: 实现客户管理后端
  - [x] SubTask 5.1: 客户信息 CRUD
  - [x] SubTask 5.2: 客户账户管理（按币种、现汇/现钞）
  - [x] SubTask 5.3: 客户余额管理
  - [x] SubTask 5.4: 客户搜索接口（供交易录入选择客户号）

- [x] Task 6: 实现履约保障参数管理后端
  - [x] SubTask 6.1: 客户授信额度 CRUD
  - [x] SubTask 6.2: 应缴保证金计算（按交易金额、保证金比例、币种）
  - [x] SubTask 6.3: 保证金账户管理（余额、冻结、占用）

- [x] Task 7: 实现公共管理前端页面
  - [x] SubTask 7.1: 系统参数管理页（日期管理、人员角色权限）
  - [x] SubTask 7.2: 客户信息维护、客户账户管理、客户余额管理页
  - [x] SubTask 7.3: 履约保障参数管理页（授信额度、保证金参数）

## 阶段三：牌价与交易录入

- [x] Task 8: 实现牌价管理后端与前端
  - [x] SubTask 8.1: 牌价 CRUD 与查询 API（按币种对、类型过滤）
  - [x] SubTask 8.2: 即期牌价展示页（市场中间价、总/分买卖价、分/客买卖价）
  - [x] SubTask 8.3: 远期牌价展示页（含远期点数）
  - [x] SubTask 8.4: 掉期牌价展示页（含掉期点数）
  - [x] SubTask 8.5: 牌价点击分/客买卖价跳转交易录入页（带参数）

- [x] Task 9: 实现即期交易录入后端与前端
  - [x] SubTask 9.1: 即期交易录入 API（含客户账户下拉、保证金校验）
  - [x] SubTask 9.2: 即期交易录入页（字段：即期汇率、客户号搜索、币种1/2账户、客户汇率、成本汇率、分行收益点、买卖方向、金额、交易日、起息日、交割类型 T+0/T+1/T+2、保证金账户、保证金金额）
  - [x] SubTask 9.3: 提交后生成交易主表记录 + 即期明细 + 复核待办

- [x] Task 10: 实现远期交易录入后端与前端
  - [x] SubTask 10.1: 远期交易录入 API
  - [x] SubTask 10.2: 远期交易录入页（即期字段 + 到期日、远期汇率、远期点数、交割方式全额/差额）
  - [x] SubTask 10.3: 提交后生成主表 + 远期明细 + 待办

- [x] Task 11: 实现掉期交易录入后端与前端
  - [x] SubTask 11.1: 掉期交易录入 API
  - [x] SubTask 11.2: 掉期交易录入页（近端/远端两腿：账户、金额、汇率、起息日/到期日；掉期类型 S/B、B/S）
  - [x] SubTask 11.3: 提交后生成主表 + 掉期明细 + 待办

## 阶段四：交易生命周期与待办

- [x] Task 12: 实现交易状态机与生命周期事件后端
  - [x] SubTask 12.1: 定义交易状态枚举（DRAFT/PENDING_CHECK/ACTIVE/MATURED/SETTLED/DEFAULTED/CLOSED/REJECTED/ROLLED_OVER 等）
  - [x] SubTask 12.2: 实现生命周期事件记录服务（fx_trade_lifecycle 写入）
  - [x] SubTask 12.3: 实现审批日志记录服务（fx_approval_log 写入）

- [x] Task 13: 实现未到期交易管理后端与前端
  - [x] SubTask 13.1: 远期未到期交易查询 API（业务编号、交易类型、货币对、机构、客户号）
  - [x] SubTask 13.2: 提前交割操作（生成一笔掉期交易 + 违约金补录）
  - [x] SubTask 13.3: 提前违约操作（生成一笔即期 + 一笔掉期 + 违约金补录）
  - [x] SubTask 13.4: 原价展期操作（生成一笔掉期 + 选择展期日期 + 违约金释放）
  - [x] SubTask 13.5: 市价展期操作（生成一笔掉期 + 展期日期 + 损益处理）
  - [x] SubTask 13.6: 保证金增补操作
  - [x] SubTask 13.7: 掉期近端未到期交易管理（仅全部违约）
  - [x] SubTask 13.8: 掉期远端未到期交易管理（同远期操作集）
  - [x] SubTask 13.9: 前端未到期交易管理页（远期/掉期近端/掉期远端三个 Tab，查询条件 + 操作按钮 + 弹窗表单）

- [x] Task 14: 实现到期交易管理后端与前端
  - [x] SubTask 14.1: 即期到期交易管理（到期交割、到期违约）
  - [x] SubTask 14.2: 远期到期交易管理（到期交割、到期违约、原价展期、市价展期）
  - [x] SubTask 14.3: 掉期近端到期交易管理（到期交割、到期违约）
  - [x] SubTask 14.4: 掉期远端到期交易管理（到期交割、到期违约、原价展期、市价展期）
  - [x] SubTask 14.5: 前端到期交易管理页（四个 Tab）

- [x] Task 15: 实现客户交易查询后端与前端
  - [x] SubTask 15.1: 客户交易查询 API（业务编号、录入信息、原交易类型、特殊交易类型、状态、交割方式、各流程审批人）
  - [x] SubTask 15.2: 前端客户交易查询页（分页、排序、详情查看）

- [x] Task 16: 实现统一待办任务后端与前端
  - [x] SubTask 16.1: 待办任务服务（创建、认领、完成、取消、超时升级）
  - [x] SubTask 16.2: 我的待办、角色池待办、机构待办查询 API
  - [x] SubTask 16.3: 待办处理回调（复核通过/拒绝/退回推进交易状态机）
  - [x] SubTask 16.4: 前端待办任务列表页（双击打开处理）
  - [x] SubTask 16.5: 首页右侧待办列表组件对接

## 阶段五：期权交易管理

- [x] Task 17: 实现期权工作台后端与前端
  - [x] SubTask 17.1: 美式期权价内提醒查询 API（业务编号、买卖方向、第一币种涨跌、货币对、参考汇率、执行汇率、期权状态、原始签约金额、已平仓金额、剩余未处理金额、观察期、交易日期、货币1/2金额、客户号/名称、交割类型）
  - [x] SubTask 17.2: 查看原交易、执行、暂不处理 API
  - [x] SubTask 17.3: 前端期权工作台页（上待办、下价内提醒）

- [x] Task 18: 实现期权交易管理（发起）后端与前端
  - [x] SubTask 18.1: 普通期权发起 API（客户号、名称、货币对、币种1/2账户、期权费账户、买卖方向、期权种类、涨跌方向、即期汇率、执行价格、期权类别美式/欧式、行权时点、交易日、到期日、交割类型 T+0/T+1/T+2、交割日、天数、期权费交割日、交割方式全额/差额）
  - [x] SubTask 18.2: 前端期权发起页

- [x] Task 19: 实现期权存续期管理后端与前端
  - [x] SubTask 19.1: 未到期期权管理 + 平仓操作（填写平仓金额 → 复核流程）
  - [x] SubTask 19.2: 欧式到期期权管理（执行、放弃、平仓）
  - [x] SubTask 19.3: 期权费交割（填写交割账户 → 复核流程）
  - [x] SubTask 19.4: 美式期权监控（执行操作）
  - [x] SubTask 19.5: 美式到期期权管理（执行、放弃、平仓）
  - [x] SubTask 19.6: 前端存续期管理页（五个 Tab）

- [x] Task 20: 实现期权交易查询与参数管理
  - [x] SubTask 20.1: 期权交易查询、平仓交易查询、期权费交割查询、行权交易查询、放弃交易查询 API 与页面
  - [x] SubTask 20.2: 期权参数管理 CRUD（自动行权阈值、波动率、点差等）与页面

## 阶段六：集成与验证

- [x] Task 21: 端到端联调与数据校验
  - [x] SubTask 21.1: 即期/远期/掉期交易录入 → 复核 → 生效全流程联调
  - [x] SubTask 21.2: 未到期/到期生命周期操作（提前交割、违约、展期、保证金增补）联调
  - [x] SubTask 21.3: 期权发起 → 权利金交割 → 行权/放弃/平仓全流程联调
  - [x] SubTask 21.4: 待办任务认领、处理、超时升级验证
  - [x] SubTask 21.5: 三级机构额度升级审批验证

# Task Dependencies
- Task 2 依赖 Task 1（前端需对接后端接口规范，可并行初始化但联调依赖后端）
- Task 3 依赖 Task 1（数据库脚本放后端工程）
- Task 4/5/6 依赖 Task 3（需建表）
- Task 7 依赖 Task 4/5/6
- Task 8 依赖 Task 3/4
- Task 9/10/11 依赖 Task 8（录入需牌价与客户账户）
- Task 12 依赖 Task 3
- Task 13/14 依赖 Task 9/10/11/12
- Task 15 依赖 Task 12
- Task 16 依赖 Task 12
- Task 17/18 依赖 Task 3/12
- Task 19 依赖 Task 18
- Task 20 依赖 Task 19
- Task 21 依赖所有前置任务
