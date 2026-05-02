<script setup lang="ts">
import { onMounted, onUnmounted, ref, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useFamilyTreeStore } from '@/stores/familyTree'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Search, Plus } from '@element-plus/icons-vue'
import { Graph } from '@antv/g6'
import type { FamilyPerson } from '@/api/familyTree'
import * as api from '@/api/familyTree'

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
  gender: 'male' as string,
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
      store.currentTree = await api.getTree(treeId)
    } catch {
      ElMessage.error('族谱不存在')
      router.push('/family-trees')
      return
    }
  }
  await nextTick()
  await initGraph()
  await loadRoots()
})

onUnmounted(() => {
  graph?.destroy()
})

async function initGraph() {
  if (!graphContainer.value) return
  graph = new Graph({
    container: graphContainer.value,
    width: graphContainer.value.clientWidth,
    height: graphContainer.value.clientHeight,
    data: { nodes: [], edges: [] },
    layout: {
      type: 'compact-box',
      direction: 'LR',
      getWidth: () => 180,
      getHeight: () => 60,
      getVGap: () => 16,
      getHGap: () => 80,
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
          lineWidth: 2,
          labelText: d.label,
          labelPlacement: 'center',
          labelFontSize: 13,
          cursor: d.gender === 'male' ? 'pointer' : 'default',
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
  })

  await graph.render()

  graph.on('node:click', (evt: any) => {
    const nodeId = evt.itemId
    if (!nodeId) return
    const nodeData = graph?.getNodeData(nodeId)
    if (!nodeData) return
    onNodeClick(nodeData as any)
  })
}

function buildNodeId(personId: number): string {
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

  graph.addNodeData([{
    id: nodeId,
    label,
    gender: person.gender,
    personId: person.id,
    hasChildren: false,
  }])

  if (parentId != null) {
    graph.addEdgeData([{
      source: buildNodeId(parentId),
      target: nodeId,
    }])
  }
}

async function onNodeClick(d: any) {
  if (d.gender === 'female') return

  const personId = d.personId as number
  try {
    const res = await store.fetchChildren(treeId, personId)

    // Remove old children of this node
    const existingEdges = graph?.getEdgeData() || []
    for (const edge of existingEdges) {
      if (edge.source === buildNodeId(personId)) {
        if (edge.id) {
          graph?.removeEdgeData([edge.id])
        }
        graph?.removeNodeData([edge.target])
      }
    }

    // Add spouse node
    if (res.person.spouse) {
      const spouseNodeId = buildNodeId(personId) + '-spouse'
      graph?.addNodeData([{
        id: spouseNodeId,
        label: `妻: ${res.person.spouse.name}`,
        gender: 'female',
      }])
      graph?.addEdgeData([{
        source: buildNodeId(personId),
        target: spouseNodeId,
        isSpouseEdge: true,
      }])
    }

    // Add children
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
    // error handled in store
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

function openCreateDialog(fatherId: number | null = null) {
  drawerMode.value = 'create'
  editingPerson.value = null
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
    // Refresh tree
    if (graph) {
      graph.setData({ nodes: [], edges: [] })
      await graph.render()
    }
    await loadRoots()
  } catch {
    // store.error
  } finally {
    drawerSubmitting.value = false
  }
}

async function handleDeletePerson(person: FamilyPerson) {
  try {
    await ElMessageBox.confirm(
      `确定要删除"${person.name}"吗？其子节点将成为新的根节点。`,
      '删除成员',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
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

    <!-- 树画布 -->
    <div ref="graphContainer" class="graph-canvas" />

    <!-- 新建/编辑抽屉 -->
    <el-drawer
      v-model="personDrawerVisible"
      :title="drawerMode === 'create' ? '添加成员' : '编辑成员'"
      size="400px"
    >
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
