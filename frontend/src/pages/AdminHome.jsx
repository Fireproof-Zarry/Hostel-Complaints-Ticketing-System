import { useEffect, useState } from 'react'

export default function AdminHome() {
  const [sidebarOpen, setSidebarOpen] = useState(true)
  const [complaints, setComplaints] = useState([])
  const [filteredComplaints, setFilteredComplaints] = useState([])
  const [searchTerm, setSearchTerm] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [admins, setAdmins] = useState([])

  // Filter values
  const [statusFilter, setStatusFilter] = useState('')
  const [categoryFilter, setCategoryFilter] = useState('')
  const [floorFilter, setFloorFilter] = useState('')
  const [fromDate, setFromDate] = useState('')
  const [toDate, setToDate] = useState('')
  const [assignedToFilter, setAssignedToFilter] = useState('')

  // Selected complaint for View
  const [selectedComplaint, setSelectedComplaint] = useState(null)

  // Complaint for status update
  const [statusComplaint, setStatusComplaint] = useState(null)
  const [newStatus, setNewStatus] = useState('')
  const [statusUpdating, setStatusUpdating] = useState(false)
  const [statusError, setStatusError] = useState('')

  // Complaint assignment
  const [assignComplaint, setAssignComplaint] = useState(null)
  const [selectedAdmin, setSelectedAdmin] = useState('')
  const [assignUpdating, setAssignUpdating] = useState(false)
  const [assignError, setAssignError] = useState('')

  // Success toast
  const [successMessage, setSuccessMessage] = useState('')

  const showSuccessMessage = (message) => {
    setSuccessMessage(message)

    setTimeout(() => {
      setSuccessMessage('')
    }, 4000)
  }

  const fetchComplaints = async (filters = {}) => {
    const idToken = localStorage.getItem('idToken')

    try {
      setLoading(true)
      setError('')

      const params = new URLSearchParams()

      if (filters.status) {
        params.append('status', filters.status)
      }

      if (filters.category) {
        params.append('category', filters.category)
      }

      if (filters.floor) {
        params.append('floor', filters.floor)
      }

      if (filters.assignedTo) {
        params.append('assignedTo', filters.assignedTo)
      }

      if (filters.from) {
        params.append('from', `${filters.from}T00:00:00`)
      }

      if (filters.to) {
        params.append('to', `${filters.to}T23:59:59`)
      }

      const queryString = params.toString()

      const response = await fetch(
        `${import.meta.env.VITE_API_URL}/admin/complaints${queryString ? `?${queryString}` : ''
        }`,
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
      setFilteredComplaints(data)

      return true
    } catch (error) {
      console.error('Failed to fetch complaints:', error)
      setError('Failed to load complaints.')

      return false
    } finally {
      setLoading(false)
    }
  }

  const fetchAdmins = async () => {
    const idToken = localStorage.getItem('idToken')

    try {
      const response = await fetch(
        `${import.meta.env.VITE_API_URL}/admin/complaints/admins`,
        {
          headers: {
            Authorization: `Bearer ${idToken}`,
          },
        }
      )

      if (!response.ok) {
        throw new Error(`Failed to fetch admins: ${response.status}`)
      }

      const data = await response.json()
      setAdmins(data)
    } catch (error) {
      console.error('Failed to fetch admins:', error)
    }
  }

  useEffect(() => {
    fetchComplaints()
    fetchAdmins()
  }, [])

  const handleSearch = () => {
    const query = searchTerm.trim().toLowerCase()

    if (!query) {
      setFilteredComplaints(complaints)
      return
    }

    const filtered = complaints.filter((complaint) => {
      const searchableText = [
        complaint.id,
        complaint.title,
        complaint.description,
        complaint.category,
        complaint.student?.email,
        complaint.student?.name,
        complaint.floor,
        complaint.room,
        complaint.assignedTo,
        complaint.status,
      ]
        .filter(Boolean)
        .join(' ')
        .toLowerCase()

      const searchWords = query
        .replace(/#/g, ' ')
        .split(/\s+/)
        .filter(Boolean)

      return searchWords.every((word) =>
        searchableText.includes(word)
      )
    })

    setFilteredComplaints(filtered)
  }

  const handleApplyFilters = async () => {
    const success = await fetchComplaints({
      status: statusFilter,
      category: categoryFilter,
      floor: floorFilter,
      assignedTo: assignedToFilter,
      from: fromDate,
      to: toDate,
    })

    if (success) {
      showSuccessMessage('Filters applied successfully')
    }
  }

  const handleResetFilters = async () => {
    setStatusFilter('')
    setCategoryFilter('')
    setFloorFilter('')
    setFromDate('')
    setToDate('')
    setAssignedToFilter('')

    const success = await fetchComplaints()

    if (success) {
      showSuccessMessage('Filters reset')
    }
  }

  const handleStatusUpdate = async () => {
    if (!statusComplaint || !newStatus) {
      return
    }

    const idToken = localStorage.getItem('idToken')

    try {
      setStatusUpdating(true)
      setStatusError('')

      const response = await fetch(
        `${import.meta.env.VITE_API_URL}/admin/complaints/${statusComplaint.id}/status?status=${encodeURIComponent(newStatus)}`,
        {
          method: 'PATCH',
          headers: {
            Authorization: `Bearer ${idToken}`,
          },
        }
      )

      if (!response.ok) {
        const message = await response.text()

        throw new Error(
          message || `Failed to update status: ${response.status}`
        )
      }

      const updatedComplaint = await response.json()

      setComplaints((currentComplaints) =>
        currentComplaints.map((complaint) =>
          complaint.id === updatedComplaint.id
            ? updatedComplaint
            : complaint
        )
      )

      setFilteredComplaints((currentComplaints) =>
        currentComplaints.map((complaint) =>
          complaint.id === updatedComplaint.id
            ? updatedComplaint
            : complaint
        )
      )

      setStatusComplaint(null)
      setNewStatus('')
      setStatusError('')

      showSuccessMessage('Status updated successfully')
    } catch (error) {
      console.error('Failed to update complaint status:', error)

      setStatusError(
        error.message || 'Failed to update complaint status.'
      )
    } finally {
      setStatusUpdating(false)
    }
  }

  const handleAssignComplaint = async () => {
    if (!assignComplaint || !selectedAdmin) {
      return
    }

    const idToken = localStorage.getItem('idToken')

    try {
      setAssignUpdating(true)
      setAssignError('')

      const response = await fetch(
        `${import.meta.env.VITE_API_URL}/admin/complaints/${assignComplaint.id}/assign?email=${encodeURIComponent(selectedAdmin)}`,
        {
          method: 'PATCH',
          headers: {
            Authorization: `Bearer ${idToken}`,
          },
        }
      )

      if (!response.ok) {
        const message = await response.text()

        throw new Error(
          message || `Failed to assign complaint: ${response.status}`
        )
      }

      const updatedComplaint = await response.json()

      setComplaints((currentComplaints) =>
        currentComplaints.map((complaint) =>
          complaint.id === updatedComplaint.id
            ? updatedComplaint
            : complaint
        )
      )

      setFilteredComplaints((currentComplaints) =>
        currentComplaints.map((complaint) =>
          complaint.id === updatedComplaint.id
            ? updatedComplaint
            : complaint
        )
      )

      setAssignComplaint(null)
      setSelectedAdmin('')
      setAssignError('')

      showSuccessMessage('Complaint assigned successfully')
    } catch (error) {
      console.error('Failed to assign complaint:', error)

      setAssignError(
        error.message || 'Failed to assign complaint.'
      )
    } finally {
      setAssignUpdating(false)
    }
  }

  const getTimeSinceRaised = (createdAt) => {
    if (!createdAt) {
      return ''
    }

    const createdDate = new Date(createdAt)
    const today = new Date()

    const createdDay = new Date(
      createdDate.getFullYear(),
      createdDate.getMonth(),
      createdDate.getDate()
    )

    const todayDay = new Date(
      today.getFullYear(),
      today.getMonth(),
      today.getDate()
    )

    const differenceDays = Math.floor(
      (todayDay - createdDay) / (1000 * 60 * 60 * 24)
    )

    if (differenceDays === 0) {
      return 'Raised today'
    }

    if (differenceDays === 1) {
      return 'Raised 1 day ago'
    }

    return `Raised ${differenceDays} days ago`
  }

  const assignedToUsers = [
    ...new Set(
      complaints
        .map((complaint) => complaint.assignedTo)
        .filter(Boolean)
    ),
  ]

  const statusOptions = [
    'PENDING',
    'ASSIGNED',
    'IN_PROGRESS',
    'RESOLVED',
    'REJECTED',
  ]

  return (
    <div className="admin-layout">

      {/* Sidebar */}
      <aside
        className={`admin-sidebar ${sidebarOpen ? 'open' : 'closed'
          }`}
      >
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
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
            />

            <button
              className="search-button"
              onClick={handleSearch}
            >
              Search
            </button>
          </div>

          {/* Filters */}
          <div className="filters-section">

            <div className="filter-dropdown">
              <label>Status</label>

              <select
                value={statusFilter}
                onChange={(e) =>
                  setStatusFilter(e.target.value)
                }
              >
                <option value="">All</option>
                <option value="PENDING">PENDING</option>
                <option value="ASSIGNED">ASSIGNED</option>
                <option value="IN_PROGRESS">
                  IN_PROGRESS
                </option>
                <option value="RESOLVED">RESOLVED</option>
                <option value="REJECTED">REJECTED</option>
              </select>
            </div>

            <div className="filter-dropdown">
              <label>Category</label>

              <select
                value={categoryFilter}
                onChange={(e) =>
                  setCategoryFilter(e.target.value)
                }
              >
                <option value="">All</option>
                <option value="Electrical">
                  Electrical
                </option>
                <option value="Plumbing">Plumbing</option>
                <option value="Carpentry">Carpentry</option>
                <option value="Cleaning">Cleaning</option>
                <option value="IT">IT/Network</option>
              </select>
            </div>

            <div className="filter-dropdown">
              <label>Floor</label>

              <select
                value={floorFilter}
                onChange={(e) =>
                  setFloorFilter(e.target.value)
                }
              >
                <option value="">All</option>
                <option value="ground">Ground</option>
                <option value="first">First</option>
                <option value="second">Second</option>
                <option value="third">Third</option>
                <option value="fourth">Fourth</option>
                <option value="fifth">Fifth</option>
                <option value="sixth">Sixth</option>
              </select>
            </div>

            <div className="date-filter">
              <label>From</label>

              <input
                type="date"
                value={fromDate}
                onChange={(e) =>
                  setFromDate(e.target.value)
                }
              />
            </div>

            <div className="date-filter">
              <label>To</label>

              <input
                type="date"
                value={toDate}
                onChange={(e) =>
                  setToDate(e.target.value)
                }
              />
            </div>

            <div className="filter-dropdown">
              <label>In-charge</label>

              <select
                value={assignedToFilter}
                onChange={(e) =>
                  setAssignedToFilter(e.target.value)
                }
              >
                <option value="">All</option>

                {assignedToUsers.map((email) => (
                  <option key={email} value={email}>
                    {email}
                  </option>
                ))}
              </select>
            </div>

            <button
              className="apply-button"
              onClick={handleApplyFilters}
            >
              Apply Filters
            </button>

            <button
              className="reset-button"
              onClick={handleResetFilters}
            >
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

            {!loading &&
              !error &&
              filteredComplaints.length === 0 && (
                <div className="table-placeholder">
                  <p>No complaints found.</p>
                </div>
              )}

            {!loading &&
              !error &&
              filteredComplaints.length > 0 && (
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
                      {filteredComplaints.map((complaint) => (
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
                            {complaint.student?.email ||
                              'No email'}
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
                            {complaint.assignedTo ||
                              'Unassigned'}
                          </td>

                          <td>
                            <div className="action-buttons">

                              <button
                                type="button"
                                className="table-action-button"
                                onClick={() =>
                                  setSelectedComplaint(
                                    complaint
                                  )
                                }
                              >
                                View
                              </button>

                              <button
                                className="table-action-button"
                                onClick={() => {
                                  setAssignComplaint(complaint)
                                  setSelectedAdmin(complaint.assignedTo || '')
                                  setAssignError('')
                                }}
                              >
                                Assign
                              </button>

                              <button
                                type="button"
                                className="table-action-button"
                                onClick={() => {
                                  setStatusComplaint(
                                    complaint
                                  )
                                  setNewStatus(
                                    complaint.status || ''
                                  )
                                  setStatusError('')
                                }}
                              >
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

      {/* Complaint Details Modal */}
      {selectedComplaint && (
        <div
          className="complaint-modal-overlay"
          onClick={() =>
            setSelectedComplaint(null)
          }
        >
          <div
            className="complaint-modal"
            onClick={(e) => e.stopPropagation()}
          >

            <div className="complaint-modal-header">
              <div>
                <h2>Complaint Details</h2>

                <div className="complaint-meta">
                  <span>
                    #{selectedComplaint.id}
                  </span>

                  <span>•</span>

                  <span>
                    {getTimeSinceRaised(
                      selectedComplaint.createdAt
                    )}
                  </span>
                </div>
              </div>

              <button
                type="button"
                className="complaint-modal-close"
                onClick={() =>
                  setSelectedComplaint(null)
                }
              >
                ×
              </button>
            </div>

            <div className="complaint-details">

              <div className="detail-item detail-item-full complaint-title-detail">
                <span className="detail-label">
                  Title
                </span>

                <span className="detail-title-value">
                  {selectedComplaint.title || '—'}
                </span>
              </div>

              <div className="detail-item">
                <span className="detail-label">
                  Category
                </span>

                <span className="detail-value detail-value-large">
                  {selectedComplaint.category || '—'}
                </span>
              </div>

              <div className="detail-item">
                <span className="detail-label">
                  Status
                </span>

                <span className="detail-value">
                  <span
                    className={`status-badge status-${selectedComplaint.status?.toLowerCase()}`}
                  >
                    {selectedComplaint.status || '—'}
                  </span>
                </span>
              </div>

              <div className="detail-item">
                <span className="detail-label">
                  Student Email
                </span>

                <span className="detail-value detail-value-large">
                  {selectedComplaint.student?.email ||
                    'No email'}
                </span>
              </div>

              <div className="detail-item">
                <span className="detail-label">
                  Assigned To
                </span>

                <span className="detail-value detail-value-large">
                  {selectedComplaint.assignedTo ||
                    'Unassigned'}
                </span>
              </div>

              <div className="detail-item">
                <span className="detail-label">
                  Floor
                </span>

                <span className="detail-value detail-value-large">
                  {selectedComplaint.floor || '—'}
                </span>
              </div>

              <div className="detail-item">
                <span className="detail-label">
                  Room No.
                </span>

                <span className="detail-value detail-value-large">
                  {selectedComplaint.room || '—'}
                </span>
              </div>

              <div className="detail-item detail-item-full">
                <span className="detail-label">
                  Description
                </span>

                <p className="detail-description">
                  {selectedComplaint.description ||
                    'No description provided.'}
                </p>
              </div>

              <div className="detail-item">
                <span className="detail-label">
                  Created At
                </span>

                <span className="detail-value">
                  {selectedComplaint.createdAt
                    ? new Date(
                      selectedComplaint.createdAt
                    ).toLocaleString()
                    : '—'}
                </span>
              </div>

              <div className="detail-item">
                <span className="detail-label">
                  Updated At
                </span>

                <span className="detail-value">
                  {selectedComplaint.updatedAt
                    ? new Date(
                      selectedComplaint.updatedAt
                    ).toLocaleString()
                    : '—'}
                </span>
              </div>

            </div>

            <div className="complaint-modal-footer">
              <button
                type="button"
                className="reset-button"
                onClick={() =>
                  setSelectedComplaint(null)
                }
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Status Update Modal */}
      {statusComplaint && (
        <div
          className="complaint-modal-overlay"
          onClick={() => {
            if (!statusUpdating) {
              setStatusComplaint(null)
              setStatusError('')
            }
          }}
        >
          <div
            className="complaint-modal status-modal"
            onClick={(e) => e.stopPropagation()}
          >

            <div className="complaint-modal-header">
              <div>
                <h2>Update Status</h2>

                <div className="complaint-meta">
                  <span>
                    #{statusComplaint.id}
                  </span>

                  <span>•</span>

                  <span>
                    {statusComplaint.title}
                  </span>
                </div>
              </div>

              <button
                type="button"
                className="complaint-modal-close"
                onClick={() => {
                  if (!statusUpdating) {
                    setStatusComplaint(null)
                    setStatusError('')
                  }
                }}
                disabled={statusUpdating}
              >
                ×
              </button>
            </div>

            <div className="complaint-details">

              {/* Current Status */}
              <div className="detail-item detail-item-full">
                <span className="detail-label">
                  Current Status
                </span>

                <span className="detail-value">
                  <span
                    className={`status-badge status-${statusComplaint.status?.toLowerCase()}`}
                  >
                    {statusComplaint.status}
                  </span>
                </span>
              </div>

              {/* New Status */}
              <div className="detail-item detail-item-full">
                <span className="detail-label">
                  New Status
                </span>

                <div className="status-options">
                  {statusOptions.map((status) => (
                    <button
                      key={status}
                      type="button"
                      className={`status-option ${newStatus === status
                        ? 'selected'
                        : ''
                        }`}
                      onClick={() => {
                        setNewStatus(status)
                        setStatusError('')
                      }}
                      disabled={statusUpdating}
                    >
                      {status}
                    </button>
                  ))}
                </div>
              </div>

              {/* Error */}
              {statusError && (
                <div className="detail-item detail-item-full">
                  <p className="status-error">
                    {statusError}
                  </p>
                </div>
              )}

            </div>

            <div className="complaint-modal-footer">

              <button
                type="button"
                className="reset-button"
                onClick={() => {
                  if (!statusUpdating) {
                    setStatusComplaint(null)
                    setStatusError('')
                  }
                }}
                disabled={statusUpdating}
              >
                Cancel
              </button>

              <button
                type="button"
                className="apply-button"
                onClick={handleStatusUpdate}
                disabled={
                  statusUpdating || !newStatus
                }
              >
                {statusUpdating
                  ? 'Updating...'
                  : 'Update Status'}
              </button>

            </div>
          </div>
        </div>
      )}

      {/* Assign Complaint Modal */}
      {assignComplaint && (
        <div
          className="complaint-modal-overlay"
          onClick={() => {
            if (!assignUpdating) {
              setAssignComplaint(null)
              setSelectedAdmin('')
              setAssignError('')
            }
          }}
        >
          <div
            className="complaint-modal assign-modal"
            onClick={(event) => event.stopPropagation()}
          >
            <div className="complaint-modal-header">
              <div>
                <h2>Assign Complaint</h2>

                <div className="complaint-meta">
                  Complaint #{assignComplaint.id}
                </div>
              </div>

              <button
                className="complaint-modal-close"
                onClick={() => {
                  if (!assignUpdating) {
                    setAssignComplaint(null)
                    setSelectedAdmin('')
                    setAssignError('')
                  }
                }}
                disabled={assignUpdating}
              >
                ×
              </button>
            </div>

            <div className="complaint-details">

              <div className="detail-item detail-item-full">
                <span className="detail-label">
                  Current Assignee
                </span>

                <span className="detail-value">
                  {assignComplaint.assignedTo || 'Unassigned'}
                </span>
              </div>

              <div className="detail-item detail-item-full">
                <span className="detail-label">
                  Assign To
                </span>

                <div className="admin-options">
                  {admins.map((admin) => (
                    <button
                      key={admin.email}
                      type="button"
                      className={`admin-option ${selectedAdmin === admin.email
                        ? 'selected'
                        : ''
                        }`}
                      onClick={() =>
                        setSelectedAdmin(admin.email)
                      }
                      disabled={assignUpdating}
                    >
                      <div className="admin-option-content">
                        <span>
                          {admin.name || admin.email}
                        </span>

                        <span className="admin-email">
                          {admin.email}
                        </span>
                      </div>

                      {selectedAdmin === admin.email && (
                        <span className="admin-option-check">✓</span>
                      )}
                    </button>
                  ))}
                </div>
              </div>

              {assignError && (
                <p className="status-error detail-item-full">
                  {assignError}
                </p>
              )}

            </div>

            <div className="complaint-modal-footer">

              <button
                className="reset-button"
                onClick={() => {
                  setAssignComplaint(null)
                  setSelectedAdmin('')
                  setAssignError('')
                }}
                disabled={assignUpdating}
              >
                Cancel
              </button>

              <button
                className="apply-button"
                onClick={handleAssignComplaint}
                disabled={assignUpdating || !selectedAdmin}
              >
                {assignUpdating
                  ? 'Assigning...'
                  : 'Assign Complaint'}
              </button>

            </div>
          </div>
        </div>
      )}

      {/* Success Toast */}
      {successMessage && (
        <div className="success-toast">
          <span className="success-toast-icon">
            ✓
          </span>

          <span>{successMessage}</span>
        </div>
      )}

    </div>
  )
}