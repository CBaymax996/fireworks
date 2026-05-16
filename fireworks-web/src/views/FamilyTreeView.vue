<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useFamilyTreeStore } from '@/stores/familyTree'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Search, Plus, Loading } from '@element-plus/icons-vue'
import type { FamilyPerson, SpouseInfo, ChildrenResponse } from '@/api/familyTree'
import * as api from '@/api/familyTree'

const route = useRoute()
const router = useRouter()
const store = useFamilyTreeStore()

const treeId = Number(route.params.id)

// ---------- 布局常量 ----------
const GAP_X = 150
const GAP_Y = 80
const ROW_HEIGHT = 28
const PADDING = 60

// ---------- 树数据结构 ----------
interface PersonNode {
  id: number
  name: string
  gender: string
  spouse: SpouseInfo | null
  children: PersonNode[]
  collapsed: boolean
  x: number
  depth: number
}

const roots = ref<PersonNode[]>([])
const loadingTree = ref(false)

// ---------- 构建完整树 ----------
async function loadFullTree() {
  loadingTree.value = true
  try {
    const rawRoots = await store.fetchRoots(treeId)
    const nodes = await Promise.all(rawRoots.map((r) => buildNode(r.id!, r.name, r.gender, 0)))
    roots.value = nodes
    layoutTree()
  } finally {
    loadingTree.value = false
  }
}

async function buildNode(id: number, name: string, gender: string, depth: number): Promise<PersonNode> {
  let spouse: SpouseInfo | null = null
  const childNodes: PersonNode[] = []

  try {
    const res: ChildrenResponse = await store.fetchChildren(treeId, id)
    spouse = res.person.spouse
    for (const c of res.children) {
      try {
        childNodes.push(await buildNode(c.id, c.name, c.gender, depth + 1))
      } catch {
        childNodes.push({ id: c.id, name: c.name, gender: c.gender, spouse: null, children: [], collapsed: false, x: 0, depth: depth + 1 })
      }
    }
  } catch {
    // 无子节点或加载失败，作为叶节点
  }

  return { id, name, gender, spouse, children: childNodes, collapsed: false, x: 0, depth }
}

// ---------- 布局算法：后序遍历分配列坐标 ----------
function layoutTree() {
  let nextCol = 0
  function walk(node: PersonNode) {
    if (node.children.length === 0) {
      node.x = nextCol++
      return
    }
    for (const child of node.children) walk(child)
    const first = node.children[0]!
    const last = node.children[node.children.length - 1]!
    node.x = (first.x + last.x) / 2
  }
  for (const root of roots.value) walk(root)
}

// ---------- 可见节点（折叠状态）----------
const visibleNodes = computed<PersonNode[]>(() => {
  const result: PersonNode[] = []
  function walk(node: PersonNode) {
    result.push(node)
    if (!node.collapsed) {
      for (const child of node.children) walk(child)
    }
  }
  for (const root of roots.value) walk(root)
  return result
})

// ---------- 世代行 ----------
const generations = computed<{ depth: number; nodes: PersonNode[] }[]>(() => {
  const map = new Map<number, PersonNode[]>()
  for (const node of visibleNodes.value) {
    const list = map.get(node.depth)
    if (list) list.push(node)
    else map.set(node.depth, [node])
  }
  const result: { depth: number; nodes: PersonNode[] }[] = []
  for (const [depth, nodes] of map) {
    nodes.sort((a, b) => a.x - b.x)
    result.push({ depth, nodes })
  }
  result.sort((a, b) => a.depth - b.depth)
  return result
})

// ---------- SVG 连线 ----------
interface LineData {
  key: string
  d: string
}

const treeLines = computed<LineData[]>(() => {
  const lines: LineData[] = []

  function addLines(node: PersonNode) {
    if (node.collapsed || node.children.length === 0) return

    const parentCenterX = node.x * GAP_X + PADDING
    const parentBottomY = node.depth * GAP_Y + PADDING + ROW_HEIGHT + 4

    const childTopY = (node.depth + 1) * GAP_Y + PADDING
    const midY = (parentBottomY + childTopY) / 2

    const childrenX = node.children.map((c) => c.x * GAP_X + PADDING)

    // 从父节点垂直到中点
    lines.push({ key: `v_${node.id}`, d: `M ${parentCenterX} ${parentBottomY} L ${parentCenterX} ${midY}` })

    if (childrenX.length > 1) {
      // 中点水平横线
      lines.push({ key: `h_${node.id}`, d: `M ${childrenX[0]} ${midY} L ${childrenX[childrenX.length - 1]} ${midY}` })
    }

    // 垂直分叉到各子节点
    for (let i = 0; i < childrenX.length; i++) {
      lines.push({ key: `cv_${node.id}_${i}`, d: `M ${childrenX[i]} ${midY} L ${childrenX[i]} ${childTopY}` })
    }

    for (const child of node.children) addLines(child)
  }

  for (const root of roots.value) addLines(root)
  return lines
})

// ---------- 画布尺寸 ----------
const contentSize = computed(() => {
  let maxCol = 0
  let maxDepth = 0
  for (const node of visibleNodes.value) {
    if (node.x > maxCol) maxCol = node.x
    if (node.depth > maxDepth) maxDepth = node.depth
  }
  return {
    width: Math.max((maxCol + 0.5) * GAP_X + PADDING * 2, 400),
    height: (maxDepth + 1) * GAP_Y + PADDING * 2,
  }
})

const svgViewBox = computed(() => `0 0 ${contentSize.value.width} ${contentSize.value.height}`)

// ---------- 辅助函数 ----------
function genTop(depth: number) {
  return `${depth * GAP_Y + PADDING}px`
}

function cellLeft(node: PersonNode) {
  return `${node.x * GAP_X + PADDING}px`
}

// ---------- 节点点击：查看/编辑详情 ----------
async function onNodeClick(node: PersonNode) {
  try {
    const person = await api.getPerson(treeId, node.id)
    openEditDialog(person)
  } catch {
    ElMessage.error('加载成员信息失败')
  }
}

function toggleCollapse(node: PersonNode) {
  node.collapsed = !node.collapsed
}

// ---------- 刷新树 ----------
async function refreshTree() {
  roots.value = []
  await loadFullTree()
}

// ---------- 搜素 ----------
const searchKeyword = ref('')
const searchResults = ref<FamilyPerson[]>([])

async function handleSearch() {
  const kw = searchKeyword.value.trim()
  if (!kw) {
    searchResults.value = []
    return
  }
  try {
    searchResults.value = await api.listPersons(treeId, kw)
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
const personDrawerVisible = ref(false)
const editingPerson = ref<FamilyPerson | null>(null)
const drawerForm = ref({
  name: '',
  gender: 'male' as string,
  birthDate: '',
  deathDate: '',
  biography: '',
  fatherId: null as number | null,
  sortOrder: 0,
})
const drawerSubmitting = ref(false)
const drawerMode = ref<'create' | 'edit'>('create')
const fatherLabelCache = ref<Record<number, string>>({})
const fatherOptions = ref<{ id: number; name: string }[]>([])
const fatherSearchLoading = ref(false)

function openCreateDialog(fatherId: number | null = null) {
  drawerMode.value = 'create'
  editingPerson.value = null
  fatherOptions.value = []
  drawerForm.value = {
    name: '',
    gender: 'male',
    birthDate: '',
    deathDate: '',
    biography: '',
    fatherId,
    sortOrder: 0,
  }
  personDrawerVisible.value = true
}

async function openEditDialog(person: FamilyPerson) {
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
  // 初始化父节点下拉选项
  if (person.fatherId) {
    if (!fatherLabelCache.value[person.fatherId]) {
      try {
        const father = await api.getPerson(treeId, person.fatherId)
        fatherLabelCache.value[person.fatherId] = father.name
      } catch {
        // ignore
      }
    }
    const name = fatherLabelCache.value[person.fatherId] || ''
    fatherOptions.value = [{ id: person.fatherId, name }]
  } else {
    fatherOptions.value = []
  }
  personDrawerVisible.value = true
}

async function handleDeletePerson() {
  if (!editingPerson.value) return
  try {
    await ElMessageBox.confirm(
      `确定要删除"${editingPerson.value.name}"吗？其子节点将成为新的根节点。`,
      '删除成员',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' },
    )
    await store.deletePerson(treeId, editingPerson.value.id!)
    ElMessage.success('成员已删除')
    personDrawerVisible.value = false
    await refreshTree()
  } catch {
    // 取消或出错
  }
}

function startAddChild() {
  if (!editingPerson.value) return
  drawerMode.value = 'create'
  drawerForm.value = {
    name: '',
    gender: 'male',
    birthDate: '',
    deathDate: '',
    biography: '',
    fatherId: editingPerson.value.id,
    sortOrder: 0,
  }
}

// ---------- 父节点搜索 ----------
async function fetchFatherOptions(keyword: string) {
  if (!keyword.trim()) {
    fatherOptions.value = []
    return
  }
  fatherSearchLoading.value = true
  try {
    const persons = await api.listPersons(treeId, keyword)
    fatherOptions.value = persons.map((p) => ({ id: p.id!, name: p.name }))
  } catch {
    fatherOptions.value = []
  } finally {
    fatherSearchLoading.value = false
  }
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
    await refreshTree()
  } catch {
    // store.error
  } finally {
    drawerSubmitting.value = false
  }
}

// ---------- 生命周期 ----------
onMounted(async () => {
  store.currentTree = store.treeList.find((t) => t.id === treeId) || null
  if (!store.currentTree) {
    try {
      store.currentTree = await api.getTree(treeId)
    } catch {
      ElMessage.error('族谱不存在')
      router.push('/family-trees')
      return
    }
  }
  await loadFullTree()
})
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
          :fetch-suggestions="
            (q: string, cb: any) => {
              handleSearch()
              cb(searchResults.map((p) => ({ value: p.name, person: p })))
            }
          "
          placeholder="搜索成员..."
          :prefix-icon="Search"
          clearable
          @select="(item: any) => goToPerson(item.person)"
          style="width: 220px"
        />
        <el-button type="primary" :icon="Plus" size="small" @click="openCreateDialog(null)">添加成员</el-button>
      </div>
    </div>

    <!-- 树区域 -->
    <div class="tree-container">
      <div v-if="loadingTree" class="tree-placeholder">
        <el-icon class="is-loading" :size="24"><Loading /></el-icon>
        <span>加载族谱中...</span>
      </div>
      <div v-else-if="!roots.length" class="tree-placeholder">
        <span>暂无成员，请点击「添加成员」开始构建族谱</span>
      </div>
      <div v-else class="tree-scroll">
        <div class="tree-content" :style="{ width: contentSize.width + 'px', height: contentSize.height + 'px' }">
          <!-- 世代行 -->
          <div
            v-for="gen in generations"
            :key="gen.depth"
            class="gen-row"
            :style="{ top: genTop(gen.depth) }"
          >
            <div
              v-for="node in gen.nodes"
              :key="node.id"
              class="person-cell"
              :style="{ left: cellLeft(node) }"
            >
              <span
                class="person-name"
                :class="{ male: node.gender === 'male', female: node.gender === 'female' }"
                @click="onNodeClick(node)"
              >{{ node.name }}</span>
              <template v-if="node.spouse">
                <span class="spouse-connector">——</span>
                <span class="person-name female" @click="onNodeClick({ id: node.spouse.id, name: node.spouse.name, gender: 'female', spouse: null, children: [], collapsed: false, x: 0, depth: 0 })">{{ node.spouse.name }}</span>
              </template>
              <span
                v-if="node.children.length"
                class="collapse-toggle"
                @click.stop="toggleCollapse(node)"
              >[{{ node.collapsed ? '+' : '−' }}]</span>
            </div>
          </div>
          <!-- SVG 连线 -->
          <svg
            class="tree-lines"
            :viewBox="svgViewBox"
            preserveAspectRatio="xMidYMid meet"
          >
            <path
              v-for="line in treeLines"
              :key="line.key"
              :d="line.d"
              class="tree-line-path"
            />
          </svg>
        </div>
      </div>

      <!-- 编辑面板（嵌入树容器内） -->
      <transition name="panel-slide">
        <div v-if="personDrawerVisible" class="person-panel">
          <div class="person-panel-header">
            <h4>{{ drawerMode === 'create' ? '添加成员' : '编辑成员' }}</h4>
            <el-button text class="panel-close-btn" @click="personDrawerVisible = false">&times;</el-button>
          </div>
          <div class="person-panel-body">
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
              <el-form-item v-if="drawerMode === 'edit'" label="父节点">
                <el-select
                  v-model="drawerForm.fatherId"
                  filterable
                  remote
                  reserve-keyword
                  clearable
                  :remote-method="fetchFatherOptions"
                  :loading="fatherSearchLoading"
                  placeholder="搜索并选择新父节点..."
                >
                  <el-option
                    v-for="p in fatherOptions"
                    :key="p.id"
                    :label="p.name"
                    :value="p.id"
                  />
                </el-select>
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
          </div>
          <div class="person-panel-footer">
            <el-button @click="personDrawerVisible = false">取消</el-button>
            <el-button v-if="drawerMode === 'edit'" type="danger" @click="handleDeletePerson">删除</el-button>
            <el-button v-if="drawerMode === 'edit'" type="success" @click="startAddChild">
              添加子节点
            </el-button>
            <el-button type="primary" :loading="drawerSubmitting" @click="handleSavePerson">
              {{ drawerMode === 'create' ? '创建' : '保存' }}
            </el-button>
          </div>
        </div>
      </transition>
    </div>
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

/* ---- 树容器 ---- */
.tree-container {
  flex: 1;
  min-height: 0;
  position: relative;
}

.tree-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  height: 100%;
  color: var(--el-text-color-secondary);
  font-size: 14px;
}

.tree-scroll {
  width: 100%;
  height: 100%;
  overflow: auto;
}

.tree-content {
  position: relative;
}

/* ---- 世代行 ---- */
.gen-row {
  position: absolute;
  left: 0;
  right: 0;
  height: 28px;
  white-space: nowrap;
}

/* ---- 人员节点 ---- */
.person-cell {
  position: absolute;
  display: inline-flex;
  align-items: center;
  gap: 2px;
  height: 28px;
  font-family: 'SF Mono', 'Cascadia Code', 'Menlo', 'Consolas', monospace;
  font-size: 14px;
  line-height: 1;
  white-space: nowrap;
}

.person-name {
  transition: opacity 0.15s;
  cursor: pointer;
}

.person-name.male {
  color: var(--el-color-primary);
}

.person-name.female {
  color: var(--el-color-danger);
}

.person-name:hover {
  opacity: 0.75;
}

.spouse-connector {
  color: var(--el-text-color-disabled);
  font-family: 'SF Mono', 'Cascadia Code', 'Menlo', 'Consolas', monospace;
  font-size: 14px;
  padding: 0 2px;
}

.collapse-toggle {
  font-family: 'SF Mono', 'Cascadia Code', 'Menlo', 'Consolas', monospace;
  font-size: 12px;
  color: var(--el-text-color-disabled);
  margin-left: 2px;
  cursor: pointer;
  user-select: none;
}

.collapse-toggle:hover {
  color: var(--el-text-color-primary);
}

/* ---- SVG 连线 ---- */
.tree-lines {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  overflow: visible;
}

:deep(.tree-line-path) {
  stroke: var(--el-border-color);
  stroke-width: 1.5;
  fill: none;
  stroke-linecap: round;
  stroke-linejoin: round;
}

/* ---- 编辑面板 ---- */
.person-panel {
  position: absolute;
  right: 0;
  top: 0;
  bottom: 0;
  width: 340px;
  background: var(--el-bg-color);
  border-left: 1px solid var(--el-border-color);
  z-index: 10;
  display: flex;
  flex-direction: column;
  box-shadow: -4px 0 16px rgba(0, 0, 0, 0.08);
}

.person-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 1rem 1rem 0.5rem;
  flex-shrink: 0;
}

.person-panel-header h4 {
  margin: 0;
  font-size: 15px;
}

.panel-close-btn {
  font-size: 18px;
  padding: 2px 6px;
}

.person-panel-body {
  flex: 1;
  padding: 0 1rem;
  overflow-y: auto;
}

.person-panel-footer {
  display: flex;
  justify-content: flex-end;
  gap: 0.5rem;
  padding: 0.75rem 1rem;
  border-top: 1px solid var(--el-border-color-light);
  flex-shrink: 0;
}

/* ---- 面板滑入动画 ---- */
.panel-slide-enter-active,
.panel-slide-leave-active {
  transition: transform 0.25s ease;
}

.panel-slide-enter-from,
.panel-slide-leave-to {
  transform: translateX(100%);
}
</style>
