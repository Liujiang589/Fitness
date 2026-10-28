import {exercises, foods} from './catalog.js';
import {VERSION, STORAGE_KEY, MAX_BACKUP_BYTES, Store, clone, uid, localDate, decimal, parseBackup, activeSession, elapsed, remainingRest, recap, startSession, pauseSession, resumeSession, finishSession, toggleSet, foodEntry, calories} from './core.js';

const $ = (q, root=document) => root.querySelector(q);
const $$ = (q, root=document) => [...root.querySelectorAll(q)];
const esc = s => String(s ?? '').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const n = (x,d=1) => Number(x).toLocaleString('zh-CN',{maximumFractionDigits:d});
const duration = ms => { const s=Math.floor(Math.max(0,ms)/1000); return `${String(Math.floor(s/3600)).padStart(2,'0')}:${String(Math.floor(s/60)%60).padStart(2,'0')}:${String(s%60).padStart(2,'0')}`; };
const shortDuration = ms => `${Math.floor(ms/60_000)} 分钟`;
const paths = {
  today:'<path d="m3 10 9-7 9 7v10H4V10M9 21v-8h6v8"/>',
  train:'<path d="M5 7v10M2 9v6m17-8v10m3-8v6M5 12h14"/>',
  library:'<path d="M4 4h7v7H4zM14 4h7v7h-7zM4 14h7v7H4zM14 14h7v7h-7z"/>',
  food:'<path d="M6 3v7m3-7v7M3 3v7c0 3 6 3 6 0M6 12v9M17 3c-3 4-3 9 1 9h2V3h-3Zm3 9v9"/>',
  history:'<path d="M4 6v5h5M4 11a8 8 0 1 1 1 6M12 7v5l3 2"/>',
  plus:'<path d="M12 5v14M5 12h14"/>',
  leaf:'<path d="M5 19C-1 6 12 3 21 3c0 12-5 20-16 16Zm0 0L16 8"/>',
  arrow:'<path d="M5 12h14m-5-5 5 5-5 5"/>',
  check:'<path d="m5 12 4 4L19 6"/>',
  share:'<path d="M12 16V2m-5 5 5-5 5 5M5 10H3v12h18V10h-2"/>',
  water:'<path d="M12 2s-7 8-7 13a7 7 0 0 0 14 0c0-5-7-13-7-13Z"/>',
  book:'<path d="M4 3h13a3 3 0 0 1 3 3v15H7a3 3 0 0 1-3-3V3Zm0 14h16M8 7h8M8 11h6"/>'
};
const icon = name => `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${paths[name]||paths.book}</svg>`;
const button = (text,action,cls='primary',extra='') => `<button type="button" class="${cls}" data-action="${action}" ${extra}>${text}</button>`;
const metric = (value,unit,label) => `<div class="metric"><strong>${value}<small>${unit}</small></strong><span>${label}</span></div>`;
const header = (eyebrow,title,sub='') => `<div class="page-head"><div class="eyebrow">${eyebrow}</div><h1>${title}</h1>${sub?`<p>${sub}</p>`:''}</div>`;
const empty = (name,title,desc,btn='') => `<div class="card empty"><div class="empty-icon">${icon(name)}</div><h2>${title}</h2><p>${desc}</p>${btn}</div>`;
let store, fatal=null;
try { store = new Store(localStorage,exercises); } catch(e) { fatal=e; }
const state = () => store.value;
const allExercises = () => [...exercises,...state().custom];
const main=$('#main'), sheet=$('#sheet');
let route=location.hash.slice(1)||'today', libraryFilter='全部', librarySearch='', nutritionDate=localDate(), historyFilter='全部';
let sheetDirty=false, sheetKind='', sheetPayload=null, lastFocus=null, toastTimer=null, updateWorker=null, conflict=false;
let selection=[], editingPlan=null, foodSelected=null;

function toast(text){ $('#toast').textContent=text;$('#toast').hidden=false;clearTimeout(toastTimer);toastTimer=setTimeout(()=>$('#toast').hidden=true,3500); }
function ask(message){
  return new Promise(resolve=>{
    const dialog=$('#confirmation');$('#confirm-message').textContent=message;
    const finish=value=>{dialog.removeEventListener('click',click);dialog.removeEventListener('cancel',cancel);dialog.close();resolve(value);};
    const click=e=>{const answer=e.target.closest('[data-answer]')?.dataset.answer;if(answer)finish(answer==='ok');};
    const cancel=e=>{e.preventDefault();finish(false);};
    dialog.addEventListener('click',click);dialog.addEventListener('cancel',cancel);dialog.showModal();
  });
}
function error(e){const el=sheet.open?$('#sheet-error'):$('#notice');el.textContent=e?.message||String(e);el.hidden=false;}
function mutate(fn, redraw=true){ if(fatal||conflict)throw new Error('请先刷新页面，恢复最新记录后再操作');store.commit(fn);if(redraw)render(); }
function navigate(to){if(route===to){render();return;}location.hash=to;}
function openSheet(title,kicker,html,kind='',payload=null){
  lastFocus=document.activeElement;sheetDirty=false;sheetKind=kind;sheetPayload=payload;
  $('#sheet-title').textContent=title;$('#sheet-kicker').textContent=kicker;$('#sheet-body').innerHTML=html;$('#sheet-error').hidden=true;
  if(!sheet.open)sheet.showModal();document.body.style.overflow='hidden';sheet.scrollTop=0;
}
async function closeSheet(force=false){if(!force&&sheetDirty&&!await ask('还没有保存，确定放弃这次编辑吗？'))return false;sheet.close();document.body.style.overflow='';sheetDirty=false;sheetKind='';lastFocus?.focus?.();return true;}
sheet.addEventListener('cancel',e=>{e.preventDefault();closeSheet();});
sheet.addEventListener('input',()=>{sheetDirty=true;$('#sheet-error').hidden=true;});
sheet.addEventListener('change',()=>{sheetDirty=true;});
window.addEventListener('hashchange',async()=>{try{requireValidSets();}catch(e){history.replaceState(null,'',`#${route}`);error(e);return;}if(sheet.open&&!await closeSheet()){history.replaceState(null,'',`#${route}`);return;}route=location.hash.slice(1)||'today';render();scrollTo(0,0);main.focus({preventScroll:true});});
window.addEventListener('storage',e=>{if(e.key!==STORAGE_KEY)return;conflict=true;$('#notice').innerHTML='另一个页面更新了记录，请刷新后继续。'+button('刷新','reload','text-button');$('#notice').hidden=false;});
window.addEventListener('beforeunload',e=>{if(sheetDirty||$$('input[data-set]:invalid').length){e.preventDefault();e.returnValue='';}});
window.addEventListener('offline',()=>toast('已离线，已缓存的页面可继续使用'));window.addEventListener('online',()=>toast('已恢复网络连接'));

const tabs=[['today','今天'],['train','训练'],['library','动作'],['food','饮食'],['history','记录']];
function render(){
  if(fatal){main.innerHTML=header('LOCAL DATA','记录暂时无法读取')+`<div class="card"><p>${esc(fatal.message)}</p><p class="caption">没有覆盖原记录。你可以先下载原始数据，再刷新页面或恢复网页备份。</p><div class="actions">${button('导出原始数据','raw-export','primary')}${button('刷新重试','reload','secondary')}</div></div>`;$('#nav').hidden=true;return;}
  $('#nav').innerHTML=tabs.map(([key,label])=>`<a href="#${key}" ${route===key?'aria-current="page"':''}>${icon(key)}<span>${label}</span></a>`).join('');
  const pages={today:renderToday,train:renderTrain,library:renderLibrary,food:renderFood,history:renderHistory,profile:renderProfile};
  if(!pages[route])route='today';main.innerHTML=pages[route]();updateTimers();
}
function renderToday(){
  const day=localDate(),today=new Date(),finished=state().sessions.filter(x=>x.endedAt!==null),active=activeSession(state());
  const first=new Date(today);first.setHours(12,0,0,0);first.setDate(first.getDate()-((first.getDay()+6)%7));
  const week=Array.from({length:7},(_,i)=>{const d=new Date(first);d.setDate(d.getDate()+i);const key=localDate(d.getTime());return{d,key,label:['一','二','三','四','五','六','日'][i]};});
  const weekDone=finished.filter(x=>localDate(x.endedAt)>=week[0].key&&localDate(x.endedAt)<=week[6].key).length;
  const mealCount=state().food.filter(x=>x.date===day).length;
  let hero=active?`<div class="card hero"><p class="eyebrow">${active.pausedAt!==null?'已暂停 · 随时继续':'进行中的训练'}</p><h2>${esc(active.title)}</h2><p>已经完成 ${recap(active).done} 组。回到你的节奏。</p>${button('继续训练 '+icon('arrow'),'go-train')}</div>`:
    state().plans.length?`<div class="card hero"><p class="eyebrow">给自己，一段专注的时间</p><h2>今天，练得从容一点。</h2><p>你的 ${state().plans.length} 个计划已经准备好，选择适合今天的一个。</p>${button('选择训练 '+icon('arrow'),'go-train')}</div>`:
    `<div class="card hero"><div class="hero-mark">${icon('train')}</div><p class="eyebrow">从你喜欢的动作开始</p><h2>按自己的节奏，<br>开始第一练。</h2><p>选几个动作，安排组数。<br>属于你的计划，由你来定。</p>${button('创建我的计划 '+icon('plus'),'plan-new')}</div>`;
  return header(`${today.getMonth()+1} 月 ${today.getDate()} 日 · 星期${['日','一','二','三','四','五','六'][today.getDay()]}`,'每一次，都算数。','专注训练，也好好照顾自己。')+hero+
    `<div class="section-head"><h2>这一周的节奏</h2><span class="muted"><small>${weekDone?`完成 ${weekDone} 次训练`:'慢慢开始就好'}</small></span></div><div class="card"><div class="week">${week.map(({d,key,label})=>`<div class="day ${key===day?'today':''} ${finished.some(x=>localDate(x.endedAt)===key)?'done':''}" ${key===day?'aria-label="今天"':''}>${label}<b>${d.getDate()}</b><i></i></div>`).join('')}</div></div>`+
    `<div class="section-head"><h2>照顾日常</h2></div><div class="card">${rowAction('food','饮食手记',mealCount?`今天已记下 ${mealCount} 份食物`:'输入食物重量，营养自动计算','go-food','green')}<hr class="rule">${rowAction('library','了解动作','40 个动作，随时查阅训练要点','go-library')}</div>`+
    `<div class="card">${rowAction('share','放到 iPhone 主屏幕','下次，像打开 App 一样打开独练','install')}</div><p class="caption">网页体验版 · 记录仅保存在当前浏览器。建议先添加到主屏幕再开始记录，并定期备份。</p>`;
}
function rowAction(ic,title,desc,action,color=''){return `<button class="row row-link" data-action="${action}"><span class="tile-icon ${color}">${icon(ic)}</span><span class="grow"><h3>${title}</h3><p>${desc}</p></span><span class="chevron">›</span></button>`;}
function renderTrain(){const t=activeSession(state());if(t)return renderActive(t);
  return header('YOUR TRAINING','我的训练','让计划适合你，而不是反过来。')+
    (state().plans.length?`<div class="section-head"><h2>${state().plans.length} 个训练计划</h2>${button('＋ 新建计划','plan-new','text-button')}</div>`+state().plans.map(p=>`<article class="card plan-card"><div class="row spread"><h3>${esc(p.name)}</h3>${button('编辑','plan-edit','text-button',`data-id="${p.id}"`)}</div><p class="preview">${p.exerciseIds.map(k=>esc(allExercises().find(e=>e.id===k)?.name)).join(' · ')}</p><span class="pill">${p.exerciseIds.length} 个动作 · 每个 ${p.sets} 组 × ${p.reps} 次</span><div class="actions">${button('开始训练 '+icon('arrow'),'start','primary',`data-id="${p.id}"`)}</div></article>`).join(''):
      empty('train','从一份自己的计划开始','动作、组数、次数，都按你的习惯来。不会自动加入默认训练。',button('创建我的计划','plan-new')))+
    `<p class="caption">训练初始负重为 0 kg。开始后按实际使用的重量填写；徒手动作保持 0 kg。</p>`;
}
function renderActive(t){const summary=recap(t);return header('STAY WITH YOUR PACE','专注这一练')+
  `<section class="card training-top"><p class="eyebrow">${t.pausedAt!==null?'训练已暂停':'正在训练 · 每组都会保存'}</p><h2>${esc(t.title)}</h2><div class="timer" data-timer>${duration(elapsed(t))}</div><small class="muted">训练用时 · 不含主动暂停</small><div class="progress"><span style="width:${summary.done/summary.total*100}%"></span></div><small class="muted">已完成 ${summary.done} / ${summary.total} 组</small><div class="actions">${button(t.pausedAt!==null?'继续训练':'暂停一下',t.pausedAt!==null?'resume':'pause','secondary')}${button('保存并结束','finish','primary')}</div></section>`+
  `<div class="rest-box" data-rest-box ${remainingRest(t)>0?'':'hidden'}><span>组间休息 <strong data-rest></strong></span>${button('跳过休息','skip-rest','text-button')}</div>`+
  t.movements.map((m,mi)=>`<section class="card"><div class="row spread"><div class="grow"><p class="eyebrow">动作 ${String(mi+1).padStart(2,'0')}</p><h3>${esc(m.name)}</h3></div>${button('要点','guide','text-button',`data-id="${m.exerciseId}"`)}</div><div class="set-labels"><span>组</span><span>重量 / kg</span><span>次数</span><span>完成</span></div>${m.sets.map((x,i)=>`<div class="set-row ${x.done?'done':''}"><span class="set-index">${i+1}</span><input type="text" inputmode="decimal" pattern="(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)" required aria-label="${esc(m.name)}第${i+1}组重量" value="${x.kg}" data-set="${x.id}" data-movement="${m.id}" data-field="kg" ${x.done||t.pausedAt!==null?'disabled':''}><input type="text" inputmode="numeric" pattern="[0-9]+" required aria-label="${esc(m.name)}第${i+1}组次数" value="${x.reps}" data-set="${x.id}" data-movement="${m.id}" data-field="reps" ${x.done||t.pausedAt!==null?'disabled':''}><button class="set-check" data-action="complete-set" data-movement="${m.id}" data-id="${x.id}" aria-label="${x.done?'取消完成':'完成'}${esc(m.name)}第${i+1}组" aria-pressed="${x.done}" ${t.pausedAt!==null?'disabled':''}>${x.done?'✓':'○'}</button></div><div class="warmup-row">${button(x.warmup?'热身组 · 不计训练量':'工作组','warmup','warmup-toggle',`aria-pressed="${x.warmup}" data-movement="${m.id}" data-id="${x.id}" ${x.done||t.pausedAt!==null?'disabled':''}`)}${m.sets.length>1?button('移除','remove-set','warmup-toggle',`aria-label="移除${esc(m.name)}第${i+1}组" data-movement="${m.id}" data-id="${x.id}" ${t.pausedAt!==null?'disabled':''}`):''}</div>`).join('')}${button('＋ 添加一组','add-set','text-button',`data-id="${m.id}" ${t.pausedAt!==null||m.sets.length>=50?'disabled':''}`)}</section>`).join('')+
  `<div class="actions">${button('保存并结束','finish')}${button('放弃这次训练','discard','secondary')}</div><p class="caption">网页关闭后，已保存的训练仍可继续。后台不会发出休息提醒；需要停表时，请先点击“暂停一下”。</p>`;
}
function renderLibrary(){return header('MOVE WITH UNDERSTANDING','动作资料库','理解每一个动作，练得更有把握。')+`<input id="library-search" class="search" type="search" placeholder="搜索动作、肌群或器械" aria-label="搜索动作" value="${esc(librarySearch)}"><div class="chips">${['全部','胸部','背部','腿部','肩部','手臂','腹部','小腿'].map(x=>button(x,'filter-library','chip',`aria-pressed="${libraryFilter===x}" data-value="${x}"`)).join('')}</div><div id="library-results" class="library-grid">${libraryCards()}</div><div class="actions">${button('＋ 添加自己的动作','exercise-new','secondary')}</div><p class="caption">本体验版提供文字动作要点，暂不包含安卓版的三维动作演示。</p>`;}
function libraryCards(){const q=librarySearch.trim().toLowerCase();const list=allExercises().filter(e=>(libraryFilter==='全部'||e.muscle===libraryFilter)&&`${e.name}${e.muscle}${e.equipment}`.toLowerCase().includes(q));return list.length?list.map(e=>`<button class="card exercise-card" data-action="guide" data-id="${e.id}"><span class="muscle">${esc(e.muscle)} / ${esc(e.equipment)}</span><h3>${esc(e.name)}</h3><small>${esc(e.focus||'我的自定义动作')}</small><span class="view">查看动作要点 ↗</span></button>`).join(''):`<div class="filter-empty">${empty('library','没有找到这个动作','换个名称试试，或在下方添加自己的动作。')}</div>`;}
function renderFood(){const list=state().food.filter(x=>x.date===nutritionDate),totals=list.reduce((a,f)=>({protein:a.protein+f.protein,carbs:a.carbs+f.carbs,fat:a.fat+f.fat}),{protein:0,carbs:0,fat:0}),energy=calories(totals);const water=state().water.filter(x=>x.date===nutritionDate).reduce((a,w)=>a+w.ml,0);
  return header('EAT & FEEL GOOD','饮食手记','选好食物，称一下重量，剩下的交给独练。')+`<label>记录日期<input class="date-control" id="nutrition-date" type="date" value="${nutritionDate}" max="${localDate()}"></label><div class="card nutrition-sum"><div class="row spread"><h3>${nutritionDate===localDate()?'今天':'这一天'}的摄入</h3><span class="pill">${list.length} 份食物</span></div><div class="metrics">${metric(n(energy,0),'kcal','已记录的能量')}</div><div class="nutrition-bars" aria-hidden="true">${[totals.protein*4,totals.carbs*4,totals.fat*9].map(v=>`<span style="width:${energy?v/energy*100:0}%"></span>`).join('')}</div><div class="metrics">${metric(n(totals.protein),'g','蛋白质')}${metric(n(totals.carbs),'g','碳水')}${metric(n(totals.fat),'g','脂肪')}</div><div class="actions">${button('记录一份食物 '+icon('plus'),'food-new')}</div></div>`+
    (list.length?['早餐','午餐','晚餐','加餐'].filter(meal=>list.some(x=>x.meal===meal)).map(meal=>`<div class="section-head"><h2>${meal}</h2></div><div class="card">${list.filter(x=>x.meal===meal).map(f=>`<div class="row food-row"><div class="grow"><h3>${esc(f.name)}</h3><p>${n(f.grams)} g · ${esc(f.preparation)}</p><p>蛋白质 ${n(f.protein)} · 碳水 ${n(f.carbs)} · 脂肪 ${n(f.fat)} g</p></div><div class="amount">${n(calories(f),0)}<small> kcal</small>${button('删除','food-delete','text-button',`data-id="${f.id}" aria-label="删除${esc(f.name)}"`)}</div></div>`).join('')}</div>`).join(''):`<p class="caption">还没有饮食记录。零摄入表示尚未记录，不代表你今天没有吃东西。</p>`)+
    `<div class="section-head"><h2>喝点水，慢一点</h2></div><div class="card"><div class="row"><span class="tile-icon green">${icon('water')}</span><div class="grow"><h3>${n(water,0)} ml</h3><p>这一天记录的饮水量</p></div></div><div class="actions">${button('＋ 250 ml','water-add','secondary')}${button('查看记录','water-list','text-button')}</div></div><p class="caption">营养数据沿用安卓版 USDA 食物库，按生重 / 熟重区分。数值是参考估算；包装食品请按营养标签录入。</p>`;
}
function renderHistory(){const finished=state().sessions.filter(x=>x.endedAt!==null).sort((a,b)=>b.endedAt-a.endedAt),month=localDate().slice(0,7);const list=finished.filter(x=>historyFilter==='全部'||localDate(x.endedAt).startsWith(month));return header('YOUR PROGRESS','留下每一份努力','不急着比较，先看见自己的坚持。')+`<div class="chips">${['全部','本月'].map(x=>button(x,'filter-history','chip',`data-value="${x}" aria-pressed="${historyFilter===x}"`)).join('')}</div>`+(list.length?list.map(t=>{const r=recap(t);return `<button class="card history-card" data-action="session-detail" data-id="${t.id}"><p class="history-date">${localDate(t.endedAt).replaceAll('-',' / ')}</p><div class="row spread"><h3>${esc(t.title)}</h3><span class="chevron">›</span></div><div class="metrics">${metric(r.done,'组','已完成')}${metric(Math.floor(r.elapsed/60000),'分钟','训练用时')}${metric(n(r.volume),'kg','完成的工作组训练量')}</div></button>`;}).join(''):empty('history','记录，会慢慢积累','完成并保存一次训练后，就能在这里回看。',button('去训练','go-train','secondary')));}
function renderProfile(){const b=[...state().body].sort((a,b)=>b.date.localeCompare(a.date))[0];return header('YOUR SPACE','我的','你的节奏，你的数据。')+`<div class="card"><div class="row"><span class="tile-icon">${icon('train')}</span><div class="grow"><h3>独练 · 网页体验版</h3><p>V${VERSION} · 适用于 iPhone Safari</p></div></div><hr class="rule"><p class="muted">训练、饮食和记录均保存在当前设备的当前浏览器中，无需注册。</p></div><div class="card">${rowAction('share','添加到主屏幕','查看 iPhone 安装步骤','install')}${rowAction('book','本版本可以做什么','训练记录、饮食、体重与本地备份','scope')}</div><div class="section-head"><h2>训练与身体</h2></div><div class="card"><div class="row-setting">${button(`<strong>默认组间休息</strong><small>${state().restSeconds} 秒 · 新建计划时沿用</small>`,'rest-settings','row-link')}</div><div class="row-setting">${button(`<strong>体重记录</strong><small>${b?`${n(b.kg)} kg · ${b.date}`:'尚未记录体重'}</small>`,'body-list','row-link')}</div></div><div class="section-head"><h2>数据与备份</h2></div><div class="card"><div class="row-setting">${button('<strong>导出备份</strong><small>将全部网页记录保存到“文件”中</small>','export','row-link')}</div><div class="row-setting">${button('<strong>从备份恢复</strong><small>仅支持独练网页体验版备份；恢复会替换本机记录</small>','import','row-link')}</div></div><p class="caption">更换手机、清除浏览器数据或切换网址，不会自动迁移记录。Safari 与主屏幕 App 的数据也可能分开，请用备份迁移。</p><div class="card">${rowAction('book','关于与隐私','LiuJiang · 独练','about')}</div>`;}

function openPlan(id=null){editingPlan=id?state().plans.find(x=>x.id===id):null;selection=editingPlan?[...editingPlan.exerciseIds]:[];openSheet(editingPlan?'编辑计划':'创建我的计划','BUILD YOUR ROUTINE',`<form id="plan-form" class="form-stack"><label>计划名称<input name="name" placeholder="例如：周末上肢训练" maxlength="100" required value="${esc(editingPlan?.name||'')}"></label><div class="form-grid"><label>每个动作的组数<input name="sets" inputmode="numeric" type="number" min="1" max="30" step="1" value="${editingPlan?.sets||3}" required></label><label>每组次数<input name="reps" inputmode="numeric" type="number" min="1" max="1000" step="1" value="${editingPlan?.reps||10}" required></label></div><label>组间休息（秒）<input name="rest" type="number" inputmode="numeric" min="0" max="600" step="1" value="${editingPlan?.restSeconds??state().restSeconds}" required></label><label>选择动作 · 点击顺序就是训练顺序<input id="plan-search" type="search" placeholder="搜索动作或肌群" aria-label="在计划中搜索动作"></label><div id="selected-order" class="selected-order"></div><div id="plan-choices" class="select-exercises"></div><p class="caption">开始训练后可以为每一组调整实际重量和次数。</p><div class="form-footer">${button('取消','close','secondary')}<button class="primary" type="submit">保存计划</button></div>${editingPlan?button('删除这个计划','plan-delete','text-button',`data-id="${editingPlan.id}"`):''}</form>`,'plan');renderPlanChoices();}
function renderPlanChoices(){const q=$('#plan-search')?.value.trim()||'';$('#plan-choices').innerHTML=allExercises().filter(e=>`${e.name}${e.muscle}${e.equipment}`.includes(q)).map(e=>`<label class="select-row"><input type="checkbox" data-plan-exercise="${e.id}" ${selection.includes(e.id)?'checked':''}><span class="grow">${esc(e.name)}<small class="muted"> · ${esc(e.muscle)}</small></span></label>`).join('')||'<p class="caption">没有匹配的动作，可先在动作页添加自定义动作。</p>';$('#selected-order').innerHTML=selection.map((key,i)=>button(`${i+1}. ${esc(allExercises().find(e=>e.id===key).name)} ×`,'unselect','',`data-id="${key}" aria-label="移除已选动作${esc(allExercises().find(e=>e.id===key).name)}"`)).join('');}
function openGuide(id){const e=allExercises().find(x=>x.id===id);if(!e){toast('这是历史记录中的动作，资料已不可用');return;}openSheet(e.name,`${e.muscle} / ${e.equipment}`,e.stages?`<span class="pill">${esc(e.focus)}</span><div class="step-list">${e.stages.map(x=>`<div class="step"><p>${esc(x)}</p></div>`).join('')}</div><div class="hint">呼吸配合：${esc(e.breathing)}</div><div class="section-head"><h3>留意这些细节</h3></div><ul class="warning-list">${e.mistakes.map(x=>`<li>${esc(x)}</li>`).join('')}</ul><p class="caption">选择适合自己的负荷和活动范围。此页为动作文字资料，三维动画尚未移植。</p>`:'<p class="muted">这是你创建的动作，可以在训练计划中选择并记录。</p>','guide');}
function openFood(){foodSelected=foods[0].id;openSheet('记录一份食物','FOOD JOURNAL',`<form id="food-form" class="form-stack"><div class="form-grid"><label>餐次<select name="meal">${['早餐','午餐','晚餐','加餐'].map(x=>`<option>${x}</option>`).join('')}</select></label><label>重量（g）<input name="grams" type="number" inputmode="decimal" min="0.1" max="3000" step="0.1" value="100" required></label></div><label>搜索食物<input id="food-search" type="search" placeholder="例如：米饭、熟鸡胸、牛奶"></label><label>选择食物与烹调状态<select name="food" id="food-select">${foodOptions('')}</select></label><div id="food-preview" class="hint"></div><p class="caption">按可食部分称重。米饭选熟重、大米选生重；烹调方式不同，营养会不同。</p><div class="form-footer">${button('取消','close','secondary')}<button class="primary" type="submit">保存到${nutritionDate===localDate()?'今天':nutritionDate}</button></div>${button('包装食品？按标签录入','food-custom','text-button')}</form>`,'food');updateFoodPreview();}
function foodOptions(query){return foods.filter(f=>`${f.name}${f.preparation}${f.keywords}`.toLowerCase().includes(query.toLowerCase())).map(f=>`<option value="${f.id}" ${f.id===foodSelected?'selected':''}>${esc(f.name)} · ${esc(f.preparation)}</option>`).join('');}
function updateFoodPreview(){const spec=foods.find(x=>x.id===$('#food-select')?.value);try{if(!spec)throw new Error('没有找到食物，请换个搜索词，或按标签录入');const grams=decimal($('#food-form [name=grams]').value,.1,3000,'食物重量');const f=foodEntry(spec,grams,'早餐',nutritionDate);$('#food-preview').innerHTML=`${n(calories(f),0)} kcal · 蛋白质 ${n(f.protein)} g · 碳水 ${n(f.carbs)} g · 脂肪 ${n(f.fat)} g<br><small>${esc(spec.source)}，按本次 ${n(grams)} g 计算</small>`;}catch(e){$('#food-preview').textContent=e.message;}}
async function openCustomFood(){if(sheetDirty&&!await ask('切换到按标签录入？当前尚未保存的食物选择将放弃。'))return;openSheet('按营养标签录入','PACKAGED FOOD',`<form id="custom-food-form" class="form-stack"><label>食品名称<input name="name" maxlength="100" required placeholder="例如：原味酸奶"></label><div class="form-grid"><label>餐次<select name="meal">${['早餐','午餐','晚餐','加餐'].map(x=>`<option>${x}</option>`).join('')}</select></label><label>本次食用重量（g）<input name="grams" type="number" inputmode="decimal" min="0.1" max="3000" step="0.1" required></label></div><div class="hint">以下填写包装上<strong>每 100 g</strong>的营养素。保存时自动换算到本次食用重量。</div>${[['protein','蛋白质'],['carbs','碳水'],['fat','脂肪']].map(([key,label])=>`<label>${label}（g / 100 g）<input name="${key}" type="number" inputmode="decimal" min="0" max="100" step="0.01" required></label>`).join('')}<div class="form-footer">${button('取消','close','secondary')}<button class="primary" type="submit">计算并保存</button></div></form>`,'custom-food');}
function openSession(id,justFinished=false){const t=state().sessions.find(x=>x.id===id);if(!t)return;const r=recap(t);openSheet(justFinished?'这一练，已保存':t.title,justFinished?'WELL DONE':'TRAINING RECAP',`${justFinished?'<div class="success-mark">✓</div><h3>'+esc(t.title)+'</h3>':''}<p class="caption">${localDate(t.startedAt)} · ${shortDuration(r.elapsed)}</p><div class="metrics">${metric(r.done,'组','已完成')}${metric(n(r.volume),'kg','工作组训练量')}</div><p class="caption">训练量 = 已完成工作组的重量 × 次数之和。热身组、未完成组不计入；徒手动作不虚构体重负荷。</p>${t.movements.map(m=>`<div class="section-head"><h3>${esc(m.name)}</h3></div>${m.sets.map((x,i)=>`<div class="detail-set"><span>${i+1} · ${x.warmup?'热身':'工作组'}</span><span>${n(x.kg)} kg × ${x.reps}</span><span class="${x.done?'badge-done':''}">${x.done?'完成':'未完成'}</span></div>`).join('')}`).join('')}<div class="actions">${button('完成','close')}${button('删除这次记录','session-delete','secondary',`data-id="${t.id}"`)}</div>`,'recap');}

function formValues(form){return Object.fromEntries(new FormData(form));}
document.addEventListener('submit',async e=>{e.preventDefault();const f=e.target;if(!f.reportValidity())return;const v=formValues(f);try{
  if(f.id==='plan-form'){const p={id:editingPlan?.id||uid(),name:v.name.trim(),exerciseIds:[...selection],sets:decimal(v.sets,1,30,'组数',true),reps:decimal(v.reps,1,1000,'次数',true),restSeconds:decimal(v.rest,0,600,'休息时间',true)};if(!p.exerciseIds.length)throw new Error('请至少选择一个动作');mutate(s=>{const i=s.plans.findIndex(x=>x.id===p.id);if(i>=0)s.plans[i]=p;else s.plans.push(p);});closeSheet(true);navigate('train');toast('计划已保存');}
  if(f.id==='exercise-form'){const x={id:uid(),name:v.name.trim(),muscle:v.muscle,equipment:v.equipment.trim()};mutate(s=>s.custom.push(x));closeSheet(true);toast('动作已加入资料库');}
  if(f.id==='food-form'){const spec=foods.find(x=>x.id===v.food);if(!spec)throw new Error('请选择一个食物');const x=foodEntry(spec,decimal(v.grams,.1,3000,'食物重量'),v.meal,nutritionDate);mutate(s=>s.food.push(x));closeSheet(true);toast('饮食已记录');}
  if(f.id==='custom-food-form'){const spec={name:v.name.trim(),source:'用户包装营养标签',preparation:'按每 100 g 标签计算',...Object.fromEntries(['protein','carbs','fat'].map(k=>[k,decimal(v[k],0,100,'每 100 g 营养素')]))};if(spec.protein+spec.carbs+spec.fat>100.01)throw new Error('每 100 g 中的三种营养素合计不能超过 100 g');const x=foodEntry(spec,decimal(v.grams,.1,3000,'食物重量'),v.meal,nutritionDate);mutate(s=>s.food.push(x));closeSheet(true);toast('已按食用重量计算并保存');}
  if(f.id==='rest-form'){mutate(s=>s.restSeconds=decimal(v.seconds,0,600,'休息时间',true));closeSheet(true);toast('默认休息时间已更新');}
  if(f.id==='body-form'){if(v.date>localDate())throw new Error('体重日期不能在未来');const x={id:uid(),date:v.date,kg:decimal(v.kg,20,500,'体重')};mutate(s=>s.body.push(x));closeSheet(true);toast('体重已保存');}
}catch(ex){error(ex);}});

document.addEventListener('input',e=>{const el=e.target;
  if(el.id==='library-search'){librarySearch=el.value;$('#library-results').innerHTML=libraryCards();}
  if(el.id==='plan-search')renderPlanChoices();
  if(el.id==='food-search'){const html=foodOptions(el.value);$('#food-select').innerHTML=html||'<option value="">没有匹配食物</option>';foodSelected=$('#food-select').value;updateFoodPreview();}
  if(el.closest('#food-form')&&el.name==='grams')updateFoodPreview();
  if(el.dataset.set){try{el.setCustomValidity('');const value=decimal(el.value,0,1000,el.dataset.field==='kg'?'重量':'次数',el.dataset.field==='reps');mutate(s=>{const t=activeSession(s);if(!t||t.pausedAt!==null)throw new Error('请先恢复训练');const x=t.movements.find(m=>m.id===el.dataset.movement)?.sets.find(x=>x.id===el.dataset.set);if(!x||x.done)throw new Error('已完成的组不能编辑');x[el.dataset.field]=value;},false);}catch(ex){el.setCustomValidity(ex.message);toast(ex.message);}}
});
document.addEventListener('change',e=>{const el=e.target;
  if(el.dataset.planExercise){if(el.checked){if(selection.length>=30){el.checked=false;error(new Error('每个计划最多 30 个动作'));return;}selection.push(el.dataset.planExercise);}else selection=selection.filter(k=>k!==el.dataset.planExercise);renderPlanChoices();}
  if(el.id==='food-select'){foodSelected=el.value;updateFoodPreview();}
  if(el.id==='nutrition-date'){if(!el.value||el.value>localDate()){el.value=nutritionDate;toast('请选择今天或更早的日期');return;}nutritionDate=el.value;render();}
  if(el.id==='backup-file')readImport(el.files?.[0]);
});

function requireValidSets(){const invalid=$$('input[data-set]').find(x=>!x.disabled&&!x.checkValidity());if(invalid){invalid.focus();invalid.reportValidity();throw new Error('请先修正训练中的重量或次数');}}
document.addEventListener('click',async e=>{const b=e.target.closest('[data-action]');if(!b||b.disabled)return;const a=b.dataset.action,id=b.dataset.id;try{
  if(a==='close'){await closeSheet();return;}if(a==='reload'){location.reload();return;}if(a==='raw-export'){const raw=localStorage.getItem(STORAGE_KEY);if(raw)download(raw,'独练-原始数据.json');else toast('未找到原始记录');return;}
  if(a.startsWith('go-')){navigate(a.slice(3));return;}if(a==='profile'){navigate('profile');return;}
  if(a==='plan-new')openPlan();if(a==='plan-edit')openPlan(id);
  if(a==='plan-delete'&&await ask('删除这个计划？已经保存的训练记录会保留。')){mutate(s=>s.plans=s.plans.filter(x=>x.id!==id));closeSheet(true);toast('计划已删除');}
  if(a==='unselect'){selection=selection.filter(x=>x!==id);sheetDirty=true;renderPlanChoices();}
  if(a==='start'){mutate(s=>{startSession(s,id,exercises);});scrollTo(0,0);}
  if(['pause','resume','finish','complete-set','add-set','remove-set','warmup','skip-rest'].includes(a))requireValidSets();
  if(a==='pause')mutate(s=>pauseSession(activeSession(s)));if(a==='resume')mutate(s=>resumeSession(activeSession(s)));
  if(a==='skip-rest')mutate(s=>{const t=activeSession(s);t.restDeadline=null;t.restRemainingMs=0;});
  if(a==='complete-set')mutate(s=>toggleSet(activeSession(s),b.dataset.movement,id,activeSession(s).restSeconds??s.restSeconds));
  if(a==='warmup')mutate(s=>{const t=activeSession(s),x=t.movements.find(m=>m.id===b.dataset.movement).sets.find(x=>x.id===id);if(t.pausedAt!==null||x.done)throw new Error('请先恢复训练或取消该组完成状态');x.warmup=!x.warmup;});
  if(a==='add-set')mutate(s=>{const t=activeSession(s);if(t.pausedAt!==null)throw new Error('请先恢复训练');const m=t.movements.find(x=>x.id===id);m.sets.push({...clone(m.sets.at(-1)),id:uid(),done:false});});
  if(a==='remove-set'&&await ask('移除这一组及其记录？'))mutate(s=>{const t=activeSession(s);if(t.pausedAt!==null)throw new Error('请先恢复训练');const m=t.movements.find(x=>x.id===b.dataset.movement);if(m.sets.length<=1)throw new Error('至少保留一组');m.sets=m.sets.filter(x=>x.id!==id);});
  if(a==='finish'){const t=activeSession(state());if(!recap(t).done)throw new Error('还没有完成任何一组。如果是误点，可以选择放弃训练。');if(await ask(`结束并保存这次训练？已完成 ${recap(t).done} 组，未完成组将保留为未完成。`)){const key=t.id;mutate(s=>finishSession(activeSession(s)));openSession(key,true);}}
  if(a==='discard'&&await ask('放弃本次训练？这次的计时和组记录将删除，计划会保留。')){mutate(s=>s.sessions=s.sessions.filter(x=>x.endedAt!==null));toast('本次训练已放弃');}
  if(a==='filter-library'){libraryFilter=b.dataset.value;render();}if(a==='filter-history'){historyFilter=b.dataset.value;render();}
  if(a==='guide')openGuide(id);
  if(a==='exercise-new')openSheet('添加自己的动作','YOUR MOVEMENT',`<form id="exercise-form" class="form-stack"><label>动作名称<input name="name" maxlength="100" required></label><label>肌群<select name="muscle">${['胸部','背部','腿部','肩部','手臂','腹部','小腿','其他'].map(x=>`<option>${x}</option>`).join('')}</select></label><label>器械<input name="equipment" maxlength="100" required placeholder="例如：徒手、哑铃"></label><div class="form-footer">${button('取消','close','secondary')}<button class="primary" type="submit">添加动作</button></div></form>`,'exercise');
  if(a==='food-new')openFood();if(a==='food-custom')await openCustomFood();
  if(a==='food-delete'&&await ask('删除这份饮食记录？')){mutate(s=>s.food=s.food.filter(x=>x.id!==id));toast('饮食记录已删除');}
  if(a==='water-add'){mutate(s=>s.water.push({id:uid(),date:nutritionDate,ml:250}));toast('已记下 250 ml 饮水');}
  if(a==='water-list')openWaterList();
  if(a==='water-delete'&&await ask('删除这条饮水记录？')){mutate(s=>s.water=s.water.filter(x=>x.id!==id));openWaterList();}
  if(a==='session-detail')openSession(id);
  if(a==='session-delete'&&await ask('永久删除这次训练记录？计划和其他记录不受影响。')){mutate(s=>s.sessions=s.sessions.filter(x=>x.id!==id));closeSheet(true);toast('训练记录已删除');}
  if(a==='rest-settings')openSheet('默认组间休息','TRAINING SETTINGS',`<form id="rest-form" class="form-stack"><label>休息时间（秒）<input name="seconds" type="number" inputmode="numeric" min="0" max="600" step="1" value="${state().restSeconds}" required></label><p class="caption">0 表示不启动组间倒计时。网页不会在后台提醒。</p><div class="form-footer">${button('取消','close','secondary')}<button class="primary" type="submit">保存设置</button></div></form>`,'rest');
  if(a==='body-list')openBodyList();if(a==='body-new')openSheet('记下今天的体重','BODY JOURNAL',`<form id="body-form" class="form-stack"><label>日期<input name="date" type="date" max="${localDate()}" value="${localDate()}" required></label><label>体重（kg）<input name="kg" type="number" inputmode="decimal" min="20" max="500" step="0.01" required></label><div class="form-footer">${button('取消','close','secondary')}<button class="primary" type="submit">保存体重</button></div></form>`,'body');
  if(a==='body-delete'&&await ask('删除这条体重记录？')){mutate(s=>s.body=s.body.filter(x=>x.id!==id));openBodyList();}
  if(a==='export')await exportBackup();if(a==='import')openImport();if(a==='import-confirm'){if(await ask('确认用这份备份替换当前网页的全部记录？此操作不能撤销，请先备份现有记录。')){store.replace(sheetPayload);closeSheet(true);render();toast('备份已恢复');}}
  if(a==='install')showInstall();if(a==='scope')showScope();if(a==='about')showAbout();
  if(a==='update'){if(activeSession(state())||sheetDirty){toast('请先结束训练并保存编辑，再更新页面');return;}updateWorker?.postMessage({type:'ACTIVATE'});}
}catch(ex){error(ex);}});

function openWaterList(){const list=state().water.filter(x=>x.date===nutritionDate);openSheet('饮水记录',nutritionDate,list.length?list.map((w,i)=>`<div class="row spread detail-set"><span>第 ${i+1} 次 · ${w.ml} ml</span>${button('删除','water-delete','text-button',`data-id="${w.id}"`)}</div>`).join(''):'<p class="muted">这一天还没有记录饮水。</p>','water');}
function openBodyList(){openSheet('体重记录','BODY JOURNAL',button('＋ 记录体重','body-new','primary full')+`<div class="section-head"><h3>最近记录</h3></div>`+([...state().body].sort((a,b)=>b.date.localeCompare(a.date)).map(x=>`<div class="row spread detail-set"><span>${x.date}</span><strong>${n(x.kg)} kg</strong>${button('删除','body-delete','text-button',`data-id="${x.id}"`)}</div>`).join('')||'<p class="muted">还没有体重记录。</p>'),'body-list');}
function download(text,filename){const blob=new Blob([text],{type:'application/json'}),url=URL.createObjectURL(blob),a=document.createElement('a');a.href=url;a.download=filename;document.body.append(a);a.click();a.remove();setTimeout(()=>URL.revokeObjectURL(url),60000);}
async function exportBackup(){const text=JSON.stringify(state(),null,2),name=`独练-网页备份-${localDate()}.json`,file=new File([text],name,{type:'application/json'});if(navigator.canShare?.({files:[file]})){try{await navigator.share({files:[file],title:'独练网页备份'});toast('分享已打开，请确认已存储到“文件”');return;}catch(e){if(e.name==='AbortError')return;}}download(text,name);toast('备份下载已发起，请确认“文件”中已保存');}
function openImport(){openSheet('从备份恢复','RESTORE YOUR DATA',`<p class="muted">请选择独练网页体验版导出的 JSON 文件。恢复会替换本机网页数据，不会合并，也不会影响安卓 App。</p><p class="caption">建议先导出当前记录。最多 5 MB，不支持安卓版本的备份文件。</p>${button('先导出当前备份','export','secondary full')}<label style="margin-top:22px">选择备份文件<input id="backup-file" type="file" accept=".json,application/json"></label><div id="import-preview"></div>`,'import');}
async function readImport(file){if(!file)return;const input=$('#backup-file');try{if(file.size>MAX_BACKUP_BYTES)throw new Error('文件超过 5 MB');const parsed=parseBackup(await file.text(),exercises);if(!sheet.open||sheetKind!=='import'||$('#backup-file')!==input||input.files?.[0]!==file)return;sheetPayload=parsed;$('#import-preview').innerHTML=`<div class="hint" style="margin-top:18px">检测到 ${parsed.plans.length} 个计划、${parsed.sessions.length} 次训练、${parsed.food.length} 条饮食记录。</div><div class="actions">${button('替换并恢复记录','import-confirm')}</div>`;$('#sheet-error').hidden=true;}catch(e){if(!sheet.open||sheetKind!=='import'||$('#backup-file')!==input)return;sheetPayload=null;$('#import-preview').innerHTML='';error(e);}}
function showInstall(){openSheet('像 App 一样打开独练','ADD TO HOME SCREEN',`<div class="row"><img src="./icon-180.png" width="60" height="60" style="border-radius:16px" alt="独练图标"><div><h3>独练</h3><p class="muted">iPhone 网页体验版 · V${VERSION}</p></div></div><ol class="install-list"><li>用 <strong>Safari 浏览器</strong>打开这个网址。如果在微信内，请复制链接后到 Safari 打开。</li><li>点击 Safari 的<strong>分享按钮</strong> ${icon('share')}，选择<strong>“添加到主屏幕”</strong>。</li><li>如果出现<strong>“作为网页 App 打开”</strong>，保持开启，再点击“添加”。</li><li>回到主屏幕，点“独练”图标开始使用。</li></ol><div class="hint">建议先添加到主屏幕，再开始正式记录。Safari 标签页与主屏幕 App 的记录可能分开；已有数据请先导出，再到新入口恢复。</div><p class="caption">第一次需要联网打开。离线缓存准备好后，可继续使用已缓存页面。此体验版无需安装 APK、描述文件或购买开发者会员。</p>`,'install');}
function showScope(){openSheet('这一版，先体验这些','WEB TRIAL V1.0',`<div class="step-list"><div class="step"><p><strong>训练闭环</strong><br>创建、编辑计划；逐组填写重量与次数；暂停、继续、放弃、完成并回顾。</p></div><div class="step"><p><strong>饮食与身体</strong><br>47 种食物的营养参考、按重量计算、包装标签录入、饮水和体重记录。</p></div><div class="step"><p><strong>自己的记录</strong><br>本地保存、网页 JSON 备份与恢复；40 个动作的文字要点。</p></div></div><div class="hint">暂不包含 GPS 路线、后台定位、自动计步、三维动作动画及安卓数据导入。不是完整原生 iOS 版本；安卓和网页不会自动同步。</div>`,'scope');}
function showAbout(){openSheet('关于独练','MAKE ROOM FOR YOURSELF',`<h3>按自己的节奏，练好每一次。</h3><p class="caption">独练网页体验版 V${VERSION}<br>作者：LiuJiang<br>联系方式：<a href="tel:17549207425">17549207425</a></p><hr class="rule"><h3>隐私与数据</h3><p class="legal" style="margin-top:12px">本体验版的训练、体重、饮食记录只写入当前浏览器的本地存储，不上传这些记录，不接入广告、分析统计，也不请求定位或运动权限。导出文件包含你的记录，请自行保管。清除网站数据、浏览器回收存储或更换设备可能导致本地记录丢失，请定期备份。托管平台可能保留提供网站服务所需的访问日志（例如 IP 和请求时间），不等于上传训练记录。</p><hr class="rule"><h3>数据来源</h3><p class="legal" style="margin-top:12px">食物库沿用安卓版的 USDA SR Legacy 数据和中文烹调状态说明；每份能量由蛋白质、碳水、脂肪分别按 4、4、9 kcal/g 计算，为估算值。动作文字沿用独练现有资料。网页版与安卓版本分别维护。</p>`,'about');}
function updateTimers(){if(fatal||route!=='train')return;const t=activeSession(state());if(!t)return;const time=$('[data-timer]');if(time)time.textContent=duration(elapsed(t));const rest=$('[data-rest]'),box=$('[data-rest-box]');if(rest&&box){const ms=remainingRest(t);box.hidden=ms<=0;rest.textContent=`${Math.ceil(ms/1000)}s${t.pausedAt!==null?' · 暂停':''}`;}}
setInterval(updateTimers,1000);document.addEventListener('visibilitychange',()=>{if(!document.hidden)updateTimers();});
render();
if('serviceWorker' in navigator && !fatal){
  navigator.serviceWorker.register('./sw.js').then(reg=>{
    const showUpdate=worker=>{updateWorker=worker;$('#notice').innerHTML='有新版本可用。保存编辑、结束训练后可更新。'+button('更新页面','update','text-button');$('#notice').hidden=false;};
    if(reg.waiting)showUpdate(reg.waiting);
    reg.addEventListener('updatefound',()=>{const worker=reg.installing;worker?.addEventListener('statechange',()=>{if(worker.state==='installed'){if(navigator.serviceWorker.controller)showUpdate(worker);else toast('离线页面已准备好');}});});
  }).catch(()=>toast('离线缓存尚未就绪，请保持联网使用'));
  let refreshing=false;navigator.serviceWorker.addEventListener('controllerchange',()=>{if(!refreshing&&updateWorker){refreshing=true;location.reload();}});
}
