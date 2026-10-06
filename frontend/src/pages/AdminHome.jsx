import { useEffect, useState } from 'react'

export default function AdminHome() {
  const [sidebarOpen, setSidebarOpen] = useState(true)
  const [complaints, setComplaints] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    const fetchComplaints = async () => {
      const idToken = localStorage.getItem('idToken')

      try {
        setLoading(true)
        setError('')

        const response = await fetch(
          `${import.meta.env.VITE_API_URL}/admin/complaints`,
          {
            headers: {
              Authorization: `Bearer ${idToken}`,
            },
          }
        )

        if (!response.ok) {
          throw new Error(`Failed to fetch complaints: ${response.status}`)
        }

        const data = await response.json()
        setComplaints(data)
      } catch (error) {
        console.error('Failed to fetch complaints:', error)
        setError('Failed to load complaints.')
      } finally {
        setLoading(false)
      }
    }

    fetchComplaints()
  }, [])

  return (
    <div className="admin-layout">

      {/* Sidebar */}
      <aside className={`admin-sidebar ${sidebarOpen ? 'open' : 'closed'}`}>

        <div className="sidebar-header">
          <h2>Hostel Complaints</h2>
        </div>

        <nav className="sidebar-nav">

          <button className="nav-item active">
            <span>⌂</span>
            {sidebarOpen && <span>Admin Home</span>}
          </button>

          <button className="nav-item">
            <span>▦</span>
            {sidebarOpen && <span>Analytics</span>}
          </button>

          <button className="nav-item">
            <span>◷</span>
            {sidebarOpen && <span>History</span>}
          </button>

        </nav>

        <div className="sidebar-bottom">
          <button className="nav-item">
            <span>◉</span>
            {sidebarOpen && <span>Profile</span>}
          </button>
        </div>

      </aside>

      {/* Main Area */}
      <main className="admin-main">

        {/* Header */}
        <header className="admin-header">

          <button
            className="menu-button"
            onClick={() => setSidebarOpen(!sidebarOpen)}
          >
            ☰
          </button>

          <h1>Admin Home</h1>

          <div className="profile-button">
            ◉
          </div>

        </header>

        {/* Content */}
        <section className="admin-content">

          {/* Search */}
          <div className="search-section">

            <input
              type="text"
              placeholder="Search complaints..."
              className="search-input"
            />

            <button className="search-button">
              Search
            </button>

          </div>

          {/* Filters */}
          <div className="filters-section">

            <select>
              <option value="">Status</option>
            </select>

            <select>
              <option value="">Category</option>
            </select>

            <select>
              <option value="">Floor</option>
            </select>

            <div className="date-filter">
              <label>From</label>
              <input type="date" />
            </div>

            <div className="date-filter">
              <label>To</label>
              <input type="date" />
            </div>

            <select>
              <option value="">In-charge</option>
            </select>

            <button className="apply-button">
              Apply Filters
            </button>

            <button className="reset-button">
              Reset
            </button>

          </div>

          {/* Complaints */}
          <div className="complaints-section">

            <h2>Complaints</h2>

            {loading && (
              <div className="table-placeholder">
                <p>Loading complaints...</p>
              </div>
            )}

            {!loading && error && (
              <div className="table-placeholder">
                <p>{error}</p>
              </div>
            )}

            {!loading && !error && complaints.length === 0 && (
              <div className="table-placeholder">
                <p>No complaints found.</p>
              </div>
            )}

            {!loading && !error && complaints.length > 0 && (
              <div className="complaints-table-wrapper">

                <table className="complaints-table">

                  <thead>
                    <tr>
                      <th>Complaint No.</th>
                      <th>Title</th>
                      <th>Category</th>
                      <th>Email</th>
                      <th>Floor</th>
                      <th>Room No.</th>
                      <th>Status</th>
                      <th>Assigned To</th>
                      <th>Action</th>
                    </tr>
                  </thead>

                  <tbody>

                    {complaints.map((complaint) => (
                      <tr key={complaint.id}>

                        <td>
                          #{complaint.id}
                        </td>

                        <td>
                          <strong className="complaint-title">
                            {complaint.title}
                          </strong>
                        </td>

                        <td>
                          {complaint.category}
                        </td>

                        <td>
                          {complaint.student?.email || 'No email'}
                        </td>

                        <td>
                          {complaint.floor}
                        </td>

                        <td>
                          {complaint.room}
                        </td>

                        <td>
                          <span
                            className={`status-badge status-${complaint.status?.toLowerCase()}`}
                          >
                            {complaint.status}
                          </span>
                        </td>

                        <td>
                          {complaint.assignedTo || 'Unassigned'}
                        </td>

                        <td>
                          <div className="action-buttons">

                            <button className="table-action-button">
                              View
                            </button>

                            <button className="table-action-button">
                              Assign
                            </button>

                            <button className="table-action-button">
                              Status
                            </button>

                          </div>
                        </td>

                      </tr>
                    ))}

                  </tbody>

                </table>

              </div>
            )}

          </div>

        </section>

      </main>

    </div>
  )
}