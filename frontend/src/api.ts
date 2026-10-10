export type JobStatus = 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED';
export interface Job { id:string; type:string; payload:string; status:JobStatus; attempts:number; maxAttempts:number; result:string|null; lastError:string|null; createdAt:string; updatedAt:string }
export interface Stats { queued:number; running:number; succeeded:number; failed:number; cancelled:number }
const API = import.meta.env.VITE_API_BASE_URL ?? '';
async function request<T>(path:string, init?:RequestInit):Promise<T>{
  const response=await fetch(`${API}${path}`,{...init,headers:{'Content-Type':'application/json',...init?.headers}});
  if(!response.ok){let message=`Request failed (${response.status})`;try{const body=await response.json();message=body.detail||body.message||message;}catch{}throw new Error(message);}
  return response.json() as Promise<T>;
}
export const api={
  list:()=>request<{content:Job[];totalElements:number}>('/api/jobs?page=0&size=100'),
  stats:()=>request<Stats>('/api/stats'),
  create:(type:string,payload:Record<string,unknown>,maxAttempts:number)=>request<Job>('/api/jobs',{method:'POST',body:JSON.stringify({type,payload,maxAttempts})}),
  cancel:(id:string)=>request<Job>(`/api/jobs/${id}/cancel`,{method:'POST'}),
};
