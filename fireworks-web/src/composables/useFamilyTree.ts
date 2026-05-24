import { useLazyQuery, useMutation } from '@vue/apollo-composable'
import {
  FamilyTrees as FAMILY_TREES,
  FamilyTree as FAMILY_TREE,
  CreateFamilyTree as CREATE_FAMILY_TREE,
  UpdateFamilyTree as UPDATE_FAMILY_TREE,
  DeleteFamilyTree as DELETE_FAMILY_TREE,
  FamilyPersons as FAMILY_PERSONS,
  FamilyPerson as FAMILY_PERSON,
  FamilyRoots as FAMILY_ROOTS,
  FamilyChildren as FAMILY_CHILDREN,
  FamilyAncestors as FAMILY_ANCESTORS,
  FamilySpouses as FAMILY_SPOUSES,
  CreateFamilyPerson as CREATE_FAMILY_PERSON,
  UpdateFamilyPerson as UPDATE_FAMILY_PERSON,
  DeleteFamilyPerson as DELETE_FAMILY_PERSON,
  AddSpouse as ADD_SPOUSE,
  RemoveSpouse as REMOVE_SPOUSE,
} from '@/graphql/familyTree.gql'

// ── 类型定义 ──

export interface FamilyTree {
  id: string | null
  name: string
  description: string | null
  createdAt: string
  updatedAt: string
}

export interface FamilyPerson {
  id: string | null
  familyTreeId: string
  name: string
  gender: string
  birthDate: string | null
  deathDate: string | null
  biography: string | null
  fatherId: string | null
  generationOrder: number
  sortOrder: number
  createdAt: string
  updatedAt: string
}

export interface SpouseInfo {
  id: string
  name: string
}

export interface PersonSpouse {
  id: string | null
  husbandId: string
  wifeId: string
  marriageOrder: number
  isPrimary: boolean
  createdAt: string
}

export interface PersonWithSpouse {
  id: string
  name: string
  gender: string
  birthDate: string | null
  deathDate: string | null
  biography: string | null
  fatherId: string | null
  generationOrder: number
  sortOrder: number
  spouse: SpouseInfo | null
}

export interface ChildInfo {
  id: string
  name: string
  gender: string
  sortOrder: number
  hasChildren: boolean
}

export interface ChildrenResponse {
  person: PersonWithSpouse
  children: ChildInfo[]
}

export interface CreateTreeInput {
  name: string
  description?: string | null
}

export interface UpdateTreeInput {
  name?: string
  description?: string | null
}

export interface CreatePersonInput {
  name: string
  gender?: string
  birthDate?: string | null
  deathDate?: string | null
  biography?: string | null
  fatherId?: string | null
  sortOrder?: number
}

export interface UpdatePersonInput {
  name?: string
  gender?: string
  birthDate?: string | null
  deathDate?: string | null
  biography?: string | null
  fatherId?: string | null
  sortOrder?: number
}

// ── Composable ──

export function useFamilyTree() {
  // —— 族谱查询 ——
  const { load: loadTrees } = useLazyQuery<{ familyTrees: FamilyTree[] }>(FAMILY_TREES, undefined, {
    fetchPolicy: 'network-only',
  })
  const { load: loadTree } = useLazyQuery<{ familyTree: FamilyTree }>(FAMILY_TREE)

  // —— 成员查询 ——
  const { load: loadPersons } = useLazyQuery<{ familyPersons: FamilyPerson[] }>(FAMILY_PERSONS, undefined, {
    fetchPolicy: 'network-only',
  })
  const { load: loadPerson } = useLazyQuery<{ familyPerson: FamilyPerson }>(FAMILY_PERSON)
  const { load: loadRoots } = useLazyQuery<{ familyRoots: FamilyPerson[] }>(FAMILY_ROOTS, undefined, {
    fetchPolicy: 'network-only',
  })
  const { load: loadChildren } = useLazyQuery<{ familyChildren: ChildrenResponse }>(FAMILY_CHILDREN, undefined, {
    fetchPolicy: 'network-only',
  })
  const { load: loadAncestors } = useLazyQuery<{ familyAncestors: FamilyPerson[] }>(FAMILY_ANCESTORS, undefined, {
    fetchPolicy: 'network-only',
  })
  const { load: loadSpouses } = useLazyQuery<{ familySpouses: PersonSpouse[] }>(FAMILY_SPOUSES, undefined, {
    fetchPolicy: 'network-only',
  })

  // —— 族谱 mutations ——
  const { mutate: createTreeMutate } = useMutation<{ createFamilyTree: FamilyTree }>(CREATE_FAMILY_TREE)
  const { mutate: updateTreeMutate } = useMutation<{ updateFamilyTree: FamilyTree }>(UPDATE_FAMILY_TREE)
  const { mutate: deleteTreeMutate } = useMutation<{ deleteFamilyTree: boolean }>(DELETE_FAMILY_TREE)

  // —— 成员 mutations ——
  const { mutate: createPersonMutate } = useMutation<{ createFamilyPerson: FamilyPerson }>(CREATE_FAMILY_PERSON)
  const { mutate: updatePersonMutate } = useMutation<{ updateFamilyPerson: FamilyPerson }>(UPDATE_FAMILY_PERSON)
  const { mutate: deletePersonMutate } = useMutation<{ deleteFamilyPerson: boolean }>(DELETE_FAMILY_PERSON)

  // —— 配偶 mutations ——
  const { mutate: addSpouseMutate } = useMutation<{ addSpouse: PersonSpouse }>(ADD_SPOUSE)
  const { mutate: removeSpouseMutate } = useMutation<{ removeSpouse: boolean }>(REMOVE_SPOUSE)

  // ===== 封装方法 =====

  /** 获取所有族谱 */
  async function fetchTrees(): Promise<FamilyTree[]> {
    const result = await loadTrees()
    return result!.familyTrees
  }

  /** 获取单个族谱 */
  async function getTree(id: string): Promise<FamilyTree> {
    const result = await loadTree(undefined, { id })
    return result!.familyTree
  }

  /** 创建族谱 */
  async function createTree(name: string, description?: string): Promise<FamilyTree> {
    const result = await createTreeMutate({ name, description: description ?? null })
    return result!.data!.createFamilyTree
  }

  /** 更新族谱 */
  async function updateTree(id: string, name?: string, description?: string): Promise<FamilyTree> {
    const result = await updateTreeMutate({ id, name: name ?? null, description: description ?? null })
    return result!.data!.updateFamilyTree
  }

  /** 删除族谱 */
  async function deleteTree(id: string): Promise<boolean> {
    const result = await deleteTreeMutate({ id })
    return result!.data!.deleteFamilyTree
  }

  /** 查询族谱成员列表 */
  async function fetchPersons(treeId: string, search?: string): Promise<FamilyPerson[]> {
    const result = await loadPersons(undefined, { treeId, search: search ?? null })
    return result!.familyPersons
  }

  /** 查询单个成员 */
  async function getPerson(treeId: string, personId: string): Promise<FamilyPerson> {
    const result = await loadPerson(undefined, { treeId, personId })
    return result!.familyPerson
  }

  /** 查询根节点 */
  async function fetchRoots(treeId: string): Promise<FamilyPerson[]> {
    const result = await loadRoots(undefined, { treeId })
    return result!.familyRoots
  }

  /** 查询子节点 */
  async function fetchChildren(treeId: string, personId: string): Promise<ChildrenResponse> {
    const result = await loadChildren(undefined, { treeId, personId })
    return result!.familyChildren
  }

  /** 查询祖先 */
  async function fetchAncestors(treeId: string, personId: string): Promise<FamilyPerson[]> {
    const result = await loadAncestors(undefined, { treeId, personId })
    return result!.familyAncestors
  }

  /** 查询配偶 */
  async function fetchSpouses(treeId: string, husbandId: string): Promise<PersonSpouse[]> {
    const result = await loadSpouses(undefined, { treeId, husbandId })
    return result!.familySpouses
  }

  /** 创建成员 */
  async function createPerson(treeId: string, input: CreatePersonInput): Promise<FamilyPerson> {
    const result = await createPersonMutate({ treeId, input })
    return result!.data!.createFamilyPerson
  }

  /** 更新成员 */
  async function updatePerson(treeId: string, personId: string, input: UpdatePersonInput): Promise<FamilyPerson> {
    const result = await updatePersonMutate({ treeId, personId, input })
    return result!.data!.updateFamilyPerson
  }

  /** 删除成员 */
  async function deletePerson(treeId: string, personId: string): Promise<boolean> {
    const result = await deletePersonMutate({ treeId, personId })
    return result!.data!.deleteFamilyPerson
  }

  /** 添加配偶 */
  async function addSpouse(
    treeId: string,
    husbandId: string,
    wifeId: string,
    isPrimary: boolean = true,
  ): Promise<PersonSpouse> {
    const result = await addSpouseMutate({ treeId, husbandId, wifeId, isPrimary })
    return result!.data!.addSpouse
  }

  /** 移除配偶 */
  async function removeSpouse(treeId: string, husbandId: string, spouseId: string): Promise<boolean> {
    const result = await removeSpouseMutate({ treeId, husbandId, spouseId })
    return result!.data!.removeSpouse
  }

  return {
    fetchTrees,
    getTree,
    createTree,
    updateTree,
    deleteTree,
    fetchPersons,
    getPerson,
    fetchRoots,
    fetchChildren,
    fetchAncestors,
    fetchSpouses,
    createPerson,
    updatePerson,
    deletePerson,
    addSpouse,
    removeSpouse,
  }
}
