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
