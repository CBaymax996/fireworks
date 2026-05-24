import { gql } from '@apollo/client/core'

export const VAULT_STATUS = gql`
  query VaultStatus {
    vaultStatus {
      initialized
      authenticated
    }
  }
`

export const VAULT_SETUP = gql`
  mutation VaultSetup($masterPassword: String!) {
    vaultSetup(masterPassword: $masterPassword)
  }
`

export const VAULT_LOGIN = gql`
  mutation VaultLogin($masterPassword: String!) {
    vaultLogin(masterPassword: $masterPassword)
  }
`

export const VAULT_LOGOUT = gql`
  mutation VaultLogout {
    vaultLogout
  }
`

export const VAULT_ENTRIES = gql`
  query VaultEntries {
    vaultEntries {
      id
      website
      username
      notes
      counter
      length
      useLowercase
      useUppercase
      useDigits
      useSymbols
      password
      mode
      createdAt
      updatedAt
    }
  }
`

export const VAULT_ENTRY = gql`
  query VaultEntry($id: Int!) {
    vaultEntry(id: $id) {
      id
      website
      username
      notes
      counter
      length
      useLowercase
      useUppercase
      useDigits
      useSymbols
      password
      mode
      createdAt
      updatedAt
    }
  }
`

export const CREATE_VAULT_ENTRY = gql`
  mutation CreateVaultEntry($input: VaultEntryInput!) {
    createVaultEntry(input: $input) {
      id
      website
      username
      notes
      counter
      length
      useLowercase
      useUppercase
      useDigits
      useSymbols
      password
      mode
      createdAt
      updatedAt
    }
  }
`

export const UPDATE_VAULT_ENTRY = gql`
  mutation UpdateVaultEntry($id: Int!, $input: VaultEntryUpdateInput!) {
    updateVaultEntry(id: $id, input: $input) {
      id
      website
      username
      notes
      counter
      length
      useLowercase
      useUppercase
      useDigits
      useSymbols
      password
      mode
      createdAt
      updatedAt
    }
  }
`

export const DELETE_VAULT_ENTRY = gql`
  mutation DeleteVaultEntry($id: Int!) {
    deleteVaultEntry(id: $id)
  }
`

export const ROTATE_VAULT_ENTRY = gql`
  mutation RotateVaultEntry($id: Int!) {
    rotateVaultEntry(id: $id) {
      id
      website
      username
      notes
      counter
      length
      useLowercase
      useUppercase
      useDigits
      useSymbols
      password
      mode
      createdAt
      updatedAt
    }
  }
`

export const DERIVE_PASSWORD = gql`
  query DerivePassword($id: Int!, $counter: Int) {
    derivePassword(id: $id, counter: $counter) {
      entry {
        id
        website
        username
        notes
        counter
        length
        useLowercase
        useUppercase
        useDigits
        useSymbols
        mode
      }
      password
      counter
    }
  }
`

export const GENERATE_PASSWORD = gql`
  query GeneratePassword($length: Int!, $lowercase: Boolean!, $uppercase: Boolean!, $digits: Boolean!, $symbols: Boolean!) {
    generatePassword(length: $length, lowercase: $lowercase, uppercase: $uppercase, digits: $digits, symbols: $symbols) {
      password
    }
  }
`
