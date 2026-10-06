import { useEffect, useState } from 'react'

export default function AdminHome() {
  const [complaints, setComplaints] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

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

  useEffect(() => {
    fetchComplaints()
  }, [])

  if (loading) return <h1>Loading complaints...</h1>
  if (error) return <h1>{error}</h1>

  return (
    <div>
      <h1>Admin Dashboard</h1>

      {complaints.length === 0 ? (
        <p>No complaints found.</p>
      ) : (
        complaints.map((complaint) => (
          <div key={complaint.id}>
            <h2>{complaint.title}</h2>
            <p>ID: {complaint.id}</p>
            <p>Category: {complaint.category}</p>
            <p>Status: {complaint.status}</p>
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