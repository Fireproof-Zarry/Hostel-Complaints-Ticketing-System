import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';

export default function StudentHome() {
  const [complaints, setComplaints] = useState([]);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  
  const [formData, setFormData] = useState({
    title: '',
    description: '',
    category: 'Electrical',
    floor: 'Second',
    room: ''
  });

  const navigate = useNavigate();
  const token = localStorage.getItem('token');

  // If there is no token, kick them back to login. Otherwise, fetch complaints.
  useEffect(() => {
    if (!token) {
      navigate('/login');
      return;
    }
    fetchMyComplaints();
  }, [navigate, token]);

  const fetchMyComplaints = async () => {
    try {
      const response = await fetch('/api/complaints/mine', {
        headers: {
          'Authorization': `Bearer ${token}`
        }
      });
      if (response.ok) {
        const data = await response.json();
        setComplaints(data);
      } else if (response.status === 401) {
        handleLogout();
      }
    } catch (err) {
      console.error("Failed to fetch complaints", err);
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setSuccess('');

    try {
      const response = await fetch('/api/complaints', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${token}`
        },
        body: JSON.stringify(formData)
      });

      if (response.status === 429) {
        setError('You have reached the limit of 5 open complaints. Please wait for them to be resolved.');
        return;
      }

      if (response.ok) {
        setSuccess('Complaint submitted successfully!');
        // Reset the text fields, keep the dropdowns
        setFormData({ ...formData, title: '', description: '', room: '' });
        fetchMyComplaints();
      } else {
        setError('Failed to submit complaint. Please check all fields.');
      }
    } catch (err) {
      setError('Network error. Please try again later.');
    }
  };

  const handleLogout = () => {
    localStorage.removeItem('token');
    navigate('/login');
  };

  return (
    <div style={{ padding: '20px', maxWidth: '800px', margin: '0 auto', position: 'relative' }}>
      <button 
        onClick={handleLogout}
        style={{ position: 'absolute', top: '20px', right: '20px', padding: '8px 16px', cursor: 'pointer' }}
      >
        Logout
      </button>

      <h2>Student Dashboard</h2>
      
      <div style={{ border: '1px solid #ccc', padding: '20px', marginBottom: '30px', borderRadius: '8px', marginTop: '40px' }}>
        <h3>File a New Complaint</h3>
        {error && <p style={{ color: 'red' }}>{error}</p>}
        {success && <p style={{ color: 'green' }}>{success}</p>}
        
        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
          <input required type="text" placeholder="Title" value={formData.title} onChange={e => setFormData({...formData, title: e.target.value})} />
          
          <select value={formData.category} onChange={e => setFormData({...formData, category: e.target.value})}>
            <option value="Electrical">Electrical</option>
            <option value="Plumbing">Plumbing</option>
            <option value="Carpentry">Carpentry</option>
            <option value="Cleaning">Cleaning</option>
            <option value="IT">IT/Network</option>
          </select>

          <select value={formData.floor} onChange={e => setFormData({...formData, floor: e.target.value})}>
            <option value="Ground">Ground</option>
            <option value="First">First</option>
            <option value="Second">Second</option>
            <option value="Third">Third</option>
            <option value="Fourth">Fourth</option>
            <option value="Fifth">Fifth</option>
            <option value="Sixth">Sixth</option>
          </select>

          <input required type="text" placeholder="Room Number (e.g. 312)" value={formData.room} onChange={e => setFormData({...formData, room: e.target.value})} />
          
          <textarea required placeholder="Describe the issue..." rows="4" value={formData.description} onChange={e => setFormData({...formData, description: e.target.value})}></textarea>
          
          <button type="submit" style={{ padding: '10px', backgroundColor: '#0056b3', color: 'white', border: 'none', borderRadius: '4px', cursor: 'pointer' }}>
            Submit Complaint
          </button>
        </form>
      </div>

      <h3>My Complaints</h3>
      {complaints.length === 0 ? <p>No complaints filed yet.</p> : (
        <table style={{ width: '100%', textAlign: 'left', borderCollapse: 'collapse' }}>
          <thead>
            <tr style={{ borderBottom: '2px solid #ddd' }}>
              <th>ID</th>
              <th>Title</th>
              <th>Status</th>
              <th>Category</th>
              <th>Date</th>
            </tr>
          </thead>
          <tbody>
            {complaints.map(c => (
              <tr key={c.id} style={{ borderBottom: '1px solid #eee' }}>
                <td>#{c.id}</td>
                <td>{c.title}</td>
                <td>
                  <span style={{ 
                    padding: '3px 8px', 
                    borderRadius: '12px', 
                    fontSize: '0.85em',
                    backgroundColor: c.status === 'PENDING' ? '#fff3cd' : '#d1e7dd'
                  }}>
                    {c.status}
                  </span>
                </td>
                <td>{c.category}</td>
                <td>{new Date(c.createdAt).toLocaleDateString()}</td>
              </tr>
            ))}
          </tbody>  
        </table>
      )}
    </div>
  );
}