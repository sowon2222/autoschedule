export {}

declare global {
  // eslint-disable-next-line no-var
  var global: typeof globalThis | undefined
}

if (typeof globalThis.global === 'undefined') {
  globalThis.global = globalThis
}

import React from 'react'
import ReactDOM from 'react-dom/client'
import { createBrowserRouter, RouterProvider } from 'react-router-dom'
import './index.css'
import Login from './pages/Login'
import Register from './pages/Register'
import Dashboard from './pages/Dashboard'
import TeamLayout from './pages/team/TeamLayout'
import Calendar from './pages/team/Calendar'
import Tasks from './pages/team/Tasks'
import WorkHours from './pages/team/WorkHours'
import Settings from './pages/team/Settings'
import Home from './pages/Home'

const router = createBrowserRouter([
  { path: '/', element: <Home /> },
  { path: '/login', element: <Login /> },
  { path: '/register', element: <Register /> },
  { path: '/dashboard', element: <Dashboard /> },
  {
    path: '/team/:id',
    element: <TeamLayout />,
    children: [
      { path: 'calendar', element: <Calendar /> },
      { path: 'tasks', element: <Tasks /> },
      { path: 'workhours', element: <WorkHours /> },
      { path: 'settings', element: <Settings /> }
    ]
  }
])

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <RouterProvider router={router} />
  </React.StrictMode>
)
