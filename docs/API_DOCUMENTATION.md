# edu-trip 接口文档

> 文档版本：1.0  
> 对应项目版本：1.4  
> 生成日期：2026-07-16  
> 文档依据：当前仓库 Controller、Entity、VO、Service 实现与配置文件

## 1. 文档范围

本文档覆盖当前项目中所有实际声明了 HTTP Mapping 的接口，共 82 个，包含：

- 系统管理：用户、角色、菜单、博物馆、文件、操作日志。
- 研学业务：活动、活动类型、活动标签、活动文件、皮肤、游客、团队、评价。
- 交易业务：下单、支付确认、退款、核销、订单查询、银联回调。
- 对账业务：银联流水导入、异常对账和已废弃兼容接口。
- 微信小程序：使用临时登录凭证换取 OpenID。

`SysExceptionController`、`ActivityScheduleController`、`OrderDetailController` 当前只有基础路由，没有可调用方法，因此不计入 82 个接口。

## 2. 基础约定

### 2.1 服务地址

| 环境 | 默认地址 | 说明 |
|---|---|---|
| 本地开发 | `http://localhost:8020` | `application.yml` 默认端口为 8020 |
| 生产环境 | 由网关或反向代理决定 | 当前生产资源域名配置为 `/tyyanxue_api` 路径前缀 |

接口路径均以 Controller 中声明的绝对路径为准。如果生产环境经网关增加上下文路径，应在下列路径前追加网关前缀。

### 2.2 数据格式

- 默认请求类型：`application/json`
- 文件上传：`multipart/form-data`
- 默认响应类型：`application/json`
- 日期：`yyyy-MM-dd`
- 时间：`HH:mm:ss`
- 日期时间：`yyyy-MM-dd HH:mm:ss`
- 金额：除银联 Excel 原始模型使用 `BigDecimal` 外，订单、退款、活动价格和新对账结果统一以“分”为单位。

### 2.3 鉴权

项目使用 Sa-Token。

- Token 名称：`sa-token`
- 推荐传递方式：HTTP Header，例如 `sa-token: <token-value>`
- 默认规则：除 `@SaIgnore` 接口外，所有接口都必须登录。
- 标注 `@SaCheckPermission` 的接口在登录基础上还必须具备对应权限码。
- `/files/**` 静态资源路径不经过 Sa-Token 登录校验。
- 部分接口虽然没有细粒度权限码，但仍受全局登录拦截器保护。

文档中的鉴权标识：

| 标识 | 含义 |
|---|---|
| 公开 | `@SaIgnore`，不要求登录 |
| 登录 | 必须登录，无额外权限注解 |
| 权限：`xxx` | 必须登录且拥有指定权限 |
| 权限：`a` 或 `b` | 满足任一权限即可 |

### 2.4 统一响应

普通接口统一返回：

```json
{
  "code": 200,
  "msg": null,
  "data": {}
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `code` | integer | 业务响应码，成功默认为 200 |
| `msg` | string/null | 提示或错误信息 |
| `data` | any/null | 业务数据 |

常见响应码：

| 响应码 | 说明 |
|---|---|
| `200` | 成功 |
| `400` | 参数或业务校验失败 |
| `403` | 未登录、Token 无效或权限不足 |
| `500` | 未指定业务码的错误或服务器异常 |
| `503` | 空指针等被转换为“服务器繁忙”的异常 |
| `555` | 重复请求、订单未查询到等项目自定义业务异常 |

分页数据结构：

```json
{
  "code": 200,
  "data": {
    "pageNum": 1,
    "pageSize": 10,
    "totalSize": 100,
    "totalPages": 10,
    "content": [],
    "map": null
  }
}
```

### 2.5 分页请求

所有继承 `BasisVO` 的查询模型均支持：

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| `pageNum` | integer | 否 | 1 | 当前页，从 1 开始 |
| `pageSize` | integer | 否 | 10 | 每页数量 |

订单分页接口额外要求 `pageNum`、`pageSize` 均大于 0。

## 3. 接口总览

### 3.1 用户管理 `/system/sys-user`

| 方法 | 路径 | 鉴权 | 请求 | 成功数据 | 关键规则 |
|---|---|---|---|---|---|
| POST | `/system/sys-user/doLogin` | 公开 | `LoginVO` | `tokenInfo`、`roleIds`、`museumId` | 7 秒内防重复；校验用户、密码、状态 |
| POST | `/system/sys-user/addUser` | 权限：`sys:user:add` | `SysUser` | 空 | 新增或更新用户；`roleIds` 不能为空 |
| GET | `/system/sys-user/updateUserStatus?id=&status=` | 权限：`sys:user:delete` | Query | 空 | 更新状态后强制该用户退出登录 |
| POST | `/system/sys-user/findPage` | 权限：`sys:user:search` | `UserVO` | `PageResult<SysUser>` | 支持用户名模糊、博物馆精确查询 |
| GET | `/system/sys-user/findUserRoles/{id}` | 权限：`sys:user:search` | Path | `SysUserRole[]` | 返回角色 ID 和角色名称 |

登录请求示例：

```json
{
  "username": "admin",
  "password": "******",
  "loginType": "PC"
}
```

登录成功数据示例：

```json
{
  "tokenInfo": {
    "tokenName": "sa-token",
    "tokenValue": "token-value",
    "loginId": "admin"
  },
  "roleIds": [1],
  "museumId": "1"
}
```

### 3.2 角色管理 `/system/sys-role`

| 方法 | 路径 | 鉴权 | 请求 | 成功数据 | 关键规则 |
|---|---|---|---|---|---|
| POST | `/system/sys-role/save` | 权限：`sys:role:add` 或 `sys:role:edit` | `SysRole` | 角色 ID | 新增时角色名称不能重复 |
| POST | `/system/sys-role/delete` | 权限：`sys:role:delete` | `Long[]` | 空 | 角色仍有用户时拒绝删除 |
| POST | `/system/sys-role/deleteUserRole` | 权限：`sys:role:delete` | `Long[]` | 空 | 清理指定角色的用户关系和菜单关系 |
| POST | `/system/sys-role/findPage` | 权限：`sys:role:search` | `RoleVO` | `PageResult<SysRole>` | 按角色名称筛选 |
| GET | `/system/sys-role/findAll` | 权限：`sys:role:search` | 无 | `SysRole[]` | 查询全部角色 |
| GET | `/system/sys-role/findRoleMenus/{roleId}` | 权限：`sys:role:search` | Path | `SysMenu[]` | 查询角色拥有的菜单 |
| POST | `/system/sys-role/saveRoleMenus` | 权限：`sys:role:permission` | `SysRoleMenu[]` | 空 | 保存角色与菜单的关系 |
| GET | `/system/sys-role/updateUserRole?userId=&roleId=` | 权限：`sys:role:permission` | Query | 空 | 更新单个用户角色 |

### 3.3 菜单管理 `/system/sys-menu`

| 方法 | 路径 | 鉴权 | 请求 | 成功数据 | 关键规则 |
|---|---|---|---|---|---|
| POST | `/system/sys-menu/save` | 权限：`sys:menu:add` 或 `sys:menu:edit` | `SysMenu` | 空 | 新增或更新菜单 |
| POST | `/system/sys-menu/delete` | 权限：`sys:menu:delete` | `Long[]` | 空 | 有子菜单时禁止删除；同时删除角色菜单关系 |
| GET | `/system/sys-menu/findNavTree/{name}` | 权限：`sys:menu:search` | Path | `SysMenu[]` | 按用户名查询可访问导航树 |
| GET | `/system/sys-menu/findMenuTree` | 权限：`sys:menu:search` | 无 | `SysMenu[]` | 查询完整菜单树 |

### 3.4 博物馆管理 `/system/museum`

| 方法 | 路径 | 鉴权 | 请求 | 成功数据 | 关键规则 |
|---|---|---|---|---|---|
| POST | `/system/museum/save` | 权限：`sys:museum:add` 或 `sys:museum:edit` | `Museum` | 博物馆 ID | 新增时名称、银联商户号 `mid` 必填；名称和 `mid` 不能重复 |
| POST | `/system/museum/disableFeature` | 权限：`sys:museum:edit` | `Long[]` | 空 | 逻辑禁用，将 `status` 置 0 |
| POST | `/system/museum/findPage` | 权限：`sys:museum:search` | `MuseumVO` | `PageResult<Museum>` | 只返回启用状态博物馆 |
| GET | `/system/museum/findAll` | 公开（临时） | 无 | `Museum[]` | 代码注释要求正式上线恢复权限校验 |

### 3.5 文件管理 `/system/sys-file`

| 方法 | 路径 | 鉴权 | 请求 | 成功数据 | 关键规则 |
|---|---|---|---|---|---|
| POST | `/system/sys-file/upload` | 登录 | `multipart/form-data`：`file` | 文件访问 URL | 文件保存到日期目录；数据库记录原名、UUID 文件名、大小和类型 |
| GET | `/system/sys-file/access/{id}` | 公开 | Path | 文件二进制流 | 校验数据库路径必须位于配置的上传根目录内 |

上传示例：

```bash
curl -X POST \
  -H "sa-token: <token>" \
  -F "file=@example.pdf" \
  http://localhost:8020/system/sys-file/upload
```

文件类型枚举：1 图片、2 文档、3 视频、4 音频、5 其他。

### 3.6 系统操作日志 `/system/sys-log`

| 方法 | 路径 | 鉴权 | 请求 | 成功数据 | 关键规则 |
|---|---|---|---|---|---|
| POST | `/system/sys-log/findPage` | 权限：`sys:log:search` | `SysLogVO` | `PageResult<SysLog>` | 按用户名、操作名称筛选 |
| GET | `/system/sys-log/listUserNames` | 权限：`sys:log:search` | 无 | `String[]` | 返回去重用户名 |
| GET | `/system/sys-log/listOperations` | 权限：`sys:log:search` | 无 | `String[]` | 返回去重操作名称 |

### 3.7 活动类型 `/trip/activity-type`

| 方法 | 路径 | 鉴权 | 请求 | 成功数据 | 关键规则 |
|---|---|---|---|---|---|
| POST | `/trip/activity-type/save` | 权限：`active:type:add` 或 `active:type:edit` | `ActivityType` | 类型 ID | 同一博物馆内类型名称唯一；新增默认启用 |
| POST | `/trip/activity-type/delete` | 权限：`active:type:delete` | `Long[]` | 空 | 逻辑删除 |
| POST | `/trip/activity-type/findPage` | 权限：`active:type:search` | `ActivityTypeVO` | `PageResult<ActivityType>` | 名称模糊、状态和博物馆精确筛选 |
| GET | `/trip/activity-type/findAll` | 权限：`active:type:search` | 无 | `ActivityType[]` | 返回全部启用且未删除类型 |
| GET | `/trip/activity-type/findAll/{museumId}` | 公开 | Path | `ActivityType[]` | 返回指定博物馆启用类型 |

### 3.8 活动标签 `/trip/activity-tag`

| 方法 | 路径 | 鉴权 | 请求 | 成功数据 | 关键规则 |
|---|---|---|---|---|---|
| POST | `/trip/activity-tag/save` | 权限：`active:tag:add` 或 `active:tag:edit` | `ActivityTag` | 标签 ID | 新增默认启用、未删除 |
| POST | `/trip/activity-tag/delete` | 权限：`active:tag:delete` | `Long[]` | 空 | 逻辑删除 |
| POST | `/trip/activity-tag/findPage` | 权限：`active:tag:search` | `ActivityTagVO` | `PageResult<ActivityTag>` | 名称模糊、状态和博物馆精确筛选 |
| GET | `/trip/activity-tag/findAll` | 权限：`active:tag:search` | 无 | `ActivityTag[]` | 返回全部启用且未删除标签 |
| GET | `/trip/activity-tag/findAll/{museumId}` | 公开 | Path | `ActivityTag[]` | 返回指定博物馆启用标签 |

### 3.9 活动管理 `/trip/activity-manage`

| 方法 | 路径 | 鉴权 | 请求 | 成功数据 | 关键规则 |
|---|---|---|---|---|---|
| POST | `/trip/activity-manage/save` | 权限：`active:person:add` 或 `active:person:edit` | `ActivityManage` | 活动 ID | 同时保存场次；新增默认禁用 |
| POST | `/trip/activity-manage/delete` | 权限：`active:person:delete` | `Long[]` | 空 | 逻辑删除活动并禁用其场次 |
| POST | `/trip/activity-manage/updateStatus` | 权限：`active:person:release` | `ActivityManageStatusVO` | 空 | `status` 仅允许 0 或 1 |
| POST | `/trip/activity-manage/findPage` | 权限：`active:person:search` | `ActivityManageVO` | `PageResult<ActivityManage>` | 支持名称、类型、热门、博物馆、状态、参与形式、标签、特价筛选 |
| GET | `/trip/activity-manage/findById/{id}` | 公开 | Path | `ActivityManage` | 返回未删除活动和启用场次 |
| GET | `/trip/activity-manage/findByMuseumId` | 公开 | Query | `ActivityManage[]` | 小程序活动列表，可返回指定日期场次已预约人数 |

`findByMuseumId` Query 参数：

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `museumId` | long | 是 | 博物馆 ID |
| `participationType` | integer | 否 | 1 团队，2 个人 |
| `activityTypeId` | long | 否 | 活动类型 ID |
| `appointmentDate` | date | 否 | `yyyy-MM-dd`；传入后回填每场 `bookedCount` |
| `isSpecialPrice` | integer | 否 | 1 查询特价；不传或 0 查询非特价 |

活动保存核心校验：

- 活动名称、类型、单价、博物馆、地点、起止日期、适用人群、报名须知、联系方式、参与形式、年龄分类和场次必填。
- 单价不能小于 0，开始日期不能晚于结束日期。
- 参与形式仅允许 1 团队、2 个人。
- 年龄分类仅允许 1 成人、2 儿童。
- 场次开始时间必须早于结束时间，容量必须大于 0。
- 更新时活动类型、价格、博物馆、活动日期等历史关键字段不可修改；应禁用旧活动后新建。

活动保存示例：

```json
{
  "activityName": "博物馆研学课程",
  "activityTypeId": 1,
  "isHot": 1,
  "price": 5000,
  "museumId": 1,
  "coverUrl": "/system/sys-file/access/10",
  "imageUrl": "/system/sys-file/access/11",
  "applicablePeople": "8-14岁学生",
  "activityLocation": "一层研学教室",
  "activityStartDate": "2026-07-20",
  "activityEndDate": "2026-08-31",
  "registrationNotice": "请提前到场",
  "contactNumber": "13800000000",
  "participationType": 2,
  "ageGroup": 2,
  "tagIds": "1,2",
  "isSpecialPrice": 0,
  "activityScheduleList": [
    {
      "startTime": "09:00:00",
      "endTime": "10:30:00",
      "scheduleNumber": 30,
      "status": 1
    }
  ]
}
```

### 3.10 活动文件 `/trip/activity-file`

| 方法 | 路径 | 鉴权 | 请求 | 成功数据 | 关键规则 |
|---|---|---|---|---|---|
| POST | `/trip/activity-file/save` | 登录 | `ActivityFile` | 文件记录 ID | 新增默认未删除 |
| POST | `/trip/activity-file/delete` | 登录 | `Long[]` | 空 | 逻辑删除 |
| POST | `/trip/activity-file/findPage` | 登录 | `ActivityFileVO` | `PageResult<ActivityFile>` | 按活动、博物馆、文件名、版本、主题、年龄段筛选 |

### 3.11 皮肤配置 `/trip/skin-management-config`

| 方法 | 路径 | 鉴权 | 请求 | 成功数据 | 关键规则 |
|---|---|---|---|---|---|
| POST | `/trip/skin-management-config/save` | 登录 | `SkinManagementConfig` | 配置 ID | 新增默认启用、未删除 |
| POST | `/trip/skin-management-config/delete` | 登录 | `Long[]` | 空 | 逻辑删除 |
| POST | `/trip/skin-management-config/findPage` | 登录 | `SkinManagementConfigVO` | `PageResult<SkinManagementConfig>` | 按博物馆、状态筛选 |
| GET | `/trip/skin-management-config/findById/{id}` | 登录 | Path | `SkinManagementConfig` | 按主键查询 |
| GET | `/trip/skin-management-config/findByMuseumId/{id}` | 公开 | Path | `SkinManagementConfig` | 查询指定博物馆启用皮肤 |

### 3.12 团队管理 `/trip/team`

| 方法 | 路径 | 鉴权 | 请求 | 成功数据 | 关键规则 |
|---|---|---|---|---|---|
| POST | `/trip/team/save` | 公开 | `Team` | 团队 ID | 同一微信 OpenID 只能有一个有效团队；可恢复已逻辑删除记录 |
| POST | `/trip/team/delete` | 公开 | `Long[]` | 空 | 逻辑删除 |
| POST | `/trip/team/findPage` | 权限：`order:team:search` | `TeamVO` | `PageResult<Team>` | 团队名、负责人、手机号模糊；OpenID 精确 |
| GET | `/trip/team/findByWechatOpenid/{wechatOpenid}` | 公开 | Path | `Team` | 返回最新一条未删除团队 |

### 3.13 游客管理 `/trip/visitor`

| 方法 | 路径 | 鉴权 | 请求 | 成功数据 | 关键规则 |
|---|---|---|---|---|---|
| POST | `/trip/visitor/save` | 公开 | `Visitor` | 游客 ID | 校验手机号、身份证、博物馆；自动补全省市、性别、年龄 |
| POST | `/trip/visitor/importExcel` | 公开 | Multipart：`file`、`teamId`、可选 `batchNo` | 导入数量 | 表头必须且只能为姓名、手机号、身份证号 |
| GET | `/trip/visitor/downloadTemplate` | 公开 | 无 | `.xlsx` 文件 | 下载空白导入模板 |
| POST | `/trip/visitor/delete` | 登录 | `Long[]` | 空 | 逻辑删除 |
| POST | `/trip/visitor/findPage` | 登录 | `VisitorVO` | `PageResult<Visitor>` | 多条件组合查询 |
| GET | `/trip/visitor/findByTeamId?teamId=&batchNo=` | 公开 | Query | `Visitor[]` | 按团队和可选批次查询 |
| GET | `/trip/visitor/findByWechatOpenid/{wechatOpenid}` | 公开 | Path | `Visitor` | 返回最新一条未删除游客 |

游客保存规则：

- 手机号必填，格式必须为 `1[3-9]` 开头的 11 位数字。
- 身份证可为空；非空时校验 15/18 位格式、出生日期、地址码和 18 位校验码。
- `museumId` 必须存在。
- 同一 `wechatOpenid` 已有有效游客时更新原记录，不新增重复记录。
- 已逻辑删除的相同 OpenID 记录会被恢复。
- 身份证存在时优先从身份证推导省市、性别、年龄；否则按手机号段推导省市，性别为未知。

游客 Excel 导入示例：

```bash
curl -X POST \
  -F "file=@游客名单.xlsx" \
  -F "teamId=100" \
  -F "batchNo=202607-A" \
  http://localhost:8020/trip/visitor/importExcel
```

导入采用“全部校验通过后才批量入库”的方式；如果有错误，`msg` 会包含所有错误行号。

### 3.14 评价管理 `/trip/evaluation`

| 方法 | 路径 | 鉴权 | 请求 | 成功数据 | 关键规则 |
|---|---|---|---|---|---|
| POST | `/trip/evaluation/save` | 公开 | `Evaluation` | 评价 ID | `orderId` 必填；每个订单只允许一条未删除评价 |
| POST | `/trip/evaluation/delete` | 公开 | `Long[]` | 空 | 逻辑删除 |
| POST | `/trip/evaluation/findPage` | 登录 | `EvaluationVO` | `PageResult<Evaluation>` | 可按订单、博物馆、活动名、评分、提交时间筛选 |
| GET | `/trip/evaluation/findByOrderId/{orderId}` | 公开 | Path | `Evaluation` | 返回最新未删除评价 |

### 3.15 订单管理 `/trip/order`

| 方法 | 路径 | 鉴权 | 请求 | 成功数据 | 关键规则 |
|---|---|---|---|---|---|
| POST | `/trip/order/add` | 公开 | `AppointmentVO` | `mini_program_pay_params`、`order_no` | 分布式锁保护场次容量；请求银联支付参数 |
| POST | `/trip/order/pay/query` | 公开 | `OrderPayQueryVO` | 支付确认结果 Map | 以后端主动查询银联结果为准 |
| GET | `/trip/order/refund` | 权限：`order:user:refund` | `orderDetailId`、`refundReason` | 空 | 单个子订单退款；7 秒防重复 |
| GET | `/trip/order/refundAll` | 权限：`order:user:fullRefund` | `orderNo`、`refundReason` | 空 | 主订单全额退款；7 秒防重复 |
| GET | `/trip/order/refundQuery/{id}` | 权限：`order:user:detail` | Path | 退款状态提示 | 主动查询银联退款状态并修正本地状态 |
| GET | `/trip/order/abandon?orderNo=` | 公开 | Query | 空 | 先查询/关闭银联订单，再放弃本地待支付订单 |
| POST | `/trip/order/verification` | 权限：`order:user:verification` | `VerificationVO` | 空 | 校验博物馆、预约日期、支付和退款状态 |
| POST | `/trip/order/findPage` | 公开 | `OrderVO` | `PageResult<Order>` | `openId`、`teamId` 至少一个 |
| POST | `/trip/order/findAdminPage` | 权限：`order:user:search` | `OrderVO` | `PageResult<Order>` | 管理端全部订单分页 |
| GET | `/trip/order/findByOrderNo/{orderNo}` | 公开 | Path | `Order` | 返回主订单、子订单及关联对象 |
| POST | `/trip/order/unionPayNotify` | 公开 | 银联表单参数 | 字符串 `SUCCESS`/`FAILED` | 验签后处理支付或退款回调 |

#### 3.15.1 下单请求

```json
{
  "money": 10000,
  "openId": "wx-openid",
  "museumId": 1,
  "visitorId": 10,
  "teamId": null,
  "batchNo": null,
  "appointmentDate": "2026-07-20",
  "list": [
    {
      "activityManageId": 100,
      "activityScheduleId": 1001,
      "num": 2
    }
  ]
}
```

下单规则：

- `visitorId` 和 `teamId` 至少一个。
- 团队下单必须传 `batchNo`；个人下单禁止传 `batchNo`。
- `openId`、`museumId`、`appointmentDate`、`money`、`list` 必填。
- 预约日期不能早于当天。
- 同一个活动不能在 `list` 中重复，应通过 `num` 表示数量。
- 游客或团队必须存在且未删除。
- 同一游客或同一团队批次不能同时存在待支付订单。
- 博物馆必须启用，并完整配置 `mid` 和 `tid`。
- 活动必须启用、未删除、属于当前博物馆、日期有效、场次有效。
- `money` 必须等于数据库活动单价乘数量的合计，不能以前端价格为准。
- 同一博物馆、预约日期、活动和场次使用 Redis 分布式锁串行校验容量。
- 待支付、支付成功和退款中订单继续占用名额；放弃支付和退款成功释放对应名额。
- 一个购买数量会拆成多个子订单，便于逐个退款和统计。
- 成功下单后 Redis 中保存 15 分钟待支付 Key。

下单成功数据：

```json
{
  "mini_program_pay_params": "{\"timeStamp\":\"...\"}",
  "order_no": "11TW..."
}
```

#### 3.15.2 主动确认支付

请求：

```json
{
  "orderNo": "11TW..."
}
```

返回数据通常包含：

| 字段 | 类型 | 说明 |
|---|---|---|
| `order_no` | string | 本地订单号 |
| `order_status` | integer | 当前本地订单状态 |
| `paySuccess` | boolean | 是否已确认支付成功 |
| `unionPayStatus` | string/null | 银联交易状态 |
| `msg` | string | 当前结果说明 |

#### 3.15.3 核销请求

```json
{
  "orderNo": "11TW...",
  "museumId": "1"
}
```

核销必须满足：

- 博物馆存在且启用，订单属于当前博物馆。
- 预约日期必须等于当天。
- 主订单已支付成功、未退款、未处于退款中、未放弃、未核销。
- 子订单数量与主订单数量一致，且全部为支付成功状态。

#### 3.15.4 银联回调

重要表单字段：

| 字段 | 说明 |
|---|---|
| `status` | `TRADE_SUCCESS` 支付成功；`TRADE_REFUND` 退款成功 |
| `merOrderId` | 本地系统订单号 |
| `targetOrderId` | 银联订单号 |
| `totalAmount` | 支付金额，单位分 |
| `refundOrderId` | 退款业务号 |
| `refundAmount` | 退款金额，单位分 |
| `mid` / `tid` | 商户号、终端号 |
| `payTime` | 支付成功时间 |
| `refundPayTime` | 退款完成时间 |
| `sign` | 回调签名 |

回调先验签。处理成功返回纯文本 `SUCCESS`，失败返回 `FAILED`。银联侧应以该字符串判断是否需要重试。

### 3.16 微信小程序 `/trip/wechat-mini-program`

| 方法 | 路径 | 鉴权 | 请求 | 成功数据 | 关键规则 |
|---|---|---|---|---|---|
| GET | `/trip/wechat-mini-program/getOpenid?code=` | 公开 | Query | OpenID 字符串 | 使用微信临时登录凭证调用微信接口 |

### 3.17 银联流水与对账 `/trip/union-pay-data`

| 方法 | 路径 | 鉴权 | 请求 | 成功数据 | 状态 |
|---|---|---|---|---|---|
| POST | `/trip/union-pay-data/uploadUnionPay` | 登录 | Multipart：`file` | 空 | 有效 |
| POST | `/trip/union-pay-data/reconciliation/abnormal` | 公开（临时） | `ReconciliationAbnormalQueryVO` | `ReconciliationAbnormalResult` | 推荐 |
| POST | `/trip/union-pay-data/billing` | 登录 | `ReconciliationVO` | `Map[]` | 已废弃 |
| POST | `/trip/union-pay-data/abnormalData` | 登录 | `ReconciliationVO` | Map | 已废弃 |
| GET | `/trip/union-pay-data/detail/{tradeNo}/{museumId}` | 登录 | Path | Map | 已废弃 |

银联 Excel 导入自动识别两种表头：

- 格式一：商户名称、商户号、终端号、消费日期、交易时间、卡号、金额、手续费、净额、参考号、交易类型、交易渠道、商户订单号、银商订单号。
- 格式二：清算日期、交易日期时间、卡号、商编、终端、参考号、交易类型、交易金额、手续费、交易方式、订单号、商户名称。

新对账请求：

```json
{
  "museumId": "1",
  "startDate": "2026-07-01",
  "endDate": "2026-07-31"
}
```

核心返回：

| 字段 | 类型 | 说明 |
|---|---|---|
| `museumId` | string | 博物馆 ID |
| `startDate` / `endDate` | date | 核对区间，包含首尾日期 |
| `amountUnit` | string | 固定 `CENT` |
| `balance` | object | 系统核销、系统退款、银联消费、银联退款和最终差额 |
| `sourceControl` | object | 原始数据覆盖、未分类、重复分类计数 |
| `billing` | object | 有效核销账单及活动维度明细 |
| `groups` | array | 固定异常分组、异常类型和完整明细 |

`balance` 重要字段：

| 字段 | 说明 |
|---|---|
| `systemVerificationQuantity` | 系统有效核销子订单数量 |
| `systemVerificationAmount` | 系统有效核销金额 |
| `systemRefundCount` / `systemRefundAmount` | 系统核对期退款数量和金额 |
| `unionPayCount` / `unionPayAmount` | 银联消费数量和金额 |
| `unionRefundCount` / `unionRefundAmount` | 银联退款数量和金额 |
| `unionNetAmount` | 银联消费减退款 |
| `abnormalAdjustmentAmount` | 异常分类调整金额 |
| `adjustedUnionNetAmount` | 调整后银联净额 |
| `balanceDifference` | 系统有效核销金额减调整后银联净额 |
| `balanced` | 金额闭环且无未分类、重复处理时为 true |

异常明细同时包含：

- 系统主订单快照。
- 系统全部子订单与核对期退款明细。
- 银联区间内和历史消费/退款流水。
- 退款双方差额。
- 当前异常承担的调整金额及其他关联异常标签。

## 4. 请求与响应模型

### 4.1 系统模型

#### LoginVO

| 字段 | 类型 | 说明 |
|---|---|---|
| `username` | string | 用户名 |
| `password` | string | 明文登录密码，服务端按用户盐加密后比对 |
| `loginType` | string | Sa-Token 登录设备/类型 |

#### SysUser

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | long | 主键；为空新增，非空更新 |
| `username` | string | 用户名 |
| `password` | string | 新增时为明文；查询返回时当前实现可能包含密文 |
| `salt` | string | 加密盐，不应由前端维护 |
| `phone` | string | 手机号 |
| `wxOpenId` | string | 微信 OpenID |
| `museumId` | string | 所属博物馆 ID |
| `museumName` | string | 查询回填字段 |
| `status` | integer | 0 禁用，1 启用 |
| `roleIds` | long[] | 用户角色 ID 列表 |
| `createBy/createTime/updateBy/updateTime` | audit | 审计字段 |

#### Museum

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | long | 主键 |
| `name` | string | 博物馆名称 |
| `mid` | string | 银联商户号 |
| `tid` | string | 银联终端号 |
| `idCardVerifyEnabled` | integer | 1 启用身份证验证，0 禁用 |
| `status` | integer | 1 启用，0 禁用 |
| `createBy/createTime/updateBy/updateTime` | audit | 审计字段 |

#### SysRole / SysRoleMenu / SysUserRole

| 模型 | 主要字段 |
|---|---|
| `SysRole` | `id`、`name`、审计字段 |
| `SysRoleMenu` | `id`、`roleId`、`menuId`、审计字段 |
| `SysUserRole` | `id`、`userId`、`roleId`、查询回填 `roleName` |

#### SysMenu

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | long | 主键 |
| `name` | string | 菜单名称 |
| `parentId` | long | 父菜单 ID，顶级为 0 |
| `url` | string | 前端路由 |
| `perms` | string | 权限码，如 `sys:user:add` |
| `type` | integer | 0 目录，1 菜单，2 按钮 |
| `icon` | string | 图标 |

### 4.2 活动模型

#### ActivityManage

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | long | 主键 |
| `activityName` | string | 活动名称 |
| `activityTypeId` | long | 活动类型 ID |
| `isHot` | integer | 1 热门，0 非热门 |
| `price` | integer | 单价，分 |
| `museumId` | long | 博物馆 ID |
| `museumName` | string | 查询回填 |
| `coverUrl` | string | 封面 URL |
| `imageUrl` | string | 成图 URL |
| `applicablePeople` | string | 适用人群 |
| `activityLocation` | string | 地点 |
| `activityStartDate` / `activityEndDate` | date | 活动有效日期 |
| `registrationNotice` | string | 报名须知 |
| `status` | integer | 1 启用，0 禁用 |
| `isDeleted` | integer | 1 已删除，0 未删除 |
| `contactNumber` | string | 联系电话 |
| `participationType` | integer | 1 团队，2 个人 |
| `ageGroup` | integer | 1 成人，2 儿童 |
| `tagIds` | string | 英文逗号分隔的标签 ID |
| `isSpecialPrice` | integer | 1 特价，0 非特价 |
| `activityScheduleList` | array | 场次列表 |

#### ActivitySchedule

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | long | 场次 ID |
| `activityId` | long | 活动 ID |
| `startTime` / `endTime` | time | 场次时间 |
| `scheduleNumber` | integer | 场次容量 |
| `status` | integer | 1 启用，0 禁用 |
| `remark` | string | 备注 |
| `bookedCount` | integer | 指定预约日期的已预约人数，动态字段 |

#### ActivityType / ActivityTag

| 模型 | 主要字段 |
|---|---|
| `ActivityType` | `id`、`typeName`、`status`、`isDeleted`、`museumId`、`museumName` |
| `ActivityTag` | `id`、`tagName`、`status`、`isDeleted`、`museumId`、`museumName` |

#### ActivityFile

`id`、`activityId`、`museumId`、`fileName`、`fileUrl`、`version`、`theme`、`ageGroup`、`isDeleted` 和审计字段。

### 4.3 人员模型

#### Visitor

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | long | 主键 |
| `mobile` | string | 手机号 |
| `idCard` | string | 身份证号 |
| `name` | string | 姓名 |
| `wechatOpenid` | string | 微信 OpenID |
| `museumId` | long | 博物馆 ID |
| `province` / `city` | string | 自动推导的省市 |
| `gender` | integer | 1 男，0 女，2 未知 |
| `age` | integer | 从身份证计算 |
| `teamId` | long | 团队 ID |
| `batchNo` | string | 团队游客批次号 |
| `isDeleted` | integer | 逻辑删除标记 |
| `remark` | string | 备注 |

#### Team

`id`、`teamName`、`principalName`、`mobile`、`idCard`、`qualificationCertificateUrl`、`wechatOpenid`、`isDeleted`、`visitorList` 和审计字段。

### 4.4 订单模型

#### Order

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | long | 主键 |
| `orderNo` | string | 本地订单号 |
| `museumId` | long | 博物馆 ID |
| `orderType` | integer | 1 个人，2 团队 |
| `payAmount` | integer | 支付金额，分 |
| `orderQuantity` | integer | 子订单数量 |
| `orderStatus` | integer | 主订单状态 |
| `unionpayOrderNo` | string | 银联订单号 |
| `isUsed` | integer | 1 已核销，0 未核销 |
| `verificationTime` | datetime | 核销时间 |
| `refundAmount` | integer | 累计退款金额，分 |
| `refundQuantity` | integer | 累计退款子订单数 |
| `miniProgramPayParams` | string | 小程序支付参数 JSON 字符串 |
| `teamId` / `batchNo` | mixed | 团队订单关联信息 |
| `visitorId` | long | 个人订单游客 ID |
| `appointmentDate` | date | 预约日期 |
| `paySuccessTime` | datetime | 支付成功时间 |
| `museum` / `visitor` / `team` | object | 查询回填关联对象 |
| `detailList` | array | 子订单 |
| `isEvaluated` | integer | 1 已评价，0 未评价 |

#### OrderDetail

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | long | 子订单 ID |
| `orderId` / `orderNo` | mixed | 主订单关联 |
| `museumId` | long | 博物馆 ID |
| `activityId` | long | 活动 ID |
| `activityScheduleId` | long | 场次 ID |
| `orderAmount` | integer | 子订单金额，分 |
| `refundAmount` | integer | 退款金额，分 |
| `refundTime` | datetime | 退款成功时间 |
| `refundReason` | string | 管理员填写的退款原因 |
| `refundId` | string | 退款业务号 |
| `orderStatus` | integer | 子订单状态 |
| `museum` / `activityManage` / `activitySchedule` | object | 查询回填 |

### 4.5 评价模型

`Evaluation` 字段：

- `id`、`orderId`
- `favoritePart`、`improvementSuggestion`、`expectation`
- `overallScore`、`arrangementScore`、`attractionScore`、`knowledgeImmersionScore`
- 查询回填：`museumId`、`activityId`、`activityName`
- `isDeleted`、`createTime`、`updateTime`

### 4.6 查询 VO

| 模型 | 筛选字段 |
|---|---|
| `UserVO` | `museumId`、`username` |
| `RoleVO` | `name` |
| `MuseumVO` | `name` |
| `SysLogVO` | `userName`、`operation` |
| `ActivityTypeVO` | `typeName`、`status`、`museumId` |
| `ActivityTagVO` | `tagName`、`status`、`museumId` |
| `ActivityManageVO` | `activityName`、`activityTypeId`、`isHot`、`museumId`、`status`、`participationType`、`tagId`、`isSpecialPrice` |
| `ActivityFileVO` | `activityId`、`museumId`、`fileName`、`version`、`theme`、`ageGroup` |
| `SkinManagementConfigVO` | `museumId`、`status` |
| `TeamVO` | `teamName`、`principalName`、`mobile`、`wechatOpenid` |
| `VisitorVO` | `mobile`、`idCard`、`name`、`wechatOpenid`、`museumId`、`province`、`city`、`gender`、`teamId`、`batchNo` |
| `EvaluationVO` | `orderId`、`museumId`、`activityName`、`overallScore`、`createTimeStart`、`createTimeEnd` |
| `OrderVO` | `openId`、`orderNo`、`museumId`、`visitorId`、`teamId`、`batchNo`、`orderType`、`orderStatus`、`isUsed`、`appointmentDate`、`orderStartDate`、`orderEndDate` |

## 5. 状态枚举

### 5.1 通用状态

| 值 | 说明 |
|---|---|
| 0 | 否、禁用、未删除、未使用，具体含义取决于字段 |
| 1 | 是、启用、已删除、已使用，具体含义取决于字段 |

### 5.2 主订单状态

| 值 | 名称 | 说明 |
|---|---|---|
| 1 | PAYING | 支付中 |
| 10 | SUCCESS | 支付成功 |
| -1 | ABANDON | 放弃支付 |
| -2 | PARTIAL_REFUND | 部分退款 |
| -10 | ALL_REFUND | 全额退款 |
| -11 | REFUNDING | 退款中 |

### 5.3 子订单状态

| 值 | 名称 | 说明 |
|---|---|---|
| 0 | INIT | 初始、等待主订单支付 |
| 10 | PAY_SUCCESS | 支付成功 |
| -1 | ABANDON | 放弃支付 |
| -2 | REFUND | 退款成功 |
| -11 | REFUNDING | 退款中 |

### 5.4 银联状态

| 状态 | 说明 |
|---|---|
| `TRADE_SUCCESS` | 支付成功 |
| `WAIT_BUYER_PAY` | 等待支付 |
| `TRADE_CLOSED` | 交易关闭 |
| `NEW_ORDER` | 新订单 |
| `UNKNOWN` | 状态不明确 |
| `TRADE_REFUND` | 退款回调 |
| `SUCCESS` | 退款成功或接口调用成功 |
| `PROCESSING` | 退款处理中 |
| `FAIL` | 退款失败 |

## 6. 异常与边界行为

- 请求 JSON 无法解析时返回 400，消息为“请求体格式错误，请检查JSON格式”。
- 未登录、Token 无效或权限不足由全局异常处理统一转换为 `HttpResult`。
- 业务 Service 多使用 `msg` Map 返回校验失败，Controller 再转换为错误响应。
- 文件下载和模板下载返回二进制，不使用 `HttpResult`。
- 银联回调返回纯文本，不使用 `HttpResult`。
- `@AvoidRepeatRequest` 使用 Session ID 与请求 URI 作为 Redis Key；登录、单笔退款和全额退款在 7 秒窗口内禁止重复请求。

## 7. 已废弃、临时与兼容说明

- `/trip/union-pay-data/billing` 已废弃，改用新对账接口返回的 `billing`。
- `/trip/union-pay-data/abnormalData` 已废弃，改用 `/reconciliation/abnormal`。
- `/trip/union-pay-data/detail/{tradeNo}/{museumId}` 已废弃，新对账接口已直接返回系统和银联完整快照。
- `/system/museum/findAll` 当前临时公开。
- `/trip/union-pay-data/reconciliation/abnormal` 当前临时公开。
- `SysExceptionController`、`ActivityScheduleController`、`OrderDetailController` 当前没有业务方法。

## 8. Swagger

开发环境默认开启 Swagger，生产环境默认关闭。

- Swagger UI：`/swagger-ui.html`
- Bootstrap UI：`/doc.html`
- OpenAPI JSON：`/v2/api-docs`

Swagger 仅作为运行时辅助；本文档以当前代码实际行为为准。
