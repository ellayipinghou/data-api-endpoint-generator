import type { ReactNode } from "react"
import { NavLink } from "react-router-dom"

interface AppShellProps {
  children: ReactNode
}

function AppShell({ children }: AppShellProps) {
  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand-block">
          <div className="brand-mark">D</div>
          <div>
            <div className="brand-name">DataServ</div>
          </div>
        </div>
        <nav className="sidebar-nav" aria-label="Sidebar navigation">
          <NavLink
            to="/datasets"
            className={({ isActive }) =>
              `nav-link ${isActive ? "active" : ""}`
            }
          >
            Datasets
          </NavLink>
        </nav>
      </aside>

      <main className="content-panel">{children}</main>
    </div>
  )
}

export default AppShell
