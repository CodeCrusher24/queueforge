import { useState, useEffect } from 'react';
import axios from 'axios';
import { Activity, CheckCircle, XCircle, Clock, Server } from 'lucide-react';

const API_BASE = 'http://localhost:8080/api/v1';

function App() {
  const [token, setToken] = useState(localStorage.getItem('token') || '');
  const [email, setEmail] = useState('admin@queueforge.local');
  const [password, setPassword] = useState('admin123');
  const [jobs, setJobs] = useState<any[]>([]);
  const [error, setError] = useState('');

  // 1. Login Logic
  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      const res = await axios.post(`${API_BASE}/auth/login`, { email, password });
      setToken(res.data.token);
      localStorage.setItem('token', res.data.token);
      setError('');
    } catch (err) {
      setError('Login failed. Check credentials.');
    }
  };

  // 2. Fetch Jobs Logic
  const fetchJobs = async () => {
    if (!token) return;
    try {
      const res = await axios.get(`${API_BASE}/jobs`, {
        headers: { Authorization: `Bearer ${token}` }
      });
      setJobs(res.data);
    } catch (err) {
      console.error("Failed to fetch jobs", err);
    }
  };

  useEffect(() => {
    fetchJobs();
    const interval = setInterval(fetchJobs, 3000); // Poll every 3 seconds
    return () => clearInterval(interval);
  }, [token]);

  // 3. UI for Login
  if (!token) {
    return (
      <div className="flex h-screen items-center justify-center bg-slate-900">
        <form onSubmit={handleLogin} className="bg-slate-800 p-8 rounded-xl shadow-2xl w-96 border border-slate-700">
          <h1 className="text-2xl font-bold mb-6 text-center text-blue-400">QueueForge Login</h1>
          {error && <p className="text-red-400 text-sm mb-4 text-center">{error}</p>}
          <input 
            type="email" value={email} onChange={e => setEmail(e.target.value)}
            className="w-full bg-slate-900 border border-slate-700 p-3 rounded mb-4 focus:outline-none focus:border-blue-500 text-white"
            placeholder="Email" 
          />
          <input 
            type="password" value={password} onChange={e => setPassword(e.target.value)}
            className="w-full bg-slate-900 border border-slate-700 p-3 rounded mb-6 focus:outline-none focus:border-blue-500 text-white"
            placeholder="Password" 
          />
          <button type="submit" className="w-full bg-blue-600 hover:bg-blue-500 text-white font-bold py-3 rounded transition-colors">
            Sign In
          </button>
        </form>
      </div>
    );
  }

  // 4. UI for Dashboard
  const successCount = jobs.filter(j => j.status === 'SUCCEEDED').length;
  const failedCount = jobs.filter(j => j.status === 'FAILED').length;
  const queuedCount = jobs.filter(j => j.status === 'QUEUED' || j.status === 'RUNNING').length;

  return (
    <div className="min-h-screen bg-slate-900 text-slate-200 p-8">
      <div className="max-w-7xl mx-auto">
        
        {/* Header */}
        <div className="flex justify-between items-center mb-8">
          <h1 className="text-3xl font-bold flex items-center gap-3">
            <Activity className="text-blue-500" size={32} /> 
            QueueForge Dashboard
          </h1>
          <button onClick={() => { setToken(''); localStorage.removeItem('token'); }} className="text-slate-400 hover:text-white transition-colors">
            Logout
          </button>
        </div>

        {/* Metric Cards */}
        <div className="grid grid-cols-1 md:grid-cols-4 gap-6 mb-8">
          <div className="bg-slate-800 p-6 rounded-xl border border-slate-700">
            <div className="flex items-center justify-between">
              <h3 className="text-slate-400 font-medium">Total Jobs</h3>
              <Server size={20} className="text-blue-400" />
            </div>
            <p className="text-3xl font-bold mt-2">{jobs.length}</p>
          </div>
          <div className="bg-slate-800 p-6 rounded-xl border border-slate-700">
            <div className="flex items-center justify-between">
              <h3 className="text-slate-400 font-medium">Succeeded</h3>
              <CheckCircle size={20} className="text-green-400" />
            </div>
            <p className="text-3xl font-bold mt-2 text-green-400">{successCount}</p>
          </div>
          <div className="bg-slate-800 p-6 rounded-xl border border-slate-700">
            <div className="flex items-center justify-between">
              <h3 className="text-slate-400 font-medium">Failed</h3>
              <XCircle size={20} className="text-red-400" />
            </div>
            <p className="text-3xl font-bold mt-2 text-red-400">{failedCount}</p>
          </div>
          <div className="bg-slate-800 p-6 rounded-xl border border-slate-700">
            <div className="flex items-center justify-between">
              <h3 className="text-slate-400 font-medium">Queued / Running</h3>
              <Clock size={20} className="text-yellow-400" />
            </div>
            <p className="text-3xl font-bold mt-2 text-yellow-400">{queuedCount}</p>
          </div>
        </div>

        {/* Job Table */}
        <div className="bg-slate-800 rounded-xl border border-slate-700 overflow-hidden">
          <table className="w-full text-left">
            <thead className="bg-slate-900/50">
              <tr>
                <th className="p-4 font-medium text-slate-400">Job ID</th>
                <th className="p-4 font-medium text-slate-400">Type</th>
                <th className="p-4 font-medium text-slate-400">Status</th>
                <th className="p-4 font-medium text-slate-400">Attempts</th>
                <th className="p-4 font-medium text-slate-400">Created At</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-700">
              {jobs.map((job) => (
                <tr key={job.id} className="hover:bg-slate-700/50 transition-colors">
                  <td className="p-4 font-mono text-sm text-slate-400">{job.id.substring(0, 8)}...</td>
                  <td className="p-4 font-medium">{job.jobType}</td>
                  <td className="p-4">
                    <span className={`px-3 py-1 rounded-full text-xs font-bold
                      ${job.status === 'SUCCEEDED' ? 'bg-green-500/20 text-green-400' : ''}
                      ${job.status === 'FAILED' ? 'bg-red-500/20 text-red-400' : ''}
                      ${job.status === 'QUEUED' ? 'bg-yellow-500/20 text-yellow-400' : ''}
                      ${job.status === 'RUNNING' ? 'bg-blue-500/20 text-blue-400' : ''}
                      ${job.status === 'RETRY_WAIT' ? 'bg-orange-500/20 text-orange-400' : ''}
                    `}>
                      {job.status}
                    </span>
                  </td>
                  <td className="p-4 text-slate-400">{job.attemptCount}</td>
                  <td className="p-4 text-slate-400 text-sm">{new Date(job.createdAt).toLocaleTimeString()}</td>
                </tr>
              ))}
              {jobs.length === 0 && (
                <tr>
                  <td colSpan={5} className="p-8 text-center text-slate-500">
                    No jobs found. Submit some jobs via the API!
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>

      </div>
    </div>
  );
}

export default App;