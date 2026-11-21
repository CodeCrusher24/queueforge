import { useState, useEffect, useRef } from 'react';
import axios from 'axios';
import { Activity, CheckCircle, XCircle, Clock, Server, Plus, X, Terminal, Users } from 'lucide-react';

const API_BASE = 'http://localhost:8080/api/v1';

function App() {
  const [token, setToken] = useState(localStorage.getItem('token') || '');
  const [email, setEmail] = useState('admin@queueforge.local');
  const [password, setPassword] = useState('admin123');
  const [jobs, setJobs] = useState<any[]>([]);
  const [logs, setLogs] = useState<string[]>([]);
  const [workerCount, setWorkerCount] = useState(0);
  const [error, setError] = useState('');

  // Refs
  const logsEndRef = useRef<HTMLDivElement>(null);
  const terminalRef = useRef<HTMLDivElement>(null); // <-- Added this

  // Submit Modal State
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [jobType, setJobType] = useState('SEND_EMAIL');
  const [payloadStr, setPayloadStr] = useState('{\n  "recipient": "user@example.com",\n  "subject": "Hello from UI"\n}');
  const [submitError, setSubmitError] = useState('');

  // Job Detail Modal State
  const [selectedJob, setSelectedJob] = useState<any>(null);
  const [eventHistory, setEventHistory] = useState<any[]>([]);

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

  // 2. Fetch Data
  const fetchData = async () => {
    if (!token) return;
    try {
      const jobsRes = await axios.get(`${API_BASE}/jobs`, { headers: { Authorization: `Bearer ${token}` } });
      setJobs(jobsRes.data);

      const logsRes = await axios.get(`${API_BASE}/system/logs`, { headers: { Authorization: `Bearer ${token}` } });
      setLogs(logsRes.data);

      const workersRes = await axios.get(`${API_BASE}/system/workers`, { headers: { Authorization: `Bearer ${token}` } });
      setWorkerCount(workersRes.data);
    } catch (err) {
      console.error("Failed to fetch data", err);
    }
  };

  useEffect(() => {
    fetchData();
    const interval = setInterval(fetchData, 3000); 
    return () => clearInterval(interval);
  }, [token]);

  // Auto-scroll logs ONLY if the user is already at the bottom
  useEffect(() => {
    const terminal = terminalRef.current;
    if (!terminal) return;

    // Check if user is scrolled to the bottom (with a 50px buffer)
    const isAtBottom = terminal.scrollHeight - terminal.scrollTop <= terminal.clientHeight + 50;

    if (isAtBottom) {
      logsEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    }
  }, [logs]);

  // 3. Submit Job Logic
  const handleSubmitJob = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitError('');
    try {
      const payload = JSON.parse(payloadStr);
      await axios.post(`${API_BASE}/jobs`, 
        { type: jobType, payload }, 
        { headers: { Authorization: `Bearer ${token}` } }
      );
      // DO NOT close the modal. Just refresh the data.
      fetchData(); 
    } catch (err: any) {
      if (err.response?.status === 429) {
        setSubmitError('RATE LIMIT EXCEEDED: You can only submit 10 jobs per minute.');
      } else {
        setSubmitError(err.response?.data?.message || 'Failed to submit job. Check JSON format.');
      }
    }
  };

  // 4. Fetch Job Details
  const handleJobClick = async (job: any) => {
    setSelectedJob(job);
    setEventHistory([]); 
    try {
      const res = await axios.get(`${API_BASE}/jobs/${job.id}/events`, { headers: { Authorization: `Bearer ${token}` } });
      setEventHistory(res.data);
    } catch (err) {
      console.error("Failed to fetch events");
    }
  };

  if (!token) {
    return (
      <div className="flex h-screen items-center justify-center bg-slate-900">
        <form onSubmit={handleLogin} className="bg-slate-800 p-8 rounded-xl shadow-2xl w-96 border border-slate-700">
          <h1 className="text-2xl font-bold mb-6 text-center text-blue-400">QueueForge Login</h1>
          {error && <p className="text-red-400 text-sm mb-4 text-center">{error}</p>}
          <input type="email" value={email} onChange={e => setEmail(e.target.value)} className="w-full bg-slate-900 border border-slate-700 p-3 rounded mb-4 focus:outline-none focus:border-blue-500 text-white" placeholder="Email" />
          <input type="password" value={password} onChange={e => setPassword(e.target.value)} className="w-full bg-slate-900 border border-slate-700 p-3 rounded mb-6 focus:outline-none focus:border-blue-500 text-white" placeholder="Password" />
          <button type="submit" className="w-full bg-blue-600 hover:bg-blue-500 text-white font-bold py-3 rounded transition-colors">Sign In</button>
        </form>
      </div>
    );
  }

  const successCount = jobs.filter(j => j.status === 'SUCCEEDED').length;
  const failedCount = jobs.filter(j => j.status === 'FAILED').length;
  const queuedCount = jobs.filter(j => ['QUEUED', 'RUNNING', 'RETRY_WAIT'].includes(j.status)).length;

  return (
    <div className="min-h-screen bg-slate-900 text-slate-200 p-8 relative">
      <div className="max-w-7xl mx-auto space-y-6">
        
        {/* Header */}
        <div className="flex justify-between items-center">
          <h1 className="text-3xl font-bold flex items-center gap-3">
            <Activity className="text-blue-500" size={32} /> QueueForge Dashboard
          </h1>
          <div className="flex gap-4">
            <button onClick={() => setIsModalOpen(true)} className="flex items-center gap-2 bg-blue-600 hover:bg-blue-500 text-white px-4 py-2 rounded font-medium transition-colors">
              <Plus size={20} /> Submit Job
            </button>
            <button onClick={() => { setToken(''); localStorage.removeItem('token'); }} className="text-slate-400 hover:text-white px-4 py-2">Logout</button>
          </div>
        </div>

        {/* Metric Cards (Now 5 columns) */}
        <div className="grid grid-cols-1 md:grid-cols-5 gap-6">
          <div className="bg-slate-800 p-6 rounded-xl border border-slate-700">
            <div className="flex items-center justify-between"><h3 className="text-slate-400 font-medium">Total</h3><Server size={20} className="text-blue-400" /></div>
            <p className="text-3xl font-bold mt-2">{jobs.length}</p>
          </div>
          <div className="bg-slate-800 p-6 rounded-xl border border-slate-700">
            <div className="flex items-center justify-between"><h3 className="text-slate-400 font-medium">Succeeded</h3><CheckCircle size={20} className="text-green-400" /></div>
            <p className="text-3xl font-bold mt-2 text-green-400">{successCount}</p>
          </div>
          <div className="bg-slate-800 p-6 rounded-xl border border-slate-700">
            <div className="flex items-center justify-between"><h3 className="text-slate-400 font-medium">Failed</h3><XCircle size={20} className="text-red-400" /></div>
            <p className="text-3xl font-bold mt-2 text-red-400">{failedCount}</p>
          </div>
          <div className="bg-slate-800 p-6 rounded-xl border border-slate-700">
            <div className="flex items-center justify-between"><h3 className="text-slate-400 font-medium">Active</h3><Clock size={20} className="text-yellow-400" /></div>
            <p className="text-3xl font-bold mt-2 text-yellow-400">{queuedCount}</p>
          </div>
          <div className="bg-slate-800 p-6 rounded-xl border border-slate-700">
            <div className="flex items-center justify-between"><h3 className="text-slate-400 font-medium">Workers Online</h3><Users size={20} className="text-purple-400" /></div>
            <p className="text-3xl font-bold mt-2 text-purple-400">{workerCount}</p>
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
                <tr key={job.id} onClick={() => handleJobClick(job)} className="hover:bg-slate-700/50 cursor-pointer transition-colors">
                  <td className="p-4 font-mono text-sm text-slate-400">{job.id.substring(0, 8)}...</td>
                  <td className="p-4 font-medium">{job.jobType}</td>
                  <td className="p-4">
                    <span className={`px-3 py-1 rounded-full text-xs font-bold
                      ${job.status === 'SUCCEEDED' ? 'bg-green-500/20 text-green-400' : ''}
                      ${job.status === 'FAILED' ? 'bg-red-500/20 text-red-400' : ''}
                      ${job.status === 'QUEUED' ? 'bg-yellow-500/20 text-yellow-400' : ''}
                      ${job.status === 'RUNNING' ? 'bg-blue-500/20 text-blue-400' : ''}
                      ${job.status === 'RETRY_WAIT' ? 'bg-orange-500/20 text-orange-400' : ''}
                    `}>{job.status}</span>
                  </td>
                  <td className="p-4 text-slate-400">{job.attemptCount}</td>
                  <td className="p-4 text-slate-400 text-sm">{new Date(job.createdAt).toLocaleTimeString()}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        {/* Live System Logs */}
        <div className="bg-black rounded-xl border border-slate-700 overflow-hidden shadow-lg">
          <div className="bg-slate-800 px-4 py-2 border-b border-slate-700 flex items-center gap-2">
            <Terminal size={16} className="text-green-400" />
            <span className="text-sm font-mono text-slate-300">Live System Logs (Workers, Kafka, RabbitMQ)</span>
          </div>
          <div 
            ref={terminalRef} 
            className="p-4 h-64 overflow-y-auto font-mono text-sm text-green-400 flex flex-col gap-1"
          >
            {logs.map((log, index) => <div key={index}>{log}</div>)}
            <div ref={logsEndRef} />
          </div>
        </div>

      </div>

      {/* Submit Job Modal */}
      {isModalOpen && (
        <div className="fixed inset-0 bg-black/70 flex items-center justify-center p-4 z-50">
          <div className="bg-slate-800 rounded-xl border border-slate-600 w-full max-w-lg shadow-2xl">
            <div className="flex justify-between items-center p-6 border-b border-slate-700">
              <h2 className="text-xl font-bold text-white">Submit New Job</h2>
              <button onClick={() => setIsModalOpen(false)} className="text-slate-400 hover:text-white"><X size={24} /></button>
            </div>
            <form onSubmit={handleSubmitJob} className="p-6">
              {submitError && <div className="bg-red-500/20 text-red-400 p-3 rounded mb-4 text-sm">{submitError}</div>}
              <div className="mb-4">
                <label className="block text-sm font-medium text-slate-400 mb-2">Job Type</label>
                <select value={jobType} onChange={(e) => {
                    setJobType(e.target.value);
                    if (e.target.value === 'SEND_EMAIL') setPayloadStr('{\n  "recipient": "user@example.com",\n  "subject": "Hello"\n}');
                    if (e.target.value === 'GENERATE_REPORT') setPayloadStr('{\n  "reportName": "Q4-Report"\n}');
                    if (e.target.value === 'PROCESS_WEBHOOK') setPayloadStr('{\n  "url": "https://api.stripe.com",\n  "simulateFailure": true\n}');
                  }} className="w-full bg-slate-900 border border-slate-700 p-3 rounded text-white focus:outline-none focus:border-blue-500">
                  <option value="SEND_EMAIL">SEND_EMAIL</option>
                  <option value="GENERATE_REPORT">GENERATE_REPORT</option>
                  <option value="PROCESS_WEBHOOK">PROCESS_WEBHOOK</option>
                </select>
              </div>
              <div className="mb-6">
                <label className="block text-sm font-medium text-slate-400 mb-2">JSON Payload</label>
                <textarea value={payloadStr} onChange={(e) => setPayloadStr(e.target.value)} rows={6} className="w-full bg-slate-900 border border-slate-700 p-3 rounded text-white font-mono text-sm focus:outline-none focus:border-blue-500" />
              </div>
              <div className="flex justify-end gap-3">
                <button type="button" onClick={() => setIsModalOpen(false)} className="px-4 py-2 text-slate-400 hover:text-white">Cancel</button>
                <button type="submit" className="bg-blue-600 hover:bg-blue-500 text-white px-6 py-2 rounded font-medium">Submit</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Job Detail Modal */}
      {selectedJob && (
        <div className="fixed inset-0 bg-black/70 flex items-center justify-center p-4 z-50">
           <div className="bg-slate-800 p-6 rounded-xl border border-slate-600 w-full max-w-2xl shadow-2xl">
              <div className="flex justify-between items-center mb-6 border-b border-slate-700 pb-4">
                <h2 className="text-xl font-bold text-white">Job Audit Trail: {selectedJob.id.substring(0, 8)}...</h2>
                <button onClick={() => setSelectedJob(null)} className="text-slate-400 hover:text-white"><X size={24} /></button>
              </div>
              
              <div className="space-y-4 max-h-96 overflow-y-auto pr-2">
                {eventHistory.length === 0 && <p className="text-slate-500 text-center py-4">Loading events...</p>}
                {eventHistory.map((event, idx) => (
                  <div key={idx} className="flex gap-4 items-start">
                    <div className="bg-blue-500/20 text-blue-400 text-xs px-2 py-1 rounded font-bold mt-1">
                      {event.eventType}
                    </div>
                    <div>
                      <p className="text-slate-300">{event.fromStatus || 'NONE'} ➔ {event.toStatus}</p>
                      <p className="text-slate-500 text-sm">{new Date(event.createdAt).toLocaleString()} | Actor: {event.actorType}</p>
                    </div>
                  </div>
                ))}
              </div>
            </div>
        </div>
      )}
    </div>
  );
}

export default App;