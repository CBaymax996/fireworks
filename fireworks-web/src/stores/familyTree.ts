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
