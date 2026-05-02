import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'home',
      component: HomeView,
    },
    {
      path: '/about',
      name: 'about',
      component: () => import('../views/AboutView.vue'),
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('../views/LoginView.vue'),
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('../views/RegisterView.vue'),
    },
    {
      path: '/vault',
      name: 'vault',
      component: () => import('../views/VaultView.vue'),
    },
    {
      path: '/family-trees',
      name: 'familyTreeList',
      component: () => import('../views/FamilyTreeListView.vue'),
    },
    {
      path: '/family-trees/:id',
      name: 'familyTree',
      component: () => import('../views/FamilyTreeView.vue'),
    },
    {
      path: '/family-trees/:id/person/:personId',
      name: 'personEdit',
      component: () => import('../views/PersonEditView.vue'),
    },
  ],
})

export default router
