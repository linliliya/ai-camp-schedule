import {createRequire} from 'node:module';
import {fileURLToPath,pathToFileURL} from 'node:url';
import path from 'node:path';
import fs from 'node:fs';
const root=fileURLToPath(new URL('../',import.meta.url));
const resolve=createRequire(path.join(root,'local-sop/package.json'));
const {build}=await import(pathToFileURL(resolve.resolve('vite')).href);
const aliases=['@tiptap/core','@tiptap/starter-kit','@tiptap/extension-image','@tiptap/extension-table','@tiptap/markdown'].map(name=>({find:new RegExp('^'+name+'$'),replacement:resolve.resolve(name).replace(/\.cjs$/,'.js')}));
const notices={name:'local-license-notices',generateBundle(_,bundle){
  const packages=new Map();
  for(const chunk of Object.values(bundle))if(chunk.type==='chunk')for(const id of Object.keys(chunk.modules)){
    const normalized=id.replaceAll('\\','/'),marker='/node_modules/',position=normalized.lastIndexOf(marker);if(position<0)continue;
    const names=normalized.slice(position+marker.length).split('/'),name=names[0].startsWith('@')?names.slice(0,2).join('/'):names[0],folder=normalized.slice(0,position+marker.length)+name;
    if(packages.has(folder)||!fs.existsSync(path.join(folder,'package.json')))continue;
    const info=JSON.parse(fs.readFileSync(path.join(folder,'package.json'),'utf8')),file=fs.readdirSync(folder).find(file=>/^licen[sc]e(?:\.|$)/i.test(file));
    if(file)packages.set(folder,`${info.name} ${info.version}\n${fs.readFileSync(path.join(folder,file),'utf8')}\n`);
  }
  this.emitFile({type:'asset',fileName:'issue-editor.js.LICENSE.txt',source:[...packages.values()].sort().join('\n--------------------\n\n')});
}};
await build({root,configFile:false,resolve:{alias:aliases},plugins:[notices],build:{outDir:'assets',emptyOutDir:false,lib:{entry:path.join(root,'web-issue/main.js'),name:'CampIssue',formats:['iife'],fileName:()=> 'issue-editor.js'},sourcemap:false}});
