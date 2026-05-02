# FamilyTree 族谱功能设计文档

> 2026-05-02 | 状态：设计完成，待实现

## 1. 概述

在 Fireworks 中新增族谱（FamilyTree）功能。支持多族谱共存，树状展示家族成员关系。成员以男性为主线分支，女性节点不展开，配偶平行依附于男性节点。数据存储在后端 SQLite，前端使用 Vue 3 + G6 进行树状渲染。

## 2. 数据模型

### 2.1 family_trees（族谱实体）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | INTEGER | 主键自增 |
| name | TEXT NOT NULL | 族谱名称 |
| description | TEXT | 族谱描述 |
| created_at | TIMESTAMP | 创建时间 |
| updated_at | TIMESTAMP | 更新时间 |

### 2.2 family_persons（家庭成员）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | INTEGER | 主键自增 |
| family_tree_id | INTEGER NOT NULL | 所属族谱 |
| name | TEXT NOT NULL | 姓名 |
| gender | TEXT NOT NULL | male / female |
| birth_date | TEXT | 出生日期 |
| death_date | TEXT | 逝世日期 |
| biography | TEXT | 生平简介 |
| father_id | INTEGER | 父亲 ID，核心分支链 |
| generation_order | INTEGER | 世代（相对始祖） |
| sort_order | INTEGER | 同辈排行顺序 |
| created_at | TIMESTAMP | 创建时间 |
| updated_at | TIMESTAMP | 更新时间 |

外键：`family_tree_id` → `family_trees(id)`，`father_id` → `family_persons(id)`

### 2.3 person_spouses（配偶关系）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | INTEGER | 主键自增 |
| husband_id | INTEGER NOT NULL | 丈夫 ID |
| wife_id | INTEGER NOT NULL | 妻子 ID |
| marriage_order | INTEGER | 婚姻次序 |
| is_primary | INTEGER | 1=原配（展示），0=不展示 |
| created_at | TIMESTAMP | 创建时间 |

外键：`husband_id` → `family_persons(id)`，`wife_id` → `family_persons(id)`
唯一约束：`(husband_id, wife_id)`

### 2.4 family_lineages（分支索引表）

| 字段 | 类型 | 说明 |
|------|------|------|
| root_id | INTEGER NOT NULL | 分支始祖 ID |
| person_id | INTEGER NOT NULL | 成员 ID |
| depth | INTEGER | 相对始祖的深度 |

唯一约束：`(root_id, person_id)`

辅助按分支+世代分页查询，避免递归 CTE 在大数据量下性能问题。在成员新增/删除/变更 father_id 时同步维护。

## 3. 关键规则

- 根节点：`family_tree_id = ? AND father_id IS NULL AND gender = 'male'`
- 展开节点时，只查直接子节点（`father_id = ?`）
- 前端女性节点不显示展开按钮
- 每个男性节点展示 `is_primary=1` 的配偶（平行排列，虚线连接）
- 存储多个配偶但只展示原配
- 自动将没有 fatherId 的男性作为族谱根节点

## 4. API 设计

所有接口前缀 `/api/family-trees`，遵循现有 REST 风格和 AppException 错误处理模式。

### 4.1 族谱 CRUD

- `GET /api/family-trees` — 列出所有族谱
- `POST /api/family-trees` — 创建族谱 `{ name, description }`
- `GET /api/family-trees/{id}` — 族谱详情
- `PUT /api/family-trees/{id}` — 更新族谱
- `DELETE /api/family-trees/{id}` — 删除族谱（级联删成员、配偶、分支索引）

### 4.2 成员 CRUD

- `GET /api/family-trees/{treeId}/persons` — 查成员列表，支持 params: `search`, `rootId`, `depth`, `page`, `size`
- `POST /api/family-trees/{treeId}/persons` — 添加成员
- `GET /api/family-trees/{treeId}/persons/{id}` — 成员详情（含配偶列表）
- `PUT /api/family-trees/{treeId}/persons/{id}` — 编辑成员
- `DELETE /api/family-trees/{treeId}/persons/{id}` — 删除成员

### 4.3 树结构查询

- `GET /api/family-trees/{treeId}/roots` — 取根节点列表
- `GET /api/family-trees/{treeId}/persons/{id}/children` — 取子节点（含配偶），返回结构：
  ```json
  {
    "person": { "...", "spouse": { "name", "id" } | null },
    "children": [ { "id", "name", "gender", "sortOrder", "hasChildren" } ]
  }
  ```
- `GET /api/family-trees/{treeId}/persons/{id}/ancestors` — 取祖先链（递归 CTE）
- `GET /api/family-trees/{treeId}/persons/{id}/spouses` — 取配偶列表

### 4.4 配偶关系

- `POST /api/family-trees/{treeId}/persons/{husbandId}/spouses` — 添加配偶 `{ wifeId, isPrimary }`
- `DELETE /api/family-trees/{treeId}/persons/{husbandId}/spouses/{spouseId}` — 删除配偶关系

### 4.5 错误码

新增：`FAMILY_TREE_NOT_FOUND`、`FAMILY_PERSON_NOT_FOUND`（已存在）。

## 5. 前端设计

### 5.1 路由

```
/family-trees                    → FamilyTreeListView   族谱列表
/family-trees/:id                → FamilyTreeView       树状展示（核心页面）
/family-trees/:id/person/:personId → PersonEditView    成员编辑页
```

### 5.2 组件树

```
FamilyTreeListView — 族谱卡片网格 + 新建族谱对话框

FamilyTreeView — 左右分栏 layout
├── SearchBar               顶部搜索
├── TreeCanvas              树状画布（核心）
│   ├── TreeNode            节点卡片（姓名/生卒/配偶名）
│   └── 连线（@antv/g6）
├── PersonDrawer            右侧抽屉（新增/编辑表单）
└── SpousePanel             配偶管理面板

PersonEditView — 独立成员详情编辑页
```

### 5.3 树渲染策略

- 使用 `@antv/g6` TreeGraph，缩进树布局
- 初始只渲染根节点
- 点击男性节点 → `fetchChildren()` → 追加子节点
- 女性节点无展开按钮
- 配偶渲染为男性节点右侧的附属节点（虚线连接）

### 5.4 状态管理

```
stores/familyTree.ts
├── treeList: FamilyTree[]
├── currentTree: FamilyTree | null
├── personMap: Map<id, Person>   已加载节点缓存
├── fetchTrees / createTree / deleteTree
├── fetchRoots / fetchChildren
├── savePerson / deletePerson
└── addSpouse / removeSpouse
```

### 5.5 API 层

`api/familyTree.ts` — 沿用现有 fetch wrapper 模式，`credentials: 'include'`。

## 6. 后端架构

遵循现有三层分层：

```
domain/family/
├── FamilyTree.kt              族谱实体
├── FamilyTreeRepository.kt    族谱仓储接口
├── FamilyPerson.kt            成员实体（改造现有）
├── FamilyPersonRepository.kt  成员仓储接口（改造现有）
├── PersonSpouse.kt            配偶关系实体
├── PersonSpouseRepository.kt  配偶仓储接口
├── FamilyLineage.kt           分支索引实体
└── FamilyLineageRepository.kt 分支索引仓储接口

infra/family/
├── FamilyTreeDO.kt            表映射 + DDL
├── FamilyTreeRepositoryImpl.kt
├── FamilyPersonDO.kt          改造
├── FamilyPersonRepositoryImpl.kt  改造
├── PersonSpouseDO.kt
├── PersonSpouseRepositoryImpl.kt
├── FamilyLineageDO.kt
└── FamilyLineageRepositoryImpl.kt

app/family/
├── FamilyTreeController.kt    REST 控制器
└── FamilyTreeService.kt       业务逻辑
```

## 7. 测试要点

### 后端
- 族谱 CRUD 正确性
- 成员 CRUD + 父子关系约束
- 配偶关系管理（原配/多配偶）
- 分支搜索分页
- 级联删除完整性
- 递归 CTE 祖先查询

### 前端
- 树展开/收起交互
- 女性节点不展开
- 配偶平行展示
- 搜索跳转编辑
- 移动端响应式
