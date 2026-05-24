import { gql } from '@apollo/client/core'

export const FAMILY_TREES = gql`
  query FamilyTrees {
    familyTrees {
      id
      name
      description
      createdAt
      updatedAt
    }
  }
`

export const FAMILY_TREE = gql`
  query FamilyTree($id: Int!) {
    familyTree(id: $id) {
      id
      name
      description
      createdAt
      updatedAt
    }
  }
`

export const CREATE_FAMILY_TREE = gql`
  mutation CreateFamilyTree($name: String!, $description: String) {
    createFamilyTree(name: $name, description: $description) {
      id
      name
      description
      createdAt
      updatedAt
    }
  }
`

export const UPDATE_FAMILY_TREE = gql`
  mutation UpdateFamilyTree($id: Int!, $name: String, $description: String) {
    updateFamilyTree(id: $id, name: $name, description: $description) {
      id
      name
      description
      createdAt
      updatedAt
    }
  }
`

export const DELETE_FAMILY_TREE = gql`
  mutation DeleteFamilyTree($id: Int!) {
    deleteFamilyTree(id: $id)
  }
`

export const FAMILY_PERSONS = gql`
  query FamilyPersons($treeId: Int!, $search: String) {
    familyPersons(treeId: $treeId, search: $search) {
      id
      familyTreeId
      name
      gender
      birthDate
      deathDate
      biography
      fatherId
      generationOrder
      sortOrder
      createdAt
      updatedAt
    }
  }
`

export const FAMILY_PERSON = gql`
  query FamilyPerson($treeId: Int!, $personId: Int!) {
    familyPerson(treeId: $treeId, personId: $personId) {
      id
      familyTreeId
      name
      gender
      birthDate
      deathDate
      biography
      fatherId
      generationOrder
      sortOrder
      createdAt
      updatedAt
    }
  }
`

export const FAMILY_ROOTS = gql`
  query FamilyRoots($treeId: Int!) {
    familyRoots(treeId: $treeId) {
      id
      familyTreeId
      name
      gender
      birthDate
      deathDate
      biography
      fatherId
      generationOrder
      sortOrder
      createdAt
      updatedAt
    }
  }
`

export const FAMILY_CHILDREN = gql`
  query FamilyChildren($treeId: Int!, $personId: Int!) {
    familyChildren(treeId: $treeId, personId: $personId) {
      person {
        id
        name
        gender
        birthDate
        deathDate
        biography
        fatherId
        generationOrder
        sortOrder
        spouse {
          id
          name
        }
      }
      children {
        id
        name
        gender
        sortOrder
        hasChildren
      }
    }
  }
`

export const FAMILY_ANCESTORS = gql`
  query FamilyAncestors($treeId: Int!, $personId: Int!) {
    familyAncestors(treeId: $treeId, personId: $personId) {
      id
      familyTreeId
      name
      gender
      birthDate
      deathDate
      biography
      fatherId
      generationOrder
      sortOrder
      createdAt
      updatedAt
    }
  }
`

export const FAMILY_SPOUSES = gql`
  query FamilySpouses($treeId: Int!, $husbandId: Int!) {
    familySpouses(treeId: $treeId, husbandId: $husbandId) {
      id
      husbandId
      wifeId
      marriageOrder
      isPrimary
      createdAt
    }
  }
`

export const CREATE_FAMILY_PERSON = gql`
  mutation CreateFamilyPerson($treeId: Int!, $input: FamilyPersonInput!) {
    createFamilyPerson(treeId: $treeId, input: $input) {
      id
      familyTreeId
      name
      gender
      birthDate
      deathDate
      biography
      fatherId
      generationOrder
      sortOrder
      createdAt
      updatedAt
    }
  }
`

export const UPDATE_FAMILY_PERSON = gql`
  mutation UpdateFamilyPerson($treeId: Int!, $personId: Int!, $input: FamilyPersonUpdateInput!) {
    updateFamilyPerson(treeId: $treeId, personId: $personId, input: $input) {
      id
      familyTreeId
      name
      gender
      birthDate
      deathDate
      biography
      fatherId
      generationOrder
      sortOrder
      createdAt
      updatedAt
    }
  }
`

export const DELETE_FAMILY_PERSON = gql`
  mutation DeleteFamilyPerson($treeId: Int!, $personId: Int!) {
    deleteFamilyPerson(treeId: $treeId, personId: $personId)
  }
`

export const ADD_SPOUSE = gql`
  mutation AddSpouse($treeId: Int!, $husbandId: Int!, $wifeId: Int!, $isPrimary: Boolean) {
    addSpouse(treeId: $treeId, husbandId: $husbandId, wifeId: $wifeId, isPrimary: $isPrimary) {
      id
      husbandId
      wifeId
      marriageOrder
      isPrimary
      createdAt
    }
  }
`

export const REMOVE_SPOUSE = gql`
  mutation RemoveSpouse($treeId: Int!, $husbandId: Int!, $spouseId: Int!) {
    removeSpouse(treeId: $treeId, husbandId: $husbandId, spouseId: $spouseId)
  }
`
