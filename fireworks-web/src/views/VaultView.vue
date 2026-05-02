<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessageBox } from 'element-plus'
import { Plus, Lock, Collection } from '@element-plus/icons-vue'
import { useVaultStore } from '@/stores/vault'
import type { PasswordEntry, EntryRequest, UpdateEntryRequest } from '@/api/vault'

const vault = useVaultStore()

// ---------- 表单数据 ----------
const masterPassword = ref('')

// 新建 / 编辑条目
const showForm = ref(false)
const editingId = ref<number | null>(null)
const form = ref<EntryRequest>({
  website: '',
  username: '',
  notes: '',
  counter: 1,
  length: 16,
  useLowercase: true,
  useUppercase: true,
  useDigits: true,
  useSymbols: true,
})

// 派生密码弹窗
const showPassword = ref<number | null>(null)
const passwordText = ref('')
const passwordCounter = ref<number | null>(null)

// 历史回放 counter（每条目独立）
const replayCounters = ref<Record<number, number>>({})

onMounted(() => {
  vault.checkStatus()
})

// ---------- 初始化 / 认证 ----------

async function handleSetup() {
  await vault.setup(masterPassword.value)
  masterPassword.value = ''
}

async function handleLogin() {
  await vault.login(masterPassword.value)
  masterPassword.value = ''
}

async function handleLogout() {
  await vault.logout()
}

// ---------- 条目 CRUD ----------

function openCreateForm() {
  editingId.value = null
  form.value = {
    website: '',
    username: '',
    notes: '',
    counter: 1,
    length: 16,
    useLowercase: true,
    useUppercase: true,
    useDigits: true,
    useSymbols: true,
  }
  showForm.value = true
}

function openEditForm(entry: PasswordEntry) {
  editingId.value = entry.id
  form.value = {
    website: entry.website,
    username: entry.username,
    notes: entry.notes,
    length: entry.length,
    useLowercase: entry.useLowercase,
    useUppercase: entry.useUppercase,
    useDigits: entry.useDigits,
    useSymbols: entry.useSymbols,
  }
  showForm.value = true
}

function closeForm() {
  showForm.value = false
  editingId.value = null
}

async function handleSubmitForm() {
  if (editingId.value != null) {
    const req: UpdateEntryRequest = { ...form.value }
    delete (req as any).counter
    await vault.editEntry(editingId.value, req)
  } else {
    await vault.addEntry(form.value)
  }
  closeForm()
}

async function handleDelete(entry: PasswordEntry) {
  try {
    await ElMessageBox.confirm(
      `确定要删除 ${entry.website} 的记录吗？`,
      '删除确认',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' },
    )
    await vault.removeEntry(entry.id!)
  } catch {
    // 用户取消
  }
}

// ---------- 派生密码 ----------

async function handleDerive(entry: PasswordEntry) {
  const c = replayCounters.value[entry.id!]
  const counter = typeof c === 'number' && c > 0 ? c : undefined
  try {
    const res = await vault.derivePassword(entry.id!, counter)
    showPassword.value = entry.id
    passwordText.value = res.password
    passwordCounter.value = res.counter
  } catch {
    // error handled in store
  }
}

function closePassword() {
  const id = showPassword.value
  showPassword.value = null
  passwordText.value = ''
  passwordCounter.value = null
  if (id != null) delete replayCounters.value[id]
}

async function copyPassword() {
  try {
    await navigator.clipboard.writeText(passwordText.value)
  } catch {
    // fallback
  }
}

// ---------- 轮换 ----------

async function handleRotate(entry: PasswordEntry) {
  try {
    await ElMessageBox.confirm(
      `轮换将递增 ${entry.website} 的 counter，旧密码仍可通过指定 counter 回放。确定？`,
      '轮换确认',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'info' },
    )
    await vault.rotate(entry.id!)
  } catch {
    // 用户取消
  }
}

// ---------- 计算属性 ----------

const charsetLabel = computed(() => {
  const parts: string[] = []
  if (form.value.useLowercase) parts.push('a-z')
  if (form.value.useUppercase) parts.push('A-Z')
  if (form.value.useDigits) parts.push('0-9')
  if (form.value.useSymbols) parts.push('符号')
  return parts.join(', ')
})

function charsetLabelFor(entry: PasswordEntry) {
  const parts: string[] = []
  if (entry.useLowercase) parts.push('a-z')
  if (entry.useUppercase) parts.push('A-Z')
  if (entry.useDigits) parts.push('0-9')
  if (entry.useSymbols) parts.push('符号')
  return parts.join(', ')
}
</script>

<template>
  <div class="vault-page">

    <!-- 错误提示 -->
    <el-alert v-if="vault.error" :title="vault.error" type="error" show-icon closable @close="vault.error = null" />

    <!-- ========== 状态：未初始化 ========== -->
    <div v-if="!vault.initialized" class="auth-center">
      <el-card class="auth-card" shadow="hover">
        <template #header><h2>初始化密码库</h2></template>
        <p class="hint">设置主密码，后续用于派生所有网站密码。请牢记！</p>
        <el-form @submit.prevent="handleSetup">
          <el-form-item label="主密码">
            <el-input v-model="masterPassword" type="password" show-password autocomplete="new-password" placeholder="输入主密码" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" native-type="submit" :loading="vault.loading" style="width: 100%">
              {{ vault.loading ? '初始化中...' : '初始化' }}
            </el-button>
          </el-form-item>
        </el-form>
      </el-card>
    </div>

    <!-- ========== 状态：已初始化但未认证 ========== -->
    <div v-else-if="!vault.authenticated" class="auth-center">
      <el-card class="auth-card" shadow="hover">
        <template #header><h2>解锁密码库</h2></template>
        <el-form @submit.prevent="handleLogin">
          <el-form-item label="主密码">
            <el-input v-model="masterPassword" type="password" show-password autocomplete="current-password" placeholder="输入主密码" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" native-type="submit" :loading="vault.loading" style="width: 100%">
              {{ vault.loading ? '解锁中...' : '解锁' }}
            </el-button>
          </el-form-item>
        </el-form>
      </el-card>
    </div>

    <!-- ========== 状态：已认证 ========== -->
    <template v-else>
      <!-- 工具栏 -->
      <div class="toolbar">
        <div class="toolbar-left">
          <span class="entry-count">
            <el-icon><Collection /></el-icon>
            {{ vault.entryCount }} 条记录
          </span>
        </div>
        <div class="toolbar-actions">
          <el-button type="primary" @click="openCreateForm">
            <el-icon><Plus /></el-icon> 新增
          </el-button>
          <el-button @click="handleLogout">
            <el-icon><Lock /></el-icon> 锁定
          </el-button>
        </div>
      </div>

      <!-- 条目列表 -->
      <el-empty v-if="vault.entries.length === 0" description="暂无密码条目">
        <el-button type="primary" @click="openCreateForm">创建第一条</el-button>
      </el-empty>

      <div v-else class="entry-list">
        <el-card v-for="entry in vault.entries" :key="entry.id ?? entry.website" class="entry-card" shadow="never">
          <div class="entry-row">
            <div class="entry-main">
              <div class="entry-title-row">
                <span class="entry-website">{{ entry.website }}</span>
                <el-tag size="small" type="info">counter={{ entry.counter }}</el-tag>
              </div>
              <div class="entry-sub">
                <span class="entry-username">{{ entry.username }}</span>
                <span class="entry-divider">·</span>
                <span>长度={{ entry.length }}</span>
                <span class="entry-divider">·</span>
                <span>{{ charsetLabelFor(entry) }}</span>
              </div>
            </div>
            <div class="entry-ops">
              <el-input-number
                v-model="replayCounters[entry.id!]"
                :min="1"
                :max="entry.counter"
                :placeholder="String(entry.counter)"
                size="small"
                controls-position="right"
                style="width: 110px"
              />
              <el-button size="small" @click="handleDerive(entry)">查看密码</el-button>
              <el-button size="small" @click="handleRotate(entry)">轮换</el-button>
              <el-button size="small" @click="openEditForm(entry)">编辑</el-button>
              <el-button size="small" type="danger" plain @click="handleDelete(entry)">删除</el-button>
            </div>
          </div>

          <!-- 派生密码结果 -->
          <div v-if="showPassword === entry.id" class="password-reveal">
            <div class="password-reveal-main">
              <code>{{ passwordText }}</code>
              <el-tag size="small">counter={{ passwordCounter }}</el-tag>
            </div>
            <div class="password-reveal-actions">
              <el-button size="small" type="success" plain @click="copyPassword">复制</el-button>
              <el-button size="small" @click="closePassword">关闭</el-button>
            </div>
          </div>
        </el-card>
      </div>

      <!-- 新建 / 编辑弹窗 -->
      <el-dialog
        v-model="showForm"
        :title="editingId != null ? '编辑条目' : '新增条目'"
        width="480px"
        :close-on-click-modal="false"
        @closed="closeForm"
      >
        <el-form label-position="top" @submit.prevent="handleSubmitForm">
          <el-form-item label="网站" required>
            <el-input v-model="form.website" maxlength="256" />
          </el-form-item>
          <el-form-item label="用户名" required>
            <el-input v-model="form.username" maxlength="256" />
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="form.notes" type="textarea" maxlength="1024" />
          </el-form-item>
          <el-form-item v-if="editingId == null" label="初始 Counter">
            <el-input-number v-model="form.counter" :min="1" style="width: 100%" />
          </el-form-item>
          <el-form-item>
            <template #label>
              <span>密码长度: {{ form.length }}</span>
            </template>
            <el-slider v-model="form.length" :min="8" :max="64" show-input />
          </el-form-item>
          <el-form-item>
            <template #label>
              <span>字符集 ({{ charsetLabel }})</span>
            </template>
            <el-checkbox v-model="form.useLowercase">a-z</el-checkbox>
            <el-checkbox v-model="form.useUppercase">A-Z</el-checkbox>
            <el-checkbox v-model="form.useDigits">0-9</el-checkbox>
            <el-checkbox v-model="form.useSymbols">符号</el-checkbox>
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="closeForm">取消</el-button>
          <el-button type="primary" :loading="vault.loading" @click="handleSubmitForm">
            {{ vault.loading ? '保存中...' : '保存' }}
          </el-button>
        </template>
      </el-dialog>
    </template>
  </div>
</template>

<style scoped>
.vault-page {
  /* container handled by el-main in App.vue */
}

/* 初始化 / 解锁 */
.auth-center {
  display: flex;
  justify-content: center;
  padding-top: 3rem;
}

.auth-card {
  width: 400px;
}

.auth-card h2 {
  margin: 0;
  font-size: 1.15rem;
}

.hint {
  color: var(--el-text-color-secondary);
  font-size: 0.9rem;
  margin-bottom: 1rem;
}

/* 工具栏 */
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  color: var(--el-text-color-secondary);
  font-size: 0.9rem;
}

.toolbar-actions {
  display: flex;
  gap: 0.5rem;
}

/* 条目列表 */
.entry-list {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.entry-card {
  border: 1px solid var(--el-border-color-lighter);
}

.entry-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 1rem;
}

.entry-main {
  flex: 1;
  min-width: 0;
}

.entry-title-row {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 0.2rem;
}

.entry-website {
  font-weight: 600;
  font-size: 1rem;
}

.entry-sub {
  display: flex;
  align-items: center;
  gap: 0.3rem;
  flex-wrap: wrap;
  color: var(--el-text-color-secondary);
  font-size: 0.8rem;
}

.entry-username {
  color: var(--el-text-color-regular);
  font-size: 0.85rem;
}

.entry-divider {
  color: var(--el-border-color);
}

.entry-ops {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  flex-shrink: 0;
  flex-wrap: wrap;
  justify-content: flex-end;
}

/* 派生密码结果 */
.password-reveal {
  margin-top: 0.75rem;
  padding: 0.6rem 0.75rem;
  background: var(--el-color-success-light-9);
  border-radius: 6px;
  border: 1px solid var(--el-color-success-light-5);
}

.password-reveal-main {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 0.4rem;
}

.password-reveal-main code {
  font-size: 1.1rem;
  letter-spacing: 0.05em;
  word-break: break-all;
  color: var(--el-color-success-dark-2);
}

.password-reveal-actions {
  display: flex;
  gap: 0.4rem;
}
</style>
