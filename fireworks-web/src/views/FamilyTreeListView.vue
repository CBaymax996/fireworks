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
