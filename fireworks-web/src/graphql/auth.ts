import { gql } from '@apollo/client/core'

export const AUTH_STATUS = gql`
  query AuthStatus {
    authStatus {
      authenticated
      accountId
    }
  }
`

export const REGISTER = gql`
  mutation Register($username: String!, $password: String!) {
    register(username: $username, password: $password) {
      id
      username
      createdAt
    }
  }
`

export const LOGIN = gql`
  mutation Login($username: String!, $password: String!) {
    login(username: $username, password: $password) {
      success
      account {
        id
        username
        createdAt
      }
    }
  }
`

export const LOGOUT = gql`
  mutation Logout {
    logout
  }
`
