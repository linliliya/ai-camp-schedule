'use strict';
const http=require('node:http');
const fs=require('node:fs/promises');
const path=require('node:path');
const {randomBytes,timingSafeEqual}=require('node:crypto');
const {GitHubClient,Journal,Problem}=require('./github-issue.cjs');
const ROOT=path.resolve(__dirname,'..');
const ALLOWED=new Set(['index.html','issue-intake.html','issue-intake.css','issue-intake.js','issue-notebook.js','issue-rich-store.js','data.js','config.js','cloud.js','task-scripts-data.js','task-scripts.js','assets/issue-editor.js','assets/issue-editor.js.LICENSE.txt']);
const TYPES={'.html':'text/html; charset=utf-8','.css':'text/css; charset=utf-8','.js':'text/javascript; charset=utf-8','.woff2':'font/woff2','.svg':'image/svg+xml','.txt':'text/plain; charset=utf-8'};
async function jsonBody(req){
  if(!/^application\/json(?:;|$)/i.test(req.headers['content-type']||''))throw new Problem('请使用 JSON 提交。',415);
  const chunks=[];let size=0;
  for await(const chunk of req){size+=chunk.length;if(size>121000000)throw new Problem('图文过大，请拆成多条记录。',413);chunks.push(chunk);}
  try{return JSON.parse(Buffer.concat(chunks).toString('utf8'));}catch{throw new Problem('提交内容未能读取，请保留草稿。');}
}
function createService({client=new GitHubClient({journal:new Journal(path.join(ROOT,'.issue-local-state'))}),port=8902,siteOrigins=port===8902?['http://127.0.0.1:8899','http://localhost:8899']:[]}={}){
  const csrf=randomBytes(32).toString('hex');
  return http.createServer(async(req,res)=>{
    const json=(code,value)=>{res.writeHead(code,{'Content-Type':'application/json; charset=utf-8','Cache-Control':'no-store','X-Content-Type-Options':'nosniff'});res.end(JSON.stringify(value));};
    try{
      const addressPort=port||req.socket.localPort,hosts=[`127.0.0.1:${addressPort}`,`localhost:${addressPort}`],host=req.headers.host;
      if(!hosts.includes(host))throw new Problem('仅允许从本机网站访问。',403);
      const origin=req.headers.origin,trustedOrigin=origin&&([`http://${host}`,...siteOrigins].includes(origin));
      if(origin&&!trustedOrigin)throw new Problem('不接受其他网站发起的请求。',403);
      if(req.headers['sec-fetch-site']==='cross-site'&&!trustedOrigin)throw new Problem('不接受跨站请求。',403);
      if(trustedOrigin){res.setHeader('Access-Control-Allow-Origin',origin);res.setHeader('Vary','Origin');}
      const url=new URL(req.url,`http://${host}`),prefix='/ai-camp-schedule';
      let pathname=decodeURIComponent(url.pathname);if(pathname===prefix)pathname=prefix+'/';if(pathname.startsWith(prefix+'/'))pathname=pathname.slice(prefix.length);
      if(pathname.startsWith('/api/issue/')){
        if(req.method==='OPTIONS'){
          if(!trustedOrigin||!['GET','POST'].includes(req.headers['access-control-request-method'])||(req.headers['access-control-request-headers']||'').split(',').map(s=>s.trim().toLowerCase()).filter(Boolean).some(s=>!['content-type','x-camp-csrf'].includes(s)))throw new Problem('不接受该跨域请求。',403);
          res.writeHead(204,{'Access-Control-Allow-Methods':'GET, POST','Access-Control-Allow-Headers':'Content-Type, X-Camp-CSRF','Cache-Control':'no-store'});res.end();return;
        }
        if(req.method==='GET'&&pathname==='/api/issue/session'){const session=await client.session();return json(200,{...session,csrf});}
        const token=Buffer.from(req.headers['x-camp-csrf']||''),expected=Buffer.from(csrf);
        if(token.length!==expected.length||!timingSafeEqual(token,expected))throw new Problem('本地连接已更新，请刷新页面后重试，草稿仍保留。',403);
        if(req.method==='GET'&&pathname==='/api/issue/repository')return json(200,await client.repository(url.searchParams.get('repo')));
        if(req.method==='GET'&&pathname==='/api/issue/history')return json(200,await client.history(url.searchParams.get('repo'),Number(url.searchParams.get('page')||1)));
        if(req.method==='POST'&&pathname==='/api/issue/submit')return json(200,await client.submit(await jsonBody(req)));
        if(req.method==='POST'&&pathname==='/api/issue/reconcile'){const body=await jsonBody(req);return json(200,await client.reconcile(body.repo,body.id));}
        throw new Problem('此接口不支持该操作。',405);
      }
      if(req.method!=='GET'&&req.method!=='HEAD')throw new Problem('此资源只支持读取。',405);
      const file=pathname==='/'?'index.html':pathname.slice(1);
      const safeAsset=/^(?:fonts|assets\/fonts)\/[A-Za-z0-9_.-]+\.woff2$/.test(file);
      if(!ALLOWED.has(file)&&!safeAsset)throw new Problem('资源不存在。',404);
      let bytes;try{bytes=await fs.readFile(path.join(ROOT,file));}catch{throw new Problem('资源尚未准备好，请运行本地网站启动脚本。',404);}
      res.writeHead(200,{'Content-Type':TYPES[path.extname(file)]||'application/octet-stream','Cache-Control':'no-store','X-Content-Type-Options':'nosniff','Referrer-Policy':'no-referrer','Cross-Origin-Resource-Policy':'same-origin'});res.end(req.method==='HEAD'?undefined:bytes);
    }catch(error){json(error.status||500,{error:error instanceof Problem?error.message:'本机操作未完成，草稿仍保留，请稍后重试。',uncertain:!!error.uncertain});}
  });
}
if(require.main===module){const server=createService();server.listen(8902,'127.0.0.1',()=>process.stdout.write('Local website: http://127.0.0.1:8902/ (also supports the existing preview on port 8899)\n'));server.on('error',error=>{process.stderr.write(error.code==='EADDRINUSE'?'8902 端口已使用；已有本地服务可能正在运行，请先查看其页面。\n':'本地网站未能启动。\n');process.exitCode=1;});}
module.exports={createService};
