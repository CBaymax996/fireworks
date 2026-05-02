# FamilyTree 族谱功能实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 Fireworks 中实现完整族谱功能——支持多族谱共存、树状展示、成员增删改查、配偶管理。

**Architecture:** 后端遵循现有三层分层（domain/infra/app），使用 Ktorm + SQLite 存储 4 张表（family_trees, family_persons, person_spouses, family_lineages）；前端 Vue 3 + Element Plus + AntV G6 TreeGraph 渲染缩进树。

**Tech Stack:** Spring Boot 4.0.6, Kotlin 2.3.20, Ktorm, SQLite, Vue 3, TypeScript, Element Plus, @antv/g6, Pinia

---

## 文件结构

```
后端 (fireworks-server):
  domain/family/
    FamilyTree.kt              — 族谱实体 data class
    FamilyTreeRepository.kt    — 族谱仓储接口
    FamilyPerson.kt            — 成员实体（改造：移除 motherId/spouseId，新增 familyTreeId/sortOrder）
    FamilyPersonRepository.kt  — 成员仓储接口（改造：新增 tree-scoped 方法）
    PersonSpouse.kt            — 配偶关系实体
    PersonSpouseRepository.kt  — 配偶仓储接口
    FamilyLineage.kt           — 分支索引实体
    FamilyLineageRepository.kt — 分支索引仓储接口
  infra/family/
    FamilyTreeDO.kt            — family_trees 表映射 + DDL
    FamilyTreeRepositoryImpl.kt
    FamilyPersonDO.kt          — 改造：更新列定义和 DDL
    FamilyPersonRepositoryImpl.kt — 改造：同步字段变更 + 新增查询方法
    PersonSpouseDO.kt
    PersonSpouseRepositoryImpl.kt
    FamilyLineageDO.kt
    FamilyLineageRepositoryImpl.kt
  app/family/
    FamilyTreeController.kt    — REST 控制器（11 个端点）
    FamilyTreeService.kt       — 业务逻辑 + lineage 同步
  app/ErrorCode.kt             — 新增 FAMILY_TREE_NOT_FOUND
  infra/config/SchemaInitializer.kt — 注册新 DDL

前端 (fireworks-web):
  src/api/familyTree.ts        — fetch 封装
  src/stores/familyTree.ts     — Pinia store
  src/views/FamilyTreeListView.vue — 族谱列表页
  src/views/FamilyTreeView.vue     — 树状展示页（核心）
  src/views/PersonEditView.vue     — 成员编辑页
  src/router/index.ts          — 新增 3 条路由
  src/App.vue                  — 导航栏新增"族谱"入口
```

---

### Task 1: 改造 FamilyPerson 域对象和仓储

**说明：** 在现有 FamilyPerson 基础上改造字段，同时更新 DO、Repository 接口和实现。这是后续所有任务的基础——后续的 PersonSpouse、FamilyLineage 都依赖 FamilyPerson 的 id。

**Files:**
- Modify: `fireworks-server/src/main/kotlin/site/hanabii/fireworks/domain/family/FamilyPerson.kt`
- Modify: `fireworks-server/src/main/kotlin/site/hanabii/fireworks/domain/family/FamilyPersonRepository.kt`
- Modify: `fireworks-server/src/main/kotlin/site/hanabii/fireworks/infra/family/FamilyPersonDO.kt`
- Modify: `fireworks-server/src/main/kotlin/site/hanabii/fireworks/infra/family/FamilyPersonRepositoryImpl.kt`

- [ ] **Step 1: 更新 FamilyPerson 实体**

将 `FamilyPerson.kt` 替换为以下内容——移除 `motherId` 和 `spouseId`，新增 `familyTreeId` 和 `sortOrder`：

```kotlin
package site.hanabii.fireworks.domain.family

import java.time.Instant

data class FamilyPerson(
    val id: Long? = null,
    val familyTreeId: Long,
    val name: String,
    val gender: String = "unknown",
    val birthDate: String? = null,
    val deathDate: String? = null,
    val biography: String? = null,
    val fatherId: Long? = null,
    val generationOrder: Int = 0,
    val sortOrder: Int = 0,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
) {
    init {
        require(name.isNotBlank()) { "name must not be blank" }
        require(gender in setOf("male", "female", "unknown")) {
            "gender must be male, female or unknown, got $gender"
        }
    }
}
```

- [ ] **Step 2: 更新 FamilyPersonRepository 接口**

将 `FamilyPersonRepository.kt` 替换为：

```kotlin
package site.hanabii.fireworks.domain.family

interface FamilyPersonRepository {
    fun save(person: FamilyPerson): FamilyPerson
    fun findById(id: Long): FamilyPerson?
    fun findByTreeId(treeId: Long): List<FamilyPerson>
    fun findByFatherId(fatherId: Long): List<FamilyPerson>
    fun countByTreeId(treeId: Long): Long
    fun deleteById(id: Long): Boolean
    fun deleteByTreeId(treeId: Long)
}
```

- [ ] **Step 3: 更新 FamilyPersonDO 表映射和 DDL**

将 `FamilyPersonDO.kt` 替换为：

```kotlin
package site.hanabii.fireworks.infra.family

import org.ktorm.dsl.QueryRowSet
import org.ktorm.schema.Table
import org.ktorm.schema.int
import org.ktorm.schema.long
import org.ktorm.schema.timestamp
import org.ktorm.schema.varchar
import site.hanabii.fireworks.domain.family.FamilyPerson
import java.time.Instant

object FamilyPersonDO : Table<Nothing>("family_persons") {
    val id = long("id").primaryKey()
    val familyTreeId = long("family_tree_id")
    val name = varchar("name")
    val gender = varchar("gender")
    val birthDate = varchar("birth_date")
    val deathDate = varchar("death_date")
    val biography = varchar("biography")
    val fatherId = long("father_id")
    val generationOrder = int("generation_order")
    val sortOrder = int("sort_order")
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")

    const val DDL: String = """
        CREATE TABLE IF NOT EXISTS family_persons (
            id               INTEGER PRIMARY KEY AUTOINCREMENT,
            family_tree_id   INTEGER NOT NULL,
            name             TEXT    NOT NULL,
            gender           TEXT    NOT NULL DEFAULT 'unknown',
            birth_date       TEXT,
            death_date       TEXT,
            biography        TEXT,
            father_id        INTEGER,
            generation_order INTEGER NOT NULL DEFAULT 0,
            sort_order       INTEGER NOT NULL DEFAULT 0,
            created_at       TIMESTAMP NOT NULL,
            updated_at       TIMESTAMP NOT NULL
        )
    """
}

fun QueryRowSet.toFamilyPerson(): FamilyPerson = FamilyPerson(
    id = this[FamilyPersonDO.id],
    familyTreeId = this[FamilyPersonDO.familyTreeId] ?: 0L,
    name = this[FamilyPersonDO.name] ?: "",
    gender = this[FamilyPersonDO.gender] ?: "unknown",
    birthDate = this[FamilyPersonDO.birthDate],
    deathDate = this[FamilyPersonDO.deathDate],
    biography = this[FamilyPersonDO.biography],
    fatherId = this[FamilyPersonDO.fatherId],
    generationOrder = this[FamilyPersonDO.generationOrder] ?: 0,
    sortOrder = this[FamilyPersonDO.sortOrder] ?: 0,
    createdAt = this[FamilyPersonDO.createdAt] ?: Instant.now(),
    updatedAt = this[FamilyPersonDO.updatedAt] ?: Instant.now()
)
```

- [ ] **Step 4: 更新 FamilyPersonRepositoryImpl**

将 `FamilyPersonRepositoryImpl.kt` 替换为：

```kotlin
package site.hanabii.fireworks.infra.family

import org.ktorm.database.Database
import org.ktorm.dsl.delete
import org.ktorm.dsl.eq
import org.ktorm.dsl.from
import org.ktorm.dsl.insert
import org.ktorm.dsl.map
import org.ktorm.dsl.select
import org.ktorm.dsl.update
import org.ktorm.dsl.where
import org.springframework.stereotype.Repository
import site.hanabii.fireworks.domain.family.FamilyPerson
import site.hanabii.fireworks.domain.family.FamilyPersonRepository
import java.time.Instant

@Repository
class FamilyPersonRepositoryImpl(
    private val database: Database
) : FamilyPersonRepository {

    override fun save(person: FamilyPerson): FamilyPerson {
        val now = Instant.now()
        return if (person.id == null) {
            database.insert(FamilyPersonDO) {
                set(it.familyTreeId, person.familyTreeId)
                set(it.name, person.name)
                set(it.gender, person.gender)
                set(it.birthDate, person.birthDate)
                set(it.deathDate, person.deathDate)
                set(it.biography, person.biography)
                set(it.fatherId, person.fatherId)
                set(it.generationOrder, person.generationOrder)
                set(it.sortOrder, person.sortOrder)
                set(it.createdAt, person.createdAt)
                set(it.updatedAt, now)
            }
            val lastId = database.useConnection { conn ->
                conn.createStatement().use { stmt ->
                    stmt.executeQuery("SELECT last_insert_rowid()").use { rs ->
                        if (rs.next()) rs.getLong(1) else throw IllegalStateException("Failed to get last insert rowid")
                    }
                }
            }
            database.from(FamilyPersonDO)
                .select()
                .where { FamilyPersonDO.id eq lastId }
                .map { it.toFamilyPerson() }
                .firstOrNull()
                ?: throw IllegalStateException("FamilyPerson insert succeeded but cannot be loaded")
        } else {
            database.update(FamilyPersonDO) {
                set(it.familyTreeId, person.familyTreeId)
                set(it.name, person.name)
                set(it.gender, person.gender)
                set(it.birthDate, person.birthDate)
                set(it.deathDate, person.deathDate)
                set(it.biography, person.biography)
                set(it.fatherId, person.fatherId)
                set(it.generationOrder, person.generationOrder)
                set(it.sortOrder, person.sortOrder)
                set(it.updatedAt, now)
                where { it.id eq person.id }
            }
            person.copy(updatedAt = now)
        }
    }

    override fun findById(id: Long): FamilyPerson? {
        return database.from(FamilyPersonDO)
            .select()
            .where { FamilyPersonDO.id eq id }
            .map { it.toFamilyPerson() }
            .firstOrNull()
    }

    override fun findByTreeId(treeId: Long): List<FamilyPerson> {
        return database.from(FamilyPersonDO)
            .select()
            .where { FamilyPersonDO.familyTreeId eq treeId }
            .map { it.toFamilyPerson() }
    }

    override fun findByFatherId(fatherId: Long): List<FamilyPerson> {
        return database.from(FamilyPersonDO)
            .select()
            .where { FamilyPersonDO.fatherId eq fatherId }
            .orderBy(FamilyPersonDO.sortOrder.asc())
            .map { it.toFamilyPerson() }
    }

    override fun countByTreeId(treeId: Long): Long {
        return database.from(FamilyPersonDO)
            .select()
            .where { FamilyPersonDO.familyTreeId eq treeId }
            .totalRecordsInAllPages.toLong()
    }

    override fun deleteById(id: Long): Boolean {
        val affected = database.delete(FamilyPersonDO) { it.id eq id }
        return affected > 0
    }

    override fun deleteByTreeId(treeId: Long) {
        database.delete(FamilyPersonDO) { it.familyTreeId eq treeId }
    }
}
```

- [ ] **Step 5: 编译验证**

```bash
cd /Users/chuhao/IdeaProjects/fireworks && ./gradlew :fireworks-server:compileKotlin
```

- [ ] **Step 6: 提交**

```bash
git add fireworks-server/src/main/kotlin/site/hanabii/fireworks/domain/family/FamilyPerson.kt \
        fireworks-server/src/main/kotlin/site/hanabii/fireworks/domain/family/FamilyPersonRepository.kt \
        fireworks-server/src/main/kotlin/site/hanabii/fireworks/infra/family/FamilyPersonDO.kt \
        fireworks-server/src/main/kotlin/site/hanabii/fireworks/infra/family/FamilyPersonRepositoryImpl.kt
git commit -m "refactor(family): 改造 FamilyPerson 数据模型，新增 familyTreeId/sortOrder，移除 motherId/spouseId"
```

---

### Task 2: 新增 FamilyTree 域对象和仓储（族谱实体）

**Files:**
- Create: `fireworks-server/src/main/kotlin/site/hanabii/fireworks/domain/family/FamilyTree.kt`
- Create: `fireworks-server/src/main/kotlin/site/hanabii/fireworks/domain/family/FamilyTreeRepository.kt`
- Create: `fireworks-server/src/main/kotlin/site/hanabii/fireworks/infra/family/FamilyTreeDO.kt`
- Create: `fireworks-server/src/main/kotlin/site/hanabii/fireworks/infra/family/FamilyTreeRepositoryImpl.kt`

- [ ] **Step 1: 创建 FamilyTree 实体**

```kotlin
package site.hanabii.fireworks.domain.family

import java.time.Instant

data class FamilyTree(
    val id: Long? = null,
    val name: String,
    val description: String? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
) {
    init {
        require(name.isNotBlank()) { "name must not be blank" }
    }
}
```

- [ ] **Step 2: 创建 FamilyTreeRepository 接口**

```kotlin
package site.hanabii.fireworks.domain.family

interface FamilyTreeRepository {
    fun save(tree: FamilyTree): FamilyTree
    fun findById(id: Long): FamilyTree?
    fun findAll(): List<FamilyTree>
    fun deleteById(id: Long): Boolean
}
```

- [ ] **Step 3: 创建 FamilyTreeDO 表映射和 DDL**

```kotlin
package site.hanabii.fireworks.infra.family

import org.ktorm.dsl.QueryRowSet
import org.ktorm.schema.Table
import org.ktorm.schema.long
import org.ktorm.schema.timestamp
import org.ktorm.schema.varchar
import site.hanabii.fireworks.domain.family.FamilyTree
import java.time.Instant

object FamilyTreeDO : Table<Nothing>("family_trees") {
    val id = long("id").primaryKey()
    val name = varchar("name")
    val description = varchar("description")
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")

    const val DDL: String = """
        CREATE TABLE IF NOT EXISTS family_trees (
            id          INTEGER PRIMARY KEY AUTOINCREMENT,
            name        TEXT NOT NULL,
            description TEXT,
            created_at  TIMESTAMP NOT NULL,
            updated_at  TIMESTAMP NOT NULL
        )
    """
}

fun QueryRowSet.toFamilyTree(): FamilyTree = FamilyTree(
    id = this[FamilyTreeDO.id],
    name = this[FamilyTreeDO.name] ?: "",
    description = this[FamilyTreeDO.description],
    createdAt = this[FamilyTreeDO.createdAt] ?: Instant.now(),
    updatedAt = this[FamilyTreeDO.updatedAt] ?: Instant.now()
)
```

- [ ] **Step 4: 创建 FamilyTreeRepositoryImpl**

```kotlin
package site.hanabii.fireworks.infra.family

import org.ktorm.database.Database
import org.ktorm.dsl.delete
import org.ktorm.dsl.eq
import org.ktorm.dsl.from
import org.ktorm.dsl.insert
import org.ktorm.dsl.map
import org.ktorm.dsl.select
import org.ktorm.dsl.update
import org.ktorm.dsl.where
import org.springframework.stereotype.Repository
import site.hanabii.fireworks.domain.family.FamilyTree
import site.hanabii.fireworks.domain.family.FamilyTreeRepository
import java.time.Instant

@Repository
class FamilyTreeRepositoryImpl(
    private val database: Database
) : FamilyTreeRepository {

    override fun save(tree: FamilyTree): FamilyTree {
        val now = Instant.now()
        return if (tree.id == null) {
            database.insert(FamilyTreeDO) {
                set(it.name, tree.name)
                set(it.description, tree.description)
                set(it.createdAt, tree.createdAt)
                set(it.updatedAt, now)
            }
            val lastId = database.useConnection { conn ->
                conn.createStatement().use { stmt ->
                    stmt.executeQuery("SELECT last_insert_rowid()").use { rs ->
                        if (rs.next()) rs.getLong(1) else throw IllegalStateException("Failed to get last insert rowid")
                    }
                }
            }
            database.from(FamilyTreeDO)
                .select()
                .where { FamilyTreeDO.id eq lastId }
                .map { it.toFamilyTree() }
                .firstOrNull()
                ?: throw IllegalStateException("FamilyTree insert succeeded but cannot be loaded")
        } else {
            database.update(FamilyTreeDO) {
                set(it.name, tree.name)
                set(it.description, tree.description)
                set(it.updatedAt, now)
                where { it.id eq tree.id }
            }
            tree.copy(updatedAt = now)
        }
    }

    override fun findById(id: Long): FamilyTree? {
        return database.from(FamilyTreeDO)
            .select()
            .where { FamilyTreeDO.id eq id }
            .map { it.toFamilyTree() }
            .firstOrNull()
    }

    override fun findAll(): List<FamilyTree> {
        return database.from(FamilyTreeDO)
            .select()
            .orderBy(FamilyTreeDO.id.asc())
            .map { it.toFamilyTree() }
    }

    override fun deleteById(id: Long): Boolean {
        val affected = database.delete(FamilyTreeDO) { it.id eq id }
        return affected > 0
    }
}
```

- [ ] **Step 5: 编译验证**

```bash
cd /Users/chuhao/IdeaProjects/fireworks && ./gradlew :fireworks-server:compileKotlin
```

- [ ] **Step 6: 提交**

```bash
git add fireworks-server/src/main/kotlin/site/hanabii/fireworks/domain/family/FamilyTree.kt \
        fireworks-server/src/main/kotlin/site/hanabii/fireworks/domain/family/FamilyTreeRepository.kt \
        fireworks-server/src/main/kotlin/site/hanabii/fireworks/infra/family/FamilyTreeDO.kt \
        fireworks-server/src/main/kotlin/site/hanabii/fireworks/infra/family/FamilyTreeRepositoryImpl.kt
git commit -m "feat(family): 新增 FamilyTree 族谱实体、仓储和表映射"
```

---

### Task 3: 新增 PersonSpouse 配偶关系实体和仓储

**Files:**
- Create: `fireworks-server/src/main/kotlin/site/hanabii/fireworks/domain/family/PersonSpouse.kt`
- Create: `fireworks-server/src/main/kotlin/site/hanabii/fireworks/domain/family/PersonSpouseRepository.kt`
- Create: `fireworks-server/src/main/kotlin/site/hanabii/fireworks/infra/family/PersonSpouseDO.kt`
- Create: `fireworks-server/src/main/kotlin/site/hanabii/fireworks/infra/family/PersonSpouseRepositoryImpl.kt`

- [ ] **Step 1: 创建 PersonSpouse 实体**

```kotlin
package site.hanabii.fireworks.domain.family

import java.time.Instant

data class PersonSpouse(
    val id: Long? = null,
    val husbandId: Long,
    val wifeId: Long,
    val marriageOrder: Int = 1,
    val isPrimary: Boolean = true,
    val createdAt: Instant = Instant.now()
)
```

- [ ] **Step 2: 创建 PersonSpouseRepository 接口**

```kotlin
package site.hanabii.fireworks.domain.family

interface PersonSpouseRepository {
    fun save(spouse: PersonSpouse): PersonSpouse
    fun findByHusbandId(husbandId: Long): List<PersonSpouse>
    fun findPrimaryByHusbandId(husbandId: Long): PersonSpouse?
    fun deleteById(id: Long): Boolean
}
```

- [ ] **Step 3: 创建 PersonSpouseDO 表映射和 DDL**

```kotlin
package site.hanabii.fireworks.infra.family

import org.ktorm.dsl.QueryRowSet
import org.ktorm.schema.Table
import org.ktorm.schema.int
import org.ktorm.schema.long
import org.ktorm.schema.timestamp
import site.hanabii.fireworks.domain.family.PersonSpouse
import java.time.Instant

object PersonSpouseDO : Table<Nothing>("person_spouses") {
    val id = long("id").primaryKey()
    val husbandId = long("husband_id")
    val wifeId = long("wife_id")
    val marriageOrder = int("marriage_order")
    val isPrimary = int("is_primary")
    val createdAt = timestamp("created_at")

    const val DDL: String = """
        CREATE TABLE IF NOT EXISTS person_spouses (
            id             INTEGER PRIMARY KEY AUTOINCREMENT,
            husband_id     INTEGER NOT NULL,
            wife_id        INTEGER NOT NULL,
            marriage_order INTEGER NOT NULL DEFAULT 1,
            is_primary     INTEGER NOT NULL DEFAULT 1,
            created_at     TIMESTAMP NOT NULL,
            UNIQUE(husband_id, wife_id)
        )
    """
}

fun QueryRowSet.toPersonSpouse(): PersonSpouse = PersonSpouse(
    id = this[PersonSpouseDO.id],
    husbandId = this[PersonSpouseDO.husbandId] ?: 0L,
    wifeId = this[PersonSpouseDO.wifeId] ?: 0L,
    marriageOrder = this[PersonSpouseDO.marriageOrder] ?: 1,
    isPrimary = (this[PersonSpouseDO.isPrimary] ?: 1) != 0,
    createdAt = this[PersonSpouseDO.createdAt] ?: Instant.now()
)
```

- [ ] **Step 4: 创建 PersonSpouseRepositoryImpl**

```kotlin
package site.hanabii.fireworks.infra.family

import org.ktorm.database.Database
import org.ktorm.dsl.delete
import org.ktorm.dsl.eq
import org.ktorm.dsl.from
import org.ktorm.dsl.insert
import org.ktorm.dsl.map
import org.ktorm.dsl.select
import org.ktorm.dsl.where
import org.springframework.stereotype.Repository
import site.hanabii.fireworks.domain.family.PersonSpouse
import site.hanabii.fireworks.domain.family.PersonSpouseRepository
import java.time.Instant

@Repository
class PersonSpouseRepositoryImpl(
    private val database: Database
) : PersonSpouseRepository {

    override fun save(spouse: PersonSpouse): PersonSpouse {
        return if (spouse.id == null) {
            database.insert(PersonSpouseDO) {
                set(it.husbandId, spouse.husbandId)
                set(it.wifeId, spouse.wifeId)
                set(it.marriageOrder, spouse.marriageOrder)
                set(it.isPrimary, if (spouse.isPrimary) 1 else 0)
                set(it.createdAt, spouse.createdAt)
            }
            val lastId = database.useConnection { conn ->
                conn.createStatement().use { stmt ->
                    stmt.executeQuery("SELECT last_insert_rowid()").use { rs ->
                        if (rs.next()) rs.getLong(1) else throw IllegalStateException("Failed to get last insert rowid")
                    }
                }
            }
            spouse.copy(id = lastId)
        } else {
            spouse // 配偶关系不支持更新，只增删
        }
    }

    override fun findByHusbandId(husbandId: Long): List<PersonSpouse> {
        return database.from(PersonSpouseDO)
            .select()
            .where { PersonSpouseDO.husbandId eq husbandId }
            .orderBy(PersonSpouseDO.marriageOrder.asc())
            .map { it.toPersonSpouse() }
    }

    override fun findPrimaryByHusbandId(husbandId: Long): PersonSpouse? {
        return database.from(PersonSpouseDO)
            .select()
            .where { (PersonSpouseDO.husbandId eq husbandId) and (PersonSpouseDO.isPrimary eq 1) }
            .map { it.toPersonSpouse() }
            .firstOrNull()
    }

    override fun deleteById(id: Long): Boolean {
        val affected = database.delete(PersonSpouseDO) { it.id eq id }
        return affected > 0
    }
}
```

- [ ] **Step 5: 编译验证**

```bash
cd /Users/chuhao/IdeaProjects/fireworks && ./gradlew :fireworks-server:compileKotlin
```

- [ ] **Step 6: 提交**

```bash
git add fireworks-server/src/main/kotlin/site/hanabii/fireworks/domain/family/PersonSpouse.kt \
        fireworks-server/src/main/kotlin/site/hanabii/fireworks/domain/family/PersonSpouseRepository.kt \
        fireworks-server/src/main/kotlin/site/hanabii/fireworks/infra/family/PersonSpouseDO.kt \
        fireworks-server/src/main/kotlin/site/hanabii/fireworks/infra/family/PersonSpouseRepositoryImpl.kt
git commit -m "feat(family): 新增 PersonSpouse 配偶关系实体、仓储和表映射"
```

---

### Task 4: 新增 FamilyLineage 分支索引实体和仓储

**Files:**
- Create: `fireworks-server/src/main/kotlin/site/hanabii/fireworks/domain/family/FamilyLineage.kt`
- Create: `fireworks-server/src/main/kotlin/site/hanabii/fireworks/domain/family/FamilyLineageRepository.kt`
- Create: `fireworks-server/src/main/kotlin/site/hanabii/fireworks/infra/family/FamilyLineageDO.kt`
- Create: `fireworks-server/src/main/kotlin/site/hanabii/fireworks/infra/family/FamilyLineageRepositoryImpl.kt`

- [ ] **Step 1: 创建 FamilyLineage 实体**

```kotlin
package site.hanabii.fireworks.domain.family

data class FamilyLineage(
    val rootId: Long,
    val personId: Long,
    val depth: Int
)
```

- [ ] **Step 2: 创建 FamilyLineageRepository 接口**

```kotlin
package site.hanabii.fireworks.domain.family

interface FamilyLineageRepository {
    fun insert(rootId: Long, personId: Long, depth: Int)
    fun deleteByPersonId(personId: Long)
    fun deleteByTreeId(treeId: Long)
    fun findByRootId(rootId: Long, maxDepth: Int?, offset: Int, size: Int): List<FamilyLineage>
    fun countByRootId(rootId: Long, maxDepth: Int?): Long
    fun rebuildForPerson(personId: Long, fatherId: Long?)
}
```

- [ ] **Step 3: 创建 FamilyLineageDO 表映射和 DDL**

```kotlin
package site.hanabii.fireworks.infra.family

import org.ktorm.dsl.QueryRowSet
import org.ktorm.schema.Table
import org.ktorm.schema.int
import org.ktorm.schema.long
import site.hanabii.fireworks.domain.family.FamilyLineage

object FamilyLineageDO : Table<Nothing>("family_lineages") {
    val rootId = long("root_id")
    val personId = long("person_id")
    val depth = int("depth")

    const val DDL: String = """
        CREATE TABLE IF NOT EXISTS family_lineages (
            root_id   INTEGER NOT NULL,
            person_id INTEGER NOT NULL,
            depth     INTEGER NOT NULL DEFAULT 0,
            UNIQUE(root_id, person_id)
        )
    """
}

fun QueryRowSet.toFamilyLineage(): FamilyLineage = FamilyLineage(
    rootId = this[FamilyLineageDO.rootId] ?: 0L,
    personId = this[FamilyLineageDO.personId] ?: 0L,
    depth = this[FamilyLineageDO.depth] ?: 0
)
```

- [ ] **Step 4: 创建 FamilyLineageRepositoryImpl**

```kotlin
package site.hanabii.fireworks.infra.family

import org.ktorm.database.Database
import org.ktorm.dsl.delete
import org.ktorm.dsl.eq
import org.ktorm.dsl.from
import org.ktorm.dsl.insert
import org.ktorm.dsl.lessEq
import org.ktorm.dsl.map
import org.ktorm.dsl.select
import org.ktorm.dsl.where
import org.springframework.stereotype.Repository
import site.hanabii.fireworks.domain.family.FamilyLineage
import site.hanabii.fireworks.domain.family.FamilyLineageRepository

@Repository
class FamilyLineageRepositoryImpl(
    private val database: Database
) : FamilyLineageRepository {

    override fun insert(rootId: Long, personId: Long, depth: Int) {
        database.insert(FamilyLineageDO) {
            set(it.rootId, rootId)
            set(it.personId, personId)
            set(it.depth, depth)
        }
    }

    override fun deleteByPersonId(personId: Long) {
        database.delete(FamilyLineageDO) { it.personId eq personId }
    }

    override fun deleteByTreeId(treeId: Long) {
        database.useConnection { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute("DELETE FROM family_lineages WHERE root_id IN (SELECT id FROM family_persons WHERE family_tree_id = $treeId)")
            }
        }
    }

    override fun findByRootId(rootId: Long, maxDepth: Int?, offset: Int, size: Int): List<FamilyLineage> {
        val query = database.from(FamilyLineageDO).select()
        val filtered = if (maxDepth != null) {
            query.where { (FamilyLineageDO.rootId eq rootId) and (FamilyLineageDO.depth lessEq maxDepth) }
        } else {
            query.where { FamilyLineageDO.rootId eq rootId }
        }
        return filtered
            .orderBy(FamilyLineageDO.depth.asc())
            .limit(size)
            .offset(offset)
            .map { it.toFamilyLineage() }
    }

    override fun countByRootId(rootId: Long, maxDepth: Int?): Long {
        val query = database.from(FamilyLineageDO).select()
        val filtered = if (maxDepth != null) {
            query.where { (FamilyLineageDO.rootId eq rootId) and (FamilyLineageDO.depth lessEq maxDepth) }
        } else {
            query.where { FamilyLineageDO.rootId eq rootId }
        }
        return filtered.totalRecordsInAllPages.toLong()
    }

    override fun rebuildForPerson(personId: Long, fatherId: Long?) {
        deleteByPersonId(personId)
        if (fatherId != null) {
            // 继承父亲的 lineage 记录 + 自己
            val parentLineages = database.from(FamilyLineageDO)
                .select()
                .where { FamilyLineageDO.personId eq fatherId }
                .map { it.toFamilyLineage() }
            for (lineage in parentLineages) {
                insert(lineage.rootId, personId, lineage.depth + 1)
            }
        }
        // 始终插入自身作为根记录（depth=0，rootId=自身）
        insert(personId, personId, 0)
    }
}
```

- [ ] **Step 5: 编译验证**

```bash
cd /Users/chuhao/IdeaProjects/fireworks && ./gradlew :fireworks-server:compileKotlin
```

- [ ] **Step 6: 提交**

```bash
git add fireworks-server/src/main/kotlin/site/hanabii/fireworks/domain/family/FamilyLineage.kt \
        fireworks-server/src/main/kotlin/site/hanabii/fireworks/domain/family/FamilyLineageRepository.kt \
        fireworks-server/src/main/kotlin/site/hanabii/fireworks/infra/family/FamilyLineageDO.kt \
        fireworks-server/src/main/kotlin/site/hanabii/fireworks/infra/family/FamilyLineageRepositoryImpl.kt
git commit -m "feat(family): 新增 FamilyLineage 分支索引实体、仓储和表映射"
```

---

### Task 5: 注册 DDL 和错误码

**Files:**
- Modify: `fireworks-server/src/main/kotlin/site/hanabii/fireworks/infra/config/SchemaInitializer.kt`
- Modify: `fireworks-server/src/main/kotlin/site/hanabii/fireworks/app/ErrorCode.kt`

- [ ] **Step 1: SchemaInitializer 新增 DDL 注册**

在 `SchemaInitializer.kt` 中新增 import 和 stmt.execute 调用：

新增 import（在现有 import 块末尾添加）：
```kotlin
import site.hanabii.fireworks.infra.family.FamilyTreeDO
import site.hanabii.fireworks.infra.family.PersonSpouseDO
import site.hanabii.fireworks.infra.family.FamilyLineageDO
```

在 init() 方法的 stmt.execute 块中，`stmt.execute(FamilyPersonDO.DDL.trimIndent())` 之后新增：
```kotlin
                stmt.execute(FamilyTreeDO.DDL.trimIndent())
                stmt.execute(PersonSpouseDO.DDL.trimIndent())
                stmt.execute(FamilyLineageDO.DDL.trimIndent())
```

完整修改后的 `SchemaInitializer.kt`：
```kotlin
package site.hanabii.fireworks.infra.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import site.hanabii.fireworks.infra.UserDO
import site.hanabii.fireworks.infra.auth.AccountDO
import site.hanabii.fireworks.infra.vault.PasswordEntryDO
import site.hanabii.fireworks.infra.family.FamilyPersonDO
import site.hanabii.fireworks.infra.family.FamilyTreeDO
import site.hanabii.fireworks.infra.family.PersonSpouseDO
import site.hanabii.fireworks.infra.family.FamilyLineageDO
import site.hanabii.fireworks.infra.vault.VaultConfigDO
import javax.sql.DataSource

@Component
class SchemaInitializer(
    private val dataSource: DataSource,
    @param:Value("\${fireworks.db.init-schema:true}") private val enabled: Boolean
) {

    @EventListener(ApplicationReadyEvent::class)
    fun init() {
        if (!enabled) return
        dataSource.connection.use { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute(UserDO.DDL.trimIndent())
                stmt.execute(VaultConfigDO.DDL.trimIndent())
                stmt.execute(PasswordEntryDO.DDL.trimIndent())
                stmt.execute(AccountDO.DDL.trimIndent())
                stmt.execute(FamilyPersonDO.DDL.trimIndent())
                stmt.execute(FamilyTreeDO.DDL.trimIndent())
                stmt.execute(PersonSpouseDO.DDL.trimIndent())
                stmt.execute(FamilyLineageDO.DDL.trimIndent())
            }
        }
    }
}
```

- [ ] **Step 2: ErrorCode 新增 FAMILY_TREE_NOT_FOUND**

在 `ErrorCode.kt` 中新增：
```kotlin
    FAMILY_TREE_NOT_FOUND,
```

完整修改后：
```kotlin
package site.hanabii.fireworks.app

enum class ErrorCode {
    USER_NOT_FOUND,
    INVALID_REQUEST,
    INTERNAL_ERROR,
    VAULT_NOT_INITIALIZED,
    NOT_AUTHENTICATED,
    INVALID_CREDENTIALS,
    ENTRY_NOT_FOUND,
    FAMILY_PERSON_NOT_FOUND,
    FAMILY_TREE_NOT_FOUND,
    ACCOUNT_EXISTS
}
```

- [ ] **Step 3: 编译验证**

```bash
cd /Users/chuhao/IdeaProjects/fireworks && ./gradlew :fireworks-server:compileKotlin
```

- [ ] **Step 4: 提交**

```bash
git add fireworks-server/src/main/kotlin/site/hanabii/fireworks/infra/config/SchemaInitializer.kt \
        fireworks-server/src/main/kotlin/site/hanabii/fireworks/app/ErrorCode.kt
git commit -m "feat(family): 注册 FamilyTree/PersonSpouse/FamilyLineage DDL，新增 FAMILY_TREE_NOT_FOUND 错误码"
```

---

### Task 6: 创建 FamilyTreeService 业务逻辑层

**Files:**
- Create: `fireworks-server/src/main/kotlin/site/hanabii/fireworks/app/family/FamilyTreeService.kt`

- [ ] **Step 1: 创建 FamilyTreeService**

```kotlin
package site.hanabii.fireworks.app.family

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
// 注意：Ktorm 未集成 Spring 事务管理器，Service 方法不使用 @Transactional。
// 复杂操作（如 deleteTree）通过 Repository 方法顺序执行，SQLite 单连接模型天然串行化。
import site.hanabii.fireworks.app.AppException
import site.hanabii.fireworks.app.ErrorCode
import site.hanabii.fireworks.domain.family.FamilyLineageRepository
import site.hanabii.fireworks.domain.family.FamilyPerson
import site.hanabii.fireworks.domain.family.FamilyPersonRepository
import site.hanabii.fireworks.domain.family.FamilyTree
import site.hanabii.fireworks.domain.family.FamilyTreeRepository
import site.hanabii.fireworks.domain.family.PersonSpouse
import site.hanabii.fireworks.domain.family.PersonSpouseRepository

@Service
class FamilyTreeService(
    private val treeRepo: FamilyTreeRepository,
    private val personRepo: FamilyPersonRepository,
    private val spouseRepo: PersonSpouseRepository,
    private val lineageRepo: FamilyLineageRepository
) {

    // ========== 族谱 CRUD ==========

    fun listTrees(): List<FamilyTree> = treeRepo.findAll()

    fun createTree(name: String, description: String?): FamilyTree {
        return treeRepo.save(FamilyTree(name = name, description = description))
    }

    fun getTree(id: Long): FamilyTree {
        return treeRepo.findById(id)
            ?: throw AppException(ErrorCode.FAMILY_TREE_NOT_FOUND, HttpStatus.NOT_FOUND, "族谱不存在: $id")
    }

    fun updateTree(id: Long, name: String?, description: String?): FamilyTree {
        val existing = getTree(id)
        return treeRepo.save(existing.copy(
            name = name ?: existing.name,
            description = description ?: existing.description
        ))
    }

    fun deleteTree(id: Long) {
        getTree(id)
        lineageRepo.deleteByTreeId(id)
        personRepo.deleteByTreeId(id)
        treeRepo.deleteById(id)
    }

    // ========== 成员 CRUD ==========

    fun listPersons(treeId: Long): List<FamilyPerson> {
        getTree(treeId)
        return personRepo.findByTreeId(treeId)
    }

    fun listRoots(treeId: Long): List<FamilyPerson> {
        getTree(treeId)
        return personRepo.findByTreeId(treeId)
            .filter { it.fatherId == null && it.gender == "male" }
    }

    fun addPerson(treeId: Long, req: CreatePersonRequest): FamilyPerson {
        getTree(treeId)
        val fatherId = req.fatherId
        var genOrder = 0
        if (fatherId != null) {
            val father = personRepo.findById(fatherId)
                ?: throw AppException(ErrorCode.FAMILY_PERSON_NOT_FOUND, HttpStatus.NOT_FOUND, "父亲不存在: $fatherId")
            if (father.familyTreeId != treeId)
                throw AppException(ErrorCode.INVALID_REQUEST, HttpStatus.BAD_REQUEST, "父亲不属于当前族谱")
            genOrder = father.generationOrder + 1
        }
        val person = personRepo.save(FamilyPerson(
            familyTreeId = treeId,
            name = req.name,
            gender = req.gender,
            birthDate = req.birthDate,
            deathDate = req.deathDate,
            biography = req.biography,
            fatherId = fatherId,
            generationOrder = genOrder,
            sortOrder = req.sortOrder
        ))
        lineageRepo.rebuildForPerson(person.id!!, fatherId)
        return person
    }

    fun getPerson(treeId: Long, personId: Long): FamilyPerson {
        getTree(treeId)
        return personRepo.findById(personId)
            ?: throw AppException(ErrorCode.FAMILY_PERSON_NOT_FOUND, HttpStatus.NOT_FOUND, "成员不存在: $personId")
    }

    fun updatePerson(treeId: Long, personId: Long, req: UpdatePersonRequest): FamilyPerson {
        val existing = getPerson(treeId, personId)
        val oldFatherId = existing.fatherId
        val newFatherId = req.fatherId ?: oldFatherId

        var genOrder = existing.generationOrder
        if (newFatherId != oldFatherId) {
            if (newFatherId != null) {
                val newFather = personRepo.findById(newFatherId)
                    ?: throw AppException(ErrorCode.FAMILY_PERSON_NOT_FOUND, HttpStatus.NOT_FOUND, "父亲不存在: $newFatherId")
                genOrder = newFather.generationOrder + 1
            } else {
                genOrder = 0
            }
        }

        val updated = personRepo.save(existing.copy(
            name = req.name ?: existing.name,
            gender = req.gender ?: existing.gender,
            birthDate = req.birthDate ?: existing.birthDate,
            deathDate = req.deathDate ?: existing.deathDate,
            biography = req.biography ?: existing.biography,
            fatherId = newFatherId,
            generationOrder = genOrder,
            sortOrder = req.sortOrder ?: existing.sortOrder
        ))

        if (newFatherId != oldFatherId) {
            lineageRepo.rebuildForPerson(personId, newFatherId)
        }
        return updated
    }

    fun deletePerson(treeId: Long, personId: Long) {
        getPerson(treeId, personId)
        // 将子孙的 fatherId 置空（他们将成为新的根节点）
        val children = personRepo.findByFatherId(personId)
        for (child in children) {
            personRepo.save(child.copy(fatherId = null, generationOrder = 0))
            lineageRepo.rebuildForPerson(child.id!!, null)
        }
        lineageRepo.deleteByPersonId(personId)
        personRepo.deleteById(personId)
    }

    fun getChildren(treeId: Long, personId: Long): ChildrenResponse {
        val person = getPerson(treeId, personId)
        val children = personRepo.findByFatherId(personId)
        val spouse = spouseRepo.findPrimaryByHusbandId(personId)?.let { s ->
            val wife = personRepo.findById(s.wifeId)
            wife?.let { SpouseInfo(id = it.id!!, name = it.name) }
        }
        return ChildrenResponse(
            person = PersonWithSpouse(
                id = person.id!!,
                name = person.name,
                gender = person.gender,
                birthDate = person.birthDate,
                deathDate = person.deathDate,
                biography = person.biography,
                fatherId = person.fatherId,
                generationOrder = person.generationOrder,
                sortOrder = person.sortOrder,
                spouse = spouse
            ),
            children = children.map {
                ChildInfo(
                    id = it.id!!,
                    name = it.name,
                    gender = it.gender,
                    sortOrder = it.sortOrder,
                    hasChildren = personRepo.findByFatherId(it.id!!).isNotEmpty()
                )
            }
        )
    }

    fun getAncestors(treeId: Long, personId: Long): List<FamilyPerson> {
        getPerson(treeId, personId)
        val result = mutableListOf<FamilyPerson>()
        var current = personRepo.findById(personId)
        while (current != null) {
            result.add(current)
            current = current.fatherId?.let { personRepo.findById(it) }
        }
        return result.reversed()
    }

    // ========== 配偶管理 ==========

    fun getSpouses(treeId: Long, husbandId: Long): List<PersonSpouse> {
        getPerson(treeId, husbandId)
        return spouseRepo.findByHusbandId(husbandId)
    }

    fun addSpouse(treeId: Long, husbandId: Long, wifeId: Long, isPrimary: Boolean): PersonSpouse {
        getPerson(treeId, husbandId)
        val wife = personRepo.findById(wifeId)
            ?: throw AppException(ErrorCode.FAMILY_PERSON_NOT_FOUND, HttpStatus.NOT_FOUND, "妻子不存在: $wifeId")
        if (wife.familyTreeId != treeId)
            throw AppException(ErrorCode.INVALID_REQUEST, HttpStatus.BAD_REQUEST, "妻子不属于当前族谱")
        if (wife.gender != "female")
            throw AppException(ErrorCode.INVALID_REQUEST, HttpStatus.BAD_REQUEST, "配偶必须是女性")
        val existing = spouseRepo.findByHusbandId(husbandId)
        val marriageOrder = existing.size + 1
        return spouseRepo.save(PersonSpouse(
            husbandId = husbandId,
            wifeId = wifeId,
            marriageOrder = marriageOrder,
            isPrimary = isPrimary
        ))
    }

    fun removeSpouse(treeId: Long, husbandId: Long, spouseId: Long) {
        getPerson(treeId, husbandId)
        val deleted = spouseRepo.deleteById(spouseId)
        if (!deleted)
            throw AppException(ErrorCode.FAMILY_PERSON_NOT_FOUND, HttpStatus.NOT_FOUND, "配偶关系不存在: $spouseId")
    }

    // ========== 搜索 ==========

    fun searchPersons(treeId: Long, search: String): List<FamilyPerson> {
        getTree(treeId)
        return personRepo.findByTreeId(treeId)
            .filter { it.name.contains(search, ignoreCase = true) }
    }
}

// ---------- 请求 / 响应 DTO ----------

data class CreatePersonRequest(
    val name: String,
    val gender: String = "unknown",
    val birthDate: String? = null,
    val deathDate: String? = null,
    val biography: String? = null,
    val fatherId: Long? = null,
    val sortOrder: Int = 0
)

data class UpdatePersonRequest(
    val name: String? = null,
    val gender: String? = null,
    val birthDate: String? = null,
    val deathDate: String? = null,
    val biography: String? = null,
    val fatherId: Long? = null,
    val sortOrder: Int? = null
)

data class SpouseInfo(
    val id: Long,
    val name: String
)

data class PersonWithSpouse(
    val id: Long,
    val name: String,
    val gender: String,
    val birthDate: String?,
    val deathDate: String?,
    val biography: String?,
    val fatherId: Long?,
    val generationOrder: Int,
    val sortOrder: Int,
    val spouse: SpouseInfo?
)

data class ChildInfo(
    val id: Long,
    val name: String,
    val gender: String,
    val sortOrder: Int,
    val hasChildren: Boolean
)

data class ChildrenResponse(
    val person: PersonWithSpouse,
    val children: List<ChildInfo>
)
```

- [ ] **Step 2: 编译验证**

```bash
cd /Users/chuhao/IdeaProjects/fireworks && ./gradlew :fireworks-server:compileKotlin
```

- [ ] **Step 3: 提交**

```bash
git add fireworks-server/src/main/kotlin/site/hanabii/fireworks/app/family/FamilyTreeService.kt
git commit -m "feat(family): 新增 FamilyTreeService 业务逻辑层（族谱/成员/配偶 CRUD + lineage 同步）"
```

---

### Task 7: 创建 FamilyTreeController REST 控制器

**Files:**
- Create: `fireworks-server/src/main/kotlin/site/hanabii/fireworks/app/family/FamilyTreeController.kt`

- [ ] **Step 1: 创建 FamilyTreeController**

```kotlin
package site.hanabii.fireworks.app.family

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import site.hanabii.fireworks.domain.family.FamilyPerson
import site.hanabii.fireworks.domain.family.FamilyTree
import site.hanabii.fireworks.domain.family.PersonSpouse

@RestController
@RequestMapping("/api/family-trees")
class FamilyTreeController(
    private val service: FamilyTreeService
) {

    // ========== 族谱 CRUD ==========

    @GetMapping
    fun listTrees(): List<FamilyTree> = service.listTrees()

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createTree(@Valid @RequestBody req: CreateTreeRequest): FamilyTree {
        return service.createTree(req.name, req.description)
    }

    @GetMapping("/{treeId}")
    fun getTree(@PathVariable treeId: Long): FamilyTree = service.getTree(treeId)

    @PutMapping("/{treeId}")
    fun updateTree(@PathVariable treeId: Long, @Valid @RequestBody req: UpdateTreeRequest): FamilyTree {
        return service.updateTree(treeId, req.name, req.description)
    }

    @DeleteMapping("/{treeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteTree(@PathVariable treeId: Long) {
        service.deleteTree(treeId)
    }

    // ========== 成员 CRUD ==========

    @GetMapping("/{treeId}/persons")
    fun listPersons(
        @PathVariable treeId: Long,
        @RequestParam(required = false) search: String?
    ): List<FamilyPerson> {
        return if (search != null) service.searchPersons(treeId, search)
        else service.listPersons(treeId)
    }

    @PostMapping("/{treeId}/persons")
    @ResponseStatus(HttpStatus.CREATED)
    fun addPerson(@PathVariable treeId: Long, @Valid @RequestBody req: CreatePersonRequest): FamilyPerson {
        return service.addPerson(treeId, req)
    }

    @GetMapping("/{treeId}/persons/{personId}")
    fun getPerson(@PathVariable treeId: Long, @PathVariable personId: Long): FamilyPerson {
        return service.getPerson(treeId, personId)
    }

    @PutMapping("/{treeId}/persons/{personId}")
    fun updatePerson(
        @PathVariable treeId: Long,
        @PathVariable personId: Long,
        @Valid @RequestBody req: UpdatePersonRequest
    ): FamilyPerson {
        return service.updatePerson(treeId, personId, req)
    }

    @DeleteMapping("/{treeId}/persons/{personId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deletePerson(@PathVariable treeId: Long, @PathVariable personId: Long) {
        service.deletePerson(treeId, personId)
    }

    // ========== 树结构查询 ==========

    @GetMapping("/{treeId}/roots")
    fun listRoots(@PathVariable treeId: Long): List<FamilyPerson> = service.listRoots(treeId)

    @GetMapping("/{treeId}/persons/{personId}/children")
    fun getChildren(@PathVariable treeId: Long, @PathVariable personId: Long): ChildrenResponse {
        return service.getChildren(treeId, personId)
    }

    @GetMapping("/{treeId}/persons/{personId}/ancestors")
    fun getAncestors(@PathVariable treeId: Long, @PathVariable personId: Long): List<FamilyPerson> {
        return service.getAncestors(treeId, personId)
    }

    // ========== 配偶管理 ==========

    @GetMapping("/{treeId}/persons/{husbandId}/spouses")
    fun getSpouses(@PathVariable treeId: Long, @PathVariable husbandId: Long): List<PersonSpouse> {
        return service.getSpouses(treeId, husbandId)
    }

    @PostMapping("/{treeId}/persons/{husbandId}/spouses")
    @ResponseStatus(HttpStatus.CREATED)
    fun addSpouse(
        @PathVariable treeId: Long,
        @PathVariable husbandId: Long,
        @Valid @RequestBody req: AddSpouseRequest
    ): PersonSpouse {
        return service.addSpouse(treeId, husbandId, req.wifeId, req.isPrimary)
    }

    @DeleteMapping("/{treeId}/persons/{husbandId}/spouses/{spouseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun removeSpouse(
        @PathVariable treeId: Long,
        @PathVariable husbandId: Long,
        @PathVariable spouseId: Long
    ) {
        service.removeSpouse(treeId, husbandId, spouseId)
    }
}

// ---------- 请求 DTO ----------

data class CreateTreeRequest(
    @field:NotBlank val name: String,
    val description: String? = null
)

data class UpdateTreeRequest(
    val name: String? = null,
    val description: String? = null
)

data class AddSpouseRequest(
    val wifeId: Long,
    val isPrimary: Boolean = true
)
```

- [ ] **Step 2: 编译验证**

```bash
cd /Users/chuhao/IdeaProjects/fireworks && ./gradlew :fireworks-server:compileKotlin
```

- [ ] **Step 3: 提交**

```bash
git add fireworks-server/src/main/kotlin/site/hanabii/fireworks/app/family/FamilyTreeController.kt
git commit -m "feat(family): 新增 FamilyTreeController REST 控制器（11 个端点）"
```

---

### Task 8: 后端集成测试

**Files:**
- Create: `fireworks-server/src/test/kotlin/site/hanabii/fireworks/app/family/FamilyTreeServiceTest.kt`

- [ ] **Step 1: 创建 Service 层单元测试**

```kotlin
package site.hanabii.fireworks.app.family

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import site.hanabii.fireworks.app.AppException
import site.hanabii.fireworks.domain.family.FamilyLineageRepository
import site.hanabii.fireworks.domain.family.FamilyPerson
import site.hanabii.fireworks.domain.family.FamilyPersonRepository
import site.hanabii.fireworks.domain.family.FamilyTree
import site.hanabii.fireworks.domain.family.FamilyTreeRepository
import site.hanabii.fireworks.domain.family.PersonSpouse
import site.hanabii.fireworks.domain.family.PersonSpouseRepository
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FamilyTreeServiceTest {

    private val treeRepo = mock<FamilyTreeRepository>()
    private val personRepo = mock<FamilyPersonRepository>()
    private val spouseRepo = mock<PersonSpouseRepository>()
    private val lineageRepo = mock<FamilyLineageRepository>()

    private val service = FamilyTreeService(treeRepo, personRepo, spouseRepo, lineageRepo)

    private val testTree = FamilyTree(id = 1L, name = "张氏族谱")
    private val testPerson = FamilyPerson(
        id = 1L, familyTreeId = 1L, name = "张三", gender = "male",
        generationOrder = 0, sortOrder = 0
    )

    @BeforeEach
    fun setUp() {
        whenever(treeRepo.findAll()).thenReturn(listOf(testTree))
        whenever(treeRepo.findById(1L)).thenReturn(testTree)
        whenever(treeRepo.save(any())).thenAnswer { it.arguments[0] as FamilyTree }
        whenever(personRepo.findById(1L)).thenReturn(testPerson)
        whenever(personRepo.save(any())).thenAnswer { it.arguments[0] as FamilyPerson }
        whenever(personRepo.findByTreeId(1L)).thenReturn(listOf(testPerson))
        whenever(personRepo.findByFatherId(1L)).thenReturn(emptyList())
    }

    @Test
    fun `listTrees 返回所有族谱`() {
        val trees = service.listTrees()
        assertEquals(1, trees.size)
        assertEquals("张氏族谱", trees[0].name)
    }

    @Test
    fun `createTree 创建族谱成功`() {
        val tree = service.createTree("新族谱", null)
        assertNotNull(tree)
        assertEquals("新族谱", tree.name)
    }

    @Test
    fun `getTree 族谱不存在抛异常`() {
        whenever(treeRepo.findById(99L)).thenReturn(null)
        val ex = assertThrows<AppException> { service.getTree(99L) }
        assertEquals("族谱不存在: 99", ex.message)
    }

    @Test
    fun `addPerson 添加成员成功`() {
        val person = service.addPerson(1L, CreatePersonRequest(
            name = "李四", gender = "male"
        ))
        assertNotNull(person)
    }

    @Test
    fun `addPerson 父亲不属于当前族谱抛异常`() {
        val otherPerson = testPerson.copy(id = 2L, familyTreeId = 2L)
        whenever(personRepo.findById(2L)).thenReturn(otherPerson)
        val ex = assertThrows<AppException> {
            service.addPerson(1L, CreatePersonRequest(
                name = "李四", gender = "male", fatherId = 2L
            ))
        }
        assertEquals("父亲不属于当前族谱", ex.message)
    }

    @Test
    fun `getChildren 返回子节点和配偶`() {
        val child = testPerson.copy(id = 2L, name = "张子", fatherId = 1L)
        whenever(personRepo.findByFatherId(1L)).thenReturn(listOf(child))
        whenever(personRepo.findByFatherId(2L)).thenReturn(emptyList())
        whenever(spouseRepo.findPrimaryByHusbandId(1L)).thenReturn(null)

        val result = service.getChildren(1L, 1L)
        assertEquals(1, result.children.size)
        assertEquals("张子", result.children[0].name)
        assertEquals(null, result.person.spouse)
    }

    @Test
    fun `getAncestors 返回祖先链（含自身）`() {
        val grandpa = testPerson.copy(id = 0L, name = "太祖")
        val father = testPerson.copy(id = 5L, name = "父亲", fatherId = 0L)
        val me = testPerson.copy(id = 10L, name = "我", fatherId = 5L)

        whenever(personRepo.findById(10L)).thenReturn(me)
        whenever(personRepo.findById(5L)).thenReturn(father)
        whenever(personRepo.findById(0L)).thenReturn(grandpa)

        val ancestors = service.getAncestors(1L, 10L)
        assertEquals(3, ancestors.size)
        assertEquals("太祖", ancestors[0].name)
        assertEquals("父亲", ancestors[1].name)
        assertEquals("我", ancestors[2].name)
    }

    @Test
    fun `addSpouse 妻子非女性抛异常`() {
        val wife = testPerson.copy(id = 3L, gender = "male")
        whenever(personRepo.findById(3L)).thenReturn(wife)
        val ex = assertThrows<AppException> {
            service.addSpouse(1L, 1L, 3L, true)
        }
        assertEquals("配偶必须是女性", ex.message)
    }
}
```

- [ ] **Step 2: 运行测试**

```bash
cd /Users/chuhao/IdeaProjects/fireworks && ./gradlew :fireworks-server:test --tests "site.hanabii.fireworks.app.family.FamilyTreeServiceTest"
```

预期：所有测试通过。

- [ ] **Step 3: 提交**

```bash
git add fireworks-server/src/test/kotlin/site/hanabii/fireworks/app/family/FamilyTreeServiceTest.kt
git commit -m "test(family): 新增 FamilyTreeService 单元测试"
```

---

### Task 9: 前端 API 层

**Files:**
- Create: `fireworks-web/src/api/familyTree.ts`

- [ ] **Step 1: 创建 familyTree.ts API 封装**

```typescript
const API_BASE = import.meta.env.DEV ? '' : ''

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`, {
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    ...options,
  })
  if (!res.ok) {
    const body = await res.json().catch(() => ({}))
    throw new Error(body.message || `HTTP ${res.status}`)
  }
  if (res.status === 204) return undefined as T
  const text = await res.text()
  if (!text) return undefined as T
  return JSON.parse(text)
}

// ---------- 类型 ----------

export interface FamilyTree {
  id: number | null
  name: string
  description: string | null
  createdAt: string
  updatedAt: string
}

export interface FamilyPerson {
  id: number | null
  familyTreeId: number
  name: string
  gender: string
  birthDate: string | null
  deathDate: string | null
  biography: string | null
  fatherId: number | null
  generationOrder: number
  sortOrder: number
  createdAt: string
  updatedAt: string
}

export interface PersonSpouse {
  id: number | null
  husbandId: number
  wifeId: number
  marriageOrder: number
  isPrimary: boolean
  createdAt: string
}

export interface SpouseInfo {
  id: number
  name: string
}

export interface PersonWithSpouse {
  id: number
  name: string
  gender: string
  birthDate: string | null
  deathDate: string | null
  biography: string | null
  fatherId: number | null
  generationOrder: number
  sortOrder: number
  spouse: SpouseInfo | null
}

export interface ChildInfo {
  id: number
  name: string
  gender: string
  sortOrder: number
  hasChildren: boolean
}

export interface ChildrenResponse {
  person: PersonWithSpouse
  children: ChildInfo[]
}

export interface CreateTreeRequest {
  name: string
  description?: string | null
}

export interface UpdateTreeRequest {
  name?: string
  description?: string | null
}

export interface CreatePersonRequest {
  name: string
  gender?: string
  birthDate?: string | null
  deathDate?: string | null
  biography?: string | null
  fatherId?: number | null
  sortOrder?: number
}

export interface UpdatePersonRequest {
  name?: string
  gender?: string
  birthDate?: string | null
  deathDate?: string | null
  biography?: string | null
  fatherId?: number | null
  sortOrder?: number
}

export interface AddSpouseRequest {
  wifeId: number
  isPrimary: boolean
}

// ---------- 族谱 CRUD ----------

export function listTrees(): Promise<FamilyTree[]> {
  return request<FamilyTree[]>('/api/family-trees')
}

export function createTree(req: CreateTreeRequest): Promise<FamilyTree> {
  return request<FamilyTree>('/api/family-trees', {
    method: 'POST',
    body: JSON.stringify(req),
  })
}

export function getTree(id: number): Promise<FamilyTree> {
  return request<FamilyTree>(`/api/family-trees/${id}`)
}

export function updateTree(id: number, req: UpdateTreeRequest): Promise<FamilyTree> {
  return request<FamilyTree>(`/api/family-trees/${id}`, {
    method: 'PUT',
    body: JSON.stringify(req),
  })
}

export function deleteTree(id: number): Promise<void> {
  return request<void>(`/api/family-trees/${id}`, { method: 'DELETE' })
}

// ---------- 成员 CRUD ----------

export function listPersons(treeId: number, search?: string): Promise<FamilyPerson[]> {
  const query = search ? `?search=${encodeURIComponent(search)}` : ''
  return request<FamilyPerson[]>(`/api/family-trees/${treeId}/persons${query}`)
}

export function createPerson(treeId: number, req: CreatePersonRequest): Promise<FamilyPerson> {
  return request<FamilyPerson>(`/api/family-trees/${treeId}/persons`, {
    method: 'POST',
    body: JSON.stringify(req),
  })
}

export function getPerson(treeId: number, personId: number): Promise<FamilyPerson> {
  return request<FamilyPerson>(`/api/family-trees/${treeId}/persons/${personId}`)
}

export function updatePerson(treeId: number, personId: number, req: UpdatePersonRequest): Promise<FamilyPerson> {
  return request<FamilyPerson>(`/api/family-trees/${treeId}/persons/${personId}`, {
    method: 'PUT',
    body: JSON.stringify(req),
  })
}

export function deletePerson(treeId: number, personId: number): Promise<void> {
  return request<void>(`/api/family-trees/${treeId}/persons/${personId}`, { method: 'DELETE' })
}

// ---------- 树结构 ----------

export function listRoots(treeId: number): Promise<FamilyPerson[]> {
  return request<FamilyPerson[]>(`/api/family-trees/${treeId}/roots`)
}

export function getChildren(treeId: number, personId: number): Promise<ChildrenResponse> {
  return request<ChildrenResponse>(`/api/family-trees/${treeId}/persons/${personId}/children`)
}

export function getAncestors(treeId: number, personId: number): Promise<FamilyPerson[]> {
  return request<FamilyPerson[]>(`/api/family-trees/${treeId}/persons/${personId}/ancestors`)
}

// ---------- 配偶 ----------

export function getSpouses(treeId: number, husbandId: number): Promise<PersonSpouse[]> {
  return request<PersonSpouse[]>(`/api/family-trees/${treeId}/persons/${husbandId}/spouses`)
}

export function addSpouse(treeId: number, husbandId: number, req: AddSpouseRequest): Promise<PersonSpouse> {
  return request<PersonSpouse>(`/api/family-trees/${treeId}/persons/${husbandId}/spouses`, {
    method: 'POST',
    body: JSON.stringify(req),
  })
}

export function removeSpouse(treeId: number, husbandId: number, spouseId: number): Promise<void> {
  return request<void>(`/api/family-trees/${treeId}/persons/${husbandId}/spouses/${spouseId}`, { method: 'DELETE' })
}
```

- [ ] **Step 2: 类型检查**

```bash
cd /Users/chuhao/IdeaProjects/fireworks/fireworks-web && npx vue-tsc --noEmit src/api/familyTree.ts
```

- [ ] **Step 3: 提交**

```bash
git add fireworks-web/src/api/familyTree.ts
git commit -m "feat(family-tree): 新增前端 API 层 familyTree.ts"
```

---

### Task 10: 前端 Pinia Store

**Files:**
- Create: `fireworks-web/src/stores/familyTree.ts`

- [ ] **Step 1: 创建 familyTree Store**

```typescript
import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import * as api from '@/api/familyTree'
import type {
  FamilyTree,
  FamilyPerson,
  PersonSpouse,
  ChildrenResponse,
  CreateTreeRequest,
  UpdateTreeRequest,
  CreatePersonRequest,
  UpdatePersonRequest,
  AddSpouseRequest,
} from '@/api/familyTree'

export const useFamilyTreeStore = defineStore('familyTree', () => {
  const treeList = ref<FamilyTree[]>([])
  const currentTree = ref<FamilyTree | null>(null)
  const personMap = ref<Map<number, FamilyPerson>>(new Map())
  const loading = ref(false)
  const error = ref<string | null>(null)

  const treeCount = computed(() => treeList.value.length)

  // ========== 族谱 CRUD ==========

  async function fetchTrees() {
    loading.value = true
    error.value = null
    try {
      treeList.value = await api.listTrees()
    } catch (e: any) {
      error.value = e.message || '加载族谱列表失败'
    } finally {
      loading.value = false
    }
  }

  async function createTree(req: CreateTreeRequest): Promise<FamilyTree> {
    loading.value = true
    error.value = null
    try {
      const tree = await api.createTree(req)
      treeList.value.push(tree)
      return tree
    } catch (e: any) {
      error.value = e.message || '创建族谱失败'
      throw e
    } finally {
      loading.value = false
    }
  }

  async function deleteTree(id: number) {
    loading.value = true
    error.value = null
    try {
      await api.deleteTree(id)
      treeList.value = treeList.value.filter((t) => t.id !== id)
    } catch (e: any) {
      error.value = e.message || '删除族谱失败'
      throw e
    } finally {
      loading.value = false
    }
  }

  async function updateTree(id: number, req: UpdateTreeRequest): Promise<FamilyTree> {
    loading.value = true
    error.value = null
    try {
      const updated = await api.updateTree(id, req)
      const idx = treeList.value.findIndex((t) => t.id === id)
      if (idx !== -1) treeList.value[idx] = updated
      if (currentTree.value?.id === id) currentTree.value = updated
      return updated
    } catch (e: any) {
      error.value = e.message || '更新族谱失败'
      throw e
    } finally {
      loading.value = false
    }
  }

  // ========== 成员操作 ==========

  async function fetchRoots(treeId: number): Promise<FamilyPerson[]> {
    loading.value = true
    error.value = null
    try {
      const roots = await api.listRoots(treeId)
      for (const p of roots) personMap.value.set(p.id!, p)
      return roots
    } catch (e: any) {
      error.value = e.message || '加载根节点失败'
      throw e
    } finally {
      loading.value = false
    }
  }

  async function fetchChildren(treeId: number, personId: number): Promise<ChildrenResponse> {
    loading.value = true
    error.value = null
    try {
      const res = await api.getChildren(treeId, personId)
      personMap.value.set(res.person.id, {
        id: res.person.id,
        familyTreeId: treeId,
        name: res.person.name,
        gender: res.person.gender,
        birthDate: res.person.birthDate,
        deathDate: res.person.deathDate,
        biography: res.person.biography,
        fatherId: res.person.fatherId,
        generationOrder: res.person.generationOrder,
        sortOrder: res.person.sortOrder,
        createdAt: '',
        updatedAt: '',
      })
      return res
    } catch (e: any) {
      error.value = e.message || '加载子节点失败'
      throw e
    } finally {
      loading.value = false
    }
  }

  async function savePerson(
    treeId: number,
    personId: number | null,
    req: CreatePersonRequest | UpdatePersonRequest,
  ): Promise<FamilyPerson> {
    loading.value = true
    error.value = null
    try {
      const saved =
        personId != null
          ? await api.updatePerson(treeId, personId, req as UpdatePersonRequest)
          : await api.createPerson(treeId, req as CreatePersonRequest)
      if (saved.id != null) personMap.value.set(saved.id, saved)
      return saved
    } catch (e: any) {
      error.value = e.message || '保存成员失败'
      throw e
    } finally {
      loading.value = false
    }
  }

  async function deletePerson(treeId: number, personId: number) {
    loading.value = true
    error.value = null
    try {
      await api.deletePerson(treeId, personId)
      personMap.value.delete(personId)
    } catch (e: any) {
      error.value = e.message || '删除成员失败'
      throw e
    } finally {
      loading.value = false
    }
  }

  function getCachedPerson(id: number): FamilyPerson | undefined {
    return personMap.value.get(id)
  }

  // ========== 配偶操作 ==========

  async function addSpouse(treeId: number, husbandId: number, req: AddSpouseRequest): Promise<PersonSpouse> {
    loading.value = true
    error.value = null
    try {
      return await api.addSpouse(treeId, husbandId, req)
    } catch (e: any) {
      error.value = e.message || '添加配偶失败'
      throw e
    } finally {
      loading.value = false
    }
  }

  async function removeSpouse(treeId: number, husbandId: number, spouseId: number) {
    loading.value = true
    error.value = null
    try {
      await api.removeSpouse(treeId, husbandId, spouseId)
    } catch (e: any) {
      error.value = e.message || '删除配偶失败'
      throw e
    } finally {
      loading.value = false
    }
  }

  function clearError() {
    error.value = null
  }

  return {
    treeList,
    currentTree,
    personMap,
    loading,
    error,
    treeCount,
    fetchTrees,
    createTree,
    deleteTree,
    updateTree,
    fetchRoots,
    fetchChildren,
    savePerson,
    deletePerson,
    getCachedPerson,
    addSpouse,
    removeSpouse,
    clearError,
  }
})
```

- [ ] **Step 2: 类型检查**

```bash
cd /Users/chuhao/IdeaProjects/fireworks/fireworks-web && npx vue-tsc --noEmit src/stores/familyTree.ts
```

- [ ] **Step 3: 提交**

```bash
git add fireworks-web/src/stores/familyTree.ts
git commit -m "feat(family-tree): 新增 Pinia familyTree Store"
```

---

### Task 11: 前端路由和导航

**Files:**
- Modify: `fireworks-web/src/router/index.ts`
- Modify: `fireworks-web/src/App.vue`

- [ ] **Step 1: 路由新增族谱相关 3 条路由**

在 `router/index.ts` 的 routes 数组中，在 `/vault` 路由之后新增：

```typescript
    {
      path: '/family-trees',
      name: 'familyTreeList',
      component: () => import('../views/FamilyTreeListView.vue'),
    },
    {
      path: '/family-trees/:id',
      name: 'familyTree',
      component: () => import('../views/FamilyTreeView.vue'),
    },
    {
      path: '/family-trees/:id/person/:personId',
      name: 'personEdit',
      component: () => import('../views/PersonEditView.vue'),
    },
```

- [ ] **Step 2: App.vue 导航栏新增"族谱"菜单**

在 `App.vue` 的 `<el-menu>` 中，`<el-menu-item index="/vault">密码本</el-menu-item>` 之后新增：

```html
          <el-menu-item index="/family-trees">族谱</el-menu-item>
```

- [ ] **Step 3: 编译验证**

```bash
cd /Users/chuhao/IdeaProjects/fireworks/fireworks-web && npx vue-tsc --noEmit
```

- [ ] **Step 4: 提交**

```bash
git add fireworks-web/src/router/index.ts fireworks-web/src/App.vue
git commit -m "feat(family-tree): 新增族谱路由和导航菜单项"
```

---

### Task 12: 族谱列表页 FamilyTreeListView

**Files:**
- Create: `fireworks-web/src/views/FamilyTreeListView.vue`

- [ ] **Step 1: 创建族谱列表页**

```vue
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useFamilyTreeStore } from '@/stores/familyTree'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'

const store = useFamilyTreeStore()
const router = useRouter()

const dialogVisible = ref(false)
const form = ref({ name: '', description: '' })
const submitting = ref(false)

onMounted(() => {
  store.fetchTrees()
})

async function handleCreate() {
  if (!form.value.name.trim()) return
  submitting.value = true
  try {
    await store.createTree({ name: form.value.name.trim(), description: form.value.description.trim() || null })
    dialogVisible.value = false
    form.value = { name: '', description: '' }
    ElMessage.success('族谱创建成功')
  } catch {
    // store.error 已设置
  } finally {
    submitting.value = false
  }
}

async function handleDelete(tree: { id: number | null; name: string }) {
  try {
    await ElMessageBox.confirm(`确定要删除"${tree.name}"吗？该操作不可撤销。`, '删除族谱', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await store.deleteTree(tree.id!)
    ElMessage.success('族谱已删除')
  } catch {
    // 取消操作或出错
  }
}

function goToTree(id: number | null) {
  router.push(`/family-trees/${id}`)
}
</script>

<template>
  <div class="tree-list-page">
    <div class="page-header">
      <h2>族谱</h2>
      <el-button type="primary" :icon="Plus" @click="dialogVisible = true">新建族谱</el-button>
    </div>

    <el-alert v-if="store.error" :title="store.error" type="error" show-icon closable @close="store.clearError()" />

    <div v-loading="store.loading" class="tree-grid">
      <el-empty v-if="!store.loading && store.treeList.length === 0" description="暂无族谱，点击上方按钮创建" />
      <el-card
        v-for="tree in store.treeList"
        :key="tree.id"
        class="tree-card"
        shadow="hover"
        @click="goToTree(tree.id)"
      >
        <div class="card-content">
          <h3>{{ tree.name }}</h3>
          <p v-if="tree.description" class="card-desc">{{ tree.description }}</p>
        </div>
        <template #footer>
          <el-button text type="danger" size="small" @click.stop="handleDelete(tree)">删除</el-button>
        </template>
      </el-card>
    </div>

    <el-dialog v-model="dialogVisible" title="新建族谱" width="400px">
      <el-form :model="form" label-position="top">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" placeholder="例如：张氏族谱" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="可选描述" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleCreate">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.tree-list-page {
  max-width: 900px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 1.5rem;
}

.page-header h2 {
  margin: 0;
}

.tree-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 1rem;
}

.tree-card {
  cursor: pointer;
  transition: transform 0.2s;
}

.tree-card:hover {
  transform: translateY(-2px);
}

.card-content h3 {
  margin: 0 0 0.5rem;
}

.card-desc {
  margin: 0;
  color: var(--el-text-color-secondary);
  font-size: 0.9rem;
}
</style>
```

- [ ] **Step 2: 类型检查**

```bash
cd /Users/chuhao/IdeaProjects/fireworks/fireworks-web && npx vue-tsc --noEmit
```

- [ ] **Step 3: 提交**

```bash
git add fireworks-web/src/views/FamilyTreeListView.vue
git commit -m "feat(family-tree): 新增族谱列表页 FamilyTreeListView"
```

---

### Task 13: 族谱树状展示页 FamilyTreeView（核心页面）

**Files:**
- Create: `fireworks-web/src/views/FamilyTreeView.vue`

- [ ] **Step 1: 安装 @antv/g6 依赖**

```bash
cd /Users/chuhao/IdeaProjects/fireworks/fireworks-web && npm install @antv/g6
```

- [ ] **Step 2: 创建 FamilyTreeView**

```vue
<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useFamilyTreeStore } from '@/stores/familyTree'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Search, Plus } from '@element-plus/icons-vue'
import { Graph } from '@antv/g6'
import type { FamilyPerson, ChildInfo } from '@/api/familyTree'

const route = useRoute()
const router = useRouter()
const store = useFamilyTreeStore()

const treeId = Number(route.params.id)
const searchKeyword = ref('')
const searchResults = ref<FamilyPerson[]>([])
const personDrawerVisible = ref(false)
const editingPerson = ref<FamilyPerson | null>(null)
const drawerForm = ref({
  name: '',
  gender: 'male',
  birthDate: '',
  deathDate: '',
  biography: '',
  fatherId: null as number | null,
  sortOrder: 0,
})
const drawerSubmitting = ref(false)
const drawerMode = ref<'create' | 'edit'>('create')

let graph: Graph | null = null
const graphContainer = ref<HTMLDivElement>()

onMounted(async () => {
  store.currentTree = store.treeList.find((t) => t.id === treeId) || null
  if (!store.currentTree) {
    try {
      const { getTree } = await import('@/api/familyTree')
      store.currentTree = await getTree(treeId)
    } catch {
      ElMessage.error('族谱不存在')
      router.push('/family-trees')
      return
    }
  }
  await initGraph()
  await loadRoots()
})

onUnmounted(() => {
  graph?.destroy()
})

async function initGraph() {
  if (!graphContainer.value) return
  const options = {
    container: graphContainer.value,
    width: graphContainer.value.clientWidth,
    height: graphContainer.value.clientHeight,
    data: { nodes: [], edges: [] },
    layout: {
      type: 'compact-box',
      direction: 'LR',
      getId(d: any) { return d.id },
      getWidth() { return 180 },
      getHeight() { return 60 },
      getVGap() { return 16 },
      getHGap() { return 80 },
    },
    behaviors: ['drag-canvas', 'zoom-canvas', 'drag-element'],
    node: {
      type: 'rect',
      style: (d: any) => {
        const isMale = d.gender === 'male'
        return {
          size: [180, 60],
          radius: 8,
          fill: isMale ? '#e6f4ff' : '#fff0f6',
          stroke: isMale ? '#1677ff' : '#eb2f96',
          lineWidth: 1.5,
          labelText: d.label,
          labelPlacement: 'center',
          labelFontSize: 13,
          cursor: 'pointer',
        }
      },
    },
    edge: {
      type: 'polyline',
      style: (d: any) => ({
        stroke: d.isSpouseEdge ? '#eb2f96' : '#999',
        lineWidth: d.isSpouseEdge ? 1 : 1.5,
        lineDash: d.isSpouseEdge ? [4, 4] : undefined,
        endArrow: !d.isSpouseEdge,
      }),
    },
  }
  graph = new Graph(options)
  await graph.render()

  graph.on('node:click', (evt: any) => {
    const nodeData = evt.target?.id
    if (!nodeData) return
    const d = graph?.getNodeData(nodeData)
    if (!d) return
    onNodeClick(d as any)
  })
}

function buildNodeId(personId: number) {
  return `person-${personId}`
}

async function loadRoots() {
  try {
    const roots = await store.fetchRoots(treeId)
    for (const root of roots) {
      addNodeToGraph(root, null)
    }
    await graph?.render()
  } catch {
    ElMessage.error('加载根节点失败')
  }
}

function addNodeToGraph(person: FamilyPerson, parentId: number | null) {
  if (!graph) return
  const nodeId = buildNodeId(person.id!)

  const labelParts = [person.name]
  if (person.birthDate || person.deathDate) {
    labelParts.push(`${person.birthDate || '?'} - ${person.deathDate || '?'}`)
  }
  const label = labelParts.join('\n')

  graph.addNodeData({
    id: nodeId,
    label,
    gender: person.gender,
    personId: person.id,
    hasChildren: false,
  })

  if (parentId != null) {
    graph.addEdgeData({
      source: buildNodeId(parentId),
      target: nodeId,
    })
  }
}

async function onNodeClick(d: any) {
  if (d.gender === 'female') return

  const personId = d.personId
  try {
    const res = await store.fetchChildren(treeId, personId)

    // 移除旧子节点
    const existingEdges = graph?.getEdgeData() || []
    for (const edge of existingEdges) {
      if (edge.source === buildNodeId(personId)) {
        graph?.removeEdgeData([edge.id!])
        graph?.removeNodeData([edge.target])
      }
    }

    // 添加配偶节点
    if (res.person.spouse) {
      const spouseNodeId = buildNodeId(personId) + '-spouse'
      const husbandNodeId = buildNodeId(personId)
      graph?.addNodeData({
        id: spouseNodeId,
        label: `妻: ${res.person.spouse.name}`,
        gender: 'female',
      })
      graph?.addEdgeData({
        source: husbandNodeId,
        target: spouseNodeId,
        isSpouseEdge: true,
      })
    }

    // 添加子节点
    for (const child of res.children) {
      addNodeToGraph(
        {
          id: child.id,
          familyTreeId: treeId,
          name: child.name,
          gender: child.gender,
          birthDate: null,
          deathDate: null,
          biography: null,
          fatherId: personId,
          generationOrder: 0,
          sortOrder: child.sortOrder,
          createdAt: '',
          updatedAt: '',
        },
        personId,
      )
    }
    await graph?.render()
  } catch {
    // 错误已在 store 中处理
  }
}

// ---------- 搜索 ----------

async function handleSearch() {
  const kw = searchKeyword.value.trim()
  if (!kw) {
    searchResults.value = []
    return
  }
  try {
    const { listPersons } = await import('@/api/familyTree')
    searchResults.value = await listPersons(treeId, kw)
  } catch {
    // ignore
  }
}

function goToPerson(person: FamilyPerson) {
  searchResults.value = []
  searchKeyword.value = ''
  router.push(`/family-trees/${treeId}/person/${person.id}`)
}

// ---------- 新建/编辑成员抽屉 ----------

function openCreateDialog(fatherId: number | null = null) {
  drawerMode.value = 'create'
  editingPerson.value = null
  drawerForm.value = { name: '', gender: 'male', birthDate: '', deathDate: '', biography: '', fatherId, sortOrder: 0 }
  personDrawerVisible.value = true
}

function openEditDialog(person: FamilyPerson) {
  drawerMode.value = 'edit'
  editingPerson.value = person
  drawerForm.value = {
    name: person.name,
    gender: person.gender,
    birthDate: person.birthDate || '',
    deathDate: person.deathDate || '',
    biography: person.biography || '',
    fatherId: person.fatherId,
    sortOrder: person.sortOrder,
  }
  personDrawerVisible.value = true
}

async function handleSavePerson() {
  if (!drawerForm.value.name.trim()) return
  drawerSubmitting.value = true
  try {
    if (drawerMode.value === 'create') {
      await store.savePerson(treeId, null, drawerForm.value)
      ElMessage.success('成员创建成功')
    } else {
      await store.savePerson(treeId, editingPerson.value!.id!, drawerForm.value)
      ElMessage.success('成员更新成功')
    }
    personDrawerVisible.value = false
    // 重新加载根节点以刷新树
    if (graph) {
      graph.setData({ nodes: [], edges: [] })
      await graph.render()
    }
    await loadRoots()
  } catch {
    // store.error 已设置
  } finally {
    drawerSubmitting.value = false
  }
}

async function handleDeletePerson(person: FamilyPerson) {
  try {
    await ElMessageBox.confirm(`确定要删除"${person.name}"吗？其子节点将成为新的根节点。`, '删除成员', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await store.deletePerson(treeId, person.id!)
    ElMessage.success('成员已删除')
    if (graph) {
      graph.setData({ nodes: [], edges: [] })
      await graph.render()
    }
    await loadRoots()
  } catch {
    // 取消或出错
  }
}

// ---------- 编辑跳转 ----------

function goToPersonEdit(personId: number) {
  router.push(`/family-trees/${treeId}/person/${personId}`)
}
</script>

<template>
  <div class="tree-view-page">
    <!-- 顶部栏 -->
    <div class="tree-toolbar">
      <el-button text :icon="ArrowLeft" @click="router.push('/family-trees')">返回列表</el-button>
      <h3>{{ store.currentTree?.name || '加载中...' }}</h3>
      <div class="toolbar-right">
        <el-autocomplete
          v-model="searchKeyword"
          :fetch-suggestions="(q: string, cb: any) => { handleSearch(); cb(searchResults.map(p => ({ value: p.name, person: p }))) }"
          placeholder="搜索成员..."
          :prefix-icon="Search"
          clearable
          @select="(item: any) => goToPerson(item.person)"
          style="width: 220px"
        />
        <el-button type="primary" :icon="Plus" size="small" @click="openCreateDialog(null)">添加成员</el-button>
      </div>
    </div>

    <!-- 树画布 -->
    <div ref="graphContainer" class="graph-canvas" />

    <!-- 新建/编辑抽屉 -->
    <el-drawer v-model="personDrawerVisible" :title="drawerMode === 'create' ? '添加成员' : '编辑成员'" size="400px">
      <el-form :model="drawerForm" label-position="top">
        <el-form-item label="姓名" required>
          <el-input v-model="drawerForm.name" placeholder="姓名" />
        </el-form-item>
        <el-form-item label="性别">
          <el-radio-group v-model="drawerForm.gender">
            <el-radio value="male">男</el-radio>
            <el-radio value="female">女</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="出生日期">
          <el-input v-model="drawerForm.birthDate" placeholder="如：1900-01-01" />
        </el-form-item>
        <el-form-item label="逝世日期">
          <el-input v-model="drawerForm.deathDate" placeholder="如：1980-12-31" />
        </el-form-item>
        <el-form-item label="生平简介">
          <el-input v-model="drawerForm.biography" type="textarea" :rows="3" placeholder="生平简介" />
        </el-form-item>
        <el-form-item label="排行">
          <el-input-number v-model="drawerForm.sortOrder" :min="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="personDrawerVisible = false">取消</el-button>
        <el-button type="primary" :loading="drawerSubmitting" @click="handleSavePerson">
          {{ drawerMode === 'create' ? '创建' : '保存' }}
        </el-button>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.tree-view-page {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 120px);
  margin: -1.5rem;
}

.tree-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.75rem 1.5rem;
  border-bottom: 1px solid var(--el-border-color-light);
  flex-shrink: 0;
}

.tree-toolbar h3 {
  margin: 0;
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.graph-canvas {
  flex: 1;
  min-height: 0;
}
</style>
```

- [ ] **Step 3: 类型检查**

```bash
cd /Users/chuhao/IdeaProjects/fireworks/fireworks-web && npx vue-tsc --noEmit
```

- [ ] **Step 4: 提交**

```bash
git add fireworks-web/src/views/FamilyTreeView.vue
git commit -m "feat(family-tree): 新增族谱树状展示页 FamilyTreeView（G6 TreeGraph）"
```

---

### Task 14: 成员编辑页 PersonEditView

**Files:**
- Create: `fireworks-web/src/views/PersonEditView.vue`

- [ ] **Step 1: 创建 PersonEditView**

```vue
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useFamilyTreeStore } from '@/stores/familyTree'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import type { FamilyPerson, PersonSpouse } from '@/api/familyTree'
import * as api from '@/api/familyTree'

const route = useRoute()
const router = useRouter()
const store = useFamilyTreeStore()

const treeId = Number(route.params.id)
const personId = Number(route.params.personId)
const person = ref<FamilyPerson | null>(null)
const spouses = ref<PersonSpouse[]>([])
const loading = ref(false)
const saving = ref(false)

const form = ref({
  name: '',
  gender: 'male',
  birthDate: '',
  deathDate: '',
  biography: '',
  fatherId: null as number | null,
  sortOrder: 0,
})

onMounted(async () => {
  loading.value = true
  try {
    const [p, s] = await Promise.all([
      api.getPerson(treeId, personId),
      api.getSpouses(treeId, personId),
    ])
    person.value = p
    spouses.value = s
    form.value = {
      name: p.name,
      gender: p.gender,
      birthDate: p.birthDate || '',
      deathDate: p.deathDate || '',
      biography: p.biography || '',
      fatherId: p.fatherId,
      sortOrder: p.sortOrder,
    }
  } catch {
    ElMessage.error('加载成员信息失败')
    router.push(`/family-trees/${treeId}`)
  } finally {
    loading.value = false
  }
})

async function handleSave() {
  if (!form.value.name.trim()) return
  saving.value = true
  try {
    const updated = await api.updatePerson(treeId, personId, form.value)
    person.value = updated
    ElMessage.success('保存成功')
  } catch (e: any) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function handleDelete() {
  try {
    const { ElMessageBox } = await import('element-plus')
    await ElMessageBox.confirm('确定要删除此成员吗？', '确认删除', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await store.deletePerson(treeId, personId)
    ElMessage.success('已删除')
    router.push(`/family-trees/${treeId}`)
  } catch {
    // 取消
  }
}

function goBack() {
  router.push(`/family-trees/${treeId}`)
}
</script>

<template>
  <div class="person-edit-page">
    <div class="page-header">
      <el-button text :icon="ArrowLeft" @click="goBack">返回族谱</el-button>
      <h3>{{ person?.name || '加载中...' }}</h3>
    </div>

    <div v-loading="loading" class="edit-content">
      <el-card>
        <template #header>基本信息</template>
        <el-form :model="form" label-width="80px">
          <el-form-item label="姓名" required>
            <el-input v-model="form.name" />
          </el-form-item>
          <el-form-item label="性别">
            <el-radio-group v-model="form.gender">
              <el-radio value="male">男</el-radio>
              <el-radio value="female">女</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="出生">
            <el-input v-model="form.birthDate" placeholder="如：1900-01-01" />
          </el-form-item>
          <el-form-item label="逝世">
            <el-input v-model="form.deathDate" placeholder="如：1980-12-31" />
          </el-form-item>
          <el-form-item label="排行">
            <el-input-number v-model="form.sortOrder" :min="0" />
          </el-form-item>
          <el-form-item label="生平">
            <el-input v-model="form.biography" type="textarea" :rows="4" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
            <el-button type="danger" plain @click="handleDelete">删除成员</el-button>
          </el-form-item>
        </el-form>
      </el-card>

      <el-card v-if="person?.gender === 'male' && spouses.length > 0" style="margin-top: 1rem">
        <template #header>配偶</template>
        <div v-for="s in spouses" :key="s.id" class="spouse-item">
          <span>配偶 #{{ s.marriageOrder }}</span>
          <el-tag v-if="s.isPrimary" type="success" size="small">原配</el-tag>
        </div>
      </el-card>
    </div>
  </div>
</template>

<style scoped>
.person-edit-page {
  max-width: 640px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  align-items: center;
  gap: 1rem;
  margin-bottom: 1.5rem;
}

.page-header h3 {
  margin: 0;
}

.spouse-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.5rem 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
</style>
```

- [ ] **Step 2: 类型检查**

```bash
cd /Users/chuhao/IdeaProjects/fireworks/fireworks-web && npx vue-tsc --noEmit
```

- [ ] **Step 3: 提交**

```bash
git add fireworks-web/src/views/PersonEditView.vue
git commit -m "feat(family-tree): 新增成员编辑页 PersonEditView"
```

---

### Task 15: 端到端验证

- [ ] **Step 1: 启动应用（开发模式）**

```bash
cd /Users/chuhao/IdeaProjects/fireworks && ./gradlew :fireworks-server:bootRunDev
```

- [ ] **Step 2: 功能验证清单**

在浏览器中打开 `http://localhost:5173`，逐项验证：

1. 导航栏出现"族谱"菜单 — 点击跳转到 `/family-trees`
2. 族谱列表页 — 点击"新建族谱"创建族谱
3. 点击族谱卡片进入树状展示页
4. 点击"添加成员"添加根节点男性成员
5. 点击男性节点展开子节点（女性节点无展开按钮）
6. 搜索成员并跳转到编辑页
7. 编辑页修改信息并保存
8. 删除成员（子节点成为新根）并验证树更新

- [ ] **Step 3: 运行全部后端测试**

```bash
cd /Users/chuhao/IdeaProjects/fireworks && ./gradlew :fireworks-server:test
```

- [ ] **Step 4: 运行前端检查**

```bash
cd /Users/chuhao/IdeaProjects/fireworks/fireworks-web && npm run type-check && npm run test:unit
```

- [ ] **Step 5: 提交（如有微调）**

```bash
git add -A && git commit -m "chore(family-tree): 端到端验证后的微调"
```
