import { ApolloClient, InMemoryCache, createHttpLink } from '@apollo/client/core'

// GraphQL endpoint
const httpLink = createHttpLink({
  uri: '/graphql',
  credentials: 'include',
})

// Apollo Client 实例（缓存策略：默认 cache-first）
const apolloClient = new ApolloClient({
  link: httpLink,
  cache: new InMemoryCache(),
  defaultOptions: {
    watchQuery: {
      fetchPolicy: 'cache-first',
      errorPolicy: 'all',
    },
    query: {
      fetchPolicy: 'cache-first',
      errorPolicy: 'all',
    },
    mutate: {
      errorPolicy: 'all',
    },
  },
})

export default apolloClient
