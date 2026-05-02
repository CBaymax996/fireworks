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
        <div v-for="s in spouses" :key="s.id ?? 0" class="spouse-item">
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
