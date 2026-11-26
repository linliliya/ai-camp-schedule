import {Editor} from '@tiptap/core';
import StarterKit from '@tiptap/starter-kit';
import Image from '@tiptap/extension-image';
import {Markdown} from '@tiptap/markdown';
import {TableKit} from '@tiptap/extension-table';
export function makeEditor(element,{body='',images=[],onUpdate,onFiles,onWarning}){
  let urls=new Map(),suspended=false;
  const Picture=Image.extend({
    addAttributes(){return {...this.parent?.(),src:{default:null,parseHTML:node=>{const src=node.getAttribute('src')||'';return /^camp-image:[A-Za-z0-9-]+$/.test(src)?src:null;}}};},
    renderHTML({HTMLAttributes}){const id=(HTMLAttributes.src||'').replace('camp-image:','');return ['img',{...HTMLAttributes,src:urls.get(id)||'',alt:HTMLAttributes.alt||'截图',loading:'lazy'}];},
  });
  const editor=new Editor({element,extensions:[StarterKit.configure({link:{openOnClick:false,protocols:['https','http','mailto'],HTMLAttributes:{rel:'noopener noreferrer',target:'_blank'}}}),Markdown,TableKit,Picture],content:'',
    editorProps:{attributes:{'aria-label':'图文记录编辑区',spellcheck:'false'},
      handlePaste(view,event){
        const files=[...(event.clipboardData?.files||[])];
        if(files.length){event.preventDefault();void onFiles(files);return true;}
        const html=event.clipboardData?.getData('text/html');
        if(html){event.preventDefault();const doc=new DOMParser().parseFromString(html,'text/html');const hadImages=doc.querySelectorAll('img').length;
          doc.querySelectorAll('script,style,iframe,object,embed,svg,math,form,input,button,video,audio,img').forEach(node=>node.remove());
          doc.querySelectorAll('*').forEach(node=>{for(const attribute of [...node.attributes])if(attribute.name!=='href'||!/^https?:\/\//i.test(attribute.value))node.removeAttribute(attribute.name);});
          editor.commands.insertContent(doc.body.innerHTML);if(hadImages)onWarning('粘贴的外部图片未导入，请把截图直接粘贴进来或点击“添加图片”。');return true;
        }
        const text=event.clipboardData?.getData('text/plain')||'';
        if(/^\s*(#{1,6}\s|[-*]\s|```|\|.+\||\*\*|!\[)/m.test(text)){event.preventDefault();const hasImages=/!\[[^\]]*\]\([^\n]*\)/.test(text);editor.commands.insertContent(hasImages?text.replace(/!\[[^\]]*\]\([^\n]*\)/g,'（图片未导入，请粘贴原图）'):text,{contentType:'markdown'});if(hasImages)onWarning('Markdown 中的外部图片未导入，请直接粘贴截图或点击“添加图片”。');return true;}
        return false;
      },
      handleDrop(view,event){const files=[...(event.dataTransfer?.files||[])];if(!files.length)return false;event.preventDefault();const pos=view.posAtCoords({left:event.clientX,top:event.clientY});if(pos)editor.commands.setTextSelection(pos.pos);void onFiles(files);return true;},
    },onUpdate(){if(!suspended)onUpdate();},
  });
  function setPictures(rows){const ids=new Set(rows.map(row=>row.id));for(const [id,url]of urls)if(!ids.has(id)){URL.revokeObjectURL(url);urls.delete(id);}for(const row of rows)if(!urls.has(row.id))urls.set(row.id,URL.createObjectURL(row.blob));}
  function set(body,rows){suspended=true;setPictures(rows);editor.commands.setContent(body,{contentType:'markdown'});suspended=false;}
  set(body,images);
  return {editor,set,body:()=>editor.getMarkdown(),images:rows=>rows.filter(row=>editor.getMarkdown().includes(`camp-image:${row.id}`)),
    insert(rows){suspended=true;for(const row of rows)urls.set(row.id,URL.createObjectURL(row.blob));editor.chain().focus().insertContent(rows.map(row=>({type:'image',attrs:{src:`camp-image:${row.id}`,alt:'截图'}}))).run();suspended=false;onUpdate();},
    destroy(){editor.destroy();for(const url of urls.values())URL.revokeObjectURL(url);},
  };
}
