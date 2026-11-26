const MAX=150000000;
const validId=value=>typeof value==='string'&&/^[A-Za-z0-9-]{8,100}$/.test(value);
const hash=async bytes=>[...new Uint8Array(await crypto.subtle.digest('SHA-256',bytes))].map(x=>x.toString(16).padStart(2,'0')).join('');
function signature(bytes,mime){return mime==='image/png'&&[137,80,78,71,13,10,26,10].every((v,i)=>bytes[i]===v)||mime==='image/jpeg'&&bytes[0]===255&&bytes[1]===216&&bytes[2]===255||mime==='image/gif'&&/^GIF8[79]a$/.test(String.fromCharCode(...bytes.slice(0,6)))||mime==='image/webp'&&String.fromCharCode(...bytes.slice(0,4))==='RIFF'&&String.fromCharCode(...bytes.slice(8,12))==='WEBP';}
async function imageRecord(image){const bytes=new Uint8Array(await image.blob.arrayBuffer());if(bytes.length<1||bytes.length>10000000||!signature(bytes,image.mime))throw Error('备份包含无法识别的图片，请保留原稿并检查。');let binary='';for(let i=0;i<bytes.length;i+=32768)binary+=String.fromCharCode(...bytes.subarray(i,i+32768));return {id:image.id,name:image.name||'截图',mime:image.mime,data:btoa(binary),sha256:await hash(bytes)};}
export async function exportDrafts(drafts){
  const rows=[];for(const draft of drafts){const images=[];for(const image of draft.images)images.push(await imageRecord(image));rows.push({id:draft.id,repo:draft.repo,body:draft.body,created:draft.created,updated:draft.updated,uncertain:!!draft.uncertain,images});}
  if(!rows.length)throw Error('没有需要备份的草稿。');
  const json=JSON.stringify({format:'camp-issue-drafts',version:1,exportedAt:new Date().toISOString(),drafts:rows});if(new Blob([json]).size>MAX)throw Error('备份超过 150 MB，请先处理部分草稿再导出。');return json;
}
export async function importDrafts(text){
  if(typeof text!=='string'||new Blob([text]).size>MAX)throw Error('备份过大或无法读取。');
  let archive;try{archive=JSON.parse(text);}catch{throw Error('备份不是完整的 JSON 文件。');}
  if(archive?.format!=='camp-issue-drafts'||archive.version!==1||!Array.isArray(archive.drafts)||archive.drafts.length>200)throw Error('备份格式或版本不支持。');
  const ids=new Set(),rows=[];
  for(const row of archive.drafts){
    if(!validId(row.id)||ids.has(row.id)||typeof row.body!=='string'||row.body.length>55000||typeof row.repo!=='string'||row.repo&&!/^[A-Za-z0-9-]{1,39}\/[A-Za-z0-9_.-]{1,100}$/.test(row.repo)||['.','..'].includes(row.repo.split('/')[1])||!Array.isArray(row.images)||row.images.length>9||typeof row.uncertain!=='boolean'||!Number.isFinite(Date.parse(row.created))||!Number.isFinite(Date.parse(row.updated)))throw Error('草稿元数据异常，未导入任何内容。');
    ids.add(row.id);const images=[],imageIds=new Set();
    for(const image of row.images){
      if(!validId(image.id)||imageIds.has(image.id)||typeof image.name!=='string'||image.name.length>500||typeof image.data!=='string'||image.data.length>13333336||image.data.length%4||!/^[A-Za-z0-9+/]*={0,2}$/.test(image.data))throw Error('图片数据异常，未导入任何内容。');
      const bytes=Uint8Array.from(atob(image.data),char=>char.charCodeAt(0));if(bytes.length<1||bytes.length>10000000||!signature(bytes,image.mime)||await hash(bytes)!==image.sha256)throw Error('图片校验失败，未导入任何内容。');
      imageIds.add(image.id);images.push({id:image.id,name:image.name,mime:image.mime,blob:new Blob([bytes],{type:image.mime})});
    }
    for(const match of row.body.matchAll(/camp-image:([A-Za-z0-9-]+)/g))if(!imageIds.has(match[1]))throw Error('正文引用的截图缺失，未导入任何内容。');
    rows.push({id:row.id,repo:row.repo,body:row.body,created:row.created,updated:row.updated,uncertain:row.uncertain,images});
  }
  return rows;
}
export async function additions(existing,incoming){
  const fingerprint=async draft=>JSON.stringify({repo:draft.repo.toLowerCase(),body:draft.body,uncertain:draft.uncertain,images:await Promise.all(draft.images.map(async image=>({id:image.id,mime:image.mime,sha256:await hash(await image.blob.arrayBuffer())})))});
  const result=[];for(const row of incoming){const old=existing.find(d=>d.id===row.id);if(old){if(await fingerprint(old)!==await fingerprint(row))throw Error('同一份草稿存在不同内容，已停止导入；现有草稿未覆盖。');}else result.push(row);}return result;
}
