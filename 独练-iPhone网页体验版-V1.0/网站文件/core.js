export const VERSION = '1.0';
export const STORAGE_KEY = 'duolian-web-v1';
export const MAX_BACKUP_BYTES = 5_000_000;
export const uid = () => globalThis.crypto.randomUUID();
export const clone = x => JSON.parse(JSON.stringify(x));
export const localDate = (time = Date.now()) => {
  const d = new Date(time);
  return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;
};
export const emptyState = () => ({format:'duolian-web', version:1, plans:[], custom:[], sessions:[], food:[], water:[], body:[], restSeconds:60});
const need = (ok, msg) => { if (!ok) throw new Error(msg); };
const number = (n, min, max, label, integer = false) => need(typeof n === 'number' && Number.isFinite(n) && n >= min && n <= max && (!integer || Number.isInteger(n)), `${label}无效`);
const label = (v, max = 100) => need(typeof v === 'string' && v.trim().length > 0 && v.length <= max, '名称或文字内容无效');
const id = v => need(typeof v === 'string' && /^[a-zA-Z0-9_-]{1,80}$/.test(v), '记录编号无效');
const date = v => { need(typeof v === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(v), '日期格式无效'); const d = new Date(v+'T12:00:00Z'); need(Number.isFinite(d.getTime()) && d.toISOString().slice(0,10) === v, '日期不存在'); };
const unique = (arr, max) => { need(Array.isArray(arr) && arr.length <= max, '记录数量超出范围'); arr.forEach(x => { need(x && typeof x === 'object', '记录无效'); id(x.id); }); need(new Set(arr.map(x=>x.id)).size === arr.length, '存在重复记录'); };
const millis = n => number(n, 1, 10_000_000_000_000, '时间', true);
const bool = v => need(typeof v === 'boolean', '状态无效');
export function decimal(text, min, max, labelText, integer = false) {
  const s = String(text).trim();
  need(/^(?:\d+(?:\.\d*)?|\.\d+)$/.test(s), `请填写有效的${labelText}`);
  const n = Number(s); number(n, min, max, labelText, integer); return n;
}
export function validate(s, catalog) {
  need(s?.format === 'duolian-web' && s.version === 1, '请选择独练网页体验版的备份文件；暂不支持安卓备份');
  unique(s.custom, 200); s.custom.forEach(e => { label(e.name); label(e.muscle); label(e.equipment); });
  const ids = [...catalog.map(e=>e.id),...s.custom.map(e=>e.id)];
  need(new Set(ids).size === ids.length, '动作编号重复');
  unique(s.plans, 200);
  s.plans.forEach(p => {
    label(p.name); number(p.sets,1,30,'组数',true); number(p.reps,1,1000,'次数',true); number(p.restSeconds,0,600,'休息时间',true);
    need(Array.isArray(p.exerciseIds) && p.exerciseIds.length > 0 && p.exerciseIds.length <= 30 && new Set(p.exerciseIds).size === p.exerciseIds.length && p.exerciseIds.every(k=>ids.includes(k)), '计划中的动作不存在或重复');
  });
  number(s.restSeconds,0,600,'休息时间',true);
  unique(s.sessions, 2000); need(s.sessions.filter(x=>x.endedAt === null).length <= 1, '只能有一个进行中的训练');
  s.sessions.forEach(t => {
    label(t.title); millis(t.startedAt); number(t.pausedMs,0,10_000_000_000_000,'暂停时长',true);
    if(t.restSeconds !== undefined) number(t.restSeconds,0,600,'训练休息时间',true);
    if (t.endedAt !== null) { millis(t.endedAt); need(t.endedAt >= t.startedAt, '训练结束时间无效'); }
    if (t.pausedAt !== null) { millis(t.pausedAt); need(t.endedAt === null && t.pausedAt >= t.startedAt, '训练暂停状态无效'); }
    need(t.pausedMs <= (t.endedAt ?? t.pausedAt ?? 10_000_000_000_000) - t.startedAt, '训练暂停时长无效');
    if (t.restDeadline !== null) millis(t.restDeadline);
    number(t.restRemainingMs,0,600_000,'剩余休息',true);
    need(t.pausedAt === null || t.restDeadline === null, '暂停时休息状态无效');
    need(t.endedAt === null || (t.pausedAt === null && t.restDeadline === null && t.restRemainingMs === 0), '结束训练的状态无效');
    unique(t.movements,30); need(t.movements.length > 0, '训练中没有动作');
    t.movements.forEach(m => { label(m.name); id(m.exerciseId); unique(m.sets,50); need(m.sets.length > 0,'动作没有组数'); m.sets.forEach(x=>{number(x.kg,0,1000,'重量'); number(x.reps,0,1000,'次数',true); bool(x.done); bool(x.warmup);}); });
  });
  unique(s.food,5000); s.food.forEach(f=>{
    label(f.name); date(f.date); need(['早餐','午餐','晚餐','加餐'].includes(f.meal), '餐次无效');
    number(f.grams,.1,3000,'食物重量'); ['protein','carbs','fat'].forEach(k=>number(f[k],0,1000,'营养素'));
    need(f.protein+f.carbs+f.fat <= f.grams*1.01,'营养素不能超过食物重量'); label(f.source,200); label(f.preparation,100);
  });
  unique(s.water,5000); s.water.forEach(w=>{date(w.date); number(w.ml,1,10000,'饮水量',true);});
  unique(s.body,2000); s.body.forEach(b=>{date(b.date);number(b.kg,20,500,'体重');});
  return s;
}
export function parseBackup(text, catalog) {
  need(new TextEncoder().encode(text).length <= MAX_BACKUP_BYTES,'备份文件过大（最多 5 MB）');
  let s; try { s = JSON.parse(text); } catch { throw new Error('文件不是有效的 JSON 备份'); }
  return validate(s,catalog);
}
export const activeSession = s => s.sessions.find(t=>t.endedAt === null);
export const elapsed = (t, now = Date.now()) => Math.max(0,(t.endedAt ?? t.pausedAt ?? now)-t.startedAt-t.pausedMs);
export const remainingRest = (t, now = Date.now()) => t.pausedAt !== null ? t.restRemainingMs : Math.max(0,(t.restDeadline ?? now)-now);
export const recap = (t, now) => ({done:t.movements.flatMap(m=>m.sets).filter(x=>x.done).length, total:t.movements.flatMap(m=>m.sets).length,
  volume:t.movements.flatMap(m=>m.sets).filter(x=>x.done && !x.warmup).reduce((a,x)=>a+x.kg*x.reps,0), elapsed:elapsed(t,now)});
export function startSession(state, planId, catalog, now = Date.now(), makeId = uid) {
  need(!activeSession(state),'已有训练进行中，请先继续或结束');
  const p = state.plans.find(x=>x.id===planId); need(p,'计划不存在');
  const all = [...catalog,...state.custom];
  const t = {id:makeId(),title:p.name,startedAt:now,endedAt:null,pausedAt:null,pausedMs:0,restDeadline:null,restRemainingMs:0,restSeconds:p.restSeconds,
    movements:p.exerciseIds.map(key=>{ const e=all.find(x=>x.id===key); need(e,'动作不存在'); return {id:makeId(),exerciseId:key,name:e.name,sets:Array.from({length:p.sets},()=>({id:makeId(),kg:0,reps:p.reps,warmup:false,done:false}))};})};
  state.sessions.push(t); return t;
}
export function pauseSession(t, now = Date.now()) { need(t && t.endedAt===null,'训练已结束'); if(t.pausedAt !== null)return; t.restRemainingMs=remainingRest(t,now);t.restDeadline=null;t.pausedAt=now; }
export function resumeSession(t, now = Date.now()) { need(t && t.endedAt===null,'训练已结束'); if(t.pausedAt===null)return; t.pausedMs+=Math.max(0,now-t.pausedAt);t.pausedAt=null;t.restDeadline=t.restRemainingMs>0?now+t.restRemainingMs:null;t.restRemainingMs=0; }
export function finishSession(t, now = Date.now()) { need(t && t.endedAt===null,'训练已结束');need(recap(t).done>0,'至少完成一组后才能保存；也可以选择放弃训练');if(t.pausedAt!==null)t.pausedMs+=Math.max(0,now-t.pausedAt);t.endedAt=now;t.pausedAt=null;t.restDeadline=null;t.restRemainingMs=0; }
export function toggleSet(t, movementId, setId, restSeconds, now = Date.now()) { need(t && t.endedAt===null && t.pausedAt===null,'请先恢复训练');const x=t.movements.find(m=>m.id===movementId)?.sets.find(x=>x.id===setId);need(x,'该组不存在'); x.done=!x.done;t.restDeadline=x.done && restSeconds>0?now+restSeconds*1000:null;t.restRemainingMs=0; }
export function foodEntry(spec, grams, meal, day, makeId = uid) { number(grams,.1,3000,'食物重量');return {id:makeId(),name:spec.name,date:day,meal,grams,protein:spec.protein*grams/100,carbs:spec.carbs*grams/100,fat:spec.fat*grams/100,source:spec.source,preparation:spec.preparation}; }
export const calories = f => f.protein*4+f.carbs*4+f.fat*9;

// Commit to storage BEFORE replacing live UI state. A quota failure never looks like a saved record.
export class Store {
  constructor(storage,catalog) { this.storage=storage; this.catalog=catalog;this.raw=storage.getItem(STORAGE_KEY);this.value=this.raw===null?emptyState():parseBackup(this.raw,catalog); }
  commit(change) {
    need(this.storage.getItem(STORAGE_KEY)===this.raw,'另一个页面修改了记录。请刷新后再继续，避免覆盖新记录。');
    const next=clone(this.value);change(next);validate(next,this.catalog);
    const raw=JSON.stringify(next);need(new TextEncoder().encode(raw).length<=MAX_BACKUP_BYTES,'记录空间已满，请先导出备份并整理旧记录');
    try { this.storage.setItem(STORAGE_KEY,raw); } catch { throw new Error('未能保存：浏览器存储不可用或空间不足。请先导出备份。'); }
    this.value=next;this.raw=raw;return next;
  }
  replace(next) { return this.commit(s=>{for(const k of Object.keys(s))delete s[k];Object.assign(s,clone(next));}); }
}
