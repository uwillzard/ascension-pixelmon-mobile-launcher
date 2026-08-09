(() => {
  const $ = (s) => document.querySelector(s);
  const $$ = (s) => [...document.querySelectorAll(s)];
  const android = window.AscensionAndroid || null;
  let state = {nick:'',engineReady:true,minecraftInstalled:false,neoforgeInstalled:false,prepared:false,busy:false};
  let toastTimer;

  function call(name, ...args){
    try { if (android && typeof android[name] === 'function') return android[name](...args); }
    catch(e) { toast('Falha na ponte Android: '+e.message, 'error'); }
  }
  function parse(v){ try { return typeof v === 'string' ? JSON.parse(v) : v; } catch { return {}; } }
  function toast(msg, kind=''){
    const el=$('#toast'); el.textContent=msg; el.className='toast '+kind; el.hidden=false;
    clearTimeout(toastTimer); toastTimer=setTimeout(()=>el.hidden=true,3200);
  }
  function render(){
    const nick=state.nick||'';
    $('#nickDisplay').textContent=nick||'Escolher Nick';
    $('#nickInput').value=nick;
    $('#dockHint').textContent=nick ? `Treinador: ${nick}` : 'Escolha seu Nick para começar.';
    const stages=[];
    stages.push(state.minecraftInstalled?'Minecraft ✓':'Minecraft 1.21.1');
    stages.push(state.neoforgeInstalled?'NeoForge ✓':'NeoForge 21.1.200');
    stages.push(state.prepared?'Modpack ✓':'Modpack');
    $('#playSubtitle').textContent=!nick?'Escolha seu Nick':(state.busy?'Preparando...':stages.join(' · '));
    $('#playButton').disabled=!!state.busy;
    $('#prepareButton').disabled=!!state.busy;
    $('#engineState').textContent='Pojav integrado';
    $('#engineSetupTitle').textContent='Pojav/Amethyst integrado';
    $('#engineSetupText').textContent='Minecraft Java roda dentro do próprio Ascension Launcher. O 1.21.1, Java 21 e NeoForge são instalados automaticamente.';
  }
  function refresh(){ const raw=call('getState'); if(raw){ state={...state,...parse(raw)}; render(); } }
  function openNick(){ $('#nickMessage').hidden=true; $('#nickModal').hidden=false; setTimeout(()=>$('#nickInput').focus(),60); }
  function closeNick(){ $('#nickModal').hidden=true; }
  function saveNick(){
    const nick=$('#nickInput').value.trim();
    if(!/^[A-Za-z0-9_]{3,16}$/.test(nick)){ $('#nickMessage').textContent='Use de 3 a 16 caracteres: letras, números ou _.'; $('#nickMessage').hidden=false; return; }
    call('saveNick',nick); state.nick=nick; render(); closeNick(); toast('Nick salvo: '+nick,'success');
  }
  function setProgress(message,p){
    $('#progressWrap').hidden=false; $('#progressLabel').textContent=message||'Preparando...';
    if(typeof p==='number'){ const x=Math.max(0,Math.min(100,p)); $('#progressPercent').textContent=x+'%'; $('#progressBar').style.width=x+'%'; }
    $('#footerText').textContent=message||'Preparando...';
  }
  window.AscensionMobile={
    onState(raw){ state={...state,...parse(raw)}; render(); },
    onEvent(raw){
      const e=parse(raw);
      if(e.type==='progress'){ state.busy=true; setProgress(e.message,e.progress); render(); }
      else if(e.type==='done'){ setProgress(e.message,100); state.prepared=true; state.busy=false; render(); toast(e.message,'success'); setTimeout(()=>$('#progressWrap').hidden=true,1800); }
      else if(e.type==='error'){ state.busy=false; $('#footerText').textContent=e.message; render(); toast(e.message,'error'); }
      else if(e.type==='needNick'){ state.busy=false; openNick(); toast(e.message,'error'); }
      else if(e.type==='nick'){ state.nick=e.message; render(); }
      else { if(e.message) toast(e.message); }
    },
    onServer(raw){ const s=parse(raw); $('#serverStatus').textContent=s.online?(s.ping>=0?`Online · ${s.ping} ms`:'Online'):'Offline'; $('#statusDot').className='status-dot '+(s.online?'online':'offline'); }
  };
  $$('.rail-button').forEach(btn=>btn.addEventListener('click',()=>{ $$('.rail-button').forEach(x=>x.classList.remove('active')); btn.classList.add('active'); $$('.page').forEach(x=>x.classList.remove('active')); $('#page-'+btn.dataset.tab).classList.add('active'); }));
  $$('[data-action="site"]').forEach(x=>x.addEventListener('click',()=>call('openWebsite')));
  $$('[data-action="discord"]').forEach(x=>x.addEventListener('click',()=>call('openDiscord')));
  $('#nickChip').addEventListener('click',openNick); $('#closeNick').addEventListener('click',closeNick); $('#saveNick').addEventListener('click',saveNick);
  $('#nickInput').addEventListener('keydown',e=>{if(e.key==='Enter')saveNick()});
  $('#nickModal').addEventListener('click',e=>{if(e.target===$('#nickModal'))closeNick()});
  $('#prepareButton').addEventListener('click',()=>call('prepare'));
  $('#playButton').addEventListener('click',()=>{ if(!state.nick) openNick(); else call('play'); });
  $('#repairButton').addEventListener('click',()=>call('repair'));
  $('#checkServerButton').addEventListener('click',()=>call('checkServer'));
  document.addEventListener('visibilitychange',()=>{if(!document.hidden)refresh()});
  refresh(); call('checkServer');
})();
