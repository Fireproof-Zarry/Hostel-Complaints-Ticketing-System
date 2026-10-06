import { useEffect, useState } from 'react'

export default function AdminHome() {
  const [complaints, setComplaints] = useState([])
  const [status, setStatus] = useState('')
  const [category, setCategory] = useState('')
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const fetchComplaints = async () => {
    const idToken = localStorage.getItem('idToken')

    try {
      setLoading(true)
      setError('')

      const params = new URLSearchParams()

      if (status) {
        params.append('status', status)
      }

      if (category) {
        params.append('category', category)
      }

      if (from && to) {
        params.append('from', `${from}T00:00:00`)
        params.append('to', `${to}T23:59:59`)
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
    } catch (error) {
      console.error('Failed to fetch complaints:', error)
      setError('Failed to load complaints.')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchComplaints()
  }, [])

  const clearFilters = () => {
    setStatus('')
    setCategory('')
    setFrom('')
    setTo('')
  }

  const updateStatus = async (complaintId, newStatus) => {
    const idToken = localStorage.getItem('idToken')

    try {
      const response = await fetch(
        `${import.meta.env.VITE_API_URL}/admin/complaints/${complaintId}/status?status=${newStatus}`,
        {
          method: 'PATCH',
          headers: {
            Authorization: `Bearer ${idToken}`,
          },
        }
      )

      if (!response.ok) {
        throw new Error(`Failed to update status: ${response.status}`)
      }

      const updatedComplaint = await response.json()

      setComplaints((currentComplaints) =>
        currentComplaints.map((complaint) =>
          complaint.id === complaintId
            ? updatedComplaint
            : complaint
        )
      )
    } catch (error) {
      console.error('Failed to update complaint status:', error)
      alert('Failed to update complaint status.')
    }
  }

  if (loading) return <h1>Loading complaints...</h1>
  if (error) return <h1>{error}</h1>

  return (
    <div>
      <h1>Admin Dashboard</h1>

      <div>
        <label>
          Status:{' '}
          <select
            value={status}
            onChange={(e) => setStatus(e.target.value)}
          >
            <option value="">All</option>
            <option value="PENDING">Pending</option>
            <option value="ASSIGNED">Assigned</option>
            <option value="IN_PROGRESS">In Progress</option>
            <option value="RESOLVED">Resolved</option>
            <option value="REJECTED">Rejected</option>
          </select>
        </label>

        <label>
          {' '}Category:{' '}
          <select
            value={category}
            onChange={(e) => setCategory(e.target.value)}
          >
            <option value="">All</option>
            <option value="Electrical">Electrical</option>
            <option value="Plumbing">Plumbing</option>
            <option value="Cleaning">Cleaning</option>
            <option value="Other">Other</option>
          </select>
        </label>

        <label>
          {' '}From:{' '}
          <input
            type="date"
            value={from}
            onChange={(e) => setFrom(e.target.value)}
          />
        </label>

        <label>
          {' '}To:{' '}
          <input
            type="date"
            value={to}
            onChange={(e) => setTo(e.target.value)}
          />
        </label>

        <button onClick={fetchComplaints}>
          Apply Filters
        </button>

        <button onClick={clearFilters}>
          Clear Filters
        </button>
      </div>

      {complaints.length === 0 ? (
        <p>No complaints found.</p>
      ) : (
        complaints.map((complaint) => (
          <div key={complaint.id}>
            <h2>{complaint.title}</h2>
            <p>ID: {complaint.id}</p>
            <p>Category: {complaint.category}</p>

            <label>
              Status:{' '}
              <select
                value={complaint.status}
                onChange={(e) =>
                  updateStatus(complaint.id, e.target.value)
                }
              >
                <option value="PENDING">Pending</option>
                <option value="ASSIGNED">Assigned</option>
                <option value="IN_PROGRESS">In Progress</option>
                <option value="RESOLVED">Resolved</option>
                <option value="REJECTED">Rejected</option>
              </select>
            </label>

            <p>Student: {complaint.student?.email}</p>

            <p>
              Assigned To: {complaint.assignedTo || 'Unassigned'}
            </p>

            <hr />
          </div>
        ))
      )}
    </div>
  )
}